package dev.mediasearch.core

/** Parse a count the platform actually displayed; absent/non-numeric labels stay unknown. */
fun displayCount(value: String): Long? {
    val match = Regex("^([0-9]+(?:\\.[0-9]+)?)\\s*(万|亿|[kKmM])?\\+?$").matchEntire(value.replace(",", "").trim()) ?: return null
    val number = match.groupValues[1].toDoubleOrNull() ?: return null
    val multiplier = when (match.groupValues[2]) { "万" -> 10000.0; "亿" -> 100000000.0; "k", "K" -> 1000.0; "m", "M" -> 1000000.0; else -> 1.0 }
    val result = number * multiplier
    return result.takeIf { it.isFinite() && it >= 0 && it < Long.MAX_VALUE.toDouble() }?.toLong()
}
