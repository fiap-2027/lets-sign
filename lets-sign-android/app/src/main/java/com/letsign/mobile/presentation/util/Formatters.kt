package com.letsign.mobile.presentation.util

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// Datas da API são ISO-8601 em UTC ("2026-10-25T14:03:00.000Z"); a tela mostra no fuso do aparelho.
// SimpleDateFormat em vez de java.time: funciona no minSdk 24 sem desugaring.

private val ISO_PATTERNS = listOf("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'")

fun parseIso(iso: String?): Date? {
    if (iso.isNullOrBlank()) return null
    for (pattern in ISO_PATTERNS) {
        val format = SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        try {
            return format.parse(iso)
        } catch (_: ParseException) {
        }
    }
    return null
}

/** "25/10/2026" */
fun formatDate(iso: String?, zone: TimeZone = TimeZone.getDefault()): String =
    parseIso(iso)?.let { SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("pt-BR")).apply { timeZone = zone }.format(it) } ?: "-"

/** "25/10/2026 às 11:03" */
fun formatDateTime(iso: String?, zone: TimeZone = TimeZone.getDefault()): String =
    parseIso(iso)?.let { SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale.forLanguageTag("pt-BR")).apply { timeZone = zone }.format(it) } ?: "-"

fun formatEpochDateTime(millis: Long, zone: TimeZone = TimeZone.getDefault()): String =
    SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale.forLanguageTag("pt-BR")).apply { timeZone = zone }.format(Date(millis))

/** 1536 -> "1,5 KB"; 1_258_291 -> "1,2 MB" */
fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(Locale.forLanguageTag("pt-BR"), "%.1f KB", bytes / 1024.0)
    else -> String.format(Locale.forLanguageTag("pt-BR"), "%.1f MB", bytes / (1024.0 * 1024.0))
}
