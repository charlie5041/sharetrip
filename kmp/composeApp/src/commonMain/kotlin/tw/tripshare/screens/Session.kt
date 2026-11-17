package tw.tripshare.screens

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import tw.tripshare.data.ExchangeRates
import tw.tripshare.data.TripRepository

/**
 * Everything the screens need for one signed-in device. [background] runs
 * writes that must outlive the screen that started them: offline, a write
 * only completes once the device is back online.
 */
class Session(
    val repo: TripRepository,
    val background: CoroutineScope,
    /** Starts over as whoever is signed in now, e.g. after switching to a Google-backed identity. */
    val restart: () -> Unit,
) {
    val rates = ExchangeRates()
}

val LocalSession = staticCompositionLocalOf<Session> { error("No session") }
