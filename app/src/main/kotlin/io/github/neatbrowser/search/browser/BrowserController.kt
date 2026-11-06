package io.github.neatbrowser.search.browser

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.neatbrowser.search.R
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebRequestError

/** What the UI shows about the page in the engine. */
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
 * Owns the GeckoSession and mirrors its state into Compose.
 * Create it with an Activity context (links for other apps are started from it); call [onPause],
 * [onResume] and [destroy] from the Activity. The view side is a GeckoView that shows [session].
 */
class BrowserController(private val context: Context) {
    private val runtime: GeckoRuntime = Engine.runtime(context)

    private var active = true
    private var lastUrl: String? = null

    /** The session events are accepted from. Plain field: delegates may fire before [session] is assigned. */
    private var live: GeckoSession? = null

    var state by mutableStateOf(PageState())
        private set

    private val progressDelegate = object : GeckoSession.ProgressDelegate {
        override fun onPageStart(s: GeckoSession, url: String) {
            if (s !== live) return
            state = state.copy(url = url, loading = true, progress = 0)
        }

        override fun onPageStop(s: GeckoSession, success: Boolean) {
            if (s !== live) return
            state = state.copy(loading = false, progress = 100)
        }

        override fun onProgressChange(s: GeckoSession, progress: Int) {
            if (s !== live) return
            state = state.copy(progress = progress)
        }
    }

    private val navigationDelegate = object : GeckoSession.NavigationDelegate {
        override fun onLocationChange(
            s: GeckoSession,
            url: String?,
            perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
            hasUserGesture: Boolean,
        ) {
            if (s !== live || url == null) return
            lastUrl = url
            state = state.copy(url = url)
        }

        override fun onCanGoBack(s: GeckoSession, canGoBack: Boolean) {
            if (s === live) state = state.copy(canGoBack = canGoBack)
        }

        override fun onCanGoForward(s: GeckoSession, canGoForward: Boolean) {
            if (s === live) state = state.copy(canGoForward = canGoForward)
        }

        override fun onLoadRequest(
            s: GeckoSession,
            request: GeckoSession.NavigationDelegate.LoadRequest,
        ): GeckoResult<AllowOrDeny>? = GeckoResult.fromValue(decide(request.uri))

        // target=_blank and window.open that Gecko lets through (user-initiated): same page for now.
        override fun onNewSession(s: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
            if (decide(uri) == AllowOrDeny.ALLOW) session.loadUri(uri)
            return null
        }

        override fun onLoadError(s: GeckoSession, uri: String?, error: WebRequestError): GeckoResult<String>? =
            GeckoResult.fromValue(errorPage(uri))
    }

    private val contentDelegate = object : GeckoSession.ContentDelegate {
        override fun onTitleChange(s: GeckoSession, title: String?) {
            if (s === live) state = state.copy(title = title.orEmpty())
        }

        override fun onCrash(s: GeckoSession) = recover(s)

        override fun onKill(s: GeckoSession) = recover(s)
    }

    /** The session the GeckoView shows. It is replaced when the content process dies. */
    var session by mutableStateOf(openSession())
        private set

    private fun openSession(): GeckoSession {
        val s = GeckoSession()
        live = s
        s.progressDelegate = progressDelegate
        s.navigationDelegate = navigationDelegate
        s.contentDelegate = contentDelegate
        s.open(runtime)
        s.setActive(active)
        return s
    }

    fun load(url: String) {
        lastUrl = url
        state = PageState(url = url, loading = true)
        session.loadUri(url)
    }

    fun goBack() = session.goBack()
    fun goForward() = session.goForward()
    fun reload() = session.reload()

    fun onResume() {
        active = true
        session.setActive(true)
    }

    fun onPause() {
        active = false
        session.setActive(false)
    }

    fun destroy() {
        session.close()
    }

    /** The page's process crashed or was killed: start a fresh session on the same address. */
    private fun recover(crashed: GeckoSession) {
        if (crashed !== live) return
        val url = lastUrl
        state = PageState(url = url, loading = url != null)
        session = openSession()
        if (url != null) session.loadUri(url)
        runCatching { crashed.close() }
    }

    /** Only web pages are loaded; other apps get mail, phone and store links. */
    private fun decide(uri: String): AllowOrDeny {
        val parsed = Uri.parse(uri)
        return when (parsed.scheme?.lowercase()) {
            "http", "https", "blob" -> AllowOrDeny.ALLOW
            "about" -> if (uri.equals("about:blank", ignoreCase = true)) AllowOrDeny.ALLOW else AllowOrDeny.DENY
            // Our own error page. Gecko itself blocks data: navigations started by web content.
            "data" -> if (uri.startsWith(ERROR_PAGE_PREFIX)) AllowOrDeny.ALLOW else AllowOrDeny.DENY
            "mailto", "tel", "sms", "market", "intent" -> {
                openExternally(parsed)
                AllowOrDeny.DENY
            }
            else -> AllowOrDeny.DENY // javascript:, file:, content:, resource: and unknown schemes
        }
    }

    private fun openExternally(uri: Uri) {
        val intent = try {
            if (uri.scheme.equals("intent", ignoreCase = true)) {
                Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME).apply {
                    // Only a browsable activity may be started; never an explicit component or selector.
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    setComponent(null)
                    setSelector(null)
                }
            } else {
                Intent(Intent.ACTION_VIEW, uri)
            }
        } catch (e: Exception) {
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // nothing can handle it; ignore
        } catch (e: SecurityException) {
            // not allowed to start it; ignore
        }
    }

    private fun errorPage(uri: String?): String {
        val html = """<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<style>body{margin:0;padding:32px 24px;background:#0B1B2B;color:#E8EAED;font:16px/1.5 sans-serif}
h1{font-size:22px;margin:48px 0 12px}p{color:#9AA0A6;word-break:break-all}</style></head>
<body><h1>${esc(context.getString(R.string.error_title))}</h1>
<p>${esc(context.getString(R.string.error_hint))}</p><p>${esc(uri.orEmpty())}</p></body></html>"""
        return ERROR_PAGE_PREFIX + Base64.encodeToString(html.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    private fun esc(text: String) =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private companion object {
        const val ERROR_PAGE_PREFIX = "data:text/html;charset=utf-8;base64,"
    }
}
