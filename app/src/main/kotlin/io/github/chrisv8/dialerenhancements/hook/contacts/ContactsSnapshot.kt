// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Chris (chris-v8)
// Origin: written by Chris for the standalone Contacts-tab-filters module (2026-11-27) and moved here.
// Facts about Google Phone 175 were found by reading its code with dexdump and jadx; no Google code is copied.

package io.github.chrisv8.dialerenhancements.hook.contacts

import android.content.ContentResolver
import android.provider.ContactsContract.CommonDataKinds.GroupMembership
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.Groups
import android.provider.ContactsContract.RawContacts
import java.text.Collator

/**
 * The accounts and labels offered in the filter sheets, each with the number of contacts the
 * Contacts tab would show for it. Loaded on a background thread each time a sheet opens.
 */
internal class ContactsSnapshot(
    /** All contacts the tab shows without a filter. */
    val total: Int,
    /** Accounts that hold contacts, plus [AccountKey.DEVICE] (always, last). */
    val accounts: List<Pair<AccountKey, Int>>,
    /** User labels grouped by account (device labels first), each with its member count. */
    val labels: List<Pair<LabelKey, Int>>,
) {
    companion object {
        fun load(resolver: ContentResolver, accountTitle: (AccountKey) -> String): ContactsSnapshot {
            // The same contacts as the tab's own query: everyone with a name.
            val visible = HashSet<Long>()
            resolver.query(
                Contacts.CONTENT_URI, arrayOf(Contacts._ID),
                "${Contacts.DISPLAY_NAME_PRIMARY} IS NOT NULL", null, null,
            )?.use { while (it.moveToNext()) visible += it.getLong(0) }

            val contactOfRawContact = HashMap<Long, Long>()
            val contactsByAccount = HashMap<AccountKey, HashSet<Long>>()
            resolver.query(
                RawContacts.CONTENT_URI,
                arrayOf(
                    RawContacts._ID, RawContacts.CONTACT_ID,
                    RawContacts.ACCOUNT_TYPE, RawContacts.ACCOUNT_NAME, RawContacts.DATA_SET,
                ),
                "${RawContacts.DELETED}=0", null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val contactId = c.getLong(1)
                    contactOfRawContact[c.getLong(0)] = contactId
                    if (contactId !in visible) continue
                    val account = AccountKey.of(c.getString(2), c.getString(3), c.getString(4))
                    contactsByAccount.getOrPut(account) { HashSet() } += contactId
                }
            }

            val groups = ArrayList<LabelKey>()
            resolver.query(
                Groups.CONTENT_URI,
                arrayOf(Groups._ID, Groups.TITLE, Groups.ACCOUNT_TYPE, Groups.ACCOUNT_NAME, Groups.DATA_SET),
                // User labels only: no "My contacts"/"Starred" style system groups.
                "${Groups.DELETED}=0 AND ${Groups.AUTO_ADD}=0 AND ${Groups.FAVORITES}=0" +
                    " AND ${Groups.SYSTEM_ID} IS NULL",
                null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val title = c.getString(1)?.takeIf { it.isNotBlank() } ?: continue
                    groups += LabelKey(c.getLong(0), title, AccountKey.of(c.getString(2), c.getString(3), c.getString(4)))
                }
            }

            val membersByGroup = HashMap<Long, HashSet<Long>>()
            resolver.query(
                Data.CONTENT_URI, arrayOf(Data.RAW_CONTACT_ID, GroupMembership.GROUP_ROW_ID),
                "${Data.MIMETYPE}=?", arrayOf(GroupMembership.CONTENT_ITEM_TYPE), null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val contactId = contactOfRawContact[c.getLong(0)] ?: continue
                    if (contactId in visible) membersByGroup.getOrPut(c.getLong(1)) { HashSet() } += contactId
                }
            }

            val collator = Collator.getInstance()
            // Google accounts first, then the others by name, the device last (as Google Contacts).
            val accountOrder = compareBy<AccountKey> { if (it.isDevice) 2 else if (it.type == "com.google") 0 else 1 }
                .thenBy(collator) { accountTitle(it) }
            val accounts = (contactsByAccount.keys + AccountKey.DEVICE)
                .sortedWith(accountOrder)
                .map { it to (contactsByAccount[it]?.size ?: 0) }

            // In the label sheet Google Contacts lists the device's labels first.
            val labelAccountOrder = compareBy<AccountKey> { if (it.isDevice) 0 else 1 }.then(accountOrder)
            val labels = groups
                .sortedWith(compareBy(labelAccountOrder) { it: LabelKey -> it.account }.thenBy(collator) { it.title })
                .map { it to (membersByGroup[it.id]?.size ?: 0) }

            return ContactsSnapshot(visible.size, accounts, labels)
        }
    }
}
