package io.gameprobe.app.controller

import java.util.Locale

/** Locale-independent number formatting so diagnostics read the same on every device. */
object Format {
    fun hex32(value: Int): String = String.format(Locale.ROOT, "0x%08X", value)

    fun hex16(value: Int): String = String.format(Locale.ROOT, "0x%04X", value)

    /** Fixed width signed value, e.g. " 0.730" / "-0.031". */
    fun axis(value: Float): String = String.format(Locale.ROOT, "% .3f", value)

    fun plain(value: Float): String = String.format(Locale.ROOT, "%.4f", value)

    /** Event uptime in seconds with millisecond precision. */
    fun seconds(uptimeMs: Long): String = String.format(Locale.ROOT, "%.3f", uptimeMs / 1000.0)
}
