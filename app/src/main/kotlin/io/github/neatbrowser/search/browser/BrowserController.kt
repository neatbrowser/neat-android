package io.github.neatbrowser.search.browser

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import io.github.neatbrowser.search.core.ClearedTabs
import io.github.neatbrowser.search.core.History
import io.github.neatbrowser.search.core.Pins
import io.github.neatbrowser.search.core.Tab
import io.github.neatbrowser.search.core.TabsState
import io.github.neatbrowser.search.core.UrlUtils
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebRequestError

/** What the UI shows about the page of a tab. */
data class PageState(
    val url: String? = null,
    val title: String = "",
    val progress: Int = 0,
    val loading: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
)

/** One GeckoRuntime per process; creating a second one throws. */
private object Engine {
    private var runtime: GeckoRuntime? = null

    @Synchronized
    fun runtime(context: Context): GeckoRuntime =
        runtime ?: GeckoRuntime.create(context.applicationContext).also { runtime = it }
}

/**
 * Owns the tabs and one GeckoSession per tab that has been shown, and mirrors their state into
 * Compose. Ghost tabs use Gecko's private mode: they are never written to disk, never recorded in
 * history and cannot be pinned.
 *
 * Create it with an Activity context (links for other apps are started from it) and call
 * [onPause], [onResume] and [destroy] from the Activity. The view side is a single GeckoView that
 * is told to show [selectedSession]; it registers itself with [attachView].
 */
class BrowserController(private val context: Context, private val storage: Storage) {
    private val runtime: GeckoRuntime = Engine.runtime(context)
    private val gate = PageGate(context)

    private var resumed = true
    private var view: GeckoView? = null
    private val engines = HashMap<Long, TabEngine>()
    private val pages = mutableStateMapOf<Long, PageState>()
    private val thumbs = mutableStateMapOf<Long, ImageBitmap>()

    var tabsState by mutableStateOf(storage.loadTabs())
        private set
    var pins by mutableStateOf(storage.loadPins())
        private set
    var history by mutableStateOf(storage.loadHistory())
        private set

    /** What "Clear All Tabs" removed last; the UI offers Undo while this is set. */
    var cleared by mutableStateOf<ClearedTabs?>(null)
        private set

    val ghostMode: Boolean get() = tabsState.ghostMode
    val visibleTabs: List<Tab> get() = tabsState.visible
    val selectedTab: Tab? get() = tabsState.selected

    /** State of the selected tab's page; empty when the home screen shows. */
    val page: PageState get() = tabsState.selected?.let { pages[it.id] } ?: PageState()

    /** The session the GeckoView should show, or null on the home screen. */
    val selectedSession: GeckoSession?
        get() = tabsState.selected?.let { engines[it.id]?.sessionOrNull }

    /** Small pictures of tabs, kept in memory only. */
    val thumbnails: Map<Long, ImageBitmap> get() = thumbs

    val isPinned: Boolean get() = !ghostMode && page.url?.let { pins.contains(it) } == true

    init {
        for (tab in tabsState.tabs) pages[tab.id] = PageState(url = tab.url, title = tab.title)
        tabsState.selected?.let { show(it.id) }
        refreshActive()
    }

    // ---- view ----

    fun attachView(v: GeckoView) {
        view = v
    }

    fun detachView(v: GeckoView) {
        if (view === v) view = null
    }

    // ---- tabs ----

    /** Opens a new tab in the current mode and shows it. */
    fun openTab(url: String) {
        captureThumbnail()
        commitClear()
        tabsState = tabsState.open(url)
        val tab = tabsState.selected ?: return
        pages[tab.id] = PageState(url = url, loading = true)
        show(tab.id)
        refreshActive()
        storage.saveTabs(tabsState)
    }

    /** A link from another app: always a normal tab. */
    fun openFromOutside(url: String) {
        if (ghostMode) setGhostMode(false)
        openTab(url)
    }

    fun select(id: Long) {
        if (tabsState.selected?.id != id) captureThumbnail()
        val next = tabsState.select(id)
        if (next === tabsState) return
        tabsState = next
        show(id)
        refreshActive()
        storage.saveTabs(tabsState)
    }

    fun closeTab(id: Long) {
        val closed = tabsState.tab(id) ?: return
        closeEngine(id)
        tabsState = tabsState.close(id)
        pages.remove(id)
        thumbs.remove(id)
        tabsState.selected?.let { show(it.id) }
        refreshActive()
        if (!closed.ghost) storage.saveTabs(tabsState)
    }

    /** Removes every tab of the current mode. The sessions stay alive until [commitClear] so that [undoClear] is instant. */
    fun clearAll() {
        commitClear()
        val (next, removed) = tabsState.clearVisible()
        if (removed.tabs.isEmpty()) return
        tabsState = next
        cleared = removed
        refreshActive()
        if (!removed.ghost) storage.saveTabs(tabsState)
    }

    fun undoClear() {
        val removed = cleared ?: return
        cleared = null
        tabsState = tabsState.restore(removed)
        tabsState.selected?.let { show(it.id) }
        refreshActive()
        if (!removed.ghost) storage.saveTabs(tabsState)
    }

    /** Undo is no longer possible: really close what was cleared. */
    fun commitClear() {
        val removed = cleared ?: return
        cleared = null
        for (tab in removed.tabs) {
            closeEngine(tab.id)
            pages.remove(tab.id)
            thumbs.remove(tab.id)
        }
    }

    fun setGhostMode(on: Boolean) {
        if (on == ghostMode) return
        captureThumbnail()
        commitClear()
        tabsState = tabsState.setGhostMode(on)
        tabsState.selected?.let { show(it.id) }
        refreshActive()
    }

    // ---- page actions ----

    fun goBack() {
        selectedEngine()?.sessionOrNull?.goBack()
    }

    fun goForward() {
        selectedEngine()?.sessionOrNull?.goForward()
    }

    fun reload() {
        val engine = selectedEngine() ?: return
        val failed = engine.errorUrl
        if (failed != null) engine.sessionOrNull?.loadUri(failed) else engine.sessionOrNull?.reload()
    }

    fun togglePin() {
        if (ghostMode) return
        val url = page.url ?: return
        if (!UrlUtils.isHttp(url)) return
        pins = pins.toggle(url, page.title)
        storage.savePins(pins)
    }

    fun removePin(url: String) {
        pins = pins.remove(url)
        storage.savePins(pins)
    }

    fun removeFromHistory(url: String) {
        history = history.remove(url)
        storage.saveHistory(history)
    }

    fun clearHistory() {
        history = history.clear()
        storage.saveHistory(history)
    }

    // ---- lifecycle ----

    fun onResume() {
        resumed = true
        refreshActive()
    }

    fun onPause() {
        resumed = false
        captureThumbnail()
        commitClear()
        refreshActive()
        storage.flush()
    }

    fun destroy() {
        storage.flush()
        for (engine in engines.values) engine.close()
        engines.clear()
    }

    // ---- thumbnails ----

    /** Takes a picture of the page on screen, for the tab switcher. Does nothing when no page shows. */
    fun captureThumbnail() {
        val v = view ?: return
        val tab = tabsState.selected ?: return
        val session = engines[tab.id]?.sessionOrNull ?: return
        if (v.session !== session) return
        val id = tab.id
        try {
            v.capturePixels().then(
                GeckoResult.OnValueListener<Bitmap, Void> { bitmap ->
                    if (bitmap != null && bitmap.width > 0 && bitmap.height > 0 && engines.containsKey(id)) {
                        val width = THUMBNAIL_WIDTH
                        val height = (bitmap.height * width / bitmap.width.toFloat()).toInt().coerceAtLeast(1)
                        thumbs[id] = Bitmap.createScaledBitmap(bitmap, width, height, true).asImageBitmap()
                    }
                    null
                },
                GeckoResult.OnExceptionListener<Void> { null },
            )
        } catch (e: RuntimeException) {
            // the view is not ready to be captured; the old picture stays
        }
    }

    // ---- engines ----

    private fun selectedEngine(): TabEngine? = tabsState.selected?.let { engines[it.id] }

    /** Makes sure the tab has a living session that is loading or showing its page. */
    private fun show(id: Long) {
        val tab = tabsState.tab(id) ?: return
        val engine = engines.getOrPut(id) { TabEngine(id, tab.ghost) }
        engine.ensureSession(tab.url)
    }

    private fun closeEngine(id: Long) {
        engines.remove(id)?.close()
    }

    /** Only the selected tab of the current mode is active (drawing, running timers at full speed). */
    private fun refreshActive() {
        val selected = tabsState.selected?.id
        for ((id, engine) in engines) engine.setActive(resumed && id == selected)
    }

    private fun updateTab(id: Long, url: String? = null, title: String? = null) {
        val tab = tabsState.tab(id) ?: return
        tabsState = tabsState.update(id, url = url, title = title)
        if (!tab.ghost) storage.saveTabs(tabsState)
    }

    private fun updatePage(id: Long, change: PageState.() -> PageState) {
        pages[id] = (pages[id] ?: PageState()).change()
    }

    private fun recordVisit(url: String, title: String) {
        history = history.record(url, title, System.currentTimeMillis())
        storage.saveHistory(history)
    }

    private fun titleKnown(url: String, title: String) {
        val next = history.updateTitle(url, title)
        if (next !== history) {
            history = next
            storage.saveHistory(history)
        }
    }

    /** One tab's GeckoSession and the events it sends. */
    private inner class TabEngine(val id: Long, val ghost: Boolean) {
        /** The session events are accepted from. Plain field: delegates may fire while it is being opened. */
        private var live: GeckoSession? = null
        private val sessionState = mutableStateOf<GeckoSession?>(null)

        /** The address that failed to load while our error page is showing. */
        var errorUrl: String? = null
            private set

        val sessionOrNull: GeckoSession? get() = sessionState.value

        fun ensureSession(url: String) {
            if (live != null) return
            val session = GeckoSession(GeckoSessionSettings.Builder().usePrivateMode(ghost).build())
            live = session
            session.progressDelegate = progressDelegate
            session.navigationDelegate = navigationDelegate
            session.contentDelegate = contentDelegate
            session.open(runtime)
            session.setActive(false)
            sessionState.value = session
            errorUrl = null
            val knownTitle = tabsState.tab(id)?.title.orEmpty()
            updatePage(id) { PageState(url = url, title = knownTitle, loading = true) }
            session.loadUri(url)
        }

        fun setActive(active: Boolean) {
            sessionState.value?.setActive(active)
        }

        fun close() {
            val session = sessionState.value
            live = null
            sessionState.value = null
            if (session != null) runCatching { session.close() }
        }

        /** The page's process crashed or was killed: start a fresh session on the same address. */
        private fun recover(crashed: GeckoSession) {
            if (crashed !== live) return
            val tab = tabsState.tab(id)
            close()
            if (tab != null && tabsState.selected?.id == id) {
                ensureSession(tab.url)
                refreshActive()
            } else if (tab != null) {
                updatePage(id) { PageState(url = tab.url, title = tab.title) }
            }
        }

        private val progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(s: GeckoSession, url: String) {
                if (s !== live || gate.isErrorPage(url)) return
                updatePage(id) { copy(url = url, loading = true, progress = 0) }
            }

            override fun onPageStop(s: GeckoSession, success: Boolean) {
                if (s !== live) return
                updatePage(id) { copy(loading = false, progress = 100) }
                val shown = pages[id] ?: return
                val url = shown.url
                if (success && !ghost && errorUrl == null && url != null && UrlUtils.isHttp(url)) {
                    recordVisit(url, shown.title)
                }
            }

            override fun onProgressChange(s: GeckoSession, progress: Int) {
                if (s !== live) return
                updatePage(id) { copy(progress = progress) }
            }
        }

        private val navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                s: GeckoSession,
                url: String?,
                perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean,
            ) {
                if (s !== live || url == null || gate.isErrorPage(url)) return
                errorUrl = null
                updatePage(id) {
                    // A new page starts without a title; a #fragment change keeps it.
                    val samePage = UrlUtils.normalize(url) == UrlUtils.normalize(this.url.orEmpty())
                    copy(url = url, title = if (samePage) title else "")
                }
                if (UrlUtils.isHttp(url)) updateTab(id, url = url)
            }

            override fun onCanGoBack(s: GeckoSession, canGoBack: Boolean) {
                if (s === live) updatePage(id) { copy(canGoBack = canGoBack) }
            }

            override fun onCanGoForward(s: GeckoSession, canGoForward: Boolean) {
                if (s === live) updatePage(id) { copy(canGoForward = canGoForward) }
            }

            override fun onLoadRequest(
                s: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest,
            ): GeckoResult<AllowOrDeny>? = GeckoResult.fromValue(gate.decide(request.uri))

            // target=_blank and window.open that Gecko lets through (user-initiated) open a new tab.
            // The page gets no window object back (null), so pop-ups that talk to their opener do not work yet.
            override fun onNewSession(s: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
                if (s === live && UrlUtils.isHttp(uri) && gate.decide(uri) == AllowOrDeny.ALLOW) openTab(uri)
                return null
            }

            override fun onLoadError(s: GeckoSession, uri: String?, error: WebRequestError): GeckoResult<String>? {
                if (s === live && uri != null) {
                    errorUrl = uri
                    updatePage(id) { copy(url = uri, title = "", loading = false, progress = 100) }
                }
                return GeckoResult.fromValue(gate.errorPage(uri))
            }
        }

        private val contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(s: GeckoSession, title: String?) {
                if (s !== live || errorUrl != null) return
                val clean = title.orEmpty()
                updatePage(id) { copy(title = clean) }
                updateTab(id, title = clean)
                val url = pages[id]?.url
                if (!ghost && url != null && UrlUtils.isHttp(url)) titleKnown(url, clean)
            }

            override fun onCrash(s: GeckoSession) = recover(s)

            override fun onKill(s: GeckoSession) = recover(s)
        }
    }

    private companion object {
        const val THUMBNAIL_WIDTH = 420
    }
}
