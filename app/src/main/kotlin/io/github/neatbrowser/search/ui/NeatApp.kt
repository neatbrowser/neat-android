package io.github.neatbrowser.search.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.mozilla.geckoview.GeckoView
import io.github.neatbrowser.search.R
import io.github.neatbrowser.search.browser.BrowserController
import io.github.neatbrowser.search.core.SearchEngine

/**
 * Step 1: one page. Home screen until something is opened, then the GeckoView with the bottom bar.
 * The tab switcher, ghost mode, history and the full page menu come in later steps.
 */
@Composable
fun NeatApp(
    browser: BrowserController,
    incomingUrl: String?,
    onIncomingConsumed: () -> Unit,
) {
    val context = LocalContext.current
    val engine = SearchEngine.Google
    var searchOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val page = browser.state

    LaunchedEffect(incomingUrl) {
        if (incomingUrl != null) {
            browser.load(incomingUrl)
            searchOpen = false
            menuOpen = false
            onIncomingConsumed()
        }
    }

    // Back closes an open panel; otherwise the system default applies.
    BackHandler(enabled = searchOpen || menuOpen) {
        searchOpen = false
        menuOpen = false
    }

    Box(Modifier.fillMaxSize().background(NeatColors.Navy)) {
        if (page.url != null) {
            Column(Modifier.fillMaxSize().background(NeatColors.PageBackground)) {
                Box(Modifier.weight(1f).fillMaxWidth().statusBarsPadding()) {
                    AndroidView(
                        factory = { ctx -> GeckoView(ctx) },
                        update = { view ->
                            // A new session replaces the old one after a content-process crash.
                            if (view.session !== browser.session) view.setSession(browser.session)
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (page.loading && page.progress < 100) {
                        LinearProgressIndicator(
                            progress = { page.progress / 100f },
                            modifier = Modifier.fillMaxWidth().height(2.dp),
                            color = NeatColors.Cyan,
                            trackColor = Color.Transparent,
                        )
                    }
                }
                BottomBar(
                    mode = BarMode.Page,
                    onTabs = { toast(context, R.string.coming_soon) },
                    onNew = { menuOpen = false; searchOpen = true },
                    onRight = { searchOpen = false; menuOpen = true },
                )
            }
        } else {
            HomeScreen()
            BottomBar(
                mode = BarMode.Home,
                onTabs = { toast(context, R.string.coming_soon) },
                onNew = { searchOpen = true },
                onRight = { toast(context, R.string.coming_soon) },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        SearchSheet(
            visible = searchOpen,
            engine = engine,
            onDismiss = { searchOpen = false },
            onOpen = { url ->
                searchOpen = false
                browser.load(url)
            },
        )

        PageMenu(
            visible = menuOpen,
            page = page,
            onDismiss = { menuOpen = false },
            onBack = { browser.goBack() },
            onForward = { browser.goForward() },
            onCopyLink = {
                page.url?.let { copyLink(context, it) }
                menuOpen = false
            },
            onReload = {
                browser.reload()
                menuOpen = false
            },
        )
    }
}

private fun toast(context: Context, @StringRes message: Int) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

private fun copyLink(context: Context, url: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("url", url))
    // Android 13 and later show their own confirmation.
    if (Build.VERSION.SDK_INT < 33) toast(context, R.string.link_copied)
}
