/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.protection

import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.model.ThreatEvent
import com.varynx.varynx20.core.model.ThreatLevel
import kotlin.uuid.Uuid

/**
 * QR / Scam Scanner — Analyzes QR code content for safety threats.
 *
 * Performs offline-first analysis of QR code payloads:
 *   - URL risk scoring (known phishing TLDs, IP-based URLs, suspicious paths)
 *   - Scam text heuristics (via ScamDetector patterns)
 *   - Redirect chain detection (shortened URLs, multi-hop redirects)
 *   - Payload type classification (URL, vCard, WiFi config, crypto address)
 *
 * Tier 1 Controllers: manual QR scan + auto analysis
 * Tier 2 Sensors: auto-only (passive QR data relay from mesh)
 */
class QrScamScanner : ProtectionModule {
    override val moduleId = "protect_qr_scanner"
    override val moduleName = "QR / Scam Scanner"
    override var state = ModuleState.IDLE

    private var lastEvent: ThreatEvent? = null
    private var lastResult: QrScanResult? = null

    private val scamDetector = ScamDetector().also { it.activate() }

    // Suspicious URL patterns
    private val suspiciousTlds = listOf(
        ".tk", ".ml", ".ga", ".cf", ".gq", ".xyz", ".top", ".buzz",
        ".work", ".click", ".link", ".info", ".online"
    )

    private val phishingKeywords = listOf(
        "login", "verify", "confirm", "secure", "update", "account",
        "banking", "paypal", "apple-id", "microsoft-verify", "password"
    )

    private val urlShorteners = listOf(
        "bit.ly", "tinyurl.com", "t.co", "goo.gl", "ow.ly",
        "is.gd", "buff.ly", "rebrand.ly", "shorturl.at"
    )

    private val cryptoAddressRegex = Regex(
        "^(bc1|[13])[a-zA-HJ-NP-Z0-9]{25,39}$|^0x[a-fA-F0-9]{40}$|^T[a-zA-Z0-9]{33}$"
    )

    override fun activate() { state = ModuleState.ACTIVE }
    override fun deactivate() { state = ModuleState.IDLE }

    override fun scan(): ThreatLevel = lastEvent?.threatLevel ?: ThreatLevel.NONE

    override fun getLastEvent(): ThreatEvent? = lastEvent

    /**
     * Analyze QR code content and return a detailed scan result.
     */
    fun analyzeQrContent(content: String): QrScanResult {
        val payloadType = classifyPayload(content)
        val findings = mutableListOf<String>()
        var score = 0

        when (payloadType) {
            QrPayloadType.URL -> {
                val urlLower = content.lowercase()

                // Check for IP-based URLs (often phishing)
                if (Regex("https?://\\d+\\.\\d+\\.\\d+\\.\\d+").containsMatchIn(urlLower)) {
                    findings.add("IP-based URL (no domain name)")
                    score += 3
                }

                // Check suspicious TLDs
                for (tld in suspiciousTlds) {
                    if (urlLower.contains(tld)) {
                        findings.add("Suspicious TLD: $tld")
                        score += 2
                        break
                    }
                }

                // Check phishing keywords in URL
                val matchedKeywords = phishingKeywords.filter { urlLower.contains(it) }
                if (matchedKeywords.isNotEmpty()) {
                    findings.add("Phishing keywords: ${matchedKeywords.joinToString(", ")}")
                    score += matchedKeywords.size
                }

                // Check URL shorteners
                for (shortener in urlShorteners) {
                    if (urlLower.contains(shortener)) {
                        findings.add("URL shortener detected: $shortener")
                        score += 1
                        break
                    }
                }

                // Check for data: URI (potential XSS)
                if (urlLower.startsWith("data:")) {
                    findings.add("Data URI scheme (potential code injection)")
                    score += 4
                }

                // Check for javascript: URI
                if (urlLower.startsWith("javascript:")) {
                    findings.add("JavaScript URI (code injection attempt)")
                    score += 5
                }
            }
            QrPayloadType.CRYPTO_ADDRESS -> {
                findings.add("Cryptocurrency address detected — verify before sending funds")
                score += 2
            }
            QrPayloadType.WIFI_CONFIG -> {
                if (content.contains("T:nopass", ignoreCase = true) ||
                    content.contains("T:WEP", ignoreCase = true)) {
                    findings.add("Insecure WiFi configuration (no password or WEP)")
                    score += 2
                }
            }
            QrPayloadType.TEXT -> {
                // Run scam text analysis
                val scamLevel = scamDetector.analyzeText(content)
                if (scamLevel > ThreatLevel.NONE) {
                    findings.add("Scam patterns detected in text content")
                    score += scamLevel.score
                }
            }
            QrPayloadType.VCARD, QrPayloadType.EMAIL, QrPayloadType.SMS, QrPayloadType.PHONE -> {
                // Lower risk but flag if combined with scam text
                val scamLevel = scamDetector.analyzeText(content)
                if (scamLevel > ThreatLevel.NONE) {
                    findings.add("Scam indicators in ${payloadType.label} payload")
                    score += scamLevel.score
                }
            }
        }

        val threatLevel = when {
            score >= 5 -> ThreatLevel.CRITICAL
            score >= 4 -> ThreatLevel.HIGH
            score >= 2 -> ThreatLevel.MEDIUM
            score >= 1 -> ThreatLevel.LOW
            else -> ThreatLevel.NONE
        }

        val result = QrScanResult(
            content = content,
            payloadType = payloadType,
            threatLevel = threatLevel,
            findings = findings,
            safe = threatLevel == ThreatLevel.NONE
        )

        lastResult = result

        if (threatLevel > ThreatLevel.NONE) {
            state = ModuleState.TRIGGERED
            lastEvent = ThreatEvent(
                id = Uuid.random().toString(),
                sourceModuleId = moduleId,
                threatLevel = threatLevel,
                title = "QR Code Risk: ${payloadType.label}",
                description = findings.joinToString("; ")
            )
        } else {
            state = ModuleState.ACTIVE
            lastEvent = null
        }

        return result
    }

    fun getLastResult(): QrScanResult? = lastResult

    private fun classifyPayload(content: String): QrPayloadType = when {
        content.startsWith("http://", ignoreCase = true) ||
            content.startsWith("https://", ignoreCase = true) ||
            content.startsWith("data:", ignoreCase = true) ||
            content.startsWith("javascript:", ignoreCase = true) -> QrPayloadType.URL
        content.startsWith("WIFI:", ignoreCase = true) -> QrPayloadType.WIFI_CONFIG
        content.startsWith("BEGIN:VCARD", ignoreCase = true) -> QrPayloadType.VCARD
        content.startsWith("mailto:", ignoreCase = true) -> QrPayloadType.EMAIL
        content.startsWith("smsto:", ignoreCase = true) ||
            content.startsWith("sms:", ignoreCase = true) -> QrPayloadType.SMS
        content.startsWith("tel:", ignoreCase = true) -> QrPayloadType.PHONE
        cryptoAddressRegex.matches(content) -> QrPayloadType.CRYPTO_ADDRESS
        else -> QrPayloadType.TEXT
    }
}

data class QrScanResult(
    val content: String,
    val payloadType: QrPayloadType,
    val threatLevel: ThreatLevel,
    val findings: List<String>,
    val safe: Boolean
)

enum class QrPayloadType(val label: String) {
    URL("URL"),
    WIFI_CONFIG("WiFi Config"),
    VCARD("Contact Card"),
    EMAIL("Email"),
    SMS("SMS"),
    PHONE("Phone"),
    CRYPTO_ADDRESS("Crypto Address"),
    TEXT("Text")
}
