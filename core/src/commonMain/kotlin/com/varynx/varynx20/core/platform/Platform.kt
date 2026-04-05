/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.platform

expect fun currentTimeMillis(): Long

expect fun nanoTime(): Long

expect inline fun <T> withLock(lock: Any, block: () -> T): T
