package com.thousandlabs.willofd.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Formats {

    fun taille(octets: Long): String {
        val ko = 1024.0
        return when {
            octets < ko -> "$octets o"
            octets < ko * ko -> String.format(Locale.FRANCE, "%.0f Ko", octets / ko)
            octets < ko * ko * ko -> String.format(Locale.FRANCE, "%.1f Mo", octets / (ko * ko))
            else -> String.format(Locale.FRANCE, "%.2f Go", octets / (ko * ko * ko))
        }
    }

    fun duree(ms: Long): String {
        val total = (ms.coerceAtLeast(0L)) / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.FRANCE, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.FRANCE, "%d:%02d", m, s)
    }

    fun date(ms: Long): String = SimpleDateFormat("d MMMM yyyy 'à' HH:mm", Locale.FRANCE).format(Date(ms))
}
