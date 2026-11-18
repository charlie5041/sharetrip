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
    /** A stop on [date]; edits [itemId] when set. */
    data class ItemForm(val tripId: String, val itemId: String?, val date: String) : Screen
    data class FlightForm(val tripId: String, val flightId: String?) : Screen
    data class LodgingForm(val tripId: String, val lodgingId: String?) : Screen
    /** 從試算表匯入 into this trip. */
    data class Import(val tripId: String) : Screen
}

/**
 * A plain back stack. Small enough not to need a navigation library, which
 * would add to the web download. Each entry has its own [currentKey], so a
 * screen's saved state (tab, picked day, scroll) waits for it underneath
 * the screens pushed on top, and [onDrop] lets that state go once the
 * entry leaves the stack.
 */
class Navigator(start: Screen) {
    private class Entry(val key: Int, val screen: Screen)

    private var nextKey = 0
    private val stack = mutableStateListOf(Entry(nextKey++, start))
    val current: Screen get() = stack.last().screen
    val currentKey: Int get() = stack.last().key
    val canGoBack: Boolean get() = stack.size > 1

    /** Told the key of each entry that leaves the stack. */
    var onDrop: (Int) -> Unit = {}

    fun push(s: Screen) { stack.add(Entry(nextKey++, s)) }

    /** Swaps the top screen, e.g. the new-trip form for the trip it made. */
    fun replace(s: Screen) {
        onDrop(currentKey)
        stack[stack.lastIndex] = Entry(nextKey++, s)
    }

    /** False when already at the first screen, so the system may close the app. */
    fun pop(): Boolean {
        if (stack.size <= 1) return false
        onDrop(stack.removeAt(stack.lastIndex).key)
        return true
    }

    fun popToRoot() { while (stack.size > 1) pop() }
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
