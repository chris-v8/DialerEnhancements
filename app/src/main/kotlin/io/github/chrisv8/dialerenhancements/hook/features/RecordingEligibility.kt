// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Chris (chris-v8)
// Origin: written for DialerEnhancements from docs/design/SPEC.md F1–F3 (markers are Google Phone's own strings).
// Both geofence names (F2) were found by Chris for CallRecording.

package io.github.chrisv8.dialerenhancements.hook.features

import io.github.chrisv8.dialerenhancements.hook.find.shape
import io.github.chrisv8.dialerenhancements.shared.Contract.FeatureIds

/** Shape shared by Google Phone's plain yes/no checks: no parameters, returns `boolean`. */
private val yesNoCheck = shape {
    paramCount(0)
    returns(Boolean::class.javaPrimitiveType!!)
}

/** SPEC F1: "may this call be recorded?" → yes. Shows the record button. */
object CanRecordFeature : FixedAnswerFeature(FeatureIds.CAN_RECORD, listOf("canRecordCall"), yesNoCheck) {
    override val answer = true
}

/**
 * SPEC F2: "is the phone inside the area where recording is allowed?" → yes.
 * Older releases (v88) call it the Crosby geofence, newer ones (2xx) the call-recording geofence.
 */
object GeofenceFeature : FixedAnswerFeature(
    FeatureIds.GEOFENCE,
    listOf("withinCrosbyGeoFence", "withinCallRecordingGeoFence"),
    yesNoCheck,
) {
    override val answer = true
}

/** SPEC F3: "is this a call-recording country?" → yes. */
object CountryFeature : FixedAnswerFeature(FeatureIds.COUNTRY, listOf("isCallRecordingCountry"), yesNoCheck) {
    override val answer = true
}
