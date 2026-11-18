@file:OptIn(ExperimentalWasmJsInterop::class)

package tw.tripshare

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document

/** index.html keeps track of how far start-up got, to explain a blank screen. */
private fun jsBoot(state: String): Unit = js("{ window.tripshareBoot = state; try { localStorage.setItem('tripshare-boot', state); } catch (_) {} }")

private fun jsReady(): Unit = js("{ if (window.tripshareReady) window.tripshareReady(); }")

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    jsBoot("started")
    document.getElementById("status")?.remove()
    // A fixed-size container: inside <body> the page height is unbounded,
    // which scrolling content can't be measured against.
    ComposeViewport(document.getElementById("app")!!) {
        App()
        LaunchedEffect(Unit) {
            withFrameNanos { }
            jsReady()
        }
    }
}
