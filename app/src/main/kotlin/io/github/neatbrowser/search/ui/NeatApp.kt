package io.github.neatbrowser.search.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import io.github.neatbrowser.search.R
import io.github.neatbrowser.search.browser.BrowserController
import io.github.neatbrowser.search.core.SearchEngine
import org.mozilla.geckoview.GeckoView

/**
 * The whole screen: the page (or the home screen) with the bottom bar, and the panels that slide
 * over it — search, page menu and tab switcher. Only one panel is open at a time.
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
    var switcherOpen by remember { mutableStateOf(false) }

    val ghost = browser.ghostMode
    val page = browser.page
    val selected = browser.selectedTab

    fun closePanels() {
        searchOpen = false
        menuOpen = false
        switcherOpen = false
    }

    LaunchedEffect(incomingUrl) {
        if (incomingUrl != null) {
            browser.openFromOutside(incomingUrl)
            closePanels()
            onIncomingConsumed()
        }
    }

    // Ghost mode hides the app in the recent-apps list and blocks screenshots.
    SecureWindow(secure = ghost)

    // Back closes the open panel; otherwise the system default applies.
    BackHandler(enabled = searchOpen || menuOpen || switcherOpen) { closePanels() }

    Box(Modifier.fillMaxSize().background(if (ghost) NeatColors.GhostTop else NeatColors.Navy)) {
        if (selected != null) {
            Column(Modifier.fillMaxSize().background(NeatColors.PageBackground)) {
                Box(Modifier.weight(1f).fillMaxWidth().statusBarsPadding()) {
                    AndroidView(
                        factory = { ctx -> GeckoView(ctx).also { browser.attachView(it) } },
                        update = { view ->
                            // Showing another tab (or a new session after a crash) means showing another session.
                            val session = browser.selectedSession
                            if (session != null && view.session !== session) view.setSession(session)
                        },
                        onRelease = { view ->
                            browser.detachView(view)
                            view.releaseSession()
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
                    ghost = ghost,
                    onTabs = {
                        browser.captureThumbnail()
                        closePanels()
                        switcherOpen = true
                    },
                    onNew = { closePanels(); searchOpen = true },
                    onRight = { closePanels(); menuOpen = true },
                )
            }
        } else {
            HomeScreen(ghost = ghost)
            BottomBar(
                mode = BarMode.Home,
                ghost = ghost,
                onTabs = { closePanels(); switcherOpen = true },
                onNew = { closePanels(); searchOpen = true },
                onRight = { toast(context, R.string.coming_soon) },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        TabSwitcher(
            visible = switcherOpen,
            tabs = browser.visibleTabs,
            selectedId = selected?.id,
            ghost = ghost,
            thumbnails = browser.thumbnails,
            onSelect = { id ->
                browser.select(id)
                switcherOpen = false
            },
            onClose = { id -> browser.closeTab(id) },
            onClearAll = { browser.clearAll() },
            onNew = { closePanels(); searchOpen = true },
            onDismiss = { switcherOpen = false },
            onToggleGhost = { browser.setGhostMode(!ghost) },
        )

        SearchSheet(
            visible = searchOpen,
            ghost = ghost,
            engine = engine,
            pins = browser.pins,
            history = browser.history,
            onToggleGhost = { browser.setGhostMode(!ghost) },
            onDismiss = { searchOpen = false },
            onOpen = { url ->
                searchOpen = false
                browser.openTab(url)
            },
            onRemovePin = browser::removePin,
            onRemoveHistory = browser::removeFromHistory,
            onClearHistory = browser::clearHistory,
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
            pinned = browser.isPinned,
            ghost = ghost,
            onTogglePin = { browser.togglePin() },
            onPinUnavailable = { toast(context, R.string.pin_unavailable_ghost) },
        )

        // Above the bottom bar, wherever the bar is.
        val cleared = browser.cleared
        UndoBar(
            visible = cleared != null && cleared.ghost == ghost,
            token = cleared,
            onUndo = { browser.undoClear() },
            onExpire = { browser.commitClear() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 88.dp),
        )
    }
}

/** While [secure], the window cannot be captured: no screenshots, and a blank card in recent apps. */
@Composable
private fun SecureWindow(secure: Boolean) {
    val context = LocalContext.current
    DisposableEffect(secure) {
        val window = context.findActivity()?.window
        if (secure) window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
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
