package com.vishwesh.slottracker.domain

/**
 * Stable slot identities used to match live task state across schedule edits
 * (docs/DECISIONS.md). An override slot copied from a template slot shares that slot's key,
 * so ticks survive creating an override for today.
 */
object SlotKeys {
    fun template(templateSlotId: Long): String = "t:$templateSlotId"

    fun override(overrideSlotId: Long, sourceTemplateSlotId: Long?): String =
        sourceTemplateSlotId?.let(::template) ?: "o:$overrideSlotId"
}
