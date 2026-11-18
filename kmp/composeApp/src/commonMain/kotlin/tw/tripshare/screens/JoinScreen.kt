package tw.tripshare.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import tw.tripshare.core.InviteException
import tw.tripshare.core.Member
import tw.tripshare.data.PendingJoin
import tw.tripshare.ui.ConfirmDialog
import tw.tripshare.ui.InkButton
import tw.tripshare.ui.InkCard
import tw.tripshare.ui.InkField
import tw.tripshare.ui.LocalNav
import tw.tripshare.ui.LocalToasts
import tw.tripshare.ui.Page
import tw.tripshare.ui.Screen
import tw.tripshare.ui.Txt

/** Shown when an invite fails; both join paths word it alike. */
fun inviteErrorText(e: Throwable): String =
    (e as? InviteException)?.kind?.message ?: "加入失敗，請檢查網路後再試一次"

/**
 * Enter a friend's invite code, then pick your own name from the places
 * the trip's owner set up. Someone with no place can't join.
 */
@Composable
fun JoinScreen() {
    val repo = LocalSession.current.repo
    val nav = LocalNav.current
    val toasts = LocalToasts.current
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<PendingJoin?>(null) }
    var places by remember { mutableStateOf<List<Member>?>(null) }
    var asking by remember { mutableStateOf<Member?>(null) }

    fun check() {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            runCatching {
                val p = repo.checkInvite(code)
                p to repo.openPlaces(p)
            }.onSuccess { (p, l) -> pending = p; places = l }
                .onFailure { error = inviteErrorText(it) }
            busy = false
        }
    }

    fun take(m: Member) {
        val p = pending ?: return
        busy = true
        scope.launch {
            runCatching { repo.join(p, m.id) }
                .onSuccess { nav.replace(Screen.TripView(p.tripId)) }
                .onFailure {
                    // Someone else took the place a moment earlier, or the owner removed it.
                    places = runCatching { repo.openPlaces(p) }.getOrNull() ?: places
                    toasts.show("這個名字剛被選走或移除了，請再選一次")
                }
            busy = false
        }
    }

    Page("加入旅程", onBack = { nav.pop() }) {
        val l = places
        when {
            l == null -> {
                Txt("請朋友在旅程的「成員」頁產生邀請碼，24 小時內有效。")
                InkField(code, { code = it }, "邀請碼（8 碼）", maxLength = 12, error = error, capitalize = true, onDone = ::check)
                InkButton(if (busy) "確認中…" else "下一步", ::check, Modifier.fillMaxWidth(), enabled = !busy)
            }
            l.isEmpty() -> {
                Txt("名單上沒有空著的名字。請群主先在「成員」頁把你加進名單，再用同一組邀請碼加入。")
                InkButton("重新整理", ::check, Modifier.fillMaxWidth(), enabled = !busy)
            }
            else -> {
                Txt("你是哪一位？", size = 22, bold = true)
                Txt("沒有你的名字的話，請群主先把你加進名單。", size = 14)
                l.forEach { m -> InkCard(Modifier.fillMaxWidth(), onClick = { if (!busy) asking = m }) { Txt(m.name, size = 18) } }
            }
        }
    }

    asking?.let { m ->
        ConfirmDialog(
            title = "你是「${m.name}」嗎？",
            body = "選了之後，記在這個名字的帳都算你的。",
            confirm = "是我",
            onConfirm = { take(m) },
            onDismiss = { asking = null },
        )
    }
}
