package tw.tripshare

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    document.getElementById("status")?.remove()
    // A fixed-size container: inside <body> the page height is unbounded,
    // which scrolling content can't be measured against.
    ComposeViewport(document.getElementById("app")!!) { App() }
}
