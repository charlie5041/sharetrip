package tw.tripshare.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import tw.tripshare.core.Currency
import tw.tripshare.core.Mahjong
import tw.tripshare.core.MahjongGame
import tw.tripshare.core.MahjongHand
import tw.tripshare.core.shortDate
import tw.tripshare.core.ymd
import tw.tripshare.loadText
import tw.tripshare.localDateTime
import tw.tripshare.nowMillis
import tw.tripshare.saveText
import tw.tripshare.ui.AppColors
import tw.tripshare.ui.AppShape
import tw.tripshare.ui.CenterMessage
import tw.tripshare.ui.ConfirmDialog
import tw.tripshare.ui.InkButton
import tw.tripshare.ui.InkCard
import tw.tripshare.ui.InkDialog
import tw.tripshare.ui.InkField
import tw.tripshare.ui.LocalNav
import tw.tripshare.ui.LocalToasts
import tw.tripshare.ui.Looks
import tw.tripshare.ui.Navigator
import tw.tripshare.ui.Page
import tw.tripshare.ui.RoundAction
import tw.tripshare.ui.Screen
import tw.tripshare.ui.SectionTitle
import tw.tripshare.ui.TextAction
import tw.tripshare.ui.Txt
import tw.tripshare.ui.deco
import tw.tripshare.ui.onColor
import tw.tripshare.ui.Chip

/*
 * 麻將計點: a small scoreboard inside the trip. Each hand records who 胡,
 * off whose discard or 自摸, and how many 台 (the players count them,
 * 莊 and 連莊 included). When the game ends its result goes into 結算 as
 * expenses; whether those count toward 總花費 is asked only then.
 */

/** "tripId/gameId" of the scoreboard to come back to after the app restarts. */
private const val OPEN_KEY = "mahjong-open"

/**
 * Reopens the scoreboard that was on screen when the app was closed, so
 * someone at the table doesn't dig through ⋯ after every phone lock.
 * Cleared when the person leaves the scoreboard or the game is settled.
 */
fun resumeMahjong(nav: Navigator) {
    val parts = loadText(OPEN_KEY)?.split('/')?.takeIf { it.size == 2 && it.all(String::isNotEmpty) } ?: return
    nav.push(Screen.TripView(parts[0]))
    nav.push(Screen.Mahjong(parts[0], parts[1]))
}

private fun gameDate(g: MahjongGame): String {
    val t = localDateTime(g.startedAt)
    return shortDate(ymd(t.date)) + " " + tw.tripshare.core.hm(t.hour, t.minute)
}

/** "+270" / "−130" / "0", without the currency, for the scoreboard. */
private fun signed(c: Currency, v: Long): String = when {
    v > 0 -> "+" + grouped(c.format(v))
    v < 0 -> "−" + grouped(c.format(-v))
    else -> "0"
}

private fun plain(c: Currency, v: Long): String = grouped(c.format(v))

private fun currencyOf(g: MahjongGame): Currency = runCatching { Currency.of(g.currency) }.getOrElse { Currency.of("TWD") }

// ---- The trip's games ----------------------------------------------------

@Composable
fun MahjongListScreen(tripId: String) {
    val nav = LocalNav.current
    WithTrip(tripId) { data ->
        Page(
            "麻將計點",
            onBack = { nav.pop() },
            bottom = {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    InkButton("＋ 開新的一場", { nav.push(Screen.MahjongNew(tripId)) }, Modifier.weight(1f))
                }
            },
        ) {
            if (data.mahjongGames.isEmpty()) {
                CenterMessage("還沒有打過麻將\n開一場，每一筆記誰胡、幾台，打完一鍵寫進結算")
            }
            data.mahjongGames.forEach { g ->
                val cur = currencyOf(g)
                val hands = data.handsOf(g)
                val scores = Mahjong.scores(g, hands)
                InkCard(Modifier.fillMaxWidth(), onClick = { nav.push(Screen.Mahjong(tripId, g.id)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Txt(deco("🀄") + gameDate(g), Modifier.weight(1f), bold = true)
                        Txt(
                            if (data.isSettled(g)) "已寫進結算" else "進行中 · ${hands.size} 筆",
                            size = 13, color = if (data.isSettled(g)) AppColors.faint else AppColors.coral,
                        )
                    }
                    Txt(
                        scores.entries.joinToString("　") { (id, v) -> "${data.nameOf(id)} ${signed(cur, v)}" },
                        size = 14, color = AppColors.faint,
                    )
                }
            }
        }
    }
}

// ---- A new game ------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MahjongNewScreen(tripId: String) {
    val nav = LocalNav.current
    val session = LocalSession.current
    val toasts = LocalToasts.current
    WithTrip(tripId) { data ->
        val cur = data.base
        val twd = cur.code == "TWD"
        var picked by remember { mutableStateOf(if (data.members.size <= 4) data.members.map { it.id }.toSet() else emptySet()) }
        var base by remember { mutableStateOf(if (twd) "50" else "") }
        var perTai by remember { mutableStateOf(if (twd) "20" else "") }
        val baseMinor = cur.parse(base)
        val perTaiMinor = cur.parse(perTai)
        val players = data.members.filter { it.id in picked }
        val problem = when {
            players.size < 3 -> "選 3 到 4 位上桌的人"
            players.size > 4 -> "一桌最多 4 位"
            baseMinor == null || baseMinor == 0L -> "請填底"
            perTaiMinor == null -> "請填每台多少"
            else -> null
        }
        Page(
            "開一場麻將",
            onBack = { nav.pop() },
            bottom = {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (problem != null) Txt(problem, size = 13, color = AppColors.faint, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    InkButton("開始", {
                        if (problem != null) return@InkButton
                        val id = session.repo.newMahjongId(tripId)
                        val game = MahjongGame(
                            id = id, players = players.map { it.id }, baseMinor = baseMinor!!, perTaiMinor = perTaiMinor!!,
                            currency = cur.code, startedAt = nowMillis(), createdBy = "",
                        )
                        session.background.launch {
                            runCatching { session.repo.saveMahjongGame(tripId, game) }.onFailure { toasts.show("沒有存成功：${it.message}") }
                        }
                        nav.replace(Screen.Mahjong(tripId, id))
                    }, Modifier.fillMaxWidth(), enabled = problem == null)
                }
            },
        ) {
            SectionTitle("誰上桌？")
            if (data.members.size < 3) Txt("旅程成員不到 3 位，先到「成員」加人。", size = 14, color = AppColors.faint)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                data.members.forEach { m ->
                    Chip(m.name, m.id in picked, { picked = if (m.id in picked) picked - m.id else picked + m.id })
                }
            }
            SectionTitle("一筆多少（${cur.code}）")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InkField(base, { base = it }, "底", Modifier.weight(1f), keyboard = KeyboardType.Decimal, maxLength = 10)
                InkField(perTai, { perTai = it }, "每台", Modifier.weight(1f), keyboard = KeyboardType.Decimal, maxLength = 10)
            }
            Txt("放槍：放槍的人付「底 + 台數 × 每台」。自摸：其他人各付一份。台數由你們自己算好再填，莊和連莊也算在裡面。", size = 14, color = AppColors.faint)
        }
    }
}

// ---- One game's scoreboard -------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MahjongScreen(tripId: String, gameId: String) {
    val nav = LocalNav.current
    val session = LocalSession.current
    val toasts = LocalToasts.current
    // Come back here after a restart, until the person leaves or the game is settled.
    LaunchedEffect(tripId, gameId) { saveText(OPEN_KEY, "$tripId/$gameId") }
    DisposableEffect(tripId, gameId) {
        onDispose { if (!nav.has(Screen.Mahjong(tripId, gameId))) saveText(OPEN_KEY, null) }
    }
    WithTrip(tripId) { data ->
        val game = data.mahjongGames.firstOrNull { it.id == gameId }
        if (game == null) {
            LaunchedEffect(Unit) { saveText(OPEN_KEY, null) }
            Page("麻將", onBack = { nav.pop() }) { CenterMessage("找不到這一場，可能已經被刪掉了。") }
            return@WithTrip
        }
        val cur = currencyOf(game)
        val hands = data.handsOf(game)
        val scores = Mahjong.scores(game, hands)
        val settled = data.isSettled(game)
        var menu by remember { mutableStateOf(false) }
        var finishing by remember { mutableStateOf(false) }
        var deletingGame by remember { mutableStateOf(false) }
        var unsettling by remember { mutableStateOf(false) }
        var deletingHand by remember { mutableStateOf<MahjongHand?>(null) }

        fun write(failure: String, block: suspend () -> Unit) {
            session.background.launch { runCatching { block() }.onFailure { toasts.show(failure + "：${it.message}") } }
        }

        Page(
            "麻將 · " + gameDate(game).substringBefore(' '),
            onBack = { nav.pop() },
            actions = {
                TextAction("＋ 記帳", { nav.push(Screen.ExpenseForm(tripId, null)) }, color = AppColors.coral)
                RoundAction("⋯", { menu = true })
            },
        ) {
            Txt("底 ${plain(cur, game.baseMinor)} · 每台 ${plain(cur, game.perTaiMinor)} · ${cur.code}", size = 14, color = AppColors.faint)
            ScoreGrid(data, scores, cur)
            if (settled) {
                val inTotal = data.expenses.firstOrNull { it.mahjongId == game.id }?.inTotal ?: false
                InkCard(Modifier.fillMaxWidth(), fill = AppColors.yamabuki) {
                    Txt("已寫進結算" + if (inTotal) "（有算進總花費）" else "（不算總花費）", bold = true)
                    Txt("要再改分數，先撤回，改完再寫一次。", size = 14)
                    TextAction("撤回，繼續記", { unsettling = true }, color = AppColors.coral)
                }
            } else {
                InkButton(deco("🀄") + "記一筆", { nav.push(Screen.MahjongHandForm(tripId, gameId, null)) }, Modifier.fillMaxWidth())
            }
            SectionTitle(if (hands.isEmpty()) "還沒有記錄" else if (settled) "每一筆" else "每一筆（左滑可刪）")
            hands.withIndex().reversed().forEach { (i, h) ->
                val row = @Composable {
                    InkCard(
                        Modifier.fillMaxWidth(),
                        onClick = if (settled) null else ({ nav.push(Screen.MahjongHandForm(tripId, gameId, h.id)) }),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Txt("${i + 1}", size = 13, color = AppColors.faint)
                            Txt(handText(data, h), Modifier.weight(1f))
                            val price = game.price(h.tai)
                            Txt(if (h.selfDrawn) "各 ${plain(cur, price)}" else plain(cur, price), bold = true)
                        }
                    }
                }
                if (settled) row() else SwipeToDelete(onDelete = { deletingHand = h }) { row() }
            }
            if (!settled && hands.isNotEmpty()) {
                InkButton("結束這場，寫進結算", { finishing = true }, Modifier.fillMaxWidth(), fill = AppColors.card)
            }
        }

        if (menu) {
            InkDialog("這一場", { menu = false }, buttons = { TextAction("關閉", { menu = false }) }) {
                ActionRowLite("所有場次", "之前打過的也在這裡") { menu = false; nav.push(Screen.MahjongList(tripId)) }
                ActionRowLite("刪除這場", if (settled) "要先撤回結算" else "連同每一筆一起刪掉") {
                    menu = false
                    if (settled) toasts.show("這場已經寫進結算，要先撤回才能刪") else deletingGame = true
                }
            }
        }
        deletingHand?.let { h ->
            ConfirmDialog("刪除這一筆？", handText(data, h), "刪除", onConfirm = {
                write("沒有刪除成功") { session.repo.deleteMahjongHand(tripId, h.id) }
            }, onDismiss = { deletingHand = null })
        }
        if (deletingGame) {
            ConfirmDialog("刪除這一場？", "每一筆都會一起刪掉，不會影響結算。", "刪除", onConfirm = {
                write("沒有刪除成功") { session.repo.deleteMahjong(tripId, gameId) }
                nav.pop()
            }, onDismiss = { deletingGame = false })
        }
        if (unsettling) {
            ConfirmDialog("撤回結算？", "記帳裡這場的麻將紀錄會刪掉，結算回到打之前的樣子，可以繼續記。", "撤回", onConfirm = {
                write("沒有撤回成功") { session.repo.unsettleMahjong(tripId, gameId) }
            }, onDismiss = { unsettling = false })
        }
        if (finishing) {
            FinishDialog(data, scores, cur, onDismiss = { finishing = false }) { inTotal ->
                finishing = false
                val expenses = Mahjong.toExpenses(game, hands, inTotal, "麻將 · " + gameDate(game).substringBefore(' '), "")
                if (expenses.isEmpty()) {
                    toasts.show("大家打平，沒有要寫進結算的")
                } else {
                    write("沒有寫進去") { session.repo.settleMahjong(tripId, expenses) }
                    saveText(OPEN_KEY, null)
                    toasts.show("已寫進結算" + deco("🀄", before = false))
                    nav.pop()
                }
            }
        }
    }
}

private fun handText(data: TripData, h: MahjongHand): String =
    if (h.selfDrawn) "${data.nameOf(h.winnerId)} 自摸 · ${h.tai} 台"
    else "${data.nameOf(h.winnerId)} 胡，${data.nameOf(h.loserId!!)} 放槍 · ${h.tai} 台"

/** Two big totals per row; the scrapbook look sticks a yellow note on whoever leads. */
@Composable
private fun ScoreGrid(data: TripData, scores: Map<String, Long>, cur: Currency) {
    val scrap = Looks.scrapbook
    val lead = scores.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.key
    scores.entries.chunked(2).forEach { pair ->
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            pair.forEach { (id, v) ->
                val note = scrap && id == lead
                InkCard(
                    Modifier.weight(1f).then(if (note) Modifier.rotate(-1.5f) else Modifier),
                    fill = if (note) AppColors.yamabuki else AppColors.card,
                ) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Txt(data.nameOf(id), bold = true, maxLines = 1)
                        Txt(
                            signed(cur, v), size = 28, bold = true,
                            color = when {
                                v > 0 -> AppColors.good
                                v < 0 -> AppColors.error
                                else -> AppColors.faint
                            },
                        )
                    }
                }
            }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

/** The only place that asks whether the result counts toward 總花費. */
@Composable
private fun FinishDialog(
    data: TripData,
    scores: Map<String, Long>,
    cur: Currency,
    onDismiss: () -> Unit,
    onConfirm: (inTotal: Boolean) -> Unit,
) {
    var inTotal by remember { mutableStateOf(false) }
    val won = scores.values.filter { it > 0 }.sum()
    InkDialog("寫進旅程結算", onDismiss, buttons = {
        TextAction("取消", onDismiss)
        InkButton("寫進結算", { onConfirm(inTotal) })
    }) {
        val winners = scores.filter { it.value > 0 }.entries.joinToString("、") { "${data.nameOf(it.key)} 贏 ${plain(cur, it.value)}" }
        Txt(if (winners.isEmpty()) "大家打平。" else "$winners，會加進結算的「誰欠誰」。", size = 15)
        Txt("要算進總花費嗎？", bold = true, modifier = Modifier.padding(top = 6.dp))
        ChoiceRow("不算進（建議）", "總花費和「每人約」不變，只改誰欠誰。", !inTotal) { inTotal = false }
        ChoiceRow("算進", "總花費會多 ${money(cur, won)}（贏的總額），每人平均也會跟著變。", inTotal) { inTotal = true }
        Txt("寫進後在記帳看得到；要改可以回到這場按「撤回」。", size = 13, color = AppColors.faint)
    }
}

@Composable
private fun ChoiceRow(title: String, hint: String, on: Boolean, onClick: () -> Unit) {
    val accent = AppColors.coral
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(AppShape.small)
            .border(if (on) 2.dp else AppShape.line, if (on) accent else AppColors.line, AppShape.small)
            .background(if (on) AppColors.yamabuki.copy(alpha = if (Looks.scrapbook) 0.35f else 1f) else AppColors.card, AppShape.small)
            .selectable(selected = on, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(22.dp).drawBehind {
                drawCircle(if (on) accent else AppColors.faint, radius = size.minDimension / 2 - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
                if (on) drawCircle(accent, radius = 5.5.dp.toPx())
            },
        )
        Column(Modifier.weight(1f)) {
            Txt(title, bold = on, color = AppColors.ink)
            Txt(hint, size = 13, color = AppColors.faint)
        }
    }
}

@Composable
private fun ActionRowLite(title: String, subtitle: String, onClick: () -> Unit) = tw.tripshare.ui.ActionRow(title, subtitle, onClick)

// ---- Recording a hand ------------------------------------------------------

private enum class Win(val label: String) { discard("放槍"), self("自摸") }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MahjongHandScreen(tripId: String, gameId: String, handId: String?) {
    val nav = LocalNav.current
    val session = LocalSession.current
    val toasts = LocalToasts.current
    WithTrip(tripId) { data ->
        val game = data.mahjongGames.firstOrNull { it.id == gameId }
        val existing = handId?.let { id -> data.mahjongHands.firstOrNull { it.id == id } }
        if (game == null || (handId != null && existing == null)) {
            Page("記一筆", onBack = { nav.pop() }) { CenterMessage("這一筆已經被刪掉了。") }
            return@WithTrip
        }
        val cur = currencyOf(game)
        var winner by remember { mutableStateOf(existing?.winnerId) }
        var how by remember { mutableStateOf(if (existing?.selfDrawn == true) Win.self else Win.discard) }
        var loser by remember { mutableStateOf(existing?.loserId) }
        var tai by remember { mutableIntStateOf(existing?.tai ?: 0) }
        var deleting by remember { mutableStateOf(false) }
        val price = game.price(tai)
        val others = game.players.filter { it != winner }
        val ready = winner != null && (how == Win.self || (loser != null && loser != winner))

        fun save() {
            if (!ready) return
            val h = MahjongHand(
                id = existing?.id ?: "", gameId = gameId, winnerId = winner!!,
                loserId = if (how == Win.self) null else loser, tai = tai,
                at = existing?.at ?: nowMillis(), createdBy = existing?.createdBy ?: "",
            )
            session.background.launch {
                runCatching { session.repo.saveMahjongHand(tripId, h) }.onFailure { toasts.show("沒有存成功：${it.message}") }
            }
            nav.pop()
        }

        Page(
            if (existing == null) "記一筆" else "改這一筆",
            onBack = { nav.pop() },
            actions = { if (existing != null) TextAction("刪除", { deleting = true }, color = AppColors.error) },
            bottom = {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    InkButton("記下來", ::save, Modifier.weight(1f), enabled = ready)
                }
            },
        ) {
            SectionTitle("誰胡？")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                game.players.forEach { id ->
                    Chip(data.nameOf(id), winner == id, {
                        winner = id
                        if (loser == id) loser = null
                    })
                }
            }
            SectionTitle("怎麼胡？")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Win.entries.forEach { w -> Chip(w.label, how == w, { how = w }) }
            }
            if (how == Win.discard) {
                SectionTitle("誰放槍？")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    others.forEach { id -> Chip(data.nameOf(id), loser == id, { loser = id }) }
                }
            }
            SectionTitle("幾台？（自己算好，含莊、連莊）")
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StepButton("−") { if (tai > 0) tai-- }
                Txt("$tai", Modifier.width(72.dp), size = 40, bold = true, align = TextAlign.Center)
                StepButton("＋") { if (tai < 99) tai++ }
            }
            InkCard(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Txt("這一筆", size = 13, color = AppColors.faint)
                    val w = winner
                    Txt(
                        when {
                            w == null -> "先選誰胡"
                            how == Win.self -> "其他 ${game.players.size - 1} 家各付 ${data.nameOf(w)} ${plain(cur, price)}"
                            loser == null -> "選誰放槍"
                            else -> "${data.nameOf(loser!!)} 付 ${data.nameOf(w)} ${plain(cur, price)}"
                        },
                        size = 20, bold = true, align = TextAlign.Center,
                    )
                    Txt("${plain(cur, game.baseMinor)} + $tai 台 × ${plain(cur, game.perTaiMinor)}", size = 13, color = AppColors.faint)
                }
            }
        }
        if (deleting && existing != null) {
            ConfirmDialog("刪除這一筆？", handText(data, existing), "刪除", onConfirm = {
                session.background.launch {
                    runCatching { session.repo.deleteMahjongHand(tripId, existing.id) }.onFailure { toasts.show("沒有刪除成功：${it.message}") }
                }
                nav.pop()
            }, onDismiss = { deleting = false })
        }
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(56.dp).clip(CircleShape).border(AppShape.line, AppColors.line, CircleShape)
            .background(AppColors.card, CircleShape).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Txt(label, size = 24, bold = true, color = onColor(AppColors.card)) }
}

/** The ⋯ menu's 麻將 rows on 記帳: straight back to a game in progress, or all games. */
@Composable
fun MahjongMenuRows(data: TripData, onPick: (Screen) -> Unit) {
    val open = data.mahjongGames.filter { !data.isSettled(it) }
    open.forEach { g ->
        tw.tripshare.ui.ActionRow(deco("🀄") + "麻將 · " + gameDate(g), "進行中 · 第 ${data.handsOf(g).size} 筆，點了回到計分板") {
            onPick(Screen.Mahjong(data.trip.id, g.id))
        }
    }
    tw.tripshare.ui.ActionRow(
        if (open.isEmpty()) deco("🀄") + "麻將計點" else "麻將計點（所有場次）",
        "記每一筆輸贏，打完寫進結算",
    ) { onPick(if (data.mahjongGames.isEmpty()) Screen.MahjongNew(data.trip.id) else Screen.MahjongList(data.trip.id)) }
}
