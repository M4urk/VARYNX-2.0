/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.platform

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun nanoTime(): Long = System.nanoTime()

actual inline fun <T> withLock(lock: Any, block: () -> T): T = synchronized(lock, block)
