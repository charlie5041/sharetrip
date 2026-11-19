package tw.tripshare

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.initialize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import tw.tripshare.data.TripRepository
import tw.tripshare.screens.ImportScreen
import tw.tripshare.screens.JoinScreen
import tw.tripshare.screens.LocalSession
import tw.tripshare.screens.LoginScreen
import tw.tripshare.data.Google
import tw.tripshare.screens.Session
import tw.tripshare.screens.TripFormScreen
import tw.tripshare.screens.TripListScreen
import tw.tripshare.screens.ExpenseFormScreen
import tw.tripshare.screens.FlightFormScreen
import tw.tripshare.screens.ItemFormScreen
import tw.tripshare.screens.LodgingFormScreen
import tw.tripshare.screens.TripScreen
import tw.tripshare.ui.AppTheme
import tw.tripshare.ui.BackPress
import tw.tripshare.ui.CenterMessage
import tw.tripshare.ui.InkButton
import tw.tripshare.ui.LocalNav
import tw.tripshare.ui.LocalToasts
import tw.tripshare.ui.Navigator
import tw.tripshare.ui.Page
import tw.tripshare.ui.Screen
import tw.tripshare.ui.Toasts
import tw.tripshare.data.Reporter
import dev.gitlive.firebase.firestore.LocalCacheSettings
import dev.gitlive.firebase.firestore.firestoreSettings
import kotlinx.coroutines.CoroutineExceptionHandler

private var firebaseStarted = false

/**
 * Starts Firebase and restores this device's account. Null when nobody is
 * signed in: 同遊 needs a Google account, so nobody is signed in anonymously
 * any more (identities from before that are linked on the login screen).
 */
private suspend fun startSession(restart: () -> Unit): Session? {
    if (!firebaseStarted) {
        installCrashHook(Reporter::recordError)
        Firebase.initialize(platformContext(), firebaseOptions)
        // Keeps trips on the phone, so the app opens and records offline.
        // Android does this by default; the web needs to be told, before
        // the first read.
        if (isWeb) {
            runCatching {
                Firebase.firestore.settings = firestoreSettings {
                    cacheSettings = LocalCacheSettings.Persistent.newBuilder().build()
                }
            }.onFailure { Reporter.recordError("persistence: $it", null) }
        }
        firebaseStarted = true
    }
    val auth = Firebase.auth
    withTimeout(20_000) {
        // On the web the saved sign-in is restored asynchronously, so
        // currentUser is still null at first; treating that as signed out
        // would send someone who is signed in to the login screen.
        auth.currentUser ?: auth.authStateChanged.first()
    } ?: return null
    val uid = auth.currentUser!!.uid
    val crashes = CoroutineExceptionHandler { _, e -> Reporter.recordError(e.toString(), e.stackTraceToString()) }
    val background = CoroutineScope(SupervisorJob() + crashes)
    Reporter.attach(Firebase.firestore, uid, background)
    return Session(TripRepository(Firebase.firestore, uid), background, restart)
}

@Composable
fun App() {
    AppTheme {
        var attempt by remember { mutableIntStateOf(0) }
        var session by remember { mutableStateOf<Session?>(null) }
        var failed by remember { mutableStateOf(false) }
        var signedOut by remember { mutableStateOf(false) }
        val restart: () -> Unit = { session = null; signedOut = false; attempt++ }
        val toasts = remember { Toasts() }
        LaunchedEffect(attempt) {
            failed = false
            runCatching { startSession(restart) }
                .onSuccess { session = it; signedOut = it == null }
                .onFailure { failed = true }
        }
        CompositionLocalProvider(LocalToasts provides toasts) {
            val s = session
            if (signedOut) {
                LoginScreen(anonymous = null, onSignedIn = restart)
            } else if (s != null && Google.linkedEmail() == null) {
                // From before Google was required: link it, keeping its uid and trips.
                LoginScreen(anonymous = s.repo, onSignedIn = restart)
            } else if (s == null) {
                Page("同遊") {
                    if (failed) {
                        CenterMessage("連不上伺服器，請確認網路後再試一次") {
                            InkButton("重試", { attempt++ }, Modifier.fillMaxWidth())
                        }
                    } else {
                        CenterMessage("同遊啟動中…")
                    }
                }
            } else {
                // A new navigator per account, so nothing from another identity survives.
                val nav = remember(s.repo.uid) { Navigator(Screen.TripList) }
                // Keeps each screen's rememberSaveable state while others sit on top of it.
                val saved = rememberSaveableStateHolder()
                remember(nav, saved) { nav.onDrop = { saved.removeState(it) } }
                DisposableEffect(nav) {
                    BackPress.handler = nav::pop
                    onDispose { BackPress.handler = null }
                }
                CompositionLocalProvider(LocalSession provides s, LocalNav provides nav) {
                    saved.SaveableStateProvider(nav.currentKey) {
                        when (val screen = nav.current) {
                            Screen.TripList -> TripListScreen()
                            Screen.NewTrip -> TripFormScreen()
                            Screen.Join -> JoinScreen()
                            is Screen.TripView -> TripScreen(screen.tripId)
                            is Screen.ExpenseForm -> ExpenseFormScreen(screen.tripId, screen.expenseId, screen.date)
                            is Screen.ItemForm -> ItemFormScreen(screen.tripId, screen.itemId, screen.date)
                            is Screen.FlightForm -> FlightFormScreen(screen.tripId, screen.flightId)
                            is Screen.LodgingForm -> LodgingFormScreen(screen.tripId, screen.lodgingId)
                            is Screen.Import -> ImportScreen(screen.tripId)
                        }
                    }
                }
            }
        }
    }
}
