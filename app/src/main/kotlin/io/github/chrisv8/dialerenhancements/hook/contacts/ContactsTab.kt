// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Chris (chris-v8)
// Origin: written by Chris for the standalone Contacts-tab-filters module (2026-11-27) and moved here.
// Facts about Google Phone 175 were found by reading its code with dexdump and jadx; no Google code is copied.

package io.github.chrisv8.dialerenhancements.hook.contacts

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * How to reload Google Phone's Contacts tab, worked out from the methods the feature found
 * (all names in Google Phone are obfuscated, so only types are followed here):
 * the tab fragment holds its "peer", the peer holds the pager and the list's layout manager,
 * and the pager holds the current paging source.
 */
class ContactsTab(
    fragmentClass: Class<*>,
    sourceClass: Class<*>,
    private val invalidate: Method,
    private val scrollToPosition: Method,
) {
    private val peerField: Field
    private val pagerField: Field
    private val sourceField: Field
    private val layoutManagerField: Field

    init {
        val layoutManagerClass = scrollToPosition.declaringClass
        // fragment → peer → pager → source, exactly one path.
        // Only Google Phone's own classes are followed (not Android's, whose fields are off limits).
        val loader = fragmentClass.classLoader
        val paths = fragmentClass.instanceFields().filter { it.type.classLoader == loader }.flatMap { peer ->
            peer.type.instanceFields().filter { it.type.classLoader == loader }.mapNotNull { pager ->
                pager.type.instanceFields().singleOrNull { it.type == sourceClass }?.let { Triple(peer, pager, it) }
            }
        }
        val (peer, pager, source) = paths.singleOrNull()
            ?: error("${paths.size} ways from the Contacts tab to its paging source")
        peerField = peer
        pagerField = pager
        sourceField = source
        layoutManagerField = peer.type.instanceFields().singleOrNull { layoutManagerClass.isAssignableFrom(it.type) }
            ?: error("no single layout manager in ${peer.type.name}")
        listOf(peerField, pagerField, sourceField, layoutManagerField).forEach { it.isAccessible = true }
        invalidate.isAccessible = true
        scrollToPosition.isAccessible = true
    }

    /**
     * Reloads the Contacts tab of [fragment] after the filter changed: back to the top of the
     * list, then a fresh query (which picks up the new filter and a matching A–Z index).
     */
    fun reload(fragment: Any) {
        val peer = peerField.get(fragment) ?: return
        layoutManagerField.get(peer)?.let { scrollToPosition.invoke(it, 0) }
        val source = pagerField.get(peer)?.let { sourceField.get(it) } ?: return
        FilterStore.startNextLoadAtTop()
        invalidate.invoke(source)
    }

    private companion object {
        fun Class<*>.instanceFields(): List<Field> = declaredFields.filter { !Modifier.isStatic(it.modifiers) }
    }
}
