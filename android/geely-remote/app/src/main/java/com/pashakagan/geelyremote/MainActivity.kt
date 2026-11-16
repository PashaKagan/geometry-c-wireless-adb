package com.pashakagan.geelyremote

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.pashakagan.geelyremote.databinding.ActivityMainBinding
import java.io.File
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private val io = Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences("cfg", MODE_PRIVATE) }



    private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            prefs.edit().putString("folder", uri.toString()).apply()
            log("APK folder set: ${uri.lastPathSegment}")
            installFolder(uri)
        }
    }

    private val pickApk = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) installSingle(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.etIp.setText(prefs.getString("ip", ""))

        b.btnConnect.setOnClickListener { connect() }
        b.btnScan.setOnClickListener { scan() }
        b.btnSeatFix.setOnClickListener { seatFix() }
        b.btnScreenshot.setOnClickListener { screenshot() }
        b.btnInstallApk.setOnClickListener {
            if (!requireConnected()) return@setOnClickListener
            pickApk.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream", "*/*"))
        }
        b.btnInstallFolder.setOnClickListener {
            if (!requireConnected()) return@setOnClickListener
            val saved = prefs.getString("folder", null)
            if (saved == null) pickFolder.launch(null) else installFolder(Uri.parse(saved))
        }
        b.btnApps.setOnClickListener { listApps() }
        b.btnReboot.setOnClickListener { rebootUnit() }
        b.btnTweaks.setOnClickListener { fastAnimations() }
        b.btnTerminal.setOnClickListener { startActivity(Intent(this, TerminalActivity::class.java)) }
        b.btnCopyLog.setOnClickListener { copyLog() }
        b.btnSaveLog.setOnClickListener { saveLog() }
        b.btnClearLog.setOnClickListener { b.tvLog.text = "" }
        if (Build.VERSION.SDK_INT < 29 &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
        b.btnToggleLog.setOnClickListener { setLogVisible(b.logCard.visibility != View.VISIBLE) }
    }

    // ---- connection ----

    private fun connect() {
        val ip = b.etIp.text.toString().trim()
        if (ip.isEmpty()) { log("Enter the head unit IP first."); setLogVisible(true); return }
        prefs.edit().putString("ip", ip).apply()
        setStatus("Connecting to $ip …")
        io.execute {
            try {
                val model = Adb.connect(ip)
                val who = if (Adb.isRoot) "root" else "NOT root — terminal will use su"
                runOnUiThread { setStatus("Connected" + (if (model.isNotEmpty()) "  ($model)" else "") + "  ·  $who") }
                log("Connected to $ip" + (if (model.isNotEmpty()) "  ($model)" else "") + " as $who")
            } catch (e: Exception) {
                runOnUiThread { setStatus("Not connected") }
                log("Connect failed: ${e.message}")
                log("Check: car on, phone + unit on the same hotspot/Wi-Fi, Wireless ADB enabled, and accept the authorise dialog on the head unit.")
                setLogVisible(true)
            }
        }
    }

    private fun scan() {
        setStatus("Scanning for head unit…")
        log("Scanning local network for an open :5555 …")
        setLogVisible(true)
        thread {
            val subnets = localSubnets()
            if (subnets.isEmpty()) { log("No local Wi-Fi/hotspot network found."); runOnUiThread { setStatus("Not connected") }; return@thread }
            val pool = Executors.newFixedThreadPool(64)
            val found = java.util.concurrent.atomic.AtomicReference<String?>(null)
            for (prefix in subnets) for (i in 1..254) {
                val host = "$prefix.$i"
                pool.execute {
                    if (found.get() != null) return@execute
                    try { Socket().use { it.connect(InetSocketAddress(host, 5555), 300); found.compareAndSet(null, host) } } catch (_: Exception) {}
                }
            }
            pool.shutdown()
            pool.awaitTermination(15, java.util.concurrent.TimeUnit.SECONDS)
            val hit = found.get()
            if (hit != null) {
                runOnUiThread { b.etIp.setText(hit); setStatus("Found $hit — tap Connect") }
                log("Found a device with :5555 open at $hit (filled in above).")
            } else {
                runOnUiThread { setStatus("Not connected") }
                log("No host with :5555 found. Enter the IP manually.")
            }
        }
    }

    private fun localSubnets(): List<String> {
        val out = mutableListOf<String>()
        try {
            for (nif in NetworkInterface.getNetworkInterfaces()) {
                if (!nif.isUp || nif.isLoopback) continue
                for (addr in nif.inetAddresses) {
                    if (addr.isLoopbackAddress) continue
                    val ip = addr.hostAddress ?: continue
                    if (addr is java.net.Inet4Address && ip.count { it == '.' } == 3) out.add(ip.substringBeforeLast('.'))
                }
            }
        } catch (_: Exception) {}
        return out.distinct()
    }

    // ---- actions ----

    private fun screenshot() {
        if (!requireConnected()) return
        io.execute {
            try {
                val dst = File(cacheDir, "geely_${ts()}.png")
                Adb.screenshot(dst)
                if (dst.length() > 0) deliver(dst, "screenshots", "image/png")
                else { log("Screenshot came back empty."); setLogVisible(true) }
            } catch (e: Exception) { log("Screenshot failed: ${e.message}"); setLogVisible(true) }
        }
    }

    private fun seatFix() {
        if (!requireConnected()) return
        AlertDialog.Builder(this)
            .setTitle("Seat-preset fix")
            .setMessage("Clears the vehicle-settings app's data and reboots the head unit. Try it when the seat preset is corrupted. Continue?")
            .setPositiveButton("Do it") { _, _ ->
                io.execute {
                    try {
                        log(Adb.shell("pm clear com.neusoft.optimus.wheeljack.setting").trim())
                        log("Rebooting head unit…")
                        reboot(); Adb.close()
                        runOnUiThread { setStatus("Not connected (unit rebooting)") }
                    } catch (e: Exception) { log("Seat fix failed: ${e.message}") }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun fastAnimations() {
        if (!requireConnected()) return
        io.execute {
            try {
                for (s in listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale"))
                    Adb.shell("settings put global $s 0.5")
                log("Animations set to 0.5x")
            } catch (e: Exception) { log("Tweaks failed: ${e.message}") }
        }
    }

    private fun rebootUnit() {
        if (!requireConnected()) return
        AlertDialog.Builder(this)
            .setTitle("Reboot head unit")
            .setMessage("Reboot the head unit now?")
            .setPositiveButton("Reboot") { _, _ ->
                io.execute { log("Rebooting head unit…"); reboot(); Adb.close(); runOnUiThread { setStatus("Not connected (unit rebooting)") } }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun reboot() {
        runCatching { log(Adb.shell("svc power reboot 2>&1 || reboot 2>&1 || setprop sys.powerctl reboot 2>&1").trim()) }
    }

    private var showSystemApps = false

    private fun listApps() = loadApps(showSystemApps)

    private fun loadApps(system: Boolean) {
        if (!requireConnected()) return
        io.execute {
            try {
                val flag = if (system) "-s" else "-3"
                val raw = Adb.shell("pm list packages $flag")
                val pkgs = raw.lines().mapNotNull { it.trim().removePrefix("package:").trim().ifEmpty { null } }
                    .filter { it.isNotBlank() && !it.startsWith("[") }.sorted()
                if (pkgs.isEmpty()) { log("No packages found (or command failed)."); return@execute }
                runOnUiThread { showAppsDialog(pkgs, system) }
            } catch (e: Exception) { log("List apps failed: ${e.message}") }
        }
    }

    private fun showAppsDialog(pkgs: List<String>, system: Boolean) {
        AlertDialog.Builder(this)
            .setTitle((if (system) "System apps" else "User apps") + " (${pkgs.size})")
            .setItems(pkgs.toTypedArray()) { _, which -> appActions(pkgs[which]) }
            .setNeutralButton(if (system) "Show user apps" else "Show system apps") { _, _ ->
                showSystemApps = !system; loadApps(!system)
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun appActions(pkg: String) {
        AlertDialog.Builder(this)
            .setTitle(pkg)
            .setItems(arrayOf("Launch on head unit", "Uninstall")) { _, which ->
                io.execute {
                    try {
                        if (which == 0) { Adb.shell("monkey -p $pkg -c android.intent.category.LAUNCHER 1"); log("Launched $pkg on the head unit.") }
                        else log("Uninstall $pkg: " + Adb.shell("pm uninstall $pkg").trim())
                    } catch (e: Exception) { log("Action failed: ${e.message}") }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---- install helpers ----

    private fun installSingle(uri: Uri) {
        io.execute {
            try {
                val tmp = copyToCache(uri, DocumentFile.fromSingleUri(this, uri)?.name?.takeIf { it.endsWith(".apk", true) } ?: "single.apk")
                log("Installing ${tmp.name} (${tmp.length() / 1024} KB) …"); Adb.install(tmp); tmp.delete(); log("  ✓ installed")
            } catch (e: Exception) { log("  ✗ install failed: ${e.message}") }
        }
    }

    private fun installFolder(treeUri: Uri) {
        io.execute {
            try {
                val dir = DocumentFile.fromTreeUri(this, treeUri)
                val apks = dir?.listFiles()?.filter { it.isFile && (it.name?.lowercase()?.endsWith(".apk") == true) } ?: emptyList()
                if (apks.isEmpty()) { log("No .apk files in that folder."); return@execute }
                log("Found ${apks.size} APK(s). Installing…")
                var ok = 0
                for (df in apks) {
                    val name = df.name ?: "app.apk"
                    try {
                        val tmp = copyToCache(df.uri, name)
                        log("Installing $name …"); Adb.install(tmp); tmp.delete(); ok++; log("  ✓ $name")
                    } catch (e: Exception) { log("  ✗ $name: ${e.message}") }
                }
                log("Done: $ok/${apks.size} installed.")
            } catch (e: Exception) { log("Folder install failed: ${e.message}") }
        }
    }

    // ---- Open Headunit fork logs (fork mirrors to /sdcard/Download/logs on the unit) ----

    // ---- log card ----

    private fun copyLog() {
        val text = b.tvLog.text.toString()
        if (text.isBlank()) { Toast.makeText(this, "Log is empty", Toast.LENGTH_SHORT).show(); return }
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Geely Remote log", text))
        Toast.makeText(this, "Copied ${text.lines().size} log lines", Toast.LENGTH_SHORT).show()
    }

    private fun saveLog() {
        val text = b.tvLog.text.toString()
        if (text.isBlank()) { Toast.makeText(this, "Log is empty", Toast.LENGTH_SHORT).show(); return }
        io.execute {
            val f = File(cacheDir, "geely-remote-log_${ts()}.txt")
            f.writeText(text)
            val uri = saveToDownloads(f, "logs", "text/plain")
            f.delete()
            if (uri != null) runOnUiThread { Toast.makeText(this, "Saved to Download/GeelyRemote/logs", Toast.LENGTH_SHORT).show() }
        }
    }

    // ---- saving to the phone's Download/GeelyRemote/<sub>/ ----

    /** Copy a local file into Download/GeelyRemote/<sub>/ and return a shareable Uri. */
    private fun saveToDownloads(src: File, sub: String, mime: String): Uri? = try {
        val rel = "GeelyRemote/$sub"
        if (Build.VERSION.SDK_INT >= 29) {
            val cv = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, src.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$rel")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)!!
            contentResolver.openOutputStream(uri)!!.use { out -> src.inputStream().use { it.copyTo(out) } }
            contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            uri
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), rel).apply { mkdirs() }
            val out = File(dir, src.name)
            src.copyTo(out, overwrite = true)
            FileProvider.getUriForFile(this, "$packageName.fileprovider", out)
        }
    } catch (e: Exception) {
        log("Couldn't save ${src.name} to Download/GeelyRemote/$sub: ${e.message}"); null
    }

    /** Save one pulled file to Download/GeelyRemote/<sub>/, log it, open the share sheet. */
    private fun deliver(src: File, sub: String, mime: String) = deliverMany(listOf(src), sub, mime)

    private fun deliverMany(srcs: List<File>, sub: String, mime: String) {
        val uris = ArrayList<Uri>()
        for (f in srcs) {
            val kb = f.length() / 1024
            val uri = saveToDownloads(f, sub, mime)
            if (uri != null) { uris.add(uri); log("Saved: Download/GeelyRemote/$sub/${f.name} ($kb KB)") }
            f.delete()
        }
        if (uris.isNotEmpty()) runOnUiThread { shareUris(uris, mime) }
    }

    private fun shareUris(uris: ArrayList<Uri>, mime: String) {
        try {
            val send = if (uris.size == 1) Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_STREAM, uris[0]) }
                       else Intent(Intent.ACTION_SEND_MULTIPLE).apply { putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris) }
            send.type = mime
            send.clipData = ClipData.newRawUri("", uris[0]).also { c -> uris.drop(1).forEach { c.addItem(ClipData.Item(it)) } }
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(send, if (uris.size == 1) "Share file" else "Share ${uris.size} files"))
        } catch (e: Exception) { log("Share failed: ${e.message}") }
    }

    // ---- helpers ----

    private fun copyToCache(uri: Uri, name: String): File {
        val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val out = File(cacheDir, safe)
        contentResolver.openInputStream(uri)!!.use { input -> out.outputStream().use { input.copyTo(it) } }
        return out
    }

    private fun requireConnected(): Boolean {
        if (!Adb.isConnected) { log("Not connected — tap Connect first."); setLogVisible(true); return false }
        return true
    }

    private fun ts() = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    private fun setStatus(s: String) { runOnUiThread { b.tvStatus.text = s } }

    private fun setLogVisible(visible: Boolean) {
        runOnUiThread {
            b.logCard.visibility = if (visible) View.VISIBLE else View.GONE
            b.btnToggleLog.text = if (visible) "Hide log ▾" else "Show log ▸"
            if (visible) b.scroll.post { b.scroll.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun log(msg: String) {
        runOnUiThread {
            val t = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
            b.tvLog.append(msg.lines().joinToString("\n") { "[$t] $it" } + "\n")
            b.scroll.post { b.scroll.fullScroll(View.FOCUS_DOWN) }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        io.shutdownNow()
        Adb.close()
    }
}
