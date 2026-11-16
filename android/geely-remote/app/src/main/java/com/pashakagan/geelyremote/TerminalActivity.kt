package com.pashakagan.geelyremote

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Terminal: paste commands (e.g. from Claude in the phone's browser), run them on the head unit
 * over the existing wireless ADB connection, and copy the result back.
 *
 * - Multi-line scripts are pushed to the unit and run with `sh`, so quoting/heredocs just work.
 * - adbd runs as root on this unit (root install 2026-11-16) => commands run as root.
 * - Output arrives when the command finishes (this unit's old adbd has no streaming shell).
 * - The whole session is auto-saved to Download/GeelyRemote/terminal/ after every run.
 * - "Get report" copies a file or folder from the unit (default /data/local/tmp/report) to
 *   Download/GeelyRemote/reports/<time>_<name>/ and puts its text in "Copy for Claude".
 */
class TerminalActivity : AppCompatActivity() {

    private val io = Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences("cfg", MODE_PRIVATE) }

    private lateinit var tvStatus: TextView
    private lateinit var etCmd: EditText
    private lateinit var tvOut: TextView
    private lateinit var btnRun: Button
    private lateinit var scroll: ScrollView

    private val session = StringBuilder()
    private var lastBlock = ""
    private var sessionUri: Uri? = null
    private var sessionFile: File? = null
    private val sessionName = "terminal_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.txt"
    @Volatile private var running = false
    private var lastReportUris = ArrayList<Uri>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Terminal"
        setContentView(buildUi())
        refreshStatus()
    }

    override fun onDestroy() { io.shutdown(); super.onDestroy() }

    // ---------- UI ----------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun buildUi(): View {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(12), dp(12), dp(12)) }

        tvStatus = TextView(this).apply { textSize = 13f; alpha = 0.8f }
        root.addView(tvStatus)

        etCmd = EditText(this).apply {
            hint = "Paste commands from Claude here…"
            typeface = Typeface.MONOSPACE
            textSize = 13f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            minLines = 4; maxLines = 12
            gravity = Gravity.TOP or Gravity.START
            setHorizontallyScrolling(false)
        }
        root.addView(etCmd, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        btnRun = btn("▶ Run") { run() }
        root.addView(row(btn("Paste") { paste() }, btnRun, btn("History") { history() }, btn("Clear") { etCmd.setText("") }))

        root.addView(TextView(this).apply { text = "OUTPUT"; textSize = 12f; alpha = 0.6f; setPadding(0, dp(10), 0, dp(4)) })
        tvOut = TextView(this).apply {
            typeface = Typeface.MONOSPACE; textSize = 12f
            setTextIsSelectable(true)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setBackgroundColor(0x11000000)
            text = "Results appear here. Tap \"Copy for Claude\" and paste them back into the chat."
        }
        root.addView(tvOut, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        root.addView(row(
            btn("Copy for Claude") { copyLast() },
            btn("Copy session") { copyText(session.toString(), "Session copied") },
            btn("Share") { share() }))
        root.addView(row(btn("📎 Get report from the unit") { askReport() }))

        scroll = ScrollView(this).apply { addView(root); isFillViewport = true }
        return scroll
    }

    private fun btn(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 13f; setOnClickListener { onClick() }
    }

    private fun row(vararg views: View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        for (v in views) addView(v, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) })
    }

    private fun toast(msg: String) = runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }

    // ---------- status ----------

    private fun refreshStatus() {
        if (!Adb.isConnected) { tvStatus.text = "Not connected — go back and tap Connect first."; return }
        tvStatus.text = "Connected — checking user…"
        io.execute {
            val id = runCatching { Adb.shell("id").trim() }.getOrDefault("?")
            val who = Regex("uid=\\d+\\(([^)]+)\\)").find(id)?.groupValues?.get(1) ?: id
            runOnUiThread { tvStatus.text = "Connected to the head unit — commands run as: $who" }
        }
    }

    // ---------- run ----------

    private fun run() {
        val script = etCmd.text.toString().trim()
        if (script.isEmpty()) { toast("Nothing to run"); return }
        if (!Adb.isConnected) { toast("Not connected — tap Connect on the main screen first"); return }
        if (running) { toast("Still running the previous command"); return }
        running = true
        btnRun.isEnabled = false; btnRun.text = "Running…"
        saveHistory(script)
        lastReportUris = ArrayList()
        val started = System.currentTimeMillis()
        val ticker = object : Runnable {
            override fun run() {
                if (!running) return
                btnRun.text = "Running… ${(System.currentTimeMillis() - started) / 1000}s"
                btnRun.postDelayed(this, 1000)
            }
        }
        btnRun.post(ticker)
        io.execute {
            val out = try { Adb.runScript(script) } catch (e: Exception) { "ERROR: ${e.javaClass.simpleName}: ${e.message}" }
            val secs = (System.currentTimeMillis() - started) / 1000.0
            val stamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
            val block = buildString {
                append("$ ").append(script.replace("\n", "\n$ ")).append('\n')
                append(out.trimEnd()).append('\n')
                append("(${stamp}, ${"%.1f".format(secs)}s)")
            }
            lastBlock = block
            session.append(block).append("\n\n")
            autoSave()
            runOnUiThread {
                running = false
                btnRun.isEnabled = true; btnRun.text = "▶ Run"
                tvOut.text = block
                scroll.post { scroll.smoothScrollTo(0, tvOut.top) }
            }
        }
    }

    // ---------- clipboard / history ----------

    private fun paste() {
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val t = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
        if (t.isNullOrBlank()) { toast("Clipboard is empty"); return }
        // Strip a surrounding ``` fence if Claude's code block was copied with it.
        etCmd.setText(t.trim().removePrefix("```bash").removePrefix("```sh").removePrefix("```").removeSuffix("```").trim())
    }

    private fun copyText(text: String, msg: String) {
        if (text.isBlank()) { toast("Nothing to copy yet"); return }
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Geely terminal", text))
        toast(msg)
    }

    private fun copyLast() = copyText(if (lastBlock.isEmpty()) "" else "Head unit output:\n```\n$lastBlock\n```", "Copied — paste it into the chat")

    private fun loadHistory(): MutableList<String> {
        val arr = runCatching { JSONArray(prefs.getString("termHistory", "[]")) }.getOrDefault(JSONArray())
        return MutableList(arr.length()) { arr.getString(it) }
    }

    private fun saveHistory(script: String) {
        val h = loadHistory().apply { remove(script); add(0, script) }.take(20)
        prefs.edit().putString("termHistory", JSONArray(h).toString()).apply()
    }

    private fun history() {
        val h = loadHistory()
        if (h.isEmpty()) { toast("No history yet"); return }
        AlertDialog.Builder(this).setTitle("Recent commands")
            .setItems(h.map { it.lines().first().take(60) + if (it.contains('\n')) "  …" else "" }.toTypedArray()) { _, i -> etCmd.setText(h[i]) }
            .setNegativeButton("Close", null).show()
    }

    // ---------- reports (unit -> phone -> Claude) ----------

    private fun askReport() {
        if (!Adb.isConnected) { toast("Not connected — tap Connect on the main screen first"); return }
        val et = EditText(this).apply {
            setText(prefs.getString("reportPath", "/data/local/tmp/report")); setSingleLine(); typeface = Typeface.MONOSPACE
        }
        AlertDialog.Builder(this).setTitle("Get report from the unit")
            .setMessage("File or folder on the head unit to copy (folders are copied with their contents).")
            .setView(et)
            .setPositiveButton("Get") { _, _ ->
                val p = et.text.toString().trim()
                if (p.isNotEmpty()) { prefs.edit().putString("reportPath", p).apply(); getReport(p) }
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun getReport(remote: String) {
        if (running) { toast("Still busy"); return }
        running = true
        tvOut.text = "Copying $remote from the unit…"
        io.execute {
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val name = remote.trimEnd('/').substringAfterLast('/').ifEmpty { "report" }
            val folder = "${stamp}_$name"
            val tmp = File(cacheDir, "report_$stamp").apply { deleteRecursively(); mkdirs() }
            val block = try {
                val files = Adb.pullTree(remote, tmp)
                val uris = ArrayList<Uri>()
                val text = StringBuilder()
                var textBytes = 0
                for ((rel, f) in files) {
                    saveFile(f, "GeelyRemote/reports/$folder/" + rel.substringBeforeLast('/', ""), f.name)?.let { uris += it }
                    val bytes = f.readBytes()
                    val isText = bytes.size < 2_000_000 && bytes.take(4096).none { it == 0.toByte() }
                    if (isText && textBytes < 60_000) {
                        val body = String(bytes).let { if (textBytes + it.length > 60_000) it.take(60_000 - textBytes) + "\n…(truncated)" else it }
                        text.append("--- ").append(rel).append(" ---\n").append(body.trimEnd()).append("\n\n")
                        textBytes += body.length
                    } else {
                        text.append("--- ").append(rel).append(" (").append(bytes.size / 1024).append(" KB, ")
                            .append(if (isText) "text, not inlined" else "binary").append(") ---\n\n")
                    }
                }
                lastReportUris = uris
                buildString {
                    append("Report: $remote — ${files.size} file(s), saved to Download/GeelyRemote/reports/$folder/\n\n")
                    append(text.toString().trimEnd())
                }
            } catch (e: Exception) {
                lastReportUris = ArrayList()
                "Report failed: ${e.javaClass.simpleName}: ${e.message}"
            } finally { tmp.deleteRecursively() }
            lastBlock = block
            session.append(block).append("\n\n")
            autoSave()
            runOnUiThread {
                running = false
                tvOut.text = block
                scroll.post { scroll.smoothScrollTo(0, tvOut.top) }
            }
        }
    }

    /** Copy [src] into Download/<relDir>/<name> and return a shareable Uri. */
    private fun saveFile(src: File, relDir: String, name: String): Uri? = try {
        val dir = relDir.trimEnd('/')
        if (Build.VERSION.SDK_INT >= 29) {
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$dir")
            })!!
            contentResolver.openOutputStream(uri)!!.use { out -> src.inputStream().use { it.copyTo(out) } }
            uri
        } else {
            @Suppress("DEPRECATION")
            val d = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), dir).apply { mkdirs() }
            val out = File(d, name); src.copyTo(out, overwrite = true)
            androidx.core.content.FileProvider.getUriForFile(this, "$packageName.fileprovider", out)
        }
    } catch (e: Exception) { null }

    // ---------- saving (Download/GeelyRemote/terminal/) ----------

    /** Rewrite the session file after every run so results land in the synced Download folder. */
    private fun autoSave() {
        val text = session.toString()
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                val uri = sessionUri ?: contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, sessionName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/GeelyRemote/terminal")
                })!!.also { sessionUri = it }
                contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "GeelyRemote/terminal").apply { mkdirs() }
                val f = sessionFile ?: File(dir, sessionName).also { sessionFile = it }
                f.writeText(text)
            }
        } catch (e: Exception) {
            toast("Couldn't save the session: ${e.message}")
        }
    }

    private fun share() {
        if (lastReportUris.isNotEmpty()) {
            val send = android.content.Intent(if (lastReportUris.size == 1) android.content.Intent.ACTION_SEND else android.content.Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                if (lastReportUris.size == 1) putExtra(android.content.Intent.EXTRA_STREAM, lastReportUris[0])
                else putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, lastReportUris)
                clipData = ClipData.newRawUri("", lastReportUris[0]).also { c -> lastReportUris.drop(1).forEach { c.addItem(ClipData.Item(it)) } }
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(android.content.Intent.createChooser(send, "Share report files")); return
        }
        if (session.isEmpty()) { toast("Nothing to share yet"); return }
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, session.toString())
        }
        startActivity(android.content.Intent.createChooser(send, "Share terminal session"))
    }
}
