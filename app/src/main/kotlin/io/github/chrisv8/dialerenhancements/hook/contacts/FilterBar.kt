// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Chris (chris-v8)
// Origin: written by Chris for the standalone Contacts-tab-filters module (2026-11-27) and moved here.
// Facts about Google Phone 175 were found by reading its code with dexdump and jadx; no Google code is copied.

package io.github.chrisv8.dialerenhancements.hook.contacts

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import io.github.chrisv8.dialerenhancements.R
import io.github.chrisv8.dialerenhancements.hook.HookLog
import io.github.chrisv8.dialerenhancements.shared.Contract.FeatureIds
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * The row of filters added to the top of Google Phone's Contacts tab: an account chip on the
 * left ("All contacts ▾") and a label button on the right, which turns into a chip with the
 * label's name and a close button while a label is chosen. Both open a [FilterSheet].
 */
internal class FilterBar private constructor(
    private val tab: ContactsTab,
    private val fragment: Any,
    private val ui: HostUi,
) {
    private val context = ui.context
    private var filter = FilterStore.get(context)
    private var loading = false

    private val accountIcon = ImageView(context)
    private val accountText = TextView(context).apply {
        ui.style(this, "textAppearanceTitleMedium", ui.onSurface)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }
    private val accountArrow = ImageView(context)
    private val accountChip = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = ui.dp(40)
        setPaddingRelative(ui.dp(12), 0, ui.dp(8), 0)
        background = pill(Color.TRANSPARENT)
        addView(accountIcon, ui.dp(24), ui.dp(24))
        addView(accountText, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = ui.dp(8) })
        addView(accountArrow, LinearLayout.LayoutParams(ui.dp(24), ui.dp(24)).apply { marginStart = ui.dp(4) })
        setOnClickListener { openAccounts() }
    }

    private val labelIcon = ImageView(context)
    private val labelText = TextView(context).apply {
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        // Room left next to the account chip, which shows only its icons while a label is chosen.
        maxWidth = context.resources.displayMetrics.widthPixels - ui.dp(BAR_PADDING_DP + 72 + 8 + 84)
    }
    private val labelClose = ImageView(context).apply {
        setPadding(ui.dp(6), ui.dp(6), ui.dp(6), ui.dp(6))
        background = ui.ripple(borderless = true)
        contentDescription = ui.string(R.string.contacts_filter_clear_label)
        setOnClickListener { apply(filter.copy(label = null)) }
    }
    private val labelChip = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = ui.dp(40)
        addView(labelIcon, ui.dp(24), ui.dp(24))
        addView(labelText, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = ui.dp(8) })
        addView(labelClose, LinearLayout.LayoutParams(ui.dp(32), ui.dp(32)).apply { marginStart = ui.dp(4) })
        setOnClickListener { openLabels() }
    }

    val view = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPaddingRelative(ui.dp(16), 0, ui.dp(BAR_PADDING_DP - 16), 0)
        setBackgroundColor(ui.background)
        // The account chip takes the free space but only as much as its text needs.
        addView(
            FrameLayout(context).apply {
                addView(accountChip, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER_VERTICAL or Gravity.START))
            },
            LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f),
        )
        addView(labelChip, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = ui.dp(8) })
    }

    init {
        render()
    }

    private fun render() {
        val account = filter.account
        val accountTitle = account?.let(::title) ?: ui.string(R.string.contacts_filter_all)
        accountText.text = accountTitle
        // A label belongs to one account, so while a label is chosen its name gets the room.
        accountText.visibility = if (filter.label == null) View.VISIBLE else View.GONE
        accountChip.contentDescription = "${ui.string(R.string.contacts_filter_by_account)}: $accountTitle"
        accountIcon.setImageDrawable(
            ui.hostIcon(if (account == null) "gs_people_vd_theme_24" else "gs_person_vd_theme_24", ui.onSurface),
        )
        setSheetOpen(accountChip, accountArrow, open = false)

        val label = filter.label
        if (label == null) {
            // A plain icon button, like Google Contacts.
            labelChip.setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8))
            labelChip.background = ui.ripple(borderless = true)
            labelChip.contentDescription = ui.string(R.string.contacts_filter_by_label)
            labelIcon.setImageDrawable(ui.moduleIcon(R.drawable.ic_contacts_label, ui.onSurface))
            labelText.visibility = View.GONE
            labelClose.visibility = View.GONE
        } else {
            labelChip.setPaddingRelative(ui.dp(12), 0, ui.dp(4), 0)
            labelChip.background = pill(ui.secondaryContainer)
            labelChip.contentDescription = "${ui.string(R.string.contacts_filter_by_label)}: ${label.title}"
            labelIcon.setImageDrawable(ui.moduleIcon(R.drawable.ic_contacts_label, ui.onSecondaryContainer))
            labelText.text = label.title
            ui.style(labelText, "textAppearanceLabelLarge", ui.onSecondaryContainer)
            labelText.visibility = View.VISIBLE
            labelClose.setImageDrawable(ui.hostIcon("gs_close_vd_theme_24", ui.onSecondaryContainer))
            labelClose.visibility = View.VISIBLE
        }
    }

    private fun openAccounts(): Unit = whenLoaded { snapshot ->
        val sheet = FilterSheet(ui, dialogContext())
        val current = filter.account
        sheet.row(
            ui.hostIcon("gs_people_vd_theme_24", ui.onSurface), ui.string(R.string.contacts_filter_all),
            snapshot.total, current == null,
        ) { chooseAccount(null) }
        for ((account, count) in snapshot.accounts) {
            sheet.row(ui.hostIcon("gs_person_vd_theme_24", ui.onSurface), title(account), count, account == current) {
                chooseAccount(account)
            }
        }
        setSheetOpen(accountChip, accountArrow, open = true)
        sheet.show { setSheetOpen(accountChip, accountArrow, open = false) }
    }

    private fun openLabels(): Unit = whenLoaded { snapshot ->
        val sheet = FilterSheet(ui, dialogContext())
        val account = filter.account
        // With one account chosen, only its labels are offered.
        val labels = snapshot.labels.filter { account == null || it.first.account == account }
        if (labels.isEmpty()) sheet.note(ui.string(R.string.contacts_filter_no_labels))
        var section: AccountKey? = null
        for ((label, count) in labels) {
            if (label.account != section) {
                section = label.account
                sheet.header(title(label.account))
            }
            sheet.row(ui.moduleIcon(R.drawable.ic_contacts_label, ui.onSurface), label.title, count, label.id == filter.label?.id) {
                chooseLabel(label)
            }
        }
        sheet.show {}
    }

    private fun chooseAccount(account: AccountKey?) {
        // A label belongs to one account; keep it only if that account stays chosen.
        val label = filter.label?.takeIf { account != null && it.account == account }
        apply(ContactFilter(account, label))
    }

    /** Choosing the current label again turns the label filter off. */
    private fun chooseLabel(label: LabelKey): Unit =
        apply(filter.copy(label = if (label.id == filter.label?.id) null else label))

    private fun apply(newFilter: ContactFilter) {
        if (newFilter == filter) return
        val reload = newFilter.selection() != filter.selection()
        filter = newFilter
        FilterStore.set(context, newFilter)
        render()
        if (reload) {
            HookLog.info(
                FeatureIds.CONTACT_FILTERS,
                "Filter changed: account=${newFilter.account?.let { if (it.isDevice) "device" else it.type } ?: "all"}" +
                    ", label=${newFilter.label?.id ?: "none"}",
            )
            try {
                tab.reload(fragment)
            } catch (t: Throwable) {
                HookLog.error(FeatureIds.CONTACT_FILTERS, "Could not reload the contacts list", t)
            }
        }
    }

    /** Drops a saved account or label that no longer exists and picks up renamed labels. */
    private fun forgetMissingChoices() {
        if (filter.account == null && filter.label == null) return
        whenLoaded(needsWindow = false) { snapshot ->
            var fixed = filter
            fixed.account?.let { account ->
                if (snapshot.accounts.none { it.first == account }) fixed = ContactFilter()
            }
            fixed.label?.let { label ->
                fixed = fixed.copy(label = snapshot.labels.firstOrNull { it.first.id == label.id }?.first)
            }
            apply(fixed)
        }
    }

    /**
     * Reads accounts and labels off the main thread, then runs [action] on it. Actions that
     * open a sheet are dropped when the tab has gone away in the meantime ([needsWindow]).
     */
    private fun whenLoaded(needsWindow: Boolean = true, action: (ContactsSnapshot) -> Unit) {
        if (loading) return
        loading = true
        val resolver = context.contentResolver
        worker.execute {
            val snapshot = try {
                ContactsSnapshot.load(resolver, ::title)
            } catch (t: Throwable) {
                HookLog.error(FeatureIds.CONTACT_FILTERS, "Could not read accounts and labels", t)
                null
            }
            mainThread.post {
                loading = false
                if (snapshot != null && (!needsWindow || view.isAttachedToWindow)) action(snapshot)
            }
        }
    }

    private fun title(account: AccountKey): String =
        if (account.isDevice) ui.string(R.string.contacts_filter_device) else account.name ?: account.type.orEmpty()

    private fun setSheetOpen(chip: View, arrow: ImageView, open: Boolean) {
        chip.background = pill(if (open) ui.secondaryContainer else Color.TRANSPARENT)
        arrow.setImageDrawable(
            ui.hostIcon(if (open) "gs_arrow_drop_up_vd_theme_24" else "gs_arrow_drop_down_vd_theme_24", ui.onSurface),
        )
    }

    /** A rounded chip background with the theme's touch ripple. */
    private fun pill(color: Int): Drawable {
        fun shape(fill: Int) = GradientDrawable().apply {
            cornerRadius = ui.dp(20).toFloat()
            setColor(fill)
        }
        return RippleDrawable(ColorStateList.valueOf(ui.highlight), shape(color), shape(Color.BLACK))
    }

    /** Dialogs need the activity itself; the fragment's context only wraps it. */
    private fun dialogContext(): Context {
        var current: Context? = context
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return context
    }

    companion object {
        private const val BAR_HEIGHT_DP = 56
        private const val BAR_PADDING_DP = 28

        private val worker: ExecutorService = Executors.newSingleThreadExecutor { task ->
            Thread(task, "ContactsTabFilters").apply { isDaemon = true }
        }
        private val mainThread = Handler(Looper.getMainLooper())

        /**
         * Adds the filter bar to a freshly created Contacts tab [root] (layout fragment_contacts:
         * a ConstraintLayout with the list, the fast scroller and the empty-list placeholder).
         */
        fun install(tab: ContactsTab, fragment: Any, root: View, moduleResources: Resources) {
            val parent = root as? ViewGroup ?: return
            // Without contacts permission the tab shows its own "allow access" screen instead.
            if (root.context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                return
            }
            val ui = HostUi(root.context, moduleResources)
            val list = root.findViewById<View>(ui.hostId("recycler_view")) ?: return
            val listTop = (list.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin ?: return
            val barHeight = ui.dp(BAR_HEIGHT_DP)

            // Everything that starts at the top of the list moves down to make room for the bar.
            for (name in listOf("recycler_view", "fast_scroller", "empty_list_view_stub", "empty_list_view")) {
                val child = root.findViewById<View>(ui.hostId(name)) ?: continue
                val params = child.layoutParams as? ViewGroup.MarginLayoutParams ?: continue
                params.topMargin += barHeight
                child.layoutParams = params
            }

            val bar = FilterBar(tab, fragment, ui)
            // No constraints: the bar sits at the top of the tab and is moved below the search bar.
            parent.addView(bar.view, ViewGroup.LayoutParams(MATCH_PARENT, barHeight))
            bar.view.translationY = listTop.toFloat()
            bar.forgetMissingChoices()
        }
    }
}
