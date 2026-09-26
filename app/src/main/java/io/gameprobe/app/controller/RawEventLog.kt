package io.gameprobe.app.controller

/** An input event as Android delivered it, kept verbatim for the raw inspector. */
sealed interface RawEvent {
    val deviceId: Int
    val eventTimeMs: Long
}

data class RawKeyEvent(
    override val deviceId: Int,
    override val eventTimeMs: Long,
    val down: Boolean,
    val keyCode: Int,
    val scanCode: Int,
    val source: Int,
) : RawEvent

/**
 * One or more consecutive joystick MotionEvents from the same device. Analog axes produce
 * events at controller report rate, so consecutive samples are merged into one entry that
 * records every axis that changed (latest value) and how many samples were merged.
 */
data class RawMotionEvent(
    override val deviceId: Int,
    override val eventTimeMs: Long,
    val source: Int,
    val changedAxes: Map<Int, Float>,
    val samples: Int = 1,
) : RawEvent

object RawEventLog {
    const val DEFAULT_CAPACITY = 60

    fun append(log: List<RawEvent>, event: RawEvent, capacity: Int = DEFAULT_CAPACITY): List<RawEvent> {
        val last = log.lastOrNull()
        val next = if (event is RawMotionEvent && last is RawMotionEvent &&
            last.deviceId == event.deviceId && last.source == event.source
        ) {
            log.dropLast(1) + last.copy(
                eventTimeMs = event.eventTimeMs,
                changedAxes = last.changedAxes + event.changedAxes,
                samples = last.samples + event.samples,
            )
        } else {
            log + event
        }
        return if (next.size > capacity) next.takeLast(capacity) else next
    }
}
