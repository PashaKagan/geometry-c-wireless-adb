package com.pashakagan.geelyremote

import dadb.AdbKeyPair
import dadb.Dadb
import java.io.File

/**
 * Thin wrapper around dadb (vendored under package `dadb`, patched so it tolerates
 * old adbd on Android 5.x that omits the "features" field and lacks shell_v2).
 * Talks to the head unit's adbd over TCP 5555 as the `shell` user. No root.
 *
 * v0.2: persist an ADB key in the app's own data dir.
 * v0.3: accept ip[:port]; verify the connection with a real command.
 * v0.5: force legacy "shell:" when shell_v2 is unavailable (done in vendored dadb).
 * v0.6: auto-reconnect+retry dropped commands; dedicated long-timeout connection for
 *       screen recording so it isn't killed by the normal socket timeout (0-KB bug).
 *
 * v0.12: long-running commands (screenrecord, logcat -f) run DETACHED from a normal
 *       one-shot shell on the main connection instead of on a 2nd simultaneous connection.
 *
 * All methods here BLOCK -- call them off the main thread.
 */
object Adb {

    @Volatile private var dadb: Dadb? = null
    @Volatile private var lastIp: String? = null
    /** True when adbd itself runs as root (uid 0). */
    @Volatile var isRoot = false; private set


    val isConnected: Boolean get() = dadb != null

    private val keyDir = File("/data/data/com.pashakagan.geelyremote/files")

    private fun keyPair(): AdbKeyPair {
        val priv = File(keyDir, "adbkey")
        val pub = File(keyDir, "adbkey.pub")
        if (!priv.exists() || !pub.exists()) AdbKeyPair.generate(priv, pub)
        return AdbKeyPair.read(priv, pub)
    }

    private fun hostOf(ip: String) = ip.trim().substringBefore(":").trim().ifEmpty { ip.trim() }
    private fun portOf(ip: String) = ip.trim().substringAfter(":", "5555").trim().toIntOrNull() ?: 5555

    private fun build(ip: String, socketTimeout: Int): Dadb =
        Dadb.create(hostOf(ip), portOf(ip), keyPair(), connectTimeout = 10000, socketTimeout = socketTimeout)

    /** Connect and verify with a real command. Returns the model. Throws on failure. */
    fun connect(ip: String): String {
        close()
        val d = build(ip, 20000)
        try {
            val model = d.shell("getprop ro.product.model").output.trim()
            // Since the 2026-11-16 root install, the unit's adbd runs as uid 0. Check it, so the
            // terminal can fall back to su if adbd ever comes up unpatched.
            isRoot = d.shell("id").output.contains("uid=0")
            dadb = d; lastIp = ip
            return model
        } catch (e: Exception) {
            runCatching { d.close() }
            throw e
        }
    }

    private fun reconnect(): Boolean {
        val ip = lastIp ?: return false
        return try { dadb = build(ip, 20000); true } catch (_: Exception) { false }
    }

    /** Run block against the live connection; on any failure, reconnect once and retry. */
    private fun <T> withRetry(block: (Dadb) -> T): T {
        val d = dadb ?: throw IllegalStateException("Not connected -- tap Connect first.")
        return try {
            block(d)
        } catch (e: Exception) {
            if (reconnect()) {
                val d2 = dadb ?: throw e
                block(d2)
            } else throw e
        }
    }

    private fun format(r: dadb.AdbShellResponse): String = buildString {
        append(r.output)
        if (r.errorOutput.isNotBlank()) append(r.errorOutput)
        if (r.exitCode != 0) append("\n[exit ${r.exitCode}]")
    }

    /** Shell with auto-reconnect+retry. */
    fun shell(cmd: String): String = withRetry { d -> format(d.shell(cmd)) }

    /** Shell with NO retry (for long/stateful commands that must not be re-run). */
    fun shellOnce(cmd: String): String {
        val d = dadb ?: throw IllegalStateException("Not connected -- tap Connect first.")
        return format(d.shell(cmd))
    }

    /** Install via a dedicated long-timeout connection: the APK push can exceed the normal
     *  socket timeout on a slow/flaky link (the "Connection lost while writing" error). */
    /** Install (or update in place, -r). Throws with pm's verdict if it didn't succeed. */
    fun install(apk: File) {
        val r = withLongConnection(300000) { it.install(apk, "-r") }
        runCatching { shell("rm -f \"/data/local/tmp/${apk.name}\"") }
        if (r is dadb.InstallResult.Failure) throw IllegalStateException(r.toString().take(400))
    }

    /**
     * v0.15: this unit's adbd REFUSES a second simultaneous TCP connection ("Connection
     * handshake failed") -- the real reason recordings/pulls on a "dedicated" connection never
     * worked. So for slow transfers we SWAP: close the main connection, open one long-timeout
     * connection, do the work, then restore the normal main connection.
     */
    @Synchronized
    private fun <T> withLongConnection(timeoutMs: Int, block: (Dadb) -> T): T {
        val ip = lastIp ?: throw IllegalStateException("Not connected -- tap Connect first.")
        runCatching { dadb?.close() }; dadb = null
        Thread.sleep(300)
        try {
            val d = build(ip, timeoutMs)
            try { return block(d) } finally { runCatching { d.close() } }
        } finally {
            Thread.sleep(300)
            if (!reconnect()) { Thread.sleep(1500); reconnect() }
        }
    }

    fun pull(remotePath: String, dst: File) = withRetry { d -> d.pull(dst, remotePath); Unit }

    /**
     * Terminal: run an arbitrary (multi-line) script on the unit and return its combined output.
     * The script is pushed to /data/local/tmp/claude_cmd.sh and run with sh, so quoting and
     * multi-line pastes work and there's no command-line length limit. Runs on one long-timeout
     * connection (up to [timeoutMs] of silence). adbd runs as root on this unit since the
     * 2026-11-16 root install, so commands run as root. The last line is "[exit N]".
     */
    fun runScript(script: String, timeoutMs: Int = 300000): String {
        val local = File.createTempFile("claude_cmd", ".sh")
        try {
            local.writeText(script.replace("\r\n", "\n").trimEnd() + "\n")
            return withLongConnection(timeoutMs) { d ->
                val push = d.push(local, "/data/local/tmp/claude_cmd.sh")
                if (push is dadb.SyncResult.Failure) throw IllegalStateException("couldn't push the script: ${push.reason}")
                val run = if (isRoot) "sh /data/local/tmp/claude_cmd.sh 2>&1"
                          else "su -c 'sh /data/local/tmp/claude_cmd.sh' 2>&1"
                format(d.shell("$run; echo \"[exit \$?]\""))
            }
        } finally { local.delete() }
    }

    /**
     * Copy a file or a whole folder (recursively, up to 3 levels / 300 files) from the unit into
     * [localRoot]. Returns the local files with their path relative to [remote]. One long-timeout
     * connection for the whole transfer. Root adbd can read anywhere.
     */
    fun pullTree(remote: String, localRoot: File): List<Pair<String, File>> = withLongConnection(120000) { d ->
        val out = mutableListOf<Pair<String, File>>()
        fun sh(c: String) = d.shell(c).let { it.output + it.errorOutput }
        // "name" is everything after the HH:MM time column (toolbox ls -l), so spaces survive.
        fun entries(dir: String): List<Pair<Boolean, String>> = sh("ls -l \"$dir\" 2>&1").lines().mapNotNull { line ->
            val t = line.trim().split(Regex("\\s+"))
            val ti = t.indexOfFirst { it.matches(Regex("\\d{1,2}:\\d{2}")) }
            if (ti < 0 || ti == t.size - 1 || line.isEmpty()) return@mapNotNull null
            when (line[0]) { 'd' -> true; '-' -> false; else -> null }?.let { isDir -> isDir to t.drop(ti + 1).joinToString(" ") }
        }
        fun walk(dir: String, rel: String, depth: Int) {
            for ((isDir, name) in entries(dir)) {
                if (out.size >= 300) return
                val r = if (rel.isEmpty()) name else "$rel/$name"
                if (isDir) { if (depth < 3) walk("$dir/$name", r, depth + 1) }
                else {
                    val f = File(localRoot, r).apply { parentFile?.mkdirs() }
                    if (d.pull(f, "$dir/$name") is dadb.SyncResult.Success) out += r to f
                }
            }
        }
        val path = remote.trimEnd('/')
        val kind = sh("ls -ld \"$path\" 2>&1").trim()
        when {
            kind.startsWith("d") -> walk(path, "", 0)
            kind.startsWith("-") -> {
                val name = path.substringAfterLast('/')
                val f = File(localRoot, name)
                val r = d.pull(f, path)
                if (r is dadb.SyncResult.Failure) throw IllegalStateException("couldn't copy $path: ${r.reason}")
                out += name to f
            }
            else -> throw IllegalStateException("not found on the unit: $path (${kind.take(120)})")
        }
        out
    }

    /** Find PIDs of a process by name via `ps` (no `pidof` on this unit). Robust to column
     *  order by taking the first numeric token on each matching line. */
    private fun pidsOf(name: String): List<Int> {
        val out = runCatching { shell("ps -A 2>/dev/null") }.getOrNull()?.takeIf { it.lineSequence().count() > 2 }
            ?: runCatching { shell("ps") }.getOrDefault("")
        return out.lineSequence()
            .filter { it.contains(name) }
            .mapNotNull { line -> line.trim().split(Regex("\\s+")).firstOrNull { it.toIntOrNull() != null }?.toIntOrNull() }
            .toList()
    }

    /** Kill a process we started, preferring the exact PID we stashed in a file at launch
     *  (deterministic), falling back to a name match. */
    private fun killStarted(pidFile: String, nameFallback: String, sigint: Boolean) {
        val sig = if (sigint) "-2 " else ""
        val pid = runCatching { shell("cat $pidFile 2>/dev/null").trim() }.getOrNull()
        if (!pid.isNullOrEmpty() && pid.all { it.isDigit() }) {
            runCatching { shell("kill $sig$pid") }
        } else {
            for (p in pidsOf(nameFallback)) runCatching { shell("kill $sig$p") }
        }
    }

    /** Pull over a dedicated long-timeout connection (big files: recordings, traces). */
    fun pullLong(remotePath: String, dst: File) = withLongConnection(120000) { it.pull(dst, remotePath); Unit }

    private fun alive(pid: String): Boolean =
        runCatching { shell("kill -0 $pid 2>/dev/null && echo ALIVE || echo DEAD") }.getOrDefault("").contains("ALIVE")

    /**
     * Start a long-running command DETACHED on the main connection and return its PID.
     * v0.12: the old approach (a 2nd simultaneous ADB connection holding the command open)
     * produced nothing on this unit -- not even the pid file. Now we background it from a
     * normal one-shot shell: `trap '' HUP` survives exec, so the process keeps running after
     * adbd closes the (pty) stream; stdin/stdout are redirected to files so it never touches
     * the dead pty. `$!` is the PID of the backgrounded sh, which exec turns into the real one.
     */
    private fun startDetached(cmd: String, logFile: String, pidFile: String): String {
        // v0.15: ignore SIGHUP in the PARENT shell before backgrounding, so the child inherits it
        // from the moment it's forked. (v0.12-0.14 set the trap inside the child -- a race: when
        // the parent exited at once, adbd's pty hangup killed the child before its trap ran,
        // which is why screenrecord "died" with no output in most attempts.) sleep 1 keeps the
        // stream open a moment longer as extra margin.
        val out = shellOnce("trap '' 1; $cmd </dev/null >$logFile 2>&1 & echo \$! > $pidFile; sleep 1; cat $pidFile")
        return out.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() && it.all(Char::isDigit) } ?: ""
    }

    /** Safe fallback: a single PNG via screencap (no video encoder involved). */
    fun screenshot(dst: File) {
        val remote = "/data/local/tmp/geely_shot.png"
        runCatching { shell("rm -f $remote") }
        shell("screencap -p $remote")
        pull(remote, dst)
        runCatching { shell("rm -f $remote") }
    }

    /** Find the installed open-headunit package: prefers the fork's ".geely" debug build. */
    fun findForkPackage(): String? {
        val pkgs = runCatching { shell("pm list packages headunitrevived") }.getOrDefault("")
            .lines().map { it.trim().removePrefix("package:").trim() }.filter { it.isNotEmpty() && !it.startsWith("[") }
        return pkgs.firstOrNull { it.endsWith(".geely") } ?: pkgs.firstOrNull()
    }

    /** List files (name, size) in a directory on the unit using `ls -l` (no `wc`/`head` here). */
    fun listFiles(dir: String): List<Pair<String, Long>> {
        val out = runCatching { shell("ls -l $dir 2>&1") }.getOrDefault("")
        return out.lines().mapNotNull { line ->
            val t = line.trim().split(Regex("\\s+"))
            if (t.size < 6 || !line.startsWith("-")) return@mapNotNull null
            // toolbox ls -l: perms owner group size date time name  (no spaces in our names)
            Pair(t.last(), t[t.size - 4].toLongOrNull() ?: 0L)
        }
    }

    fun close() {
        runCatching { dadb?.close() }; dadb = null
    }
}
