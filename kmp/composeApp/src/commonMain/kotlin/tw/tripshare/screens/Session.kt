package tw.tripshare.screens

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import tw.tripshare.data.TripRepository

/**
 * Everything the screens need for one signed-in device. [background] runs
 * writes that must outlive the screen that started them: offline, a write
 * only completes once the device is back online.
 */
class Session(val repo: TripRepository, val background: CoroutineScope)

val LocalSession = staticCompositionLocalOf<Session> { error("No session") }
