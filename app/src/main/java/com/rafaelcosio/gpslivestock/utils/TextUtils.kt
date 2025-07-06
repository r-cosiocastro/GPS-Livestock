package com.rafaelcosio.gpslivestock.utils

import android.content.Context
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan

object TextUtils {
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
