package tw.tripshare.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import tw.tripshare.prefersReducedMotion
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.datetime.daysUntil
import tw.tripshare.art.TripScene
import tw.tripshare.core.ChecklistKind
import tw.tripshare.core.Flight
import tw.tripshare.core.ItineraryItem
import tw.tripshare.core.Lodging
import tw.tripshare.core.Transport
import tw.tripshare.core.datesBetween
import tw.tripshare.core.daysUntil
import tw.tripshare.core.mapLink
import tw.tripshare.core.parseYmd
import tw.tripshare.core.shortDate
import tw.tripshare.core.ymd
import tw.tripshare.openUrl
import tw.tripshare.today
import tw.tripshare.ui.AppColors
import tw.tripshare.ui.AppShape
import tw.tripshare.ui.CheckRow
import tw.tripshare.ui.Chip
import tw.tripshare.ui.HeroCard
import tw.tripshare.ui.InkButton
import tw.tripshare.ui.InkCard
import tw.tripshare.ui.InkField
import tw.tripshare.ui.LocalNav
import tw.tripshare.ui.LocalToasts
import tw.tripshare.ui.Looks
import tw.tripshare.ui.RoundAction
import tw.tripshare.ui.Screen
import tw.tripshare.ui.SectionTitle
import tw.tripshare.ui.Tag
import tw.tripshare.ui.TextAction
import tw.tripshare.ui.Txt

/** Runs a write in the background and says so if it fails. */
@Composable
private fun rememberWriter(): (suspend () -> Unit) -> Unit {
    val session = LocalSession.current
    val toasts = LocalToasts.current
    return { block -> session.background.launch { runCatching { block() }.onFailure { toasts.show("沒有存成功：${it.message}") } } }
}

/** A "地圖" link when there's somewhere to go, opening Google Maps. */
@Composable
private fun MapAction(label: String, place: String?, mapUrl: String?) {
    val link = mapLink(place, mapUrl) ?: return
    TextAction("📍 $label", { openUrl(link) })
}

// ---- Overview --------------------------------------------------------------

/** Title, countdown, flights and where everyone sleeps. */
@Composable
fun OverviewTab(data: TripData) {
    val nav = LocalNav.current
    val trip = data.trip
    val now = today()
    val days = daysUntil(trip.startDate, now)
    val ended = daysUntil(trip.endDate, now) < 0
    val (label, big) = when {
        days > 0 -> "距離出發還有" to "$days 天"
        ended -> "旅程已結束" to "辛苦了"
        else -> "旅行中" to "第 ${1 - days} 天"
    }
    if (!Looks.scrapbook) {
        // The minimal look keeps the top plain: no illustration, the countdown in big type.
        HeroCard(Modifier.fillMaxWidth()) {
            Txt("${shortDate(trip.startDate)} – ${shortDate(trip.endDate)}", size = 14)
            Txt(trip.name, size = 24, bold = true)
            Txt(label, Modifier.padding(top = 10.dp), size = 14)
            Txt(big, size = 34, bold = true)
        }
    } else InkCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TripScene(trip.name)
            Txt(trip.name, size = 26, bold = true, align = TextAlign.Center)
            Txt("${shortDate(trip.startDate)} – ${shortDate(trip.endDate)}")
            InkCard(fill = AppColors.coral) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Txt(label)
                    Txt(big, size = 32, bold = true)
                }
            }
        }
    }

    SectionTitle("航班") { TextAction("＋ 新增", { nav.push(Screen.FlightForm(trip.id, null)) }) }
    if (data.flights.isEmpty()) Txt("還沒有航班，新增後大家都看得到", size = 14, color = AppColors.faint)
    data.flights.forEach { FlightCard(trip.id, it) }

    SectionTitle("住宿") { TextAction("＋ 新增", { nav.push(Screen.LodgingForm(trip.id, null)) }) }
    if (data.lodgings.isEmpty()) Txt("還沒有住宿資訊", size = 14, color = AppColors.faint)
    data.lodgings.forEach { LodgingCard(trip.id, it) }

    InkCard(Modifier.fillMaxWidth(), onClick = { nav.push(Screen.Import(trip.id)) }) {
        Txt("📋 從試算表匯入", bold = true)
        Txt("把 Google 試算表或 Excel 上的花費、行程、住宿一次搬進來", size = 14, color = AppColors.faint)
    }
}

@Composable
private fun FlightCard(tripId: String, f: Flight) {
    val nav = LocalNav.current
    @Composable
    fun End(code: String, name: String?, date: String?, time: String?, modifier: Modifier, end: Boolean) {
        Column(modifier, horizontalAlignment = if (end) Alignment.End else Alignment.Start) {
            Txt(code, size = 26, bold = true)
            name?.let { Txt(it, size = 13) }
            time?.let { Txt(it, size = 18, bold = true) }
            date?.let { Txt(shortDate(it), size = 13) }
        }
    }
    InkCard(Modifier.fillMaxWidth(), fill = AppColors.yamabuki, onClick = { nav.push(Screen.FlightForm(tripId, f.id)) }) {
        Txt("✈ " + listOfNotNull(f.airline, f.flightNo).joinToString(" "), bold = true)
        Row(verticalAlignment = Alignment.CenterVertically) {
            End(f.fromCode, f.fromName, f.departDate, f.departTime, Modifier.weight(1f), end = false)
            Txt("→", size = 22)
            End(f.toCode, f.toName, f.arriveDate, f.arriveTime, Modifier.weight(1f), end = true)
        }
        f.note?.let { Txt(it, size = 14) }
    }
}

@Composable
private fun LodgingCard(tripId: String, l: Lodging) {
    val nav = LocalNav.current
    val nights = runCatching { parseYmd(l.checkIn).daysUntil(parseYmd(l.checkOut)) }.getOrDefault(0)
    InkCard(Modifier.fillMaxWidth(), fill = AppColors.wakatake, onClick = { nav.push(Screen.LodgingForm(tripId, l.id)) }) {
        Txt("🛏 ${l.name}", bold = true)
        Txt("${shortDate(l.checkIn)} 入住 → ${shortDate(l.checkOut)} 退房（$nights 晚）", size = 14)
        l.confirmation?.let { Txt("訂房編號：$it", size = 14) }
        l.address?.let { Txt(it, size = 14) }
        l.note?.let { Txt(it, size = 14) }
        MapAction("地圖", l.address ?: l.name, l.mapUrl)
    }
}

// ---- Itinerary -------------------------------------------------------------

fun transportColor(t: Transport): Color = when (t) {
    Transport.walk, Transport.car -> AppColors.wakatake
    Transport.train, Transport.flight, Transport.ferry -> AppColors.mizu
    Transport.bus, Transport.taxi -> AppColors.yamabuki
    Transport.none -> AppColors.white
}

/** Date switcher on top, the chosen day's timeline below. */
@Composable
fun ItineraryTab(data: TripData) {
    val nav = LocalNav.current
    val days = datesBetween(data.trip.startDate, data.trip.endDate)
    var picked by rememberSaveable(data.trip.id) { mutableStateOf<String?>(null) }
    val todayYmd = ymd(today())
    val day = picked?.takeIf { it in days } ?: todayYmd.takeIf { it in days } ?: days.first()
    val items = data.items.filter { it.date == day }.sortedWith(ItineraryItem.timelineOrder)

    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        days.forEachIndexed { i, d -> Chip("Day ${i + 1}　${shortDate(d)}", d == day, { picked = d }) }
    }
    InkButton("＋ 加行程", { nav.push(Screen.ItemForm(data.trip.id, null, day)) }, Modifier.fillMaxWidth())
    if (items.isEmpty()) {
        Txt("這天還沒有行程", Modifier.fillMaxWidth().padding(24.dp), align = TextAlign.Center, color = AppColors.faint)
        return
    }
    // A little bounce when switching days, unless the phone asks for stillness.
    val pop = remember { Animatable(1f) }
    val calm = remember { prefersReducedMotion() }
    var shown by remember { mutableStateOf(day) }
    LaunchedEffect(day) {
        if (day != shown && !calm) {
            pop.snapTo(0f)
            shown = day
            pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 320f))
        }
        shown = day
    }
    Column(
        Modifier.graphicsLayer {
            alpha = pop.value.coerceIn(0f, 1f)
            val s = 0.92f + 0.08f * pop.value
            scaleX = s
            scaleY = s
        },
    ) {
        items.forEachIndexed { i, item -> TimelineRow(data.trip.id, item, first = i == 0, last = i == items.lastIndex) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimelineRow(tripId: String, item: ItineraryItem, first: Boolean, last: Boolean) {
    val nav = LocalNav.current
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Txt(item.time.orEmpty(), Modifier.width(48.dp).padding(top = 20.dp), size = 15, bold = true, align = TextAlign.End)
        Box(Modifier.width(28.dp).fillMaxHeight().drawBehind { rail(first, last, item.mustGo) })
        InkCard(
            Modifier.weight(1f).padding(vertical = 6.dp),
            fill = if (item.mustGo) Color(0xFFFFF0EC) else AppColors.white,
            onClick = { nav.push(Screen.ItemForm(tripId, item.id, item.date)) },
        ) {
            if (item.transport != Transport.none || item.mustGo) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (item.transport != Transport.none) Tag(item.transport.label, transportColor(item.transport))
                    if (item.mustGo) Tag("必去", AppColors.coral)
                }
            }
            Txt(item.title, size = 17, bold = true)
            item.note?.let { Txt(it, size = 14) }
            MapAction(item.place ?: "開啟地圖", item.place, item.mapUrl)
        }
    }
}

/** The timeline's vertical line and dot. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.rail(first: Boolean, last: Boolean, mustGo: Boolean) {
    val x = size.width / 2
    val dotY = 30.dp.toPx()
    val w = 3.dp.toPx()
    drawLine(AppColors.line, Offset(x, if (first) dotY else 0f), Offset(x, if (last) dotY else size.height), w)
    drawCircle(if (mustGo) AppColors.coral else AppColors.yamabuki, 8.dp.toPx(), Offset(x, dotY))
    drawCircle(AppColors.card, 8.dp.toPx(), Offset(x, dotY), style = Stroke(w))
}

// ---- Checklists ------------------------------------------------------------

/** The three shared lists: to-do, before leaving, and packing. */
@Composable
fun ChecklistTab(data: TripData) {
    val session = LocalSession.current
    val write = rememberWriter()
    val tripId = data.trip.id
    val myName = data.me?.name.orEmpty()
    var kind by rememberSaveable { mutableStateOf(ChecklistKind.todo) }
    var input by rememberSaveable { mutableStateOf("") }
    val entries = data.checklist.filter { it.kind == kind }
    val done = entries.count { it.done }

    fun add() {
        val t = input.trim()
        if (t.isEmpty()) return
        input = ""
        val k = kind
        write { session.repo.addChecklistEntries(tripId, k, listOf(t)) }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChecklistKind.entries.forEach { k -> Chip(k.label, kind == k, { kind = k }, Modifier.weight(1f), color = AppColors.yamabuki) }
    }
    if (entries.isNotEmpty()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Txt("完成 $done / ${entries.size}", size = 14)
            Box(
                Modifier.weight(1f).height(12.dp).background(AppColors.line, AppShape.pill),
            ) {
                Box(
                    Modifier.fillMaxHeight().fillMaxWidth(done.toFloat() / entries.size)
                        .background(AppColors.good, AppShape.pill),
                )
            }
        }
    }
    if (entries.isEmpty()) {
        if (kind.suggestions.isNotEmpty()) {
            val k = kind
            InkButton("✨ 加入常用的「${k.label}」項目", { write { session.repo.addChecklistEntries(tripId, k, k.suggestions) } }, Modifier.fillMaxWidth(), fill = AppColors.paper)
        } else {
            Txt("還沒有項目，在下面輸入新增", Modifier.fillMaxWidth().padding(16.dp), align = TextAlign.Center, color = AppColors.faint)
        }
    }
    entries.forEach { e ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                CheckRow(e.text, e.done, { write { session.repo.setChecklistDone(tripId, e.id, !e.done, myName) } })
                if (e.done && !e.doneBy.isNullOrEmpty()) Txt("${e.doneBy} 完成", Modifier.padding(start = 36.dp), size = 13, color = AppColors.faint)
            }
            TextAction("✕", { write { session.repo.deleteChecklistEntry(tripId, e.id) } }, color = AppColors.faint)
        }
    }
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        InkField(input, { input = it }, "新增「${kind.label}」項目", Modifier.weight(1f), maxLength = 100, onDone = ::add)
        RoundAction("＋", ::add, fill = AppColors.yamabuki)
    }
}
