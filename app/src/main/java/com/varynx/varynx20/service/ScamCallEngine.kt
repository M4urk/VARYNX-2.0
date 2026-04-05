/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.CallLog
import android.provider.Telephony
import android.util.Log
import androidx.core.content.ContextCompat
import com.varynx.varynx20.core.model.ThreatEvent
import com.varynx.varynx20.core.model.ThreatLevel
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * SMS + Call Log Scam Analysis Engine (Android-specific)
 *
 * Analyses SMS content and call log metadata for scam, phishing,
 * and robocall behavior patterns. Does NOT listen to or record
 * call audio — Android does not permit third-party call recording.
 *
 * Detects:
 * - Phishing links and URL shorteners in SMS (HIGH / CRITICAL)
 * - Scam keyword clusters in messages (MEDIUM → CRITICAL)
 * - Robocall patterns: repeated unknown/private callers (HIGH)
 * - Rapid-fire call bursts from similar numbers (HIGH)
 * - Short-duration spam call patterns (MEDIUM)
 * - SMS → call bait sequences (CRITICAL)
 * - Call screening service burst signals (HIGH)
 */
class ScamCallEngine(private val context: Context) {

    companion object {
        private const val TAG = "ScamCallEngine"

        // Known scam URL patterns
        private val SCAM_URL_PATTERNS = listOf(
            """bit\.ly/""",
            """tinyurl\.com/""",
            """goo\.gl/""",
            """t\.co/""",
            """ow\.ly/""",
            """is\.gd/""",
            """buff\.ly/""",
            """adf\.ly/""",
            """j\.mp/""",
            """rb\.gy/"""
        ).map { Regex(it, RegexOption.IGNORE_CASE) }

        // Suspicious keywords in messages
        private val SCAM_KEYWORDS = listOf(
            "won",
            "winner",
            "prize",
            "lottery",
            "claim",
            "urgent",
            "act now",
            "limited time",
            "free gift",
            "congratulations",
            "selected",
            "verify your account",
            "suspended",
            "unusual activity",
            "click here",
            "gift card",
            "bitcoin",
            "crypto",
            "inheritance",
            "nigerian prince",
            "wire transfer"
        )

        // Phishing domain patterns
        private val PHISHING_PATTERNS = listOf(
            """paypa[li1]\..*""",
            """amaz[o0]n\..*""",
            """g[o0][o0]gle\..*""",
            """faceb[o0][o0]k\..*""",
            """\.ru/""",
            """\.cn/""",
            """\.tk/""",
            """\.ml/""",
            """\.ga/"""
        ).map { Regex(it, RegexOption.IGNORE_CASE) }

        // Time window for recent messages / calls (last 24 hours)
        private const val TIME_WINDOW_MS = 24 * 60 * 60 * 1000L

        // ── Call heuristic thresholds ──

        /** Calls from unknown/private numbers to flag as suspicious. */
        private const val UNKNOWN_CALLER_THRESHOLD = 3

        /** Calls within the burst window to flag as rapid-fire. */
        private const val BURST_CALL_THRESHOLD = 4

        /** Burst detection window (10 minutes). */
        private const val BURST_WINDOW_MS = 10 * 60 * 1000L

        /** Calls shorter than this are potential robocall probes (seconds). */
        private const val SHORT_CALL_DURATION_SEC = 5

        /** Minimum short-duration calls to surface a finding. */
        private const val SHORT_CALL_THRESHOLD = 3

        /**
         * Time gap between an incoming SMS and a call from the same number
         * to flag as a bait sequence (30 minutes).
         */
        private const val SMS_CALL_BAIT_WINDOW_MS = 30 * 60 * 1000L

        /** SharedPreferences name written by VarynxCallScreeningService. */
        private const val SCREENING_PREFS = "varynx_call_screening"
        private const val KEY_RECENT_SCREEN_COUNT = "recent_screen_count"
        private const val KEY_WINDOW_START = "window_start"
    }

    data class ScamFinding(
        val threatLevel: ThreatLevel,
        val title: String,
        val description: String
    )

    /**
     * Run the full scam analysis cycle. Returns a list of findings
     * sorted by threat level (highest first).
     *
     * Gracefully degrades when SMS/Call permissions are not granted.
     */
    fun analyze(nowMillis: Long = System.currentTimeMillis()): List<ScamFinding> {
        Log.i(TAG, "Scam/call analysis triggered")
        val findings = mutableListOf<ScamFinding>()

        try {
            val smsNumbers: Map<String, Long> // number → latest suspicious SMS timestamp

            // ── SMS analysis ──
            if (hasSmsPermission()) {
                val (smsFindings, suspiciousSenders) = checkRecentSms(nowMillis)
                smsNumbers = suspiciousSenders
                findings.addAll(smsFindings)
            } else {
                smsNumbers = emptyMap()
                Log.d(TAG, "SMS permission not granted — skipping SMS analysis")
            }

            // ── Call log analysis ──
            if (hasCallLogPermission()) {
                val callFindings = checkRecentCalls(nowMillis, smsNumbers)
                findings.addAll(callFindings)
            } else {
                Log.d(TAG, "Call log permission not granted — skipping call analysis")
            }

            // ── Call screening service signal ──
            val screenFinding = checkCallScreeningSignal(nowMillis)
            if (screenFinding != null) findings.add(screenFinding)

        } catch (e: SecurityException) {
            Log.d(TAG, "SMS/Call permission not granted: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Scam detector check failed", e)
        }

        return findings.sortedByDescending { it.threatLevel.ordinal }
    }

    /**
     * Convert findings into ThreatEvents for the V2 Guardian architecture.
     */
    @OptIn(ExperimentalUuidApi::class)
    fun analyzeAsEvents(nowMillis: Long = System.currentTimeMillis()): List<ThreatEvent> {
        return analyze(nowMillis).map { finding ->
            ThreatEvent(
                id = Uuid.random().toString(),
                sourceModuleId = "protect_scam_detector",
                threatLevel = finding.threatLevel,
                title = finding.title,
                description = finding.description
            )
        }
    }

    // ── Permission checks ──

    private fun hasSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasCallLogPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
    }

    // ── SMS scanning ──

    /**
     * Returns SMS-based findings AND the set of sender numbers that
     * triggered suspicious results (used for SMS→call bait-sequence detection).
     */
    private fun checkRecentSms(nowMillis: Long): Pair<List<ScamFinding>, Map<String, Long>> {
        val cutoffTime = nowMillis - TIME_WINDOW_MS
        val findings = mutableListOf<ScamFinding>()
        val suspiciousSenders = mutableMapOf<String, Long>()

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                "${Telephony.Sms.DATE} > ? AND ${Telephony.Sms.TYPE} = ?",
                arrayOf(cutoffTime.toString(), Telephony.Sms.MESSAGE_TYPE_INBOX.toString()),
                "${Telephony.Sms.DATE} DESC"
            ) ?: return emptyList<ScamFinding>() to emptyMap()

            while (cursor.moveToNext()) {
                val body = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)) ?: continue
                val sender = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)) ?: ""
                val smsDate = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE))

                val result = analyzeMessageContent(body, sender)
                if (result != null) {
                    findings.add(result)
                    val normalized = normalizeNumber(sender)
                    val existing = suspiciousSenders[normalized]
                    if (existing == null || smsDate > existing) {
                        suspiciousSenders[normalized] = smsDate
                    }
                }
            }
        } finally {
            cursor?.close()
        }

        return findings to suspiciousSenders
    }

    // ── Call log scanning ──

    /**
     * Analyses call log for multiple patterns:
     * 1. Repeated unknown/private callers
     * 2. Rapid-fire call bursts from similar number prefixes
     * 3. Short-duration calls (robocall probes)
     * 4. SMS → call bait sequences
     */
    private fun checkRecentCalls(
        nowMillis: Long,
        suspiciousSmsSenders: Map<String, Long>
    ): List<ScamFinding> {
        val cutoffTime = nowMillis - TIME_WINDOW_MS
        val results = mutableListOf<ScamFinding>()

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.DATE,
                    CallLog.Calls.DURATION,
                    CallLog.Calls.TYPE
                ),
                "${CallLog.Calls.DATE} > ?",
                arrayOf(cutoffTime.toString()),
                "${CallLog.Calls.DATE} DESC"
            ) ?: return emptyList()

            var unknownCallerCount = 0
            var shortCallCount = 0
            // prefix → list of timestamps for burst detection
            val prefixTimestamps = mutableMapOf<String, MutableList<Long>>()
            val incomingNumbers = mutableListOf<Pair<String, Long>>() // number, timestamp

            while (cursor.moveToNext()) {
                val number = cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)) ?: ""
                val date = cursor.getLong(cursor.getColumnIndexOrThrow(CallLog.Calls.DATE))
                val duration = cursor.getLong(cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION))
                val type = cursor.getInt(cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE))

                val isIncoming = type == CallLog.Calls.INCOMING_TYPE
                        || type == CallLog.Calls.MISSED_TYPE

                // 1. Unknown / private callers
                if (number.isEmpty() || number == "Private" || number == "Unknown"
                    || number == "Restricted") {
                    unknownCallerCount++
                }

                // 2. Burst detection — group by 6-digit prefix
                if (isIncoming && number.length >= 6) {
                    val prefix = normalizeNumber(number).take(6)
                    prefixTimestamps.getOrPut(prefix) { mutableListOf() }.add(date)
                }

                // 3. Short-duration incoming calls (robocall probes)
                if (isIncoming && duration in 1..SHORT_CALL_DURATION_SEC.toLong()) {
                    shortCallCount++
                }

                // 4. Track incoming calls for bait-sequence check
                if (isIncoming) {
                    incomingNumbers.add(normalizeNumber(number) to date)
                }
            }

            // ── Evaluate findings ──

            // 1. Unknown callers
            if (unknownCallerCount >= UNKNOWN_CALLER_THRESHOLD) {
                results.add(ScamFinding(
                    ThreatLevel.HIGH,
                    "Unknown Caller Surge",
                    "$unknownCallerCount calls from unknown numbers (24h)"
                ))
            }

            // 2. Rapid-fire bursts
            for ((prefix, timestamps) in prefixTimestamps) {
                if (timestamps.size < BURST_CALL_THRESHOLD) continue
                timestamps.sort()
                // Sliding window check
                for (i in 0..timestamps.size - BURST_CALL_THRESHOLD) {
                    val windowEnd = timestamps[i + BURST_CALL_THRESHOLD - 1]
                    val windowStart = timestamps[i]
                    if (windowEnd - windowStart <= BURST_WINDOW_MS) {
                        results.add(ScamFinding(
                            ThreatLevel.HIGH,
                            "Rapid-Fire Call Burst",
                            "Rapid-fire calls from ${prefix}xx ($BURST_CALL_THRESHOLD+ in 10 min)"
                        ))
                        break
                    }
                }
            }

            // 3. Short-duration calls
            if (shortCallCount >= SHORT_CALL_THRESHOLD) {
                results.add(ScamFinding(
                    ThreatLevel.MEDIUM,
                    "Robocall Probe Pattern",
                    "$shortCallCount very short calls (≤${SHORT_CALL_DURATION_SEC}s) — robocall probe pattern"
                ))
            }

            // 4. SMS → call bait sequence (call must follow SMS within 30 min)
            if (suspiciousSmsSenders.isNotEmpty()) {
                for ((callNumber, callTime) in incomingNumbers) {
                    val smsTime = suspiciousSmsSenders[callNumber] ?: continue
                    if (callTime >= smsTime && callTime - smsTime <= SMS_CALL_BAIT_WINDOW_MS) {
                        results.add(ScamFinding(
                            ThreatLevel.CRITICAL,
                            "SMS-to-Call Bait Sequence",
                            "SMS then call from same number within 30 min — bait sequence"
                        ))
                        break // one finding is enough
                    }
                }
            }

        } finally {
            cursor?.close()
        }

        return results
    }

    // ── Call Screening Service signal ──

    /**
     * Reads data from [VarynxCallScreeningService]'s shared prefs to
     * check if a burst of screened calls was seen recently.
     */
    private fun checkCallScreeningSignal(nowMillis: Long): ScamFinding? {
        return try {
            val prefs = context.getSharedPreferences(SCREENING_PREFS, Context.MODE_PRIVATE)
            val windowStart = prefs.getLong(KEY_WINDOW_START, 0L)
            val count = prefs.getInt(KEY_RECENT_SCREEN_COUNT, 0)

            // Only surface if the screening window is recent (last 15 min)
            if (nowMillis - windowStart > 15 * 60 * 1000L) return null
            if (count < BURST_CALL_THRESHOLD) return null

            ScamFinding(
                ThreatLevel.HIGH,
                "Robocall Burst (Call Screening)",
                "$count incoming calls screened in 10 min — robocall burst"
            )
        } catch (e: Exception) {
            Log.d(TAG, "Call screening prefs not available: ${e.message}")
            null
        }
    }

    // ── SMS content analysis ──

    private fun analyzeMessageContent(body: String, sender: String): ScamFinding? {
        val lowerBody = body.lowercase()
        val senderLabel = if (sender.length > 15) "${sender.take(15)}…" else sender

        // Check for URL shorteners (often used in scams)
        for (pattern in SCAM_URL_PATTERNS) {
            if (pattern.containsMatchIn(body)) {
                return ScamFinding(
                    ThreatLevel.HIGH,
                    "Suspicious Shortened URL",
                    "SMS from $senderLabel: shortened URL detected"
                )
            }
        }

        // Check for phishing domains
        for (pattern in PHISHING_PATTERNS) {
            if (pattern.containsMatchIn(body)) {
                return ScamFinding(
                    ThreatLevel.CRITICAL,
                    "Phishing Link Detected",
                    "SMS from $senderLabel: potential phishing link"
                )
            }
        }

        // Count scam keywords
        val keywordMatches = SCAM_KEYWORDS.count { keyword ->
            lowerBody.contains(keyword.lowercase())
        }

        return when {
            keywordMatches >= 3 -> ScamFinding(
                ThreatLevel.CRITICAL,
                "Multiple Scam Indicators",
                "SMS from $senderLabel: $keywordMatches scam keywords"
            )
            keywordMatches >= 2 -> ScamFinding(
                ThreatLevel.HIGH,
                "Suspicious Keywords",
                "SMS from $senderLabel: $keywordMatches suspicious keywords"
            )
            keywordMatches >= 1 -> ScamFinding(
                ThreatLevel.MEDIUM,
                "Potential Scam Keyword",
                "SMS from $senderLabel: potential scam keyword"
            )
            else -> null
        }
    }

    // ── Helpers ──

    /** Strips non-digit characters for consistent number comparison. */
    private fun normalizeNumber(number: String): String {
        return number.replace(Regex("[^\\d]"), "")
    }
}
