package io.github.neatbrowser.search.browser

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import io.github.neatbrowser.search.R
import org.mozilla.geckoview.AllowOrDeny

/**
 * Decides which addresses the engine may load, hands mail, phone and store links to other apps,
 * and builds the page shown when a load fails.
 */
internal class PageGate(private val context: Context) {

    /** Only web pages are loaded; other apps get mail, phone and store links. */
    fun decide(uri: String): AllowOrDeny {
        val parsed = Uri.parse(uri)
        return when (parsed.scheme?.lowercase()) {
            "http", "https", "blob" -> AllowOrDeny.ALLOW
            "about" -> if (uri.equals("about:blank", ignoreCase = true)) AllowOrDeny.ALLOW else AllowOrDeny.DENY
            // Our own error page. Gecko itself blocks data: navigations started by web content.
            "data" -> if (isErrorPage(uri)) AllowOrDeny.ALLOW else AllowOrDeny.DENY
            "mailto", "tel", "sms", "market", "intent" -> {
                openExternally(parsed)
                AllowOrDeny.DENY
            }
            else -> AllowOrDeny.DENY // javascript:, file:, content:, resource: and unknown schemes
        }
    }

    fun isErrorPage(uri: String): Boolean = uri.startsWith(ERROR_PAGE_PREFIX)

    fun errorPage(uri: String?): String {
        val title = esc(context.getString(R.string.page_error_title))
        val html = """<!doctype html><html><head><meta charset="utf-8"><title>$title</title>
<meta name="viewport" content="width=device-width,initial-scale=1">
<style>body{margin:0;padding:32px 24px;background:#0B1B2B;color:#E8EAED;font:16px/1.5 sans-serif}
h1{font-size:22px;margin:48px 0 12px}p{color:#9AA0A6;word-break:break-all}</style></head>
<body><h1>$title</h1>
<p>${esc(context.getString(R.string.page_error_hint))}</p><p>${esc(uri.orEmpty())}</p></body></html>"""
        return ERROR_PAGE_PREFIX + Base64.encodeToString(html.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
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

    private fun esc(text: String) =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    companion object {
        const val ERROR_PAGE_PREFIX = "data:text/html;charset=utf-8;base64,"
    }
}
