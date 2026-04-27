package com.ismartcoding.plain.enums

import android.content.Context
import com.ismartcoding.plain.R

enum class PanelTheme(val value: Int) {
    Matrix(0),
    Classic(1),
    ;

    fun getText(context: Context): String =
        when (this) {
            Matrix -> context.getString(R.string.panel_theme_matrix)
            Classic -> context.getString(R.string.panel_theme_classic)
        }

    companion object {
        fun parse(value: Int): PanelTheme = entries.find { it.value == value } ?: Matrix
    }
}
