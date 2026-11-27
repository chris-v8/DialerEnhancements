// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Chris (chris-v8)
// Origin: written by Chris for the standalone Contacts-tab-filters module (2026-11-27) and moved here.
// Facts about Google Phone 175 were found by reading its code with dexdump and jadx; no Google code is copied.

package io.github.chrisv8.dialerenhancements.hook.contacts

import android.content.Context
import android.content.SharedPreferences
import android.provider.ContactsContract.CommonDataKinds.GroupMembership
import java.util.concurrent.atomic.AtomicBoolean

/** A contacts account. Contacts stored only on the phone all count as [DEVICE]. */
internal data class AccountKey(val type: String?, val name: String?, val dataSet: String?) {

    val isDevice: Boolean get() = type.isNullOrEmpty() || type in DEVICE_ACCOUNT_TYPES

    companion object {
        val DEVICE = AccountKey(null, null, null)

        /** Account types phone makers use for contacts kept only on the phone. */
        val DEVICE_ACCOUNT_TYPES = listOf(
            "com.android.localphone",
            "com.oppo.contacts.device",
            "vnd.sec.contact.phone",
            "com.android.huawei.phone",
            "com.sonyericsson.localcontacts",
            "com.lge.sync",
            "com.htc.android.pcsc",
            "com.android.contacts.default",
            "Local Phone Account",
        )

        fun of(type: String?, name: String?, dataSet: String?): AccountKey =
            AccountKey(type, name, dataSet).let { if (it.isDevice) DEVICE else it }
    }
}

/** A contact label (a contacts group). [id] is the group's row id. */
internal data class LabelKey(val id: Long, val title: String, val account: AccountKey)

/** What the Contacts tab shows: everything, one account, and/or the members of one label. */
internal data class ContactFilter(val account: AccountKey? = null, val label: LabelKey? = null) {

    /**
     * Extra SQL condition on the `Contacts` table (column `_id` is the contact id) with its
     * arguments, or null when nothing is filtered.
     */
    fun selection(): Pair<String, List<String>>? {
        val clauses = ArrayList<String>(2)
        val args = ArrayList<String>()
        account?.let { clauses += accountClause(it, args) }
        label?.let {
            args += it.id.toString()
            clauses += "_id IN (SELECT contact_id FROM view_data" +
                " WHERE mimetype='${GroupMembership.CONTENT_ITEM_TYPE}' AND data1=?" +
                " AND raw_contact_id IN (SELECT _id FROM raw_contacts WHERE deleted=0))"
        }
        return if (clauses.isEmpty()) null else clauses.joinToString(" AND ") to args
    }

    private fun accountClause(account: AccountKey, args: MutableList<String>): String {
        val where = if (account.isDevice) {
            args += AccountKey.DEVICE_ACCOUNT_TYPES
            val marks = AccountKey.DEVICE_ACCOUNT_TYPES.joinToString(",") { "?" }
            "(account_type IS NULL OR account_type='' OR account_type IN ($marks))"
        } else {
            listOf(
                equalsOrNull("account_type", account.type, args),
                equalsOrNull("account_name", account.name, args),
                equalsOrNull("data_set", account.dataSet, args),
            ).joinToString(" AND ")
        }
        return "_id IN (SELECT contact_id FROM view_raw_contacts WHERE deleted=0 AND $where)"
    }

    private fun equalsOrNull(column: String, value: String?, args: MutableList<String>): String =
        if (value == null) {
            "$column IS NULL"
        } else {
            args += value
            "$column=?"
        }
}

/**
 * The chosen filter, kept in Google Phone's own private storage so it survives restarts.
 * Read by the query hook on background threads and written from the UI thread.
 */
internal object FilterStore {
    private const val PREFS = "io.github.chrisv8.dialerenhancements.contact_filters"

    @Volatile
    private var cached: ContactFilter? = null

    private val nextLoadAtTop = AtomicBoolean(false)

    fun get(context: Context): ContactFilter =
        cached ?: synchronized(this) {
            cached ?: read(prefs(context)).also { cached = it }
        }

    fun set(context: Context, filter: ContactFilter) {
        cached = filter
        prefs(context).edit().apply {
            clear()
            filter.account?.let { putAccount("account", it) }
            filter.label?.let {
                putLong("label_id", it.id)
                putString("label_title", it.title)
                putAccount("label_account", it.account)
            }
        }.apply()
    }

    /** The next reload of the list starts at the first contact instead of the old position. */
    fun startNextLoadAtTop() = nextLoadAtTop.set(true)

    fun takeStartAtTop(): Boolean = nextLoadAtTop.getAndSet(false)

    private fun prefs(context: Context): SharedPreferences =
        (context.applicationContext ?: context).getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun read(prefs: SharedPreferences): ContactFilter {
        val account = prefs.getAccount("account")
        val labelId = prefs.getLong("label_id", -1)
        val label = if (labelId < 0) {
            null
        } else {
            LabelKey(
                labelId,
                prefs.getString("label_title", null).orEmpty(),
                prefs.getAccount("label_account") ?: AccountKey.DEVICE,
            )
        }
        return ContactFilter(account, label)
    }

    private fun SharedPreferences.Editor.putAccount(prefix: String, account: AccountKey) {
        putBoolean(prefix, true)
        putString("${prefix}_type", account.type)
        putString("${prefix}_name", account.name)
        putString("${prefix}_data_set", account.dataSet)
    }

    private fun SharedPreferences.getAccount(prefix: String): AccountKey? =
        if (!getBoolean(prefix, false)) {
            null
        } else {
            AccountKey.of(
                getString("${prefix}_type", null),
                getString("${prefix}_name", null),
                getString("${prefix}_data_set", null),
            )
        }
}
