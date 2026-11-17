package tw.tripshare

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseOptions
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.initialize
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Firebase project tripshare-f6209, shared with the Flutter app. */
expect val firebaseOptions: FirebaseOptions

/** The context Firebase needs on Android; null on the web. */
expect fun platformContext(): Any?

expect val platformName: String

data class TripRow(val id: String, val name: String, val startDate: String)

object Backend {
    private var started = false

    fun start() {
        if (started) return
        Firebase.initialize(platformContext(), firebaseOptions)
        started = true
    }

    /** Signs in anonymously (or reuses this device's account); returns its uid. */
    suspend fun signIn(): String {
        val auth = Firebase.auth
        auth.currentUser?.let { return it.uid }
        return auth.signInAnonymously().user!!.uid
    }

    fun myTrips(uid: String): Flow<List<TripRow>> = Firebase.firestore
        .collection("trips")
        .where { "memberUids" contains uid }
        .snapshots
        .map { snap ->
            snap.documents.map { d ->
                TripRow(
                    id = d.id,
                    name = runCatching { d.get<String>("name") }.getOrDefault("（無名）"),
                    startDate = runCatching { d.get<String>("startDate") }.getOrDefault(""),
                )
            }.sortedByDescending { it.startDate }
        }
}
