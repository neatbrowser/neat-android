package io.github.neatbrowser.search.core

import java.text.Normalizer
import java.util.Locale

/** Lower-cases text and removes accents so that "doc bao" finds "Đọc báo". */
object TextFold {
    private val marks = Regex("\\p{Mn}+")

    fun fold(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(marks, "")
            .replace('đ', 'd')
            .replace('Đ', 'D')
            .lowercase(Locale.ROOT)
}
