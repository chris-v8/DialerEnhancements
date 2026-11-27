// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Chris (chris-v8)
// Origin: written by Chris for the standalone Contacts-tab-filters module (2026-11-27) and moved here.
// Facts about Google Phone 175 were found by reading its code with dexdump and jadx; no Google code is copied.

package io.github.chrisv8.dialerenhancements.hook.contacts

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.Window
import android.view.WindowInsets
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.chrisv8.dialerenhancements.R

/**
 * A bottom sheet with a list of choices, built like the account and label sheets of
 * Google Contacts: a drag handle, optional section headers, and rows with an icon, a name,
 * a contact count and a check mark on the current choice.
 */
internal class FilterSheet(private val ui: HostUi, context: Context) {
    private val dialog = Dialog(context)
    private val rows = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    fun header(text: String) {
        rows += TextView(rows.context).apply {
            this.text = text
            ui.style(this, "textAppearanceTitleSmall", ui.onSurfaceVariant)
            minHeight = ui.dp(48)
            gravity = Gravity.CENTER_VERTICAL
            setPaddingRelative(ui.dp(16), ui.dp(8), ui.dp(16), ui.dp(8))
            isFocusable = true
        }
    }

    fun note(text: String) {
        rows += TextView(rows.context).apply {
            this.text = text
            ui.style(this, "textAppearanceBodyLarge", ui.onSurfaceVariant)
            minHeight = ui.dp(56)
            gravity = Gravity.CENTER_VERTICAL
            setPaddingRelative(ui.dp(16), 0, ui.dp(16), 0)
        }
    }

    fun row(icon: Drawable?, text: String, count: Int, selected: Boolean, onClick: () -> Unit) {
        val context = rows.context
        rows += LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = ui.dp(56)
            setPaddingRelative(ui.dp(16), ui.dp(8), ui.dp(16), ui.dp(8))
            background = ui.ripple()
            isSelected = selected
            contentDescription = "$text, ${ui.string(R.string.contacts_filter_count, count)}"
            setOnClickListener {
                dialog.dismiss()
                onClick()
            }

            addView(ImageView(context).apply { setImageDrawable(icon) }, LinearLayout.LayoutParams(ui.dp(24), ui.dp(24)))
            addView(
                TextView(context).apply {
                    this.text = text
                    ui.style(this, "textAppearanceBodyLarge", ui.onSurface)
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                },
                LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginStart = ui.dp(16) },
            )
            addView(
                TextView(context).apply {
                    this.text = count.toString()
                    ui.style(this, "textAppearanceBodyMedium", ui.onSurfaceVariant)
                },
                LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = ui.dp(16) },
            )
            addView(
                ImageView(context).apply {
                    setImageDrawable(ui.hostIcon("gs_check_vd_theme_24", ui.onSurface))
                    visibility = if (selected) View.VISIBLE else View.INVISIBLE
                },
                LinearLayout.LayoutParams(ui.dp(24), ui.dp(24)).apply { marginStart = ui.dp(12) },
            )
        }
    }

    fun show(onDismiss: () -> Unit) {
        val context = rows.context
        val radius = ui.dp(28).toFloat()
        val list = CappedScrollView(context, (context.resources.displayMetrics.heightPixels * 0.7f).toInt())
        val sheet = DraggableSheet(context, list) { dialog.dismiss() }.apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(ui.surfaceContainerLow)
                cornerRadii = floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)
            }
            addView(
                View(context).apply {
                    background = GradientDrawable().apply {
                        setColor(ui.onSurfaceVariant)
                        alpha = 0x66
                        cornerRadius = ui.dp(2).toFloat()
                    }
                },
                LinearLayout.LayoutParams(ui.dp(32), ui.dp(4)).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    topMargin = ui.dp(22)
                    bottomMargin = ui.dp(22)
                },
            )
            addView(list.apply { addView(rows) }, MATCH_PARENT, WRAP_CONTENT)
            // Draw behind the gesture/navigation bar like other sheets, but keep the rows above it.
            setOnApplyWindowInsetsListener { view, insets ->
                view.setPadding(0, 0, 0, insets.getInsets(WindowInsets.Type.navigationBars()).bottom + ui.dp(32))
                insets
            }
        }

        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(sheet)
        dialog.setCanceledOnTouchOutside(true)
        dialog.setOnDismissListener { onDismiss() }
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(MATCH_PARENT, WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
            setWindowAnimations(android.R.style.Animation_InputMethod)
            setDimAmount(0.32f)
            setDecorFitsSystemWindows(false)
            attributes = attributes.apply { fitInsetsTypes = 0 }
            navigationBarColor = Color.TRANSPARENT
            isNavigationBarContrastEnforced = false
        }
        dialog.show()
    }

    private operator fun LinearLayout.plusAssign(child: View) = addView(child, MATCH_PARENT, WRAP_CONTENT)

    /**
     * The sheet's frame. Dragging it down closes the sheet, from anywhere when the [list] is
     * scrolled to the top (otherwise the list scrolls first).
     */
    private class DraggableSheet(
        context: Context,
        private val list: View,
        private val onDraggedAway: () -> Unit,
    ) : LinearLayout(context) {
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        private val flingVelocity = 1000 * resources.displayMetrics.density
        private var startY = 0f
        private var dragging = false
        private var velocity: VelocityTracker? = null

        override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startY = event.rawY
                    dragging = false
                }
                MotionEvent.ACTION_MOVE ->
                    if (!dragging && event.rawY - startY > touchSlop && !list.canScrollVertically(-1)) {
                        dragging = true
                        startY += touchSlop
                    }
            }
            if (dragging) track(event)
            return dragging
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            track(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> startY = event.rawY
                MotionEvent.ACTION_MOVE -> translationY = (event.rawY - startY).coerceAtLeast(0f)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val tracker = velocity
                    tracker?.computeCurrentVelocity(1000)
                    val flung = (tracker?.yVelocity ?: 0f) > flingVelocity
                    if (event.actionMasked == MotionEvent.ACTION_UP && (flung || translationY > height / 4f)) {
                        animate().translationY(height.toFloat()).setDuration(150).withEndAction(onDraggedAway)
                    } else {
                        animate().translationY(0f).setDuration(150)
                    }
                    tracker?.recycle()
                    velocity = null
                    dragging = false
                }
            }
            return true
        }

        /** Velocity from screen coordinates: the view itself moves while being dragged. */
        private fun track(event: MotionEvent) {
            val tracker = velocity ?: VelocityTracker.obtain().also { velocity = it }
            val copy = MotionEvent.obtain(event)
            copy.setLocation(event.rawX, event.rawY)
            tracker.addMovement(copy)
            copy.recycle()
        }
    }

    /** A ScrollView that never grows taller than [maxHeight]. */
    private class CappedScrollView(context: Context, private val maxHeight: Int) : ScrollView(context) {
        init {
            isVerticalScrollBarEnabled = false
            overScrollMode = OVER_SCROLL_NEVER
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST))
        }
    }
}
