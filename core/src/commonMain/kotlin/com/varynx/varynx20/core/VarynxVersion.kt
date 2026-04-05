/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core

import com.varynx.varynx20.core.platform.currentTimeMillis

/**
 * VARYNX 2.0 — Central version constants.
 *
 * Semantic versioning: MAJOR.MINOR.PATCH
 * Channel: ALPHA → BETA → STABLE
 *
 * All modules (core, app, desktop, service, linux, tools) share the same version.
 */
object VarynxVersion {
    const val MAJOR = 2
    const val MINOR = 0
    const val PATCH = 0

    val channel: ReleaseChannel = ReleaseChannel.ALPHA

    val versionName: String = "$MAJOR.$MINOR.$PATCH-${channel.suffix}"
    val versionCode: Int = MAJOR * 10000 + MINOR * 100 + PATCH

    val fullIdentifier: String = "VARYNX $versionName"

    /** Build timestamp — set at compile time. */
    val buildTimestamp: Long = currentTimeMillis()

    override fun toString(): String = versionName
}

enum class ReleaseChannel(val suffix: String, val label: String, val priority: Int) {
    ALPHA("alpha", "Alpha", 0),
    BETA("beta", "Beta", 1),
    STABLE("stable", "Stable", 2);

    val isPreRelease: Boolean get() = this != STABLE
}

/**
 * Intelligence pack versioning — separate from app version.
 */
data class IntelPackVersion(
    val packId: String,
    val version: Int,
    val timestamp: Long,
    val channel: ReleaseChannel = ReleaseChannel.STABLE
) {
    val displayVersion: String get() = "$packId-v$version"
    
    fun isNewerThan(other: IntelPackVersion): Boolean =
        this.packId == other.packId && this.version > other.version
}
