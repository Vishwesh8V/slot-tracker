package com.vishwesh.slottracker.ui.items

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import android.graphics.Color as AndroidColor

/** The ~16 preset colours (spec §6.2), including the six seed colours. */
val PresetColors: List<Int> = listOf(
    0xFF5C6BC0, 0xFF5E35B1, 0xFF8E24AA, 0xFFD81B60,
    0xFFE53935, 0xFFD84315, 0xFFFB8C00, 0xFFFFB300,
    0xFF7CB342, 0xFF43A047, 0xFF00897B, 0xFF00ACC1,
    0xFF039BE5, 0xFF1E88E5, 0xFF6D4C41, 0xFF546E7A,
).map { it.toInt() }

private val Rainbow = Brush.sweepGradient(
    listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red),
)

/** Preset swatches plus a custom swatch that opens a full colour picker. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorChooser(selected: Int, onSelect: (Int) -> Unit) {
    var customOpen by remember { mutableStateOf(false) }
    val isCustom = selected !in PresetColors
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PresetColors.forEach { color ->
            Swatch(Modifier.background(Color(color)), selected = color == selected, onClick = { onSelect(color) })
        }
        Swatch(
            if (isCustom) Modifier.background(Color(selected)) else Modifier.background(Rainbow),
            selected = isCustom,
            onClick = { customOpen = true },
        ) {
            if (!isCustom) Icon(Icons.Outlined.Palette, contentDescription = "Custom colour", tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
    if (customOpen) {
        CustomColorDialog(
            initial = selected,
            onDismiss = { customOpen = false },
            onPick = {
                onSelect(it)
                customOpen = false
            },
        )
    }
}

@Composable
private fun Swatch(modifier: Modifier, selected: Boolean, onClick: () -> Unit, content: @Composable () -> Unit = {}) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
            .padding(if (selected) 5.dp else 0.dp)
            .clip(CircleShape)
            .then(modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Outlined.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        content()
    }
}

/** Hue / saturation / brightness sliders with a hex field. */
@Composable
private fun CustomColorDialog(initial: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val hsv = remember { FloatArray(3).also { AndroidColor.colorToHSV(initial, it) } }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var saturation by remember { mutableFloatStateOf(hsv[1]) }
    var value by remember { mutableFloatStateOf(hsv[2]) }
    val color = AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value))
    var hex by remember { mutableStateOf(toHex(initial)) }

    fun setFrom(argb: Int) {
        val out = FloatArray(3)
        AndroidColor.colorToHSV(argb, out)
        hue = out[0]; saturation = out[1]; value = out[2]
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom colour") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(56.dp).clip(CircleShape).background(Color(color)))
                    Spacer(Modifier.width(16.dp))
                    OutlinedTextField(
                        value = hex,
                        onValueChange = { text ->
                            hex = text
                            parseHex(text)?.let(::setFrom)
                        },
                        label = { Text("Hex") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                LabeledSlider("Hue", hue, 0f..360f) { hue = it; hex = toHex(AndroidColor.HSVToColor(floatArrayOf(it, saturation, value))) }
                LabeledSlider("Saturation", saturation, 0f..1f) { saturation = it; hex = toHex(AndroidColor.HSVToColor(floatArrayOf(hue, it, value))) }
                LabeledSlider("Brightness", value, 0f..1f) { value = it; hex = toHex(AndroidColor.HSVToColor(floatArrayOf(hue, saturation, it))) }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(color) }) { Text("Use colour") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
    Slider(value = value, onValueChange = onChange, valueRange = range)
}

private fun toHex(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)

private fun parseHex(text: String): Int? {
    val digits = text.trim().removePrefix("#")
    if (digits.length != 6) return null
    return digits.toIntOrNull(16)?.let { it or 0xFF000000.toInt() }
}
