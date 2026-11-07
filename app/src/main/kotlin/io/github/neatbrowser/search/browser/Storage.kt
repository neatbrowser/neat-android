package io.github.neatbrowser.search.browser

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AtomicFile
import io.github.neatbrowser.search.core.History
import io.github.neatbrowser.search.core.Persist
import io.github.neatbrowser.search.core.Pins
import io.github.neatbrowser.search.core.TabsState
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors

/**
 * Tabs, history and pins as small files in the app's private storage. Saves are delayed a moment
 * and merged, so a burst of changes writes once; [flush] writes what is waiting right now.
 * Ghost tabs and ghost browsing never reach this class.
 */
class Storage(context: Context) {
    private val dir: File = context.filesDir
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val lock = Any()

    /** Latest text to write per file, waiting for its turn. Touched on the main thread only. */
    private val waiting = LinkedHashMap<String, () -> String>()

    fun loadTabs(): TabsState = read(TABS)?.let(Persist::decodeTabs) ?: TabsState()
    fun loadHistory(): History = read(HISTORY)?.let(Persist::decodeHistory) ?: History()
    fun loadPins(): Pins = read(PINS)?.let(Persist::decodePins) ?: Pins()

    // The states are immutable, so encoding can happen later and off the main thread.
    fun saveTabs(state: TabsState) = schedule(TABS) { Persist.encodeTabs(state) }
    fun saveHistory(history: History) = schedule(HISTORY) { Persist.encodeHistory(history) }
    fun savePins(pins: Pins) = schedule(PINS) { Persist.encodePins(pins) }

    /** Writes everything still waiting, now. Called when the app goes to the background. */
    fun flush() {
        val jobs = waiting.toList()
        waiting.clear()
        for ((name, encode) in jobs) write(name, encode)
    }

    private fun schedule(name: String, encode: () -> String) {
        val alreadyWaiting = name in waiting
        waiting[name] = encode
        if (alreadyWaiting) return
        main.postDelayed({
            val latest = waiting.remove(name) ?: return@postDelayed
            io.execute { write(name, latest) }
        }, SAVE_DELAY_MS)
    }

    private fun write(name: String, encode: () -> String) {
        val bytes = encode().toByteArray(Charsets.UTF_8)
        synchronized(lock) {
            val file = AtomicFile(File(dir, name))
            val out = file.startWrite()
            try {
                out.write(bytes)
                file.finishWrite(out)
            } catch (e: IOException) {
                file.failWrite(out)
            }
        }
    }

    private fun read(name: String): String? {
        synchronized(lock) {
            val file = File(dir, name)
            if (!file.isFile || file.length() > MAX_FILE_BYTES) return null
            return try {
                String(AtomicFile(file).readFully(), Charsets.UTF_8)
            } catch (e: IOException) {
                null
            }
        }
    }

    private companion object {
        const val TABS = "tabs.tsv"
        const val HISTORY = "history.tsv"
        const val PINS = "pins.tsv"
        const val SAVE_DELAY_MS = 1000L
        const val MAX_FILE_BYTES = 8L * 1024 * 1024
    }
}
