package tw.tripshare.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import tw.tripshare.core.Flight
import tw.tripshare.core.ItineraryItem
import tw.tripshare.core.Lodging
import tw.tripshare.core.Transport
import tw.tripshare.core.datesBetween
import tw.tripshare.core.hm
import tw.tripshare.core.orNullIfBlank
import tw.tripshare.core.parseHm
import tw.tripshare.core.parseYmd
import tw.tripshare.core.shortDate
import tw.tripshare.core.ymd
import tw.tripshare.today
import tw.tripshare.ui.ActionRow
import tw.tripshare.ui.AppColors
import tw.tripshare.ui.CenterMessage
import tw.tripshare.ui.CheckRow
import tw.tripshare.ui.Chip
import tw.tripshare.ui.ConfirmDialog
import tw.tripshare.ui.DateRangeDialog
import tw.tripshare.ui.InkButton
import tw.tripshare.ui.InkDialog
import tw.tripshare.ui.InkField
import tw.tripshare.ui.LocalNav
import tw.tripshare.ui.LocalToasts
import tw.tripshare.ui.Page
import tw.tripshare.ui.TextAction
import tw.tripshare.ui.Txt

/**
 * The frame every small add/edit form shares: a save button, an optional
 * delete action with a confirmation, and an error line.
 */
@Composable
private fun FormPage(
    title: String,
    what: String,
    error: String?,
    onSave: () -> Unit,
    onDelete: (() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    val nav = LocalNav.current
    var confirming by remember { mutableStateOf(false) }
    Page(
        title,
        onBack = { nav.pop() },
        actions = { if (onDelete != null) TextAction("刪除", { confirming = true }, color = AppColors.error) },
    ) {
        content()
        error?.let { Txt(it, color = AppColors.error) }
        InkButton("儲存", onSave, Modifier.fillMaxWidth())
    }
    if (confirming && onDelete != null) {
        ConfirmDialog("刪除$what？", "大家都會看不到。", "刪除", onConfirm = onDelete, onDismiss = { confirming = false })
    }
}

/** Runs a write in the background (offline it finishes later), then leaves the form. */
@Composable
private fun rememberSaver(): (suspend () -> Unit) -> Unit {
    val session = LocalSession.current
    val toasts = LocalToasts.current
    val nav = LocalNav.current
    var done by remember { mutableStateOf(false) }
    return { block ->
        // A second tap before the page is gone would save a second copy.
        if (!done) {
            done = true
            session.background.launch { runCatching { block() }.onFailure { toasts.show("沒有存成功：${it.message}") } }
            nav.pop()
        }
    }
}

/** Finds the record being edited, or shows that it's gone; [content] gets null for a new one. */
@Composable
private fun <T : Any> Editing(
    tripId: String,
    id: String?,
    title: String,
    find: (TripData) -> List<T>,
    idOf: (T) -> String,
    content: @Composable (TripData, T?) -> Unit,
) {
    val nav = LocalNav.current
    WithTrip(tripId) { data ->
        val existing = remember { id?.let { want -> find(data).firstOrNull { idOf(it) == want } } }
        if (id != null && existing == null) {
            Page(title, onBack = { nav.pop() }) { CenterMessage("這筆已經被刪掉了。") }
        } else {
            content(data, existing)
        }
    }
}

/** A time field's text → "HH:mm", null when empty, or an error. */
private fun timeOrError(text: String): Result<String?> {
    if (text.isBlank()) return Result.success(null)
    val t = parseHm(text) ?: return Result.failure(IllegalArgumentException("時間格式要像 09:30"))
    return Result.success(hm(t.first, t.second))
}

// ---- Itinerary stop --------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ItemFormScreen(tripId: String, itemId: String?, date: String) {
    Editing<ItineraryItem>(tripId, itemId, "編輯行程", { it.items }, { it.id }) { data, existing ->
        val save = rememberSaver()
        val session = LocalSession.current
        val days = remember { datesBetween(data.trip.startDate, data.trip.endDate) }
        var title by remember { mutableStateOf(existing?.title.orEmpty()) }
        var day by remember { mutableStateOf(existing?.date ?: date) }
        var time by remember { mutableStateOf(existing?.time.orEmpty()) }
        var transport by remember { mutableStateOf(existing?.transport ?: Transport.none) }
        var mustGo by remember { mutableStateOf(existing?.mustGo ?: false) }
        var place by remember { mutableStateOf(existing?.place.orEmpty()) }
        var mapUrl by remember { mutableStateOf(existing?.mapUrl.orEmpty()) }
        var note by remember { mutableStateOf(existing?.note.orEmpty()) }
        var error by remember { mutableStateOf<String?>(null) }
        var pickingDay by remember { mutableStateOf(false) }

        fun dayLabel(d: String): String {
            val i = days.indexOf(d)
            return if (i < 0) shortDate(d) else "Day ${i + 1}　${shortDate(d)}"
        }

        FormPage(
            if (existing == null) "加行程" else "編輯行程", "這個行程", error,
            onSave = {
                val t = timeOrError(time)
                error = when {
                    title.isBlank() -> "請填行程名稱"
                    t.isFailure -> t.exceptionOrNull()!!.message
                    else -> null
                }
                if (error == null) {
                    val item = ItineraryItem(
                        id = existing?.id.orEmpty(), date = day, title = title.trim(), order = existing?.order ?: 0,
                        time = t.getOrNull(), place = place.orNullIfBlank(), mapUrl = mapUrl.orNullIfBlank(),
                        note = note.orNullIfBlank(), transport = transport, mustGo = mustGo,
                    )
                    save { session.repo.saveItem(tripId, item) }
                }
            },
            onDelete = existing?.let { e -> { save { session.repo.deleteItem(tripId, e.id) } } },
        ) {
            InkField(title, { title = it; error = null }, "要做什麼，例如「淺草寺」", maxLength = 60)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InkButton(dayLabel(day), { pickingDay = true }, Modifier.weight(1f), fill = AppColors.paper)
                InkField(time, { time = it; error = null }, "時間（可不填）", Modifier.weight(0.7f), maxLength = 5, keyboard = KeyboardType.Number)
            }
            Txt("怎麼去", size = 14, color = AppColors.faint)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Transport.entries.forEach { t -> Chip(t.label, transport == t, { transport = t }, color = AppColors.mizu) }
            }
            CheckRow("標記為「必去」", mustGo, { mustGo = !mustGo })
            InkField(place, { place = it }, "地點（可不填）", maxLength = 100)
            InkField(mapUrl, { mapUrl = it }, "地圖連結（可不填，沒填就用地點搜尋）", maxLength = 500, keyboard = KeyboardType.Uri)
            InkField(note, { note = it }, "備註（可不填）", maxLength = 300)
        }
        if (pickingDay) {
            InkDialog("哪一天", { pickingDay = false }, buttons = { TextAction("取消", { pickingDay = false }) }) {
                days.forEach { d -> ActionRow(dayLabel(d)) { day = d; pickingDay = false } }
            }
        }
    }
}

// ---- Flight ----------------------------------------------------------------

@Composable
fun FlightFormScreen(tripId: String, flightId: String?) {
    Editing<Flight>(tripId, flightId, "編輯航班", { it.flights }, { it.id }) { data, existing ->
        val save = rememberSaver()
        val session = LocalSession.current
        var no by remember { mutableStateOf(existing?.flightNo.orEmpty()) }
        var airline by remember { mutableStateOf(existing?.airline.orEmpty()) }
        var from by remember { mutableStateOf(existing?.fromCode.orEmpty()) }
        var fromName by remember { mutableStateOf(existing?.fromName.orEmpty()) }
        var to by remember { mutableStateOf(existing?.toCode.orEmpty()) }
        var toName by remember { mutableStateOf(existing?.toName.orEmpty()) }
        var departDate by remember { mutableStateOf(existing?.departDate) }
        var departTime by remember { mutableStateOf(existing?.departTime.orEmpty()) }
        var arriveDate by remember { mutableStateOf(existing?.arriveDate) }
        var arriveTime by remember { mutableStateOf(existing?.arriveTime.orEmpty()) }
        var note by remember { mutableStateOf(existing?.note.orEmpty()) }
        var error by remember { mutableStateOf<String?>(null) }
        var picking by remember { mutableStateOf<String?>(null) }

        FormPage(
            if (existing == null) "新增航班" else "編輯航班", "這個航班", error,
            onSave = {
                val dt = timeOrError(departTime)
                val at = timeOrError(arriveTime)
                error = when {
                    no.isBlank() || from.isBlank() || to.isBlank() || departDate == null || departTime.isBlank() ->
                        "請填航班編號、出發與抵達機場、出發日期和時間"
                    dt.isFailure -> dt.exceptionOrNull()!!.message
                    at.isFailure -> at.exceptionOrNull()!!.message
                    else -> null
                }
                if (error == null) {
                    val f = Flight(
                        id = existing?.id.orEmpty(), flightNo = no.trim().uppercase(), fromCode = from.trim().uppercase(),
                        toCode = to.trim().uppercase(), departDate = departDate!!, departTime = dt.getOrNull()!!,
                        airline = airline.orNullIfBlank(), fromName = fromName.orNullIfBlank(), toName = toName.orNullIfBlank(),
                        arriveDate = arriveDate, arriveTime = at.getOrNull(), note = note.orNullIfBlank(),
                    )
                    save { session.repo.saveFlight(tripId, f) }
                }
            },
            onDelete = existing?.let { e -> { save { session.repo.deleteFlight(tripId, e.id) } } },
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InkField(no, { no = it; error = null }, "航班編號 BR198", Modifier.weight(1f), maxLength = 10, capitalize = true)
                InkField(airline, { airline = it }, "航空公司（可不填）", Modifier.weight(1f), maxLength = 30)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InkField(from, { from = it; error = null }, "出發機場 TPE", Modifier.weight(1f), maxLength = 4, capitalize = true)
                InkField(fromName, { fromName = it }, "名稱（可不填）", Modifier.weight(1f), maxLength = 30)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InkField(to, { to = it; error = null }, "抵達機場 NRT", Modifier.weight(1f), maxLength = 4, capitalize = true)
                InkField(toName, { toName = it }, "名稱（可不填）", Modifier.weight(1f), maxLength = 30)
            }
            Txt("出發（當地時間）", size = 14, color = AppColors.faint)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InkButton(departDate?.let(::shortDate) ?: "出發日期", { picking = "depart" }, Modifier.weight(1f), fill = AppColors.paper)
                InkField(departTime, { departTime = it; error = null }, "時間", Modifier.weight(0.7f), maxLength = 5, keyboard = KeyboardType.Number)
            }
            Txt("抵達（當地時間，可不填）", size = 14, color = AppColors.faint)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InkButton(arriveDate?.let(::shortDate) ?: "抵達日期", { picking = "arrive" }, Modifier.weight(1f), fill = AppColors.paper)
                InkField(arriveTime, { arriveTime = it; error = null }, "時間", Modifier.weight(0.7f), maxLength = 5, keyboard = KeyboardType.Number)
            }
            InkField(note, { note = it }, "備註，例如航廈、行李額度（可不填）", maxLength = 300)
        }
        val current = if (picking == "depart") departDate else arriveDate ?: departDate
        if (picking != null) {
            val d = (current ?: data.trip.startDate).let(::parseYmd)
            DateRangeDialog(
                d, d, today(),
                onDone = { s, _ ->
                    if (picking == "depart") departDate = ymd(s) else arriveDate = ymd(s)
                    error = null
                    picking = null
                },
                onDismiss = { picking = null },
                single = true,
            )
        }
    }
}

// ---- Lodging ---------------------------------------------------------------

@Composable
fun LodgingFormScreen(tripId: String, lodgingId: String?) {
    Editing<Lodging>(tripId, lodgingId, "編輯住宿", { it.lodgings }, { it.id }) { data, existing ->
        val save = rememberSaver()
        val session = LocalSession.current
        var name by remember { mutableStateOf(existing?.name.orEmpty()) }
        var checkIn by remember { mutableStateOf(existing?.checkIn) }
        var checkOut by remember { mutableStateOf(existing?.checkOut) }
        var address by remember { mutableStateOf(existing?.address.orEmpty()) }
        var mapUrl by remember { mutableStateOf(existing?.mapUrl.orEmpty()) }
        var conf by remember { mutableStateOf(existing?.confirmation.orEmpty()) }
        var note by remember { mutableStateOf(existing?.note.orEmpty()) }
        var error by remember { mutableStateOf<String?>(null) }
        var picking by remember { mutableStateOf(false) }

        FormPage(
            if (existing == null) "新增住宿" else "編輯住宿", "這個住宿", error,
            onSave = {
                val i = checkIn
                val o = checkOut
                error = when {
                    name.isBlank() || i == null || o == null -> "請填住宿名稱、入住和退房日期"
                    o <= i -> "退房要在入住之後"
                    else -> null
                }
                if (error == null) {
                    val l = Lodging(
                        id = existing?.id.orEmpty(), name = name.trim(), checkIn = i!!, checkOut = o!!,
                        address = address.orNullIfBlank(), mapUrl = mapUrl.orNullIfBlank(),
                        confirmation = conf.orNullIfBlank(), note = note.orNullIfBlank(),
                    )
                    save { session.repo.saveLodging(tripId, l) }
                }
            },
            onDelete = existing?.let { e -> { save { session.repo.deleteLodging(tripId, e.id) } } },
        ) {
            InkField(name, { name = it; error = null }, "住宿名稱", maxLength = 60)
            InkButton(
                if (checkIn == null) "選擇入住、退房日期" else "${shortDate(checkIn!!)} 入住 → ${checkOut?.let(::shortDate) ?: "？"} 退房",
                { picking = true }, Modifier.fillMaxWidth(), fill = AppColors.paper,
            )
            InkField(address, { address = it }, "地址（可不填）", maxLength = 200)
            InkField(mapUrl, { mapUrl = it }, "地圖連結（可不填）", maxLength = 500, keyboard = KeyboardType.Uri)
            InkField(conf, { conf = it }, "訂房編號（可不填）", maxLength = 60)
            InkField(note, { note = it }, "備註，例如入住時間、門禁密碼（可不填）", maxLength = 300)
        }
        if (picking) {
            DateRangeDialog(
                (checkIn ?: data.trip.startDate).let(::parseYmd), (checkOut ?: data.trip.endDate).let(::parseYmd), today(),
                onDone = { s, e -> checkIn = ymd(s); checkOut = ymd(e); error = null; picking = false },
                onDismiss = { picking = false },
                startLabel = "入住日",
                endLabel = "退房日",
            )
        }
    }
}
