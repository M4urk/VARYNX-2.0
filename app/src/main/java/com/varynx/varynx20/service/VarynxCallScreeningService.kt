/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.service

import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.Connection
import android.util.Log

/**
 * System-invoked call screening service.
 *
 * When the user sets VARYNX as their call screening app (or the system
 * delegates a STIR/SHAKEN lookup), Android invokes [onScreenCall] for every
 * incoming call **before** the phone rings.
 *
 * Current capabilities (no audio access — Android blocks that):
 * - Flag calls from private / unknown / restricted numbers
 * - Detect rapid-fire bursts (many calls in quick succession)
 * - Log the screening event so [ScamCallEngine] can incorporate it
 *   in its next Guardian scan cycle
 *
 * The service does NOT block any calls — it only annotates.
 * Blocking decisions belong to the user or the system dialer.
 */
class VarynxCallScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val number = callDetails.handle?.schemeSpecificPart ?: ""
        val verificationStatus = callDetails.callerNumberVerificationStatus

        Log.d(TAG, "Call screening: number=${maskNumber(number)}, verification=$verificationStatus")

        // Track screening events in shared prefs so ScamCallEngine can read them
        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val now = System.currentTimeMillis()

        // Record this call event
        val recentCount = prefs.getInt(KEY_RECENT_SCREEN_COUNT, 0)
        val windowStart = prefs.getLong(KEY_WINDOW_START, now)

        // Compute the actual new count BEFORE writing, so burst detection
        // uses the correct value even after a window reset.
        val newCount = if (now - windowStart > WINDOW_MS) 1 else recentCount + 1

        // Reset window if older than 10 minutes
        if (now - windowStart > WINDOW_MS) {
            prefs.edit()
                .putInt(KEY_RECENT_SCREEN_COUNT, 1)
                .putLong(KEY_WINDOW_START, now)
                .putLong(KEY_LAST_SCREEN_TIME, now)
                .apply()
        } else {
            prefs.edit()
                .putInt(KEY_RECENT_SCREEN_COUNT, newCount)
                .putLong(KEY_LAST_SCREEN_TIME, now)
                .apply()
        }

        // Determine screening response
        val isUnknown = number.isEmpty()
                || verificationStatus == Connection.VERIFICATION_STATUS_FAILED

        val isBurst = newCount >= BURST_THRESHOLD

        val response = CallResponse.Builder()

        if (isUnknown || isBurst) {
            Log.i(TAG, "Call flagged: unknown=$isUnknown, burst=$isBurst (count=$newCount)")
        }

        // Always allow — VARYNX only monitors, never blocks calls
        respondToCall(callDetails, response.build())
    }

    /** Mask all but last 4 digits for log safety. */
    private fun maskNumber(number: String): String {
        if (number.length <= 4) return "****"
        return "***" + number.takeLast(4)
    }

    companion object {
        private const val TAG = "VarynxCallScreen"
        const val PREFS_NAME = "varynx_call_screening"
        const val KEY_RECENT_SCREEN_COUNT = "recent_screen_count"
        const val KEY_WINDOW_START = "window_start"
        const val KEY_LAST_SCREEN_TIME = "last_screen_time"

        /** 10-minute sliding window for burst detection. */
        private const val WINDOW_MS = 10 * 60 * 1000L

        /** Number of calls within the window to flag as a burst. */
        private const val BURST_THRESHOLD = 4
    }
}
