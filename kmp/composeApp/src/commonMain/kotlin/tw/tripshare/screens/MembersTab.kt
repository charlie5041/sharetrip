package tw.tripshare.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import tw.tripshare.copyToClipboard
import tw.tripshare.core.InviteCodes
import tw.tripshare.core.Member
import tw.tripshare.data.RefusedException
import tw.tripshare.ui.ActionRow
import tw.tripshare.ui.AppColors
import tw.tripshare.ui.Avatar
import tw.tripshare.ui.ConfirmDialog
import tw.tripshare.ui.InkButton
import tw.tripshare.ui.InkCard
import tw.tripshare.ui.InkDialog
import tw.tripshare.ui.InkField
import tw.tripshare.ui.LocalNav
import tw.tripshare.ui.LocalToasts
import tw.tripshare.ui.TextAction
import tw.tripshare.ui.Txt

private sealed interface MemberDialog {
    data class Menu(val m: Member) : MemberDialog
    data class Name(val title: String, val initial: String, val m: Member?) : MemberDialog
    data class Confirm(val title: String, val body: String, val confirm: String, val action: suspend () -> Unit) : MemberDialog
    data class Invite(val code: String) : MemberDialog
}

@Composable
fun MembersTab(data: TripData) {
    val session = LocalSession.current
    val repo = session.repo
    val toasts = LocalToasts.current
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf<MemberDialog?>(null) }
    val tripId = data.trip.id

    /** Writes without waiting for the server; offline they land later. */
    fun write(block: suspend () -> Unit) {
        session.background.launch {
            runCatching { block() }.onFailure {
                toasts.show(if (it is RefusedException) it.message!! else "沒有存成功：${it.message}")
            }
        }
    }

    data.members.forEach { m ->
        val isMe = m.uid != null && m.uid == data.myUid
        // Only the owner manages the list; anyone may rename themselves.
        val canAct = data.isOwner || isMe
        InkCard(Modifier.fillMaxWidth(), onClick = if (canAct) ({ dialog = MemberDialog.Menu(m) }) else null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Avatar(m.name, joined = m.uid != null)
                Column(Modifier.weight(1f)) {
                    Txt(if (isMe) "${m.name}（我）" else m.name, size = 18)
                    when {
                        m.uid == null -> Txt("還沒加入", size = 13, color = AppColors.faint)
                        m.uid == data.trip.ownerUid -> Txt("群主", size = 13, color = AppColors.faint)
                    }
                }
            }
        }
    }

    InkButton("產生邀請碼", {
        scope.launch {
            runCatching { repo.createInvite(tripId) }
                .onSuccess { dialog = MemberDialog.Invite(it) }
                .onFailure { toasts.show("產生失敗，請確認網路後再試") }
        }
    }, Modifier.fillMaxWidth(), fill = AppColors.yamabuki)
    Txt(
        if (data.isOwner) "朋友加入時，要從「還沒加入」的名字裡選自己。名單上沒有的人進不來，請先加進名單。"
        else "名單由群主管理。要加人或刪人，請找群主。",
        size = 13, color = AppColors.faint,
    )
    if (data.isOwner) {
        InkButton("加入成員", { dialog = MemberDialog.Name("加入成員", "", null) }, Modifier.fillMaxWidth(), fill = AppColors.paper)
        TextAction("解散旅程", {
            dialog = MemberDialog.Confirm(
                "解散「${data.trip.name}」？", "所有人的行程、記帳、清單都會一起刪除，無法復原。", "解散",
            ) {
                nav.popToRoot()
                repo.dissolveTrip(tripId)
            }
        }, Modifier.fillMaxWidth(), color = AppColors.error)
    }

    when (val d = dialog) {
        null -> {}
        is MemberDialog.Menu -> {
            val m = d.m
            val isMe = m.uid != null && m.uid == data.myUid
            val canRename = data.isOwner || isMe
            val canRemove = data.isOwner && !isMe
            val canUnbind = canRemove && m.uid != null
            run {
                InkDialog(m.name, { dialog = null }, buttons = { TextAction("取消", { dialog = null }) }) {
                    if (canRename) ActionRow("改名字") { dialog = MemberDialog.Name("改名字", m.name, m) }
                    if (canUnbind) ActionRow("解除綁定", "他換手機或資料不見時用：名字和帳都留著，他再用邀請碼加入、選回這個名字就好") {
                        dialog = MemberDialog.Confirm(
                            "解除「${m.name}」的綁定？",
                            "他目前的手機會看不到這趟旅程。名字和帳都會留著，他用新的邀請碼加入、選「${m.name}」就能接回來。",
                            "解除綁定",
                        ) { repo.unbindMember(tripId, m) }
                    }
                    if (canRemove) ActionRow("移除成員") {
                        dialog = MemberDialog.Confirm(
                            "移除「${m.name}」？",
                            if (m.uid == null) "名單上不會再有這個名字。" else "他會看不到這趟旅程，名單上也不會再有這個名字。",
                            "移除",
                        ) { repo.removeMember(tripId, m) }
                    }
                }
            }
        }
        is MemberDialog.Name -> {
            var text by remember(d) { mutableStateOf(d.initial) }
            fun done() {
                val v = text.trim()
                dialog = null
                if (v.isEmpty()) return
                if (d.m == null) write { repo.addMember(tripId, v) } else write { repo.renameMember(tripId, d.m.id, v) }
            }
            InkDialog(d.title, { dialog = null }, buttons = {
                TextAction("取消", { dialog = null })
                InkButton("確定", ::done)
            }) { InkField(text, { text = it }, "名字", maxLength = 20, onDone = ::done) }
        }
        is MemberDialog.Confirm -> ConfirmDialog(d.title, d.body, d.confirm, onConfirm = { write(d.action) }, onDismiss = { dialog = null })
        is MemberDialog.Invite -> {
            val shown = InviteCodes.format(d.code)
            InkDialog("邀請碼", { dialog = null }, buttons = {
                TextAction("複製", { copyToClipboard(shown); toasts.show("已複製"); dialog = null })
                InkButton("好", { dialog = null })
            }) {
                Txt(shown, size = 34, bold = true)
                Txt("24 小時內有效。朋友打開同遊，按右上角「用邀請碼加入」。", size = 14)
            }
        }
    }
}
