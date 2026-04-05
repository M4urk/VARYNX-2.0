/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.storage

import com.varynx.varynx20.core.logging.GuardianLog
import java.io.File

/**
 * File-system backed storage adapter for JVM platforms.
 *
 * Keys are mapped to file paths under `baseDir`. Nested keys (with '/')
 * create subdirectories automatically.
 *
 * Thread safety: all operations synchronize on this instance.
 * Atomic writes: writes go to a .tmp file first, then rename.
 */
class FileStorageAdapter(
    private val baseDir: File
) : StorageAdapter {

    init {
        baseDir.mkdirs()
    }

    override fun readBytes(key: String): ByteArray? = synchronized(this) {
        val file = keyToFile(key)
        if (file.exists()) file.readBytes() else null
    }

    override fun writeBytes(key: String, data: ByteArray): Unit = synchronized(this) {
        val file = keyToFile(key)
        file.parentFile?.mkdirs()
        val tmp = File(file.parent, "${file.name}.tmp")
        try {
            tmp.writeBytes(data)
            tmp.renameTo(file)
        } catch (e: Exception) {
            tmp.delete()
            GuardianLog.logSystem("file-storage", "Write failed for key=$key: ${e.message}")
            throw e
        }
    }

    override fun delete(key: String): Boolean = synchronized(this) {
        val file = keyToFile(key)
        file.delete()
    }

    override fun exists(key: String): Boolean = synchronized(this) {
        keyToFile(key).exists()
    }

    override fun listKeys(prefix: String): List<String> = synchronized(this) {
        val dir = keyToFile(prefix)
        if (!dir.exists()) return emptyList()
        // If it's a directory, list children; otherwise list siblings matching prefix
        if (dir.isDirectory) {
            dir.walkTopDown()
                .filter { it.isFile && !it.name.endsWith(".tmp") }
                .map { it.relativeTo(baseDir).path.replace(File.separatorChar, '/') }
                .toList()
        } else {
            val parent = dir.parentFile ?: return emptyList()
            val name = dir.name
            parent.listFiles()
                ?.filter { it.isFile && it.name.startsWith(name) && !it.name.endsWith(".tmp") }
                ?.map { it.relativeTo(baseDir).path.replace(File.separatorChar, '/') }
                ?: emptyList()
        }
    }

    private fun keyToFile(key: String): File {
        // Sanitize key to prevent path traversal
        val sanitized = key.replace("..", "").replace("\\", "/")
        return File(baseDir, sanitized)
    }
}
