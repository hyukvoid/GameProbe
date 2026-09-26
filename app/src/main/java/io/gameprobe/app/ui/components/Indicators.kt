package io.gameprobe.app.ui.components

import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.gameprobe.app.controller.Format
import io.gameprobe.app.controller.StickState
import io.gameprobe.app.controller.TriggerState

@Composable
fun Section(
    title: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                action?.invoke()
            }
            content()
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(min = 120.dp, max = 120.dp),
        )
        Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun MonoText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    )
}

/** A button that lights up while held. Pressed state is exposed to accessibility services. */
@Composable
fun ButtonIndicator(label: String, pressed: Boolean, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(size)
            .semantics { stateDescription = if (pressed) "pressed" else "released" }
            .background(if (pressed) colors.primary else colors.surface, RoundedCornerShape(size / 2))
            .border(1.dp, colors.outline, RoundedCornerShape(size / 2)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (pressed) colors.onPrimary else colors.onSurface,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun TriggerBar(label: String, digitalLabel: String, trigger: TriggerState, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            MonoText(if (trigger.hasAnalog) Format.axis(trigger.value) else "no axis")
        }
        LinearProgressIndicator(
            progress = { trigger.value },
            modifier = Modifier.fillMaxWidth().height(8.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            ButtonIndicator(digitalLabel, trigger.digitalPressed, size = 32.dp)
            Spacer(Modifier.size(8.dp))
            Text(
                trigger.axis?.let { MotionEvent.axisToString(it) } ?: "digital key only",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Stick position. The outlined inner circle is the MotionRange.flat dead zone; the hollow dot
 * is the raw value and the filled dot is the value after the dead zone.
 */
@Composable
fun StickView(label: String, stick: StickState, clickLabel: String, clickPressed: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontWeight = FontWeight.SemiBold)
        Canvas(Modifier.size(112.dp)) {
            val radius = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(colors.outline, radius, center, style = Stroke(2f))
            val flat = maxOf(stick.flatX, stick.flatY).coerceIn(0f, 1f)
            if (flat > 0f) drawCircle(colors.outlineVariant, radius * flat, center, style = Stroke(2f))
            drawLine(colors.outlineVariant, Offset(center.x - radius, center.y), Offset(center.x + radius, center.y))
            drawLine(colors.outlineVariant, Offset(center.x, center.y - radius), Offset(center.x, center.y + radius))
            val rawPoint = Offset(
                center.x + stick.rawX.coerceIn(-1f, 1f) * radius,
                center.y + stick.rawY.coerceIn(-1f, 1f) * radius,
            )
            drawCircle(colors.tertiary, 7f, rawPoint, style = Stroke(3f))
            val point = Offset(
                center.x + stick.x.coerceIn(-1f, 1f) * radius,
                center.y + stick.y.coerceIn(-1f, 1f) * radius,
            )
            drawCircle(colors.primary, 9f, point)
        }
        if (stick.available) {
            MonoText("raw  X${Format.axis(stick.rawX)} Y${Format.axis(stick.rawY)}")
            MonoText("norm X${Format.axis(stick.x)} Y${Format.axis(stick.y)}")
            MonoText("flat X ${Format.plain(stick.flatX)} Y ${Format.plain(stick.flatY)}")
            Text(
                listOf(stick.axisX, stick.axisY).joinToString(" / ") { axis ->
                    axis?.let { MotionEvent.axisToString(it) } ?: "none"
                },
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
            )
        } else {
            Text("No axes reported", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        }
        ButtonIndicator(clickLabel, clickPressed, size = 36.dp)
    }
}
