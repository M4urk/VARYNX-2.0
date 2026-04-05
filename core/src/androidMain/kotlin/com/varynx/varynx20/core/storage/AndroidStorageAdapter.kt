/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.storage

import android.content.Context
import com.varynx.varynx20.core.logging.GuardianLog
import java.io.File

/**
 * Android file-system backed storage adapter.
 *
 * Uses [Context.getFilesDir] — NOT cache — so data survives across
 * app updates and is never auto-cleared by the OS.
 *
 * Keys map to file paths under `{filesDir}/varynx/{subDir}/`.
 * Thread-safe via synchronized access. Atomic writes via .tmp rename.
 */
class AndroidStorageAdapter(
    context: Context,
    subDir: String = "guardian"
) : StorageAdapter {

    private val baseDir = File(context.filesDir, "varynx/$subDir")

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
            GuardianLog.logSystem("android-storage", "Write failed for key=$key: ${e.message}")
            throw e
        }
    }

    override fun delete(key: String): Boolean = synchronized(this) {
        keyToFile(key).delete()
    }

    override fun exists(key: String): Boolean = synchronized(this) {
        keyToFile(key).exists()
    }

    override fun listKeys(prefix: String): List<String> = synchronized(this) {
        val dir = keyToFile(prefix)
        if (!dir.exists()) return emptyList()
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
        val sanitized = key.replace("..", "").replace("\\", "/")
        return File(baseDir, sanitized)
    }
}
