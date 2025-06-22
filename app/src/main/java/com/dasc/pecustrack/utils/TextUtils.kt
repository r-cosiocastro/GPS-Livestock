package com.dasc.pecustrack.utils

import android.content.Context
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan

object TextUtils {

    /**
     * Devuelve un SpannableStringBuilder donde el "título" se muestra en negrita
     * y el "contenido" en estilo normal.
     *
     * Ejemplo: boldTitle("Nombre: ", "Rafael") -> **Nombre:** Rafael
     */
    fun boldTitle(title: String, content: String): SpannableStringBuilder {
        return SpannableStringBuilder().apply {
            append(title)
            setSpan(
                StyleSpan(Typeface.BOLD),
                0,
                title.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            append(content)
        }
    }

    /**
     * Similar a boldTitle, pero usando un recurso string del tipo "Título: %1$s"
     * Asume que el título está al principio y el contenido al final.
     */
    fun boldTitleFromResource(
        context: Context,
        stringResId: Int,
        content: String
    ): SpannableStringBuilder {
        val fullText = context.getString(stringResId, content)
        val title = fullText.removeSuffix(content)
        return boldTitle(title, content)
    }
}
