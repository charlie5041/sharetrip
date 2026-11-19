package tw.tripshare.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

@Composable
fun Txt(
    text: String,
    modifier: Modifier = Modifier,
    size: Int = 16,
    color: Color = LocalContentColor.current ?: AppColors.ink,
    bold: Boolean = false,
    align: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
) {
    BasicText(
        text,
        modifier,
        style = LocalTextStyle.current.copy(
            fontSize = size.sp,
            color = color,
            fontWeight = if (bold) FontWeight.Bold else null,
            textAlign = align ?: TextAlign.Unspecified,
            lineHeight = (size * 1.45).sp,
        ),
        maxLines = maxLines,
    )
}

/**
 * Lifts something off the page: a soft shadow in the minimal look, a flat
 * paper shadow slightly down and right in the scrapbook look.
 */
fun Modifier.lifted(shape: Shape, elevation: Dp = 3.dp): Modifier =
    if (Looks.scrapbook) {
        val color = AppColors.shadow
        drawBehind {
            val outline = shape.createOutline(size, layoutDirection, this)
            translate(2.dp.toPx(), 3.dp.toPx()) { drawOutline(outline, color) }
        }
    } else {
        shadow(elevation, shape, clip = false, ambientColor = Color(0x22000000), spotColor = Color(0x22000000))
    }

/** A card: white, lifted off the page. */
@Composable
fun InkCard(
    modifier: Modifier = Modifier,
    fill: Color = AppColors.card,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = AppShape.radius
    Column(
        modifier
            .lifted(shape, elevation = 2.dp)
            .clip(shape)
            .background(fill, shape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val scope = this
        CompositionLocalProvider(LocalContentColor provides onColor(fill)) { scope.content() }
    }
}

/**
 * The big card at the top of 記帳 and 結算: a bold block of the main color
 * in the minimal look, a ticket stub with a strip of tape in the scrapbook look.
 */
@Composable
fun HeroCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val fill = AppColors.coral
    val scrap = Looks.scrapbook
    val shape = if (scrap) TicketShape(0.74f, 9.dp, 10.dp) else RoundedCornerShape(24.dp)
    val tape = AppColors.yamabuki.copy(alpha = 0.7f)
    val dash = onColor(fill).copy(alpha = 0.45f)
    Column(
        modifier
            .then(
                if (scrap) Modifier.lifted(shape)
                else Modifier.shadow(10.dp, shape, clip = false, ambientColor = fill, spotColor = fill),
            )
            .background(fill, shape)
            .then(
                if (!scrap) Modifier else Modifier.drawWithContent {
                    drawContent()
                    val x = size.width * 0.74f
                    drawLine(
                        dash, Offset(x, 12.dp.toPx()), Offset(x, size.height - 12.dp.toPx()), 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
                    )
                    rotate(-6f, Offset(56.dp.toPx(), 0f)) {
                        drawRect(tape, Offset(20.dp.toPx(), (-9).dp.toPx()), Size(72.dp.toPx(), 20.dp.toPx()))
                    }
                },
            )
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val scope = this
        CompositionLocalProvider(LocalContentColor provides onColor(fill)) { scope.content() }
    }
}

/** A rounded rectangle with a half-circle bitten out of the top and bottom edges at [at] of its width. */
class TicketShape(private val at: Float, private val notch: Dp, private val corner: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { notch.toPx() }
        val c = with(density) { corner.toPx() }
        val card = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(c, c))) }
        val x = size.width * at
        val bites = Path().apply {
            addOval(Rect(Offset(x, 0f), r))
            addOval(Rect(Offset(x, size.height), r))
        }
        return Outline.Generic(Path().apply { op(card, bites, PathOperation.Difference) })
    }
}

/** The round ＋ that floats above the tabs: adds an expense from anywhere in a trip. */
@Composable
fun AddButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val fill = AppColors.coral
    val mark = onColor(fill)
    val scrap = Looks.scrapbook
    Box(
        modifier
            .size(64.dp)
            .then(if (scrap) Modifier.rotate(-8f) else Modifier)
            .lifted(CircleShape, elevation = 8.dp)
            .clip(CircleShape)
            .background(fill, CircleShape)
            .then(if (scrap) Modifier.border(3.dp, AppColors.card, CircleShape) else Modifier)
            .clickable(role = Role.Button, onClickLabel = "記一筆", onClick = onClick)
            .drawBehind {
                val w = 3.5.dp.toPx()
                val h = 12.dp.toPx()
                val c = center
                drawLine(mark, Offset(c.x - h, c.y), Offset(c.x + h, c.y), w, StrokeCap.Round)
                drawLine(mark, Offset(c.x, c.y - h), Offset(c.x, c.y + h), w, StrokeCap.Round)
            },
    )
}

/** The main button: filled with [fill]; light fills get an outline. */
@Composable
fun InkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fill: Color = AppColors.coral,
) {
    val bg = if (enabled) fill else AppColors.line
    val light = bg.luminance() > 0.8f
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(AppShape.pill)
            .then(if (light) Modifier.border(AppShape.border, AppColors.line, AppShape.pill) else Modifier)
            .background(bg, AppShape.pill)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Txt(text, color = if (enabled) onColor(bg) else AppColors.faint, bold = true)
    }
}

/** A plain text button for secondary actions. */
@Composable
fun TextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = AppColors.ink) {
    Box(
        modifier.heightIn(min = 44.dp).clip(AppShape.pill).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Txt(text, color = color) }
}

/** A round icon-sized button showing a short symbol or word. */
@Composable
fun RoundAction(label: String, onClick: () -> Unit, fill: Color = AppColors.card) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).border(AppShape.line, AppColors.line, CircleShape)
            .background(fill, CircleShape).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Txt(label, size = 14, color = onColor(fill)) }
}

/** A small flat label, e.g. a transport tag or 必去. */
@Composable
fun Tag(text: String, color: Color) {
    Box(
        Modifier.border(AppShape.line, AppColors.line, AppShape.pill).background(color, AppShape.pill)
            .padding(horizontal = 10.dp, vertical = 2.dp),
    ) { Txt(text, size = 13, color = onColor(color)) }
}

@Composable
fun InkField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    maxLength: Int = 200,
    error: String? = null,
    capitalize: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
    lines: Int = 1,
    onDone: (() -> Unit)? = null,
    onFocus: (() -> Unit)? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Txt(label, size = 14, color = AppColors.faint)
        BasicTextField(
            value = value,
            onValueChange = { onValueChange(it.take(maxLength)) },
            // Multi-line fields scroll inside themselves; their height must be
            // bounded or a scrolling page can't measure them.
            singleLine = lines == 1,
            minLines = lines,
            maxLines = if (lines == 1) 1 else lines * 2,
            textStyle = LocalTextStyle.current.copy(fontSize = 18.sp, color = AppColors.ink),
            cursorBrush = SolidColor(AppColors.ink),
            keyboardOptions = KeyboardOptions(
                capitalization = if (capitalize) KeyboardCapitalization.Characters else KeyboardCapitalization.None,
                keyboardType = keyboard,
                imeAction = if (onDone != null) ImeAction.Done else ImeAction.Default,
            ),
            keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
            modifier = Modifier.fillMaxWidth()
                .then(if (onFocus != null) Modifier.onFocusChanged { if (it.isFocused) onFocus() } else Modifier)
                .then(if (lines > 1) Modifier.heightIn(max = (lines * 2 * 28 + 24).dp) else Modifier)
                .border(AppShape.border, if (error != null) AppColors.error else AppColors.line, AppShape.small)
                .background(AppColors.card, AppShape.small)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        )
        if (error != null) Txt(error, size = 13, color = AppColors.error)
    }
}

/** One option in a row of choices, e.g. a category or a split mode. */
@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = AppColors.coral) {
    Box(
        modifier.heightIn(min = 40.dp).clip(AppShape.pill)
            .then(if (selected) Modifier else Modifier.border(AppShape.line, AppColors.line, AppShape.pill))
            .background(if (selected) color else AppColors.card, AppShape.pill)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) { Txt(text, size = 15, bold = selected, color = if (selected) onColor(color) else AppColors.ink) }
}

/** A square tick box with a label, the whole row tappable. */
@Composable
fun CheckRow(text: String, checked: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val on = AppColors.coral
    Row(
        modifier.heightIn(min = 44.dp).clip(AppShape.small).clickable(role = Role.Checkbox, onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(26.dp)
                .then(if (checked) Modifier else Modifier.border(AppShape.line, AppColors.line, AppShape.tiny))
                .background(if (checked) on else AppColors.card, AppShape.tiny),
            contentAlignment = Alignment.Center,
        ) { if (checked) Txt("✓", size = 16, bold = true, color = onColor(on)) }
        Txt(text)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: (@Composable RowScope.() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Txt(text, Modifier.weight(1f), size = 15, bold = true, color = AppColors.faint)
        action?.invoke(this)
    }
}

/** A round letter avatar: filled when the person has joined. */
@Composable
fun Avatar(name: String, joined: Boolean) {
    Box(
        Modifier.size(40.dp).border(AppShape.line, AppColors.line, CircleShape)
            .background(if (joined) AppColors.wakatake else AppColors.card, CircleShape),
        contentAlignment = Alignment.Center,
    ) { Txt(name.take(1), bold = true) }
}

/** A modal card with a title, a body and buttons. */
@Composable
fun InkDialog(
    title: String,
    onDismiss: () -> Unit,
    buttons: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Dialog(onDismissRequest = onDismiss) {
        InkCard(Modifier.widthIn(max = 420.dp).fillMaxWidth()) {
            Txt(title, size = 20, bold = true)
            Column(
                Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                content = buttons,
            )
        }
    }
}

/** Asks before a step that can't be undone, or that changes things for other people. */
@Composable
fun ConfirmDialog(title: String, body: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    InkDialog(title, onDismiss, buttons = {
        TextAction("取消", onDismiss)
        InkButton(confirm, { onDismiss(); onConfirm() })
    }) { Txt(body) }
}

/** One choice in a list of actions, e.g. the member menu. */
@Composable
fun ActionRow(title: String, subtitle: String? = null, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(AppShape.small).clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
    ) {
        Txt(title, bold = true)
        if (subtitle != null) Txt(subtitle, size = 13, color = AppColors.faint)
    }
}

@Composable
fun CenterMessage(text: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit = {}) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Txt(text, align = TextAlign.Center)
        content()
    }
}
