// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Chris (chris-v8)
// Origin: written by Chris for the standalone Contacts-tab-filters module (2026-11-27) and moved here.
// Facts about Google Phone 175 were found by reading its code with dexdump and jadx; no Google code is copied.

package io.github.chrisv8.dialerenhancements.hook.contacts

import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.widget.TextView
import io.github.chrisv8.dialerenhancements.shared.Contract
import kotlin.math.roundToInt

/**
 * Look and text for the views this module adds to Google Phone: colours, text styles and most
 * icons come from Google Phone's own theme, so the filters match it (also in dark mode).
 * The module's own APK provides the strings and the label icon.
 */
internal class HostUi(val context: Context, private val module: Resources) {
    private val res = context.resources

    val onSurface = color("colorOnSurface", Color.BLACK)
    val onSurfaceVariant = color("colorOnSurfaceVariant", Color.DKGRAY)
    val secondaryContainer = color("colorSecondaryContainer", Color.LTGRAY)
    val onSecondaryContainer = color("colorOnSecondaryContainer", Color.BLACK)
    val surfaceContainerLow = color("colorSurfaceContainerLow", Color.WHITE)
    val background = resolveColor(android.R.attr.colorBackground) ?: Color.WHITE
    val highlight = resolveColor(android.R.attr.colorControlHighlight) ?: 0x1F000000

    fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), res.displayMetrics).roundToInt()

    fun hostId(name: String): Int = res.getIdentifier(name, "id", Contract.GOOGLE_PHONE)

    fun string(id: Int, vararg args: Any): String = module.getString(id, *args)

    fun moduleIcon(id: Int, tint: Int): Drawable = module.getDrawable(id, null).mutate().apply { setTint(tint) }

    fun hostIcon(name: String, tint: Int): Drawable? =
        res.getIdentifier(name, "drawable", Contract.GOOGLE_PHONE).takeIf { it != 0 }
            ?.let { context.getDrawable(it) }?.mutate()?.apply { setTint(tint) }

    /** Applies one of Google Phone's Material text styles, e.g. "textAppearanceBodyLarge". */
    fun style(view: TextView, textAppearance: String, color: Int) {
        val attr = res.getIdentifier(textAppearance, "attr", Contract.GOOGLE_PHONE)
        val value = TypedValue()
        if (attr != 0 && context.theme.resolveAttribute(attr, value, true)) {
            val style = if (value.resourceId != 0) value.resourceId else value.data
            if (style != 0) view.setTextAppearance(style)
        }
        view.setTextColor(color)
    }

    /** The theme's ripple for list rows and chips. */
    fun ripple(borderless: Boolean = false): Drawable? {
        val attr = if (borderless) {
            android.R.attr.selectableItemBackgroundBorderless
        } else {
            android.R.attr.selectableItemBackground
        }
        val a = context.obtainStyledAttributes(intArrayOf(attr))
        return try {
            a.getDrawable(0)
        } finally {
            a.recycle()
        }
    }

    private fun color(attrName: String, fallback: Int): Int {
        val attr = res.getIdentifier(attrName, "attr", Contract.GOOGLE_PHONE)
        return if (attr == 0) fallback else resolveColor(attr) ?: fallback
    }

    private fun resolveColor(attr: Int): Int? {
        val value = TypedValue()
        if (!context.theme.resolveAttribute(attr, value, true)) return null
        return when {
            value.type in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT -> value.data
            value.resourceId != 0 -> context.getColorStateList(value.resourceId).defaultColor
            else -> null
        }
    }
}
