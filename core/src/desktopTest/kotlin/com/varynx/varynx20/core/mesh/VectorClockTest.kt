/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.mesh

import kotlin.test.*

class VectorClockTest {

    @Test
    fun tickIncrementsCounter() {
        val vc = VectorClock()
        assertEquals(0, vc.get("device-a"))
        vc.tick("device-a")
        assertEquals(1, vc.get("device-a"))
        vc.tick("device-a")
        assertEquals(2, vc.get("device-a"))
    }

    @Test
    fun tickIndependentDevices() {
        val vc = VectorClock()
        vc.tick("a")
        vc.tick("b")
        vc.tick("a")
        assertEquals(2, vc.get("a"))
        assertEquals(1, vc.get("b"))
    }

    @Test
    fun mergeMaxes() {
        val a = VectorClock()
        a.tick("x"); a.tick("x")  // x=2
        a.tick("y")               // y=1

        val b = VectorClock()
        b.tick("x")               // x=1
        b.tick("y"); b.tick("y")  // y=2
        b.tick("z")               // z=1

        a.merge(b)
        assertEquals(2, a.get("x"))  // max(2,1)
        assertEquals(2, a.get("y"))  // max(1,2)
        assertEquals(1, a.get("z"))  // max(0,1)
    }

    @Test
    fun isBeforeCorrect() {
        val a = VectorClock()
        a.tick("d1")  // {d1:1}

        val b = VectorClock()
        b.tick("d1"); b.tick("d1")  // {d1:2}

        assertTrue(a.isBefore(b), "a(d1=1) should be before b(d1=2)")
        assertFalse(b.isBefore(a))
    }

    @Test
    fun isBeforeRequiresAtLeastOneLess() {
        val a = VectorClock()
        a.tick("d1")

        val b = VectorClock()
        b.tick("d1")

        assertFalse(a.isBefore(b), "Equal clocks are not before each other")
    }

    @Test
    fun isConcurrentDetected() {
        val a = VectorClock()
        a.tick("d1")  // {d1:1, d2:0}

        val b = VectorClock()
        b.tick("d2")  // {d1:0, d2:1}

        assertTrue(a.isConcurrent(b))
        assertTrue(b.isConcurrent(a))
    }

    @Test
    fun isConcurrentFalseForOrdered() {
        val a = VectorClock()
        a.tick("d1")

        val b = VectorClock()
        b.tick("d1"); b.tick("d1"); b.tick("d2")

        assertFalse(a.isConcurrent(b))
    }

    @Test
    fun toMapAndFromMapRoundTrip() {
        val vc = VectorClock()
        vc.tick("alpha"); vc.tick("alpha"); vc.tick("beta")
        val map = vc.toMap()
        val restored = VectorClock.fromMap(map)
        assertEquals(2, restored.get("alpha"))
        assertEquals(1, restored.get("beta"))
    }

    @Test
    fun emptyClockBehavior() {
        val a = VectorClock()
        val b = VectorClock()
        assertFalse(a.isBefore(b))
        assertFalse(a.isConcurrent(b))
        assertEquals(emptyMap(), a.toMap())
    }
}
