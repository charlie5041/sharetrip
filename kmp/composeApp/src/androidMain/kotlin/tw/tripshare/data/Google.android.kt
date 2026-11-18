package tw.tripshare.data

import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.FirebaseAuthUserCollisionException
import dev.gitlive.firebase.auth.GoogleAuthProvider
import dev.gitlive.firebase.auth.auth
import tw.tripshare.currentActivity

/**
 * Asks Android's account picker for a Google id token. The browser flow
 * can't find its way back to the app on some phones, which is why the
 * Flutter app used the system picker too.
 */
private suspend fun googleIdToken(): String {
    val activity = currentActivity?.get() ?: error("畫面還沒準備好")
    val option = GetSignInWithGoogleOption.Builder(GOOGLE_WEB_CLIENT_ID).build()
    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
    val credential = CredentialManager.create(activity).getCredential(activity, request).credential
    if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        error("Google 沒有回傳登入憑證")
    }
    return GoogleIdTokenCredential.createFrom(credential.data).idToken
}

private suspend fun attempt(step: suspend () -> Unit): GoogleResult = try {
    step()
    GoogleResult.Ok
} catch (e: GetCredentialCancellationException) {
    GoogleResult.Cancelled
} catch (e: NoCredentialException) {
    GoogleResult.Failed("這台手機叫不出 Google 帳號選單，請確認已登入 Google 帳號")
} catch (e: FirebaseAuthUserCollisionException) {
    GoogleResult.InUse(null)
} catch (e: Exception) {
    GoogleResult.Failed("Google 登入失敗（${e.message ?: e::class.simpleName}）")
}

actual fun platformGoogleLink(): suspend () -> GoogleResult = {
    attempt {
        val user = Firebase.auth.currentUser ?: error("還沒登入")
        user.linkWithCredential(GoogleAuthProvider.credential(googleIdToken(), null))
    }
}

actual fun platformGoogleSignIn(): suspend () -> GoogleResult = {
    attempt { Firebase.auth.signInWithCredential(GoogleAuthProvider.credential(googleIdToken(), null)) }
}
