/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.desktop

import javafx.application.Application
import javafx.application.Platform
import javafx.concurrent.Worker
import javafx.scene.Scene
import javafx.scene.image.Image
import javafx.scene.layout.StackPane
import javafx.scene.paint.Color
import javafx.scene.web.WebView
import javafx.stage.Stage
import javafx.stage.StageStyle
import netscape.javascript.JSObject

class VarynxDesktopApp : Application() {

    private val serviceUrl = "http://127.0.0.1:42400/"

    override fun start(primaryStage: Stage) {
        primaryStage.initStyle(StageStyle.UNDECORATED)
        Platform.setImplicitExit(false)

        VarynxTray.install(
            onOpen = { Platform.runLater { primaryStage.show(); primaryStage.toFront() } },
            onToggleAutostart = { Autostart.toggle(it) },
            initialAutostart = Autostart.isEnabled()
        )

        EmbeddedService.start()

        val webView = WebView()
        webView.isContextMenuEnabled = false

        val windowBridge = WindowBridge(primaryStage)

        // Redirect JS console.log / console.error to stdout for debugging
        webView.engine.loadWorker.stateProperty().addListener { _, _, state ->
            if (state == Worker.State.SUCCEEDED) {
                val window = webView.engine.executeScript("window") as JSObject
                window.setMember("javaLog", JavaBridge())
                window.setMember("varynxWindow", windowBridge)

                // Inject IPC auth token so the UI can authenticate API/WS calls
                val token = EmbeddedService.authToken
                if (token != null) {
                    webView.engine.executeScript("window.__VARYNX_TOKEN = '$token';")
                    // Explicitly start the WebSocket now that the token is available.
                    // The page's own connect() may have failed silently without a token.
                    webView.engine.executeScript("if (typeof connect === 'function') connect();")
                }

                // Activate custom window chrome (drag, resize, controls)
                webView.engine.executeScript("if (typeof initWindowChrome === 'function') initWindowChrome();")

                webView.engine.executeScript("""
                    (function() {
                        var bridge = window.javaLog;
                        var origLog = console.log, origErr = console.error, origWarn = console.warn;
                        console.log = function() { origLog.apply(console, arguments); try { bridge.log(Array.prototype.join.call(arguments, ' ')); } catch(e) {} };
                        console.error = function() { origErr.apply(console, arguments); try { bridge.error(Array.prototype.join.call(arguments, ' ')); } catch(e) {} };
                        console.warn = function() { origWarn.apply(console, arguments); try { bridge.warn(Array.prototype.join.call(arguments, ' ')); } catch(e) {} };
                        window.onerror = function(msg, src, line, col, err) { bridge.error('JS ERROR: ' + msg + ' at ' + src + ':' + line + ':' + col); };
                    })();
                """.trimIndent())
            }
            if (state == Worker.State.FAILED) {
                Thread { Thread.sleep(2000); Platform.runLater { webView.engine.load(serviceUrl) } }
                    .apply { isDaemon = true }.start()
            }
        }

        webView.engine.load(serviceUrl)

        val root = StackPane(webView)
        root.style = "-fx-background-color: #050608;"

        val icon = javaClass.getResourceAsStream("/icons/varynx-desktop.png")
        if (icon != null) primaryStage.icons.add(Image(icon))

        primaryStage.title = "VARYNX — Control Center"
        primaryStage.scene = Scene(root, 1280.0, 800.0, Color.web("#050608"))
        primaryStage.minWidth = 960.0
        primaryStage.minHeight = 600.0

        primaryStage.setOnCloseRequest {
            it.consume()
            primaryStage.hide()
            VarynxTray.showMessage("VARYNX", "Running in background. Right-click tray to exit.")
        }

        primaryStage.show()
    }
}

fun main(args: Array<String>) {
    Application.launch(VarynxDesktopApp::class.java, *args)
}

/** Bridge for JS console output → JVM stdout */
class JavaBridge {
    fun log(msg: String) { println("[JS] $msg") }
    fun warn(msg: String) { println("[JS-WARN] $msg") }
    fun error(msg: String) { System.err.println("[JS-ERROR] $msg") }
}

/** Bridge for JS → JavaFX window control (undecorated chrome). */
class WindowBridge(private val stage: Stage) {
    fun minimize() { stage.isIconified = true }
    fun maximize() { stage.isMaximized = !stage.isMaximized }
    fun isMaximized(): Boolean = stage.isMaximized
    fun close() {
        stage.hide()
        VarynxTray.showMessage("VARYNX", "Running in background. Right-click tray to exit.")
    }
    fun getX(): Double = stage.x
    fun getY(): Double = stage.y
    fun getWidth(): Double = stage.width
    fun getHeight(): Double = stage.height
    fun getMinWidth(): Double = stage.minWidth
    fun getMinHeight(): Double = stage.minHeight
    fun setPosition(x: Double, y: Double) { stage.x = x; stage.y = y }
    fun setBounds(x: Double, y: Double, w: Double, h: Double) {
        stage.x = x; stage.y = y; stage.width = w; stage.height = h
    }
}
