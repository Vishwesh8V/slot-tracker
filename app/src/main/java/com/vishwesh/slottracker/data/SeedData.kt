package com.vishwesh.slottracker.data

import java.time.DayOfWeek
import java.time.LocalTime

/** First-launch seed configuration (spec §4). Written once; everything is editable afterwards. */
object SeedData {
    data class SeedItem(val name: String, val colorArgb: Int)
    data class SeedSlot(val label: String, val start: LocalTime, val end: LocalTime, val itemNames: List<String>)

    private const val DIFFUSION = "Diffusion Models"
    private const val GAME_DEV = "Game Development"
    private const val LEETCODE = "Leetcode / DSA"
    private const val MATH = "Math Puzzle"
    private const val ROMAN_ART = "Roman Art"
    private const val LANGUAGE = "Language Study"

    val items = listOf(
        SeedItem(DIFFUSION, 0xFF5C6BC0.toInt()),
        SeedItem(GAME_DEV, 0xFF43A047.toInt()),
        SeedItem(LEETCODE, 0xFFFFB300.toInt()),
        SeedItem(MATH, 0xFF00897B.toInt()),
        SeedItem(ROMAN_ART, 0xFFD84315.toInt()),
        SeedItem(LANGUAGE, 0xFF8E24AA.toInt()),
    )

    private fun morning(vararg names: String) =
        SeedSlot("Morning", LocalTime.of(10, 0), LocalTime.of(13, 0), names.toList())

    private fun evening(vararg names: String) =
        SeedSlot("Evening", LocalTime.of(20, 0), LocalTime.of(22, 0), names.toList())

    val week: Map<DayOfWeek, List<SeedSlot>> = mapOf(
        DayOfWeek.MONDAY to listOf(morning(DIFFUSION), evening(LEETCODE)),
        DayOfWeek.TUESDAY to listOf(morning(GAME_DEV), evening(ROMAN_ART, LANGUAGE)),
        DayOfWeek.WEDNESDAY to listOf(morning(DIFFUSION), evening(LEETCODE, MATH)),
        DayOfWeek.THURSDAY to listOf(morning(GAME_DEV), evening(ROMAN_ART, LANGUAGE)),
        DayOfWeek.FRIDAY to listOf(morning(DIFFUSION), evening(LEETCODE, MATH)),
        DayOfWeek.SATURDAY to listOf(morning(GAME_DEV), evening(LEETCODE)),
        DayOfWeek.SUNDAY to listOf(morning(DIFFUSION), evening(LEETCODE)),
    )
}
