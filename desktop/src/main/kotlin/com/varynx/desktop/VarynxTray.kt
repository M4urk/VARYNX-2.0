/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.desktop

import java.awt.*
import javax.swing.SwingUtilities

object VarynxTray {

    private var trayIcon: TrayIcon? = null

    fun install(
        onOpen: () -> Unit,
        onToggleAutostart: (Boolean) -> Unit,
        initialAutostart: Boolean
    ) {
        if (!SystemTray.isSupported()) return

        val tray = SystemTray.getSystemTray()
        val image = loadIcon()
        val popup = PopupMenu()

        popup.add(MenuItem("Open VARYNX").apply { addActionListener { onOpen() } })
        popup.addSeparator()
        popup.add(CheckboxMenuItem("Auto-start", initialAutostart).apply {
            addItemListener { onToggleAutostart(state) }
        })
        popup.addSeparator()
        popup.add(MenuItem("Exit").apply {
            addActionListener { tray.remove(trayIcon); System.exit(0) }
        })

        trayIcon = TrayIcon(image, "VARYNX Desktop", popup).apply {
            isImageAutoSize = true
            addActionListener { onOpen() }
        }
        tray.add(trayIcon)
    }

    private fun loadIcon(): Image {
        val url = VarynxTray::class.java.getResource("/icons/varynx-desktop.png")
        if (url != null) return Toolkit.getDefaultToolkit().getImage(url)

        // Fallback
        val img = java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.color = Color(0, 229, 255)
        g.fillRect(0, 0, 16, 16)
        g.color = Color(10, 10, 10)
        g.font = Font("SansSerif", Font.BOLD, 11)
        g.drawString("V", 3, 13)
        g.dispose()
        return img
    }

    fun showMessage(title: String, message: String) {
        SwingUtilities.invokeLater {
            trayIcon?.displayMessage(title, message, TrayIcon.MessageType.INFO)
        }
    }
}
