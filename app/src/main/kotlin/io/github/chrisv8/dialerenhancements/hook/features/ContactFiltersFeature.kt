// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Chris (chris-v8)
// Origin: written by Chris for the standalone Contacts-tab-filters module (2026-11-27) and moved here.
// Facts about Google Phone 175 were found by reading its code with dexdump and jadx; no Google code is copied.

package io.github.chrisv8.dialerenhancements.hook.features

import android.content.res.Resources
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import io.github.chrisv8.dialerenhancements.hook.AppContext
import io.github.chrisv8.dialerenhancements.hook.Discovery
import io.github.chrisv8.dialerenhancements.hook.Feature
import io.github.chrisv8.dialerenhancements.hook.HookLog
import io.github.chrisv8.dialerenhancements.hook.HookSettings
import io.github.chrisv8.dialerenhancements.hook.Hooks
import io.github.chrisv8.dialerenhancements.hook.Installed
import io.github.chrisv8.dialerenhancements.hook.Phase
import io.github.chrisv8.dialerenhancements.hook.contacts.ContactsTab
import io.github.chrisv8.dialerenhancements.hook.contacts.FilterBar
import io.github.chrisv8.dialerenhancements.hook.contacts.FilterStore
import io.github.chrisv8.dialerenhancements.hook.find.Finder
import io.github.chrisv8.dialerenhancements.hook.find.Match
import io.github.chrisv8.dialerenhancements.hook.find.shape
import io.github.chrisv8.dialerenhancements.shared.Contract.FeatureIds
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Account and label filters on Google Phone's Contacts tab, like those of Google Contacts.
 *
 * - The tab's onCreateView gets a filter bar under the search bar ([FilterBar]).
 * - The tab's single list query gets an extra condition for the chosen account or label, so
 *   the list, its A–Z index and the fast scroller all come from the same filtered query.
 * - After a filter change the reloaded list starts at the top.
 *
 * Switched with the "Contacts tab filters" setting: the bar appears from the next start of
 * Google Phone, and while the setting is off the list is never filtered.
 */
object ContactFiltersFeature : Feature {
    override val id = FeatureIds.CONTACT_FILTERS

    // Critical so the hooks are in place before Google Phone opens straight on the Contacts tab.
    override val phase = Phase.CRITICAL

    /** Trace label in the tab's onCreateView. */
    private const val VIEW_MARKER = "ContactsPagingFragmentPeer_onCreateView"

    /** Trace label in the paging source's load(). */
    private const val LOAD_MARKER = "ContactsPagingSource#load"

    /** Android's own name for this class survived Google's renaming. */
    private const val LINEAR_LAYOUT_MANAGER = "android.support.v7.widget.LinearLayoutManager"

    /** The tab's list query: contacts with the A–Z index; no other Google Phone query uses it. */
    private const val TAB_URI = "content://com.android.contacts/contacts?android.provider.extra.ADDRESS_BOOK_INDEX=true"
    private val TAB_SELECTIONS = setOf("display_name IS NOT NULL", "display_name_alt IS NOT NULL")

    private const val VIEW = "onCreateView"
    private const val QUERY = "query"
    private const val REFRESH_KEY = "refreshKey"
    private const val INVALIDATE = "invalidate"
    private const val SCROLL = "scrollToPosition"

    @Volatile
    private var moduleResources: Resources? = null

    override fun discover(finder: Finder): Discovery {
        val view = finder.unique(VIEW_MARKER, shape {
            params(LayoutInflater::class.java, ViewGroup::class.java, Bundle::class.java)
            returnsSubtypeOf(View::class.java)
        }).methodOr { return it }
        val load = finder.unique(LOAD_MARKER, shape { paramCount(2) }).methodOr { return it }
        val source = load.declaringClass
        // The method that loads one page (start, size, …) and runs the query.
        val page = finder.uniqueDeclared(source, shape {
            paramCount(4)
            where("(int, int, …)") { it.parameterTypes[0] == Int::class.javaPrimitiveType && it.parameterTypes[1] == Int::class.javaPrimitiveType }
        }).methodOr { return it }
        val query = finder.uniqueCallee(listOf(page), shape {
            params(Uri::class.java, Array<String>::class.java, String::class.java, Array<String>::class.java, String::class.java)
            where("not static") { !Modifier.isStatic(it.modifiers) }
        }).methodOr { return it }
        // PagingSource.getRefreshKey(state): the paging source's only one-argument method.
        val refreshKey = finder.uniqueDeclared(source, shape {
            paramCount(1)
            returns(Any::class.java)
        }).methodOr { return it }
        // PagingSource.invalidate(): final, no arguments, nothing returned.
        val invalidate = finder.uniqueDeclared(source.superclass, shape {
            paramCount(0)
            returns(Void.TYPE)
            where("final") { Modifier.isFinal(it.modifiers) }
        }).methodOr { return it }
        // LinearLayoutManager.scrollToPosition(int): its one (int) method overriding RecyclerView's.
        val layoutManager = try {
            Class.forName(LINEAR_LAYOUT_MANAGER, false, view.declaringClass.classLoader)
        } catch (_: ClassNotFoundException) {
            return Discovery.nothing("no $LINEAR_LAYOUT_MANAGER")
        }
        val scroll = finder.uniqueDeclared(layoutManager, shape {
            params(Int::class.javaPrimitiveType!!)
            returns(Void.TYPE)
            where("overrides the layout manager base") { method ->
                runCatching { layoutManager.superclass.getDeclaredMethod(method.name, *method.parameterTypes) }.isSuccess
            }
        }).methodOr { return it }
        return Discovery(
            mapOf(VIEW to view, QUERY to query, REFRESH_KEY to refreshKey, INVALIDATE to invalidate, SCROLL to scroll),
        )
    }

    override fun install(discovery: Discovery, hooks: Hooks): Installed {
        val targets = discovery.targets
        val view = targets.getValue(VIEW)
        val refreshKey = targets.getValue(REFRESH_KEY)
        val tab = ContactsTab(view.declaringClass, refreshKey.declaringClass, targets.getValue(INVALIDATE), targets.getValue(SCROLL))
        val moduleInfo = hooks.moduleApplicationInfo

        hooks.intercept(targets.getValue(QUERY)) { chain ->
            val uri = chain.getArg(0) as Uri?
            val selection = chain.getArg(2) as String?
            val context = AppContext.application
            if (uri?.toString() != TAB_URI || selection !in TAB_SELECTIONS || context == null || !HookSettings.contactFilters) {
                return@intercept chain.proceed()
            }
            val (extra, extraArgs) = FilterStore.get(context).selection() ?: return@intercept chain.proceed()
            @Suppress("UNCHECKED_CAST")
            val args = (chain.getArg(3) as Array<String>?).orEmpty().toList() + extraArgs
            chain.proceed(arrayOf(uri, chain.getArg(1), "($selection) AND $extra", args.toTypedArray(), chain.getArg(4)))
        }

        hooks.intercept(refreshKey) { chain -> if (FilterStore.takeStartAtTop()) null else chain.proceed() }

        hooks.intercept(view) { chain ->
            val root = chain.proceed()
            // Whatever goes wrong here, Google Phone keeps its normal Contacts tab.
            try {
                if (root is View && HookSettings.contactFilters) {
                    val resources = moduleResources
                        ?: root.context.packageManager.getResourcesForApplication(moduleInfo).also { moduleResources = it }
                    FilterBar.install(tab, chain.thisObject, root, resources)
                }
            } catch (t: Throwable) {
                HookLog.error(id, "can't add the filters to the Contacts tab", t)
            }
            root
        }
        return Installed(3)
    }

    private inline fun Match.methodOr(otherwise: (Discovery) -> Nothing): Method =
        methodOrNull ?: otherwise(Discovery.nothing(describe()))
}
