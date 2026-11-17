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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun Txt(
    text: String,
    modifier: Modifier = Modifier,
    size: Int = 16,
    color: Color = AppColors.ink,
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

/** A flat card with the thick ink outline. */
@Composable
fun InkCard(
    modifier: Modifier = Modifier,
    fill: Color = AppColors.paper,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(AppShape.radius)
            .border(AppShape.border, AppColors.ink, AppShape.radius)
            .background(fill, AppShape.radius)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

/** The main button: filled, outlined in ink. */
@Composable
fun InkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fill: Color = AppColors.coral,
) {
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(AppShape.pill)
            .border(AppShape.border, if (enabled) AppColors.ink else AppColors.faint, AppShape.pill)
            .background(if (enabled) fill else AppColors.paper, AppShape.pill)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Txt(text, color = if (enabled) AppColors.ink else AppColors.faint, bold = true)
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
fun RoundAction(label: String, onClick: () -> Unit, fill: Color = AppColors.paper) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).border(2.dp, AppColors.ink, CircleShape)
            .background(fill, CircleShape).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Txt(label, size = 14) }
}

/** A small flat label with an ink outline, e.g. a transport tag or 必去. */
@Composable
fun Tag(text: String, color: Color) {
    Box(
        Modifier.border(2.dp, AppColors.ink, AppShape.pill).background(color, AppShape.pill)
            .padding(horizontal = 10.dp, vertical = 2.dp),
    ) { Txt(text, size = 13) }
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
    onDone: (() -> Unit)? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Txt(label, size = 14, color = AppColors.faint)
        BasicTextField(
            value = value,
            onValueChange = { onValueChange(it.take(maxLength)) },
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(fontSize = 18.sp),
            cursorBrush = SolidColor(AppColors.ink),
            keyboardOptions = KeyboardOptions(
                capitalization = if (capitalize) KeyboardCapitalization.Characters else KeyboardCapitalization.None,
                imeAction = if (onDone != null) ImeAction.Done else ImeAction.Default,
            ),
            keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
            modifier = Modifier.fillMaxWidth()
                .border(AppShape.border, if (error != null) AppColors.error else AppColors.ink, AppShape.small)
                .background(AppColors.white, AppShape.small)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        )
        if (error != null) Txt(error, size = 13, color = AppColors.error)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: (@Composable RowScope.() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Txt(text, Modifier.weight(1f), size = 18, bold = true)
        action?.invoke(this)
    }
}

/** A round letter avatar: filled when the person has joined. */
@Composable
fun Avatar(name: String, joined: Boolean) {
    Box(
        Modifier.size(40.dp).border(2.dp, AppColors.ink, CircleShape)
            .background(if (joined) AppColors.wakatake else AppColors.white, CircleShape),
        contentAlignment = Alignment.Center,
    ) { Txt(name.take(1)) }
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
