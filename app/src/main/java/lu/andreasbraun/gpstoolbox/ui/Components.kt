package lu.andreasbraun.gpstoolbox.ui

import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import lu.andreasbraun.gpstoolbox.ui.theme.C
import lu.andreasbraun.gpstoolbox.ui.theme.Mono

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    radius: Dp = 16.dp,
    padding: Dp = 14.dp,
    spacing: Dp = 10.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    Column(
        modifier
            .clip(shape)
            .background(C.Surface)
            .border(1.dp, C.Line, shape)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier = modifier, fontSize = 12.sp, letterSpacing = 1.sp, color = C.Muted)
}

@Composable
fun PanelTitle(title: String, trailing: String? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = C.Text, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, fontSize = 12.sp, color = C.Muted, fontFamily = Mono)
    }
}

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String?,
    onBack: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = if (onBack != null) 8.dp else 20.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconBtn(Ic.back, "Back", onBack, bordered = false)
            Spacer(Modifier.width(4.dp))
        }
        if (leading != null) {
            leading()
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            if (subtitle != null) {
                Text(subtitle, fontSize = 13.sp, color = C.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = C.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

@Composable
fun IconBtn(icon: ImageVector, description: String, onClick: () -> Unit, bordered: Boolean = true, tint: Color = C.Text) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier
            .size(44.dp)
            .clip(shape)
            .then(if (bordered) Modifier.background(C.Surface).border(1.dp, C.Line, shape) else Modifier)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    color: Color = C.Accent,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    height: Dp = 52.dp,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = height),
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = if (primary) {
            ButtonDefaults.buttonColors(
                containerColor = color, contentColor = C.Bg,
                disabledContainerColor = C.SurfaceHi, disabledContentColor = C.Faint,
            )
        } else {
            ButtonDefaults.buttonColors(
                containerColor = C.Surface, contentColor = C.Text,
                disabledContainerColor = C.Surface, disabledContentColor = C.Faint,
            )
        },
        border = if (primary) null else BorderStroke(1.dp, C.Line),
        contentPadding = PaddingValues(horizontal = 14.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1)
    }
}

@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit, color: Color = C.Accent) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        Modifier
            .heightIn(min = 36.dp)
            .clip(shape)
            .then(if (selected) Modifier.background(color) else Modifier.border(1.dp, C.Line, shape))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) C.Bg else C.TextSoft,
        )
    }
}

@Composable
fun StatTile(label: String, value: String, unit: String?, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .clip(shape)
            .background(C.Surface)
            .border(1.dp, C.Line, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, fontSize = 12.sp, color = C.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row {
            Text(
                value, fontFamily = Mono, fontSize = 24.sp, fontWeight = FontWeight.Medium, color = C.Text,
                maxLines = 1, modifier = Modifier.alignByBaseline(),
            )
            if (unit != null) {
                Spacer(Modifier.width(6.dp))
                Text(
                    unit, fontSize = 13.sp, color = C.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.alignByBaseline(),
                )
            }
        }
    }
}

/** A labelled value in a small grid cell (used inside panels). */
@Composable
fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, fontSize = 12.sp, color = C.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, fontFamily = Mono, fontSize = 15.sp, color = C.Text, maxLines = 1)
    }
}

@Composable
fun ListRow(
    title: String,
    subtitle: String? = null,
    trailing: String? = null,
    trailingColor: Color = C.Muted,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = C.Text)
            if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = C.Muted)
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            Text(trailing, fontSize = 12.sp, color = trailingColor, maxLines = 1)
        }
    }
}

@Composable
fun ToggleRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(role = Role.Switch) { onChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = C.Text)
            if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = C.Muted)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = C.Bg,
                checkedTrackColor = C.Accent,
                uncheckedThumbColor = C.Muted,
                uncheckedTrackColor = C.Deep,
                uncheckedBorderColor = C.Line,
            ),
        )
    }
}

@Composable
fun NameDialog(title: String, initial: String, confirm: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.Surface,
        titleContentColor = C.Text,
        textContentColor = C.Text,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Name") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = C.Accent,
                    unfocusedBorderColor = C.Line,
                    focusedLabelColor = C.Accent,
                    unfocusedLabelColor = C.Muted,
                    cursorColor = C.Accent,
                    focusedTextColor = C.Text,
                    unfocusedTextColor = C.Text,
                ),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(name.trim()) }) { Text(confirm, color = C.Accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = C.Muted) } },
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit, destructive: Boolean = false) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.Surface,
        titleContentColor = C.Text,
        textContentColor = C.TextSoft,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirm, color = if (destructive) C.Red else C.Accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = C.Muted) } },
    )
}

/** Elapsed-realtime clock that ticks while the composable is shown. */
@Composable
fun rememberElapsedMs(periodMs: Long = 500L): State<Long> = produceState(SystemClock.elapsedRealtime()) {
    while (true) {
        delay(periodMs)
        value = SystemClock.elapsedRealtime()
    }
}

@Composable
fun rememberWallClock(periodMs: Long = 30_000L): State<Long> = produceState(System.currentTimeMillis()) {
    while (true) {
        delay(periodMs)
        value = System.currentTimeMillis()
    }
}
