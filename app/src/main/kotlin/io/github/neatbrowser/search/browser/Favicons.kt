package io.github.neatbrowser.search.browser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import io.github.neatbrowser.search.core.FaviconLinks
import io.github.neatbrowser.search.core.FaviconPolicy
import io.github.neatbrowser.search.core.UrlUtils
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * The little pictures of sites. GeckoView has no favicon callback, so the app fetches them itself.
 *
 * Privacy, on purpose:
 * - Only the site's own host is asked: its home page (never the page the user is on, which may carry
 *   a one-time link or a token), the icons that page points to on the same host, and /favicon.ico.
 *   No favicon service is used, so no third party learns which sites are visited.
 * - Only http and https; at most [FaviconPolicy.MAX_REDIRECTS] redirects, each again http(s) only.
 * - No cookie, referrer or login is sent. The page read is cut at [FaviconPolicy.MAX_HTML_BYTES], an
 *   image above [FaviconPolicy.MAX_IMAGE_BYTES] is refused, and a request gives up after
 *   [FaviconPolicy.TIMEOUT_MS].
 *
 * Two caches that never meet:
 * - Normal tabs: PNG files of at most 64 px in `filesDir/favicons`, named from the site key, wiped by
 *   [clearDisk] when the history is cleared.
 * - Ghost tabs: memory only. A ghost tab neither reads the normal cache nor writes to disk; [clearGhost]
 *   empties it when the last ghost tab is gone. The process ending does the same.
 *
 * Showing an icon never starts a network request ([load] only reads the disk); requests start when a
 * page has finished loading ([fetchIfMissing]).
 */
class Favicons(context: Context) {
    private val dir = File(context.filesDir, "favicons")
    private val main = Handler(Looper.getMainLooper())

    /** All disk work in order: a delete queued after a write really comes after it. */
    private val diskExecutor = Executors.newSingleThreadExecutor(daemon("favicons-disk"))
    private val diskDispatcher: CoroutineDispatcher = diskExecutor.asCoroutineDispatcher()
    private val netExecutor = Executors.newFixedThreadPool(2, daemon("favicons-net"))

    private val normal = mutableStateMapOf<String, ImageBitmap>()
    private val ghost = mutableStateMapOf<String, ImageBitmap>()

    // Main thread only.
    private val diskChecked = HashSet<String>()
    private val busy = HashSet<String>()
    private val missedAt = HashMap<String, Long>()

    /** Bumped when a cache is emptied: a job that started before must not put its result in afterwards. */
    @Volatile private var normalEpoch = 0
    @Volatile private var ghostEpoch = 0

    /** The icon of a site if it is in memory; reading this in a composable makes it redraw when the icon arrives. */
    fun cached(siteKey: String, ghost: Boolean): ImageBitmap? =
        if (ghost) this.ghost[siteKey] else normal[siteKey]

    /** Brings the icon of [siteKey] from the disk into memory (normal mode only; ghost icons are never on disk). */
    suspend fun load(siteKey: String, ghost: Boolean) {
        if (ghost || normal.containsKey(siteKey) || !diskChecked.add(siteKey)) return
        val epoch = normalEpoch
        val bitmap = withContext(diskDispatcher) { readFile(siteKey) }
        if (bitmap != null && epoch == normalEpoch) normal[siteKey] = bitmap.asImageBitmap()
    }

    /**
     * Gets the icon of the site that [pageUrl] belongs to when there is none yet. Call it when a page has
     * finished loading. Does nothing for a site that failed less than [RETRY_AFTER_MS] ago.
     */
    fun fetchIfMissing(pageUrl: String, ghost: Boolean) {
        if (!UrlUtils.isHttp(pageUrl)) return
        val key = UrlUtils.siteKey(pageUrl) ?: return
        if ((if (ghost) this.ghost else normal).containsKey(key)) return
        val token = (if (ghost) "g:" else "n:") + key
        if (token in busy) return
        val failedAt = missedAt[token]
        if (failedAt != null && SystemClock.elapsedRealtime() - failedAt < RETRY_AFTER_MS) return
        busy.add(token)

        val epoch = if (ghost) ghostEpoch else normalEpoch
        val startDownload = {
            netExecutor.execute {
                val bitmap = try {
                    download(pageUrl)
                } catch (e: Exception) {
                    null // network, address and decoder trouble all mean the same: no icon this time
                } catch (e: OutOfMemoryError) {
                    null // a picture too big to decode; without this the site would stay "busy" for the whole session
                }
                main.post { finish(token, key, ghost = ghost, epoch = epoch, bitmap = bitmap, fromDisk = false) }
            }
        }
        if (ghost) {
            startDownload()
        } else {
            diskExecutor.execute {
                val onDisk = readFile(key)
                if (onDisk != null) {
                    main.post { finish(token, key, ghost = false, epoch = epoch, bitmap = onDisk, fromDisk = true) }
                } else {
                    startDownload()
                }
            }
        }
    }

    /** The history was cleared: forget every stored icon, on disk and in memory. */
    fun clearDisk() {
        normalEpoch++
        normal.clear()
        diskChecked.clear()
        missedAt.keys.removeAll { it.startsWith("n:") }
        diskExecutor.execute { dir.listFiles()?.forEach { it.delete() } }
    }

    /** No ghost tab is left (or the app is closing): forget the ghost icons. */
    fun clearGhost() {
        ghostEpoch++
        ghost.clear()
        missedAt.keys.removeAll { it.startsWith("g:") }
    }

    private fun finish(token: String, key: String, ghost: Boolean, epoch: Int, bitmap: Bitmap?, fromDisk: Boolean) {
        busy.remove(token)
        if (epoch != (if (ghost) ghostEpoch else normalEpoch)) return
        if (bitmap == null) {
            missedAt[token] = SystemClock.elapsedRealtime()
            return
        }
        missedAt.remove(token)
        if (ghost) {
            this.ghost[key] = bitmap.asImageBitmap()
        } else {
            normal[key] = bitmap.asImageBitmap()
            if (!fromDisk) {
                diskExecutor.execute { if (epoch == normalEpoch) writeFile(key, bitmap) }
            }
        }
    }

    // ---- network (background threads) ----

    /** The icon for the site of [pageUrl], or null: read its home page, then try the icons it names, then /favicon.ico. */
    private fun download(pageUrl: String): Bitmap? {
        val origin = UrlUtils.origin(pageUrl) ?: return null
        val home = fetch("$origin/", FaviconPolicy.MAX_HTML_BYTES, "text/html,application/xhtml+xml;q=0.9,*/*;q=0.5", cut = true)
        val html = home?.let { String(it.bytes, Charsets.UTF_8) }
        for (url in FaviconLinks.plan(html, home?.finalUrl, pageUrl)) {
            val image = fetch(url, FaviconPolicy.MAX_IMAGE_BYTES, "image/*,*/*;q=0.5", cut = false) ?: continue
            val bitmap = decode(image.bytes)
            if (bitmap != null) return bitmap
        }
        return null
    }

    private class Fetched(val finalUrl: String, val bytes: ByteArray)

    /**
     * GET of [startUrl], following up to [FaviconPolicy.MAX_REDIRECTS] redirects by hand so that every hop
     * can be checked. With [cut] the body stops at [limit] bytes; without it a longer body is refused.
     */
    private fun fetch(startUrl: String, limit: Int, accept: String, cut: Boolean): Fetched? {
        var url = startUrl
        var hops = 0
        while (true) {
            if (!UrlUtils.isHttp(url)) return null
            val connection = URL(url).openConnection() as? HttpURLConnection ?: return null
            try {
                connection.instanceFollowRedirects = false
                connection.useCaches = false
                connection.connectTimeout = FaviconPolicy.TIMEOUT_MS
                connection.readTimeout = FaviconPolicy.TIMEOUT_MS
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", USER_AGENT)
                connection.setRequestProperty("Accept", accept)
                // Nothing else is sent: no Cookie, Referer or Authorization, and the app never installs a CookieHandler.
                val code = connection.responseCode
                if (code in 300..399) {
                    if (hops >= FaviconPolicy.MAX_REDIRECTS) return null
                    url = FaviconPolicy.redirectTarget(url, connection.getHeaderField("Location")) ?: return null
                    hops++
                    continue
                }
                if (code != HttpURLConnection.HTTP_OK) return null
                if (!cut && connection.contentLengthLong > limit) return null
                val bytes = readUpTo(connection.inputStream, limit, cut) ?: return null
                return Fetched(url, bytes)
            } finally {
                connection.disconnect()
            }
        }
    }

    /** Reads at most [limit] bytes; null when the body is longer and [cut] is off, or the time is up. */
    private fun readUpTo(input: InputStream, limit: Int, cut: Boolean): ByteArray? {
        val deadline = SystemClock.elapsedRealtime() + FaviconPolicy.TIMEOUT_MS
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        input.use { stream ->
            while (true) {
                if (SystemClock.elapsedRealtime() > deadline) return null
                val count = stream.read(buffer)
                if (count < 0) break
                val room = limit - out.size()
                if (count > room) {
                    if (!cut) return null
                    out.write(buffer, 0, room)
                    break
                }
                out.write(buffer, 0, count)
            }
        }
        return out.toByteArray()
    }

    /** Decodes with a sample size so a huge picture never fills memory, then shrinks to about 64 px. */
    private fun decode(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) return null
        if (minOf(width, height) < FaviconPolicy.MIN_SIDE || maxOf(width, height) > FaviconPolicy.MAX_SOURCE_SIDE) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = FaviconPolicy.sampleSize(width, height)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        val (targetWidth, targetHeight) = FaviconPolicy.storedSize(decoded.width, decoded.height)
        if (targetWidth == decoded.width && targetHeight == decoded.height) return decoded
        val scaled = Bitmap.createScaledBitmap(decoded, targetWidth, targetHeight, true)
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }

    // ---- disk (the disk thread) ----

    private fun readFile(siteKey: String): Bitmap? {
        val file = File(dir, FaviconPolicy.fileName(siteKey))
        if (!file.isFile || file.length() > FaviconPolicy.MAX_IMAGE_BYTES) return null
        return BitmapFactory.decodeFile(file.path)
    }

    private fun writeFile(siteKey: String, bitmap: Bitmap) {
        try {
            dir.mkdirs()
            val target = File(dir, FaviconPolicy.fileName(siteKey))
            val temp = File(dir, target.name + ".tmp")
            temp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (!temp.renameTo(target)) temp.delete()
        } catch (e: IOException) {
            // no space or no permission: the icon is simply fetched again next time
        }
    }

    private fun daemon(name: String) = ThreadFactory { task -> Thread(task, name).apply { isDaemon = true } }

    private companion object {
        const val USER_AGENT = "Mozilla/5.0 (Android; Mobile) NEATSearch"

        /** A site that gave no icon is asked again after this long, not on every page load. */
        const val RETRY_AFTER_MS = 30L * 60 * 1000
    }
}
