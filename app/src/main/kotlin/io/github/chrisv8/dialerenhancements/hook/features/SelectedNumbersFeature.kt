// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Chris (chris-v8)
// Origin: written for DialerEnhancements from docs/design/SPEC.md F10 and REWRITE_PLAN.md S6–S10.
// The feature's design (multi-select through Google's own code paths, queue, snapshot) is Chris's, from CallRecording.

package io.github.chrisv8.dialerenhancements.hook.features

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import io.github.chrisv8.dialerenhancements.BuildConfig
import io.github.chrisv8.dialerenhancements.hook.AppContext
import io.github.chrisv8.dialerenhancements.hook.BridgeClient
import io.github.chrisv8.dialerenhancements.hook.Discovery
import io.github.chrisv8.dialerenhancements.hook.Feature
import io.github.chrisv8.dialerenhancements.hook.HookLog
import io.github.chrisv8.dialerenhancements.hook.HookSettings
import io.github.chrisv8.dialerenhancements.hook.Hooks
import io.github.chrisv8.dialerenhancements.hook.Installed
import io.github.chrisv8.dialerenhancements.hook.Phase
import io.github.chrisv8.dialerenhancements.hook.find.Finder
import io.github.chrisv8.dialerenhancements.hook.find.Match
import io.github.chrisv8.dialerenhancements.hook.find.shape
import io.github.chrisv8.dialerenhancements.hook.util.ProtoNumbers
import io.github.chrisv8.dialerenhancements.shared.Contract
import io.github.chrisv8.dialerenhancements.shared.Contract.FeatureIds
import java.io.File
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.WeakHashMap

/**
 * SPEC F10: several numbers at once on Google Phone's Selected-numbers list
 * (Settings → Call recording → Selected numbers).
 *
 * - **From Google Phone**: its Add button opens the module's multi-select list instead of the
 *   system's one-number picker. Ticked numbers are added, unticked ones removed, all at once.
 * - **From the module app**: changes are queued in the app and applied the next time Google's
 *   Selected-numbers screen opens.
 *
 * The module never writes Google's settings file. Every change goes through Google Phone's own
 * code: additions through its picker-result handler (once per number), removals through the
 * listener its "remove" buttons use. Google applies each one as a separate small change.
 */
object SelectedNumbersFeature : Feature {
    override val id = FeatureIds.SELECTED_NUMBERS
    override val phase = Phase.DEFERRED

    private const val RESULT_MARKER = "uri received from system contact picker"
    private const val CLICK_MARKER = "invoke the system contact picker"

    private const val RESULT = "resultHandler"
    private const val VIEW_CREATED = "viewCreated"
    private const val REGISTER = "registerListener"
    private const val ADD_CLICK = "addClick"

    /** What the system's "pick one phone number" screen is asked for. */
    private const val PHONE_PICK_TYPE = "vnd.android.cursor.dir/phone_v2"

    /** Request code Google's Add button uses (seen on v88 and v247); replaced by the one learned at run time. */
    private const val DEFAULT_PICK_REQUEST_CODE = 9

    /** Google's settings file holding the list (same name on v88 and v247). */
    private const val SETTINGS_FILE = "CallRecordingSettingsData.pb"

    /** Google saves the list in the background; read it again only after this long. */
    private const val SNAPSHOT_DELAY_MS = 3_000L

    private val main = Handler(Looper.getMainLooper())
    private lateinit var hooks: Hooks
    private lateinit var resultHandler: Method

    /** True on a thread while Google's Add button handler runs. */
    private val insideAddClick = ThreadLocal<Boolean>()

    /** How to remove a number on one Selected-numbers screen: Google's own "remove" listener. */
    private class Remover(val listener: Any, val method: Method, val event: Constructor<*>) {
        fun remove(number: String) {
            method.invoke(listener, event.newInstance(number))
        }
    }

    /** Marks a screen where more than one listener qualified: removal is turned off there. */
    private val AMBIGUOUS = Any()

    /** Per screen (fragment) instance; weak so closed screens can be cleaned up. */
    private val removers = WeakHashMap<Any, Any>()

    @Volatile
    private var learnedRequestCode: Int? = null

    override fun discover(finder: Finder): Discovery {
        val result = when (
            val match = finder.unique(RESULT_MARKER, shape {
                params(Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Intent::class.java)
                returns(Void.TYPE)
            })
        ) {
            is Match.Found -> match.method
            else -> return Discovery.nothing(match.describe())
        }
        val fragment = result.declaringClass
        val found = linkedMapOf(RESULT to result)
        val notes = mutableListOf<String>()

        val viewCreated = finder.uniqueDeclared(fragment, shape {
            params(View::class.java, Bundle::class.java)
            returns(Void.TYPE)
        }).methodOrNull
        if (viewCreated != null) {
            found[VIEW_CREATED] = viewCreated
            val register = finder.uniqueCallee(listOf(viewCreated), shape {
                paramCount(3)
                isStatic()
                where("(screen, Class, interface)") {
                    val p = it.parameterTypes
                    p[0].isAssignableFrom(fragment) && p[1] == Class::class.java && p[2].isInterface
                }
            }).methodOrNull
            if (register != null) found[REGISTER] = register else notes += "removing unavailable (no listener registration)"
        } else {
            notes += "removing and the app's queue unavailable (no screen-created method)"
        }

        val click = finder.unique(CLICK_MARKER, shape {
            params(View::class.java)
            returns(Void.TYPE)
        }).methodOrNull
        if (click != null) found[ADD_CLICK] = click else notes += "Add button keeps the system picker"

        return Discovery(found, notes.joinToString("; "))
    }

    override fun install(discovery: Discovery, hooks: Hooks): Installed {
        this.hooks = hooks
        resultHandler = discovery.targets.getValue(RESULT)
        val fragment = resultHandler.declaringClass
        val notes = mutableListOf<String>()
        var installed = 0

        hookResultHandler()
        installed++

        discovery.targets[ADD_CLICK]?.let {
            hookAddClick(it)
            hookPickerLaunch()
            installed += 2
            HookLog.info(id, "Google Phone's Add button will open the multi-select list")
        }

        discovery.targets[REGISTER]?.let { register ->
            val listener = listenerMethodOf(register.parameterTypes[2])
            if (listener == null) {
                notes += "removing unavailable (unexpected listener type)"
            } else {
                hookRegistration(register, fragment, listener)
                installed++
            }
        }

        discovery.targets[VIEW_CREATED]?.let {
            hookScreenShown(it)
            installed++
        }
        return Installed(installed, notes.joinToString("; "))
    }

    // ---- Route A: Google's Add button opens the module's picker (SPEC F10.2) ----

    private fun hookAddClick(click: Method) {
        hooks.intercept(click) { chain ->
            insideAddClick.set(true)
            try {
                chain.proceed()
            } finally {
                insideAddClick.remove()
            }
        }
    }

    private fun hookPickerLaunch() {
        val start = Activity::class.java.getMethod(
            "startActivityForResult", Intent::class.java, Int::class.javaPrimitiveType, Bundle::class.java,
        )
        hooks.intercept(start) { chain ->
            val pick = chain.getArg(0) as? Intent
            val activity = chain.thisObject as? Activity
            if (insideAddClick.get() != true || pick == null || activity == null ||
                pick.component != null || pick.action != Intent.ACTION_PICK || pick.type != PHONE_PICK_TYPE ||
                !HookSettings.multiSelect
            ) {
                return@intercept chain.proceed()
            }
            val picker = Intent(pick).setComponent(ComponentName(BuildConfig.APPLICATION_ID, Contract.Picker.ACTIVITY))
            if (activity.packageManager.resolveActivity(picker, 0) == null) {
                HookLog.warn(id, "the module's picker can't be opened; using the system picker")
                return@intercept chain.proceed()
            }
            currentNumbers(activity)?.let {
                picker.putStringArrayListExtra(Contract.Picker.EXTRA_EXISTING, ArrayList(it))
                BridgeClient.putSnapshot(it)
            }
            HookLog.debug(id) { "Add button: opening the multi-select list" }
            chain.proceed(arrayOf(picker, chain.getArg(1), chain.getArg(2)))
        }
    }

    // ---- Picker result: apply everything through Google's own code (SPEC F10.3) ----

    private fun hookResultHandler() {
        hooks.intercept(resultHandler) { chain ->
            val requestCode = chain.getArg(0) as Int
            val resultCode = chain.getArg(1) as Int
            val data = chain.getArg(2) as? Intent
            val adds = urisFrom(data)
            val removes = stringsFrom(data, Contract.Picker.EXTRA_REMOVE)
            if (data?.hasExtra(Contract.Picker.EXTRA_ADD) == true || data?.hasExtra(Contract.Picker.EXTRA_REMOVE) == true) {
                rememberRequestCode(requestCode)
            }
            // Nothing to remove and at most one to add: Google's normal path handles it, exactly as
            // with the system picker (our result's data is the first added number).
            if (removes.isEmpty() && adds.size < 2) return@intercept chain.proceed()

            val fragment = chain.thisObject ?: return@intercept chain.proceed()
            apply(fragment, requestCode, resultCode, adds, removes, "picker")
            null
        }
    }

    /**
     * Removes, then adds, each through Google's own code. Returns true if every change went in.
     * Must run on the main thread, like Google's own handlers.
     */
    private fun apply(
        fragment: Any,
        requestCode: Int,
        resultCode: Int,
        adds: List<Uri>,
        removes: List<String>,
        source: String,
    ): Boolean {
        var removed = 0
        if (removes.isNotEmpty()) {
            val remover = synchronized(removers) { removers[fragment] } as? Remover
            if (remover == null) {
                HookLog.warn(id, "$source: can't remove ${removes.size} number(s) on this screen")
            } else {
                for (number in removes) {
                    try {
                        remover.remove(number)
                        removed++
                    } catch (t: Throwable) {
                        HookLog.warn(id, "$source: one removal failed (${t.javaClass.simpleName})")
                    }
                }
            }
        }
        var added = 0
        for (uri in adds) {
            try {
                hooks.callOriginal(resultHandler, fragment, requestCode, resultCode, Intent().setData(uri))
                added++
            } catch (t: Throwable) {
                HookLog.warn(id, "$source: one addition failed (${t.javaClass.simpleName})")
            }
        }
        HookLog.info(id, "$source: added $added of ${adds.size}, removed $removed of ${removes.size}")
        BridgeClient.reportHit(id)
        main.postDelayed({ AppContext.application?.let(::sendSnapshot) }, SNAPSHOT_DELAY_MS)
        return added == adds.size && removed == removes.size
    }

    // ---- Removal: Google's own "remove" listener (SPEC F10.4) ----

    /** The single abstract one-parameter method of the listener interface, or null. */
    private fun listenerMethodOf(type: Class<*>): Method? {
        val abstract = type.methods.filter { Modifier.isAbstract(it.modifiers) }
        return abstract.singleOrNull()?.takeIf { it.parameterCount == 1 }
    }

    private fun hookRegistration(register: Method, fragment: Class<*>, listenerMethod: Method) {
        listenerMethod.isAccessible = true
        hooks.intercept(register) { chain ->
            val owner = chain.getArg(0)
            val eventType = chain.getArg(1) as? Class<*>
            val listener = chain.getArg(2)
            if (owner != null && owner.javaClass == fragment && eventType != null && listener != null) {
                capture(owner, eventType, listener, listenerMethod)
            }
            chain.proceed()
        }
        HookLog.debug(id) { "remove listener hooked: ${register.declaringClass.name}.${register.name}" }
    }

    private fun capture(owner: Any, eventType: Class<*>, listener: Any, listenerMethod: Method) {
        // The remove event carries the stored number; other events (for example "turn on") don't.
        val event = try {
            eventType.getDeclaredConstructor(String::class.java).apply { isAccessible = true }
        } catch (_: NoSuchMethodException) {
            return
        }
        if (!listenerMethod.parameterTypes[0].isAssignableFrom(eventType)) return
        synchronized(removers) {
            val previous = removers[owner]
            removers[owner] = if (previous == null) Remover(listener, listenerMethod, event) else AMBIGUOUS
            if (previous != null && previous !== AMBIGUOUS) {
                HookLog.warn(id, "more than one remove listener on this screen; removing turned off there")
            }
        }
    }

    // ---- Screen opened: snapshot and the app's queue (SPEC F10.5, F10.6) ----

    private fun hookScreenShown(viewCreated: Method) {
        hooks.intercept(viewCreated) { chain ->
            val result = chain.proceed()
            val fragment = chain.thisObject
            val view = chain.getArg(0) as? View
            // Posted, so it runs after the screen has finished starting.
            if (fragment != null && view != null) view.post { onScreenShown(fragment, view.context) }
            result
        }
    }

    private fun onScreenShown(fragment: Any, context: Context) {
        BridgeClient.inBackground {
            sendSnapshot(context)
            val queue = BridgeClient.peekQueue(context) ?: return@inBackground
            if (queue.isEmpty) return@inBackground
            main.post {
                val code = requestCode(context)
                val complete = apply(fragment, code, Activity.RESULT_OK, queue.addUris, queue.removeNumbers, "queued in the app")
                // Kept in the app if anything failed, so it's tried again next time (re-adding is harmless).
                if (complete) BridgeClient.inBackground { BridgeClient.ackQueue(context, queue.token) }
            }
        }
    }

    // ---- Helpers ----

    /** Google's list as stored, read from its settings file, or null if it can't be read. */
    private fun currentNumbers(context: Context): List<String>? {
        val numbers = ProtoNumbers.read(File(context.filesDir, SETTINGS_FILE))
        if (numbers == null) HookLog.warn(id, "can't read Google's current Selected-numbers list")
        return numbers
    }

    private fun sendSnapshot(context: Context) {
        currentNumbers(context)?.let(BridgeClient::putSnapshot)
    }

    private fun requestCodeFile(context: Context) =
        File(context.createDeviceProtectedStorageContext().dataDir, "dialerenhancements/pick_request_code")

    /** The request code Google's Add button used last time, or the one seen on known versions. */
    private fun requestCode(context: Context): Int {
        learnedRequestCode?.let { return it }
        val saved = try {
            requestCodeFile(context).takeIf { it.isFile }?.readText()?.trim()?.toIntOrNull()
        } catch (_: Throwable) {
            null
        }
        return (saved ?: DEFAULT_PICK_REQUEST_CODE).also { learnedRequestCode = it }
    }

    private fun rememberRequestCode(code: Int) {
        if (learnedRequestCode == code) return
        learnedRequestCode = code
        val context = AppContext.application ?: return
        BridgeClient.inBackground {
            requestCodeFile(context).apply { parentFile?.mkdirs() }.writeText(code.toString())
            HookLog.debug(id) { "Add button request code is $code" }
        }
    }

    @Suppress("DEPRECATION")
    private fun urisFrom(data: Intent?): List<Uri> = try {
        data?.getParcelableArrayListExtra<Uri>(Contract.Picker.EXTRA_ADD).orEmpty().filterNotNull()
    } catch (_: Throwable) {
        emptyList()
    }

    private fun stringsFrom(data: Intent?, key: String): List<String> = try {
        data?.getStringArrayListExtra(key).orEmpty().filterNotNull()
    } catch (_: Throwable) {
        emptyList()
    }
}
