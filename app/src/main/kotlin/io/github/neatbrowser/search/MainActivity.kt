package io.github.neatbrowser.search

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.neatbrowser.search.browser.BrowserController
import io.github.neatbrowser.search.browser.Storage
import io.github.neatbrowser.search.core.UrlUtils
import io.github.neatbrowser.search.ui.NeatApp
import io.github.neatbrowser.search.ui.NeatTheme

class MainActivity : ComponentActivity() {
    private lateinit var browser: BrowserController
    private var incomingUrl by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        browser = BrowserController(this, Storage(this))
        incomingUrl = webUrlFrom(intent)
        setContent {
            NeatTheme {
                NeatApp(
                    browser = browser,
                    incomingUrl = incomingUrl,
                    onIncomingConsumed = { incomingUrl = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingUrl = webUrlFrom(intent)
    }

    override fun onResume() {
        super.onResume()
        browser.onResume()
    }

    override fun onPause() {
        browser.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        browser.destroy()
        super.onDestroy()
    }

    /** Only http and https links from other apps are opened. */
    private fun webUrlFrom(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        return intent.dataString?.takeIf { UrlUtils.isHttp(it) }
    }
}
