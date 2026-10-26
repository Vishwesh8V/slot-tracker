package com.vishwesh.slottracker.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The app's colours, shared by the Compose app and the Glance widget. Neutral surfaces with
 * a single indigo accent; item colours carry the rest (docs/DECISIONS.md, "Visual design").
 */
object Palette {
    data class Tone(val light: Color, val dark: Color)

    val surface = Tone(Color(0xFFFFFFFF), Color(0xFF1C1D22))
    val background = Tone(Color(0xFFF6F6FA), Color(0xFF121317))
    val onSurface = Tone(Color(0xFF1A1B21), Color(0xFFE8E8EE))
    val secondaryText = Tone(Color(0xFF6B6F80), Color(0xFF9A9DAD))
    val slotTint = Tone(Color(0xFFF1F2F7), Color(0xFF26282F))
    val highlightTint = Tone(Color(0xFFE7EAFF), Color(0xFF2A2F5A))
    val accent = Tone(Color(0xFF4A56D6), Color(0xFFAAB4FF))
    val onAccent = Tone(Color(0xFFFFFFFF), Color(0xFF151833))
    val divider = Tone(Color(0xFFDDDFE8), Color(0xFF383B46))
    val warning = Tone(Color(0xFFB25E00), Color(0xFFFFB86B))
    val danger = Tone(Color(0xFFC62828), Color(0xFFFF8A80))
}
