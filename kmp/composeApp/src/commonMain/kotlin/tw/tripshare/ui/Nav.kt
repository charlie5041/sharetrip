package tw.tripshare.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/** Every screen the app can show; the stack holds these. */
sealed interface Screen {
    data object TripList : Screen
    data object NewTrip : Screen
    data object Join : Screen
    data class TripView(val tripId: String) : Screen
    /** Adds an expense when [expenseId] is null, otherwise edits it. */
    data class ExpenseForm(val tripId: String, val expenseId: String?) : Screen
}

/**
 * A plain back stack. Small enough not to need a navigation library, which
 * would add to the web download.
 */
class Navigator(start: Screen) {
    private val stack = mutableStateListOf(start)
    val current: Screen get() = stack.last()
    val canGoBack: Boolean get() = stack.size > 1

    fun push(s: Screen) { stack.add(s) }

    /** Swaps the top screen, e.g. the new-trip form for the trip it made. */
    fun replace(s: Screen) { stack[stack.lastIndex] = s }

    /** False when already at the first screen, so the system may close the app. */
    fun pop(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    fun popToRoot() { while (stack.size > 1) stack.removeAt(stack.lastIndex) }
}

val LocalNav = staticCompositionLocalOf<Navigator> { error("No navigator") }

/** Short messages at the bottom of the screen, like a snack bar. */
class Toasts {
    var message by mutableStateOf<String?>(null)
        private set

    fun show(text: String) { message = text }
    fun clear() { message = null }
}

val LocalToasts = staticCompositionLocalOf<Toasts> { error("No toasts") }

/** Hook for the Android back button; set by the app, called by MainActivity. */
object BackPress {
    var handler: (() -> Boolean)? = null
    fun handle(): Boolean = handler?.invoke() ?: false
}
