/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.desktop

import java.util.prefs.Preferences

object Autostart {
    private val prefs: Preferences = Preferences.userRoot().node("varynx/desktop")

    fun isEnabled(): Boolean = prefs.getBoolean("autostart", true)

    fun toggle(enabled: Boolean) {
        prefs.putBoolean("autostart", enabled)
        prefs.flush()
    }
}
