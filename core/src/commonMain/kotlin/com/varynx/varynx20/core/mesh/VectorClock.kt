/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.mesh

import com.varynx.varynx20.core.platform.withLock

/**
 * Vector clock for conflict-free event merge across mesh devices.
 * Each device maintains its own logical counter. On sync, clocks are merged
 * by taking the max of each device's counter. No data loss, no overwrites.
 */
data class VectorClock(
    private val clocks: MutableMap<String, Long> = mutableMapOf()
) {
    private val lock = Any()

    /** Increment this device's counter. Call before sending a message. */
    fun tick(deviceId: String): VectorClock {
        withLock(lock) { clocks[deviceId] = (clocks[deviceId] ?: 0L) + 1 }
        return this
    }

    /** Get the current counter for a device. */
    fun get(deviceId: String): Long = withLock(lock) { clocks[deviceId] ?: 0L }

    /**
     * Merge with another vector clock (received from a peer).
     * Takes the max of each device's counter — ensures causal ordering.
     */
    fun merge(other: VectorClock): VectorClock {
        val otherSnapshot = other.toMap()
        withLock(lock) {
            for ((deviceId, otherTime) in otherSnapshot) {
                clocks[deviceId] = maxOf(clocks[deviceId] ?: 0L, otherTime)
            }
        }
        return this
    }

    /**
     * Check if this clock is causally before another clock.
     * True if every counter in this clock is <= the other, and at least one is strictly <.
     */
    fun isBefore(other: VectorClock): Boolean {
        val thisSnapshot = toMap()
        val otherSnapshot = other.toMap()
        val allKeys = thisSnapshot.keys + otherSnapshot.keys
        var atLeastOneLess = false
        for (key in allKeys) {
            val thisVal = thisSnapshot[key] ?: 0L
            val otherVal = otherSnapshot[key] ?: 0L
            if (thisVal > otherVal) return false
            if (thisVal < otherVal) atLeastOneLess = true
        }
        return atLeastOneLess
    }

    /** Check if two clocks are concurrent (neither is before the other). */
    fun isConcurrent(other: VectorClock): Boolean =
        !isBefore(other) && !other.isBefore(this) && this != other

    /** Snapshot for serialization. */
    fun toMap(): Map<String, Long> = withLock(lock) { clocks.toMap() }

    companion object {
        fun fromMap(map: Map<String, Long>): VectorClock = VectorClock(map.toMutableMap())
    }
}
