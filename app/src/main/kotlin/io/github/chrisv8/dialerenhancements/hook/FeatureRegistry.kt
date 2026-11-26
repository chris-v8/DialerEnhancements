// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Chris (chris-v8)
// Origin: written for DialerEnhancements from docs/design/REWRITE_PLAN.md §3 and SPEC.md §1.2.

package io.github.chrisv8.dialerenhancements.hook

import io.github.chrisv8.dialerenhancements.hook.features.BubbleFeature
import io.github.chrisv8.dialerenhancements.hook.features.CanRecordFeature
import io.github.chrisv8.dialerenhancements.hook.features.CountryFeature
import io.github.chrisv8.dialerenhancements.hook.features.GeofenceFeature
import io.github.chrisv8.dialerenhancements.hook.features.RecordAllFeature
import io.github.chrisv8.dialerenhancements.hook.features.RecordingSavedMessageFeature
import io.github.chrisv8.dialerenhancements.hook.features.SelectedNumbersFeature
import io.github.chrisv8.dialerenhancements.hook.features.SetupCheckFeature
import io.github.chrisv8.dialerenhancements.hook.features.TtsFallbackFeature

/** Every feature, in the order they are set up (SPEC §1.2). */
object FeatureRegistry {
    val features: List<Feature> = listOf(
        // Critical phase: before Google Phone's own code runs.
        CanRecordFeature,
        GeofenceFeature,
        CountryFeature,
        TtsFallbackFeature,
        // Deferred phase.
        RecordAllFeature,
        BubbleFeature,
        SelectedNumbersFeature,
        RecordingSavedMessageFeature,
        SetupCheckFeature,
    )
}
