/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.service.ipc

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class IpcServer(
    private val handler: IpcHandler,
    private val port: Int = 42400
) {
    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
    private val sessions = ConcurrentHashMap<Long, WebSocketServerSession>()
    private val nextId = AtomicLong(0)

    /** Bearer token required for /api and /ws connections. */
    val authToken: String = generateToken()

    private val json = Json {
        encodeDefaults = true
        prettyPrint = false
    }

    fun start() {
        server = embeddedServer(Netty, port = port, host = "127.0.0.1") {
            install(ContentNegotiation) { json(json) }
            install(WebSockets) {
                pingPeriodMillis = 30_000
                timeoutMillis = 90_000
                maxFrameSize = 256 * 1024L  // 256 KB — reject oversized frames
                masking = false
            }
            install(CORS) {
                allowHost("localhost", schemes = listOf("http"))
                allowHost("127.0.0.1", schemes = listOf("http"))
                allowHeader(HttpHeaders.ContentType)
                allowHeader(HttpHeaders.Authorization)
                allowMethod(HttpMethod.Post)
            }
            intercept(ApplicationCallPipeline.Plugins) {
                val path = call.request.path()
                // Require bearer token for API and WebSocket endpoints
                if (path == "/api" || path == "/ws") {
                    val header = call.request.headers[HttpHeaders.Authorization]
                    // WebSocket upgrades may pass token as query param
                    val queryToken = call.request.queryParameters["token"]
                    val bearerToken = header?.removePrefix("Bearer ")?.trim()
                    val supplied = bearerToken ?: queryToken
                    if (supplied != authToken) {
                        call.respondText("Unauthorized", ContentType.Text.Plain, HttpStatusCode.Unauthorized)
                        finish()
                        return@intercept
                    }
                }
            }
            intercept(ApplicationCallPipeline.Plugins) {
                call.response.header(
                    "Content-Security-Policy",
                    "default-src 'self'; " +
                    "script-src 'self'; " +
                    "style-src 'self' 'unsafe-inline'; " +
                    "img-src 'self' data:; " +
                    "connect-src 'self' ws://127.0.0.1:$port; " +
                    "frame-ancestors 'none'; " +
                    "base-uri 'none'; " +
                    "object-src 'none'; " +
                    "form-action 'self'"
                )
            }
            routing {
                staticResources("/static", "static")
                get("/") { call.respondRedirect("/static/index.html") }
                get("/health") { call.respondText("OK", ContentType.Text.Plain) }

                post("/api") {
                    val body = call.receiveText()
                    val envelope = try {
                        json.decodeFromString(IpcEnvelope.serializer(), body)
                    } catch (e: Exception) {
                        val err = IpcEnvelope(response = IpcResponse.Error("Bad request: ${e.message}"))
                        call.respondText(json.encodeToString(IpcEnvelope.serializer(), err), ContentType.Application.Json, HttpStatusCode.BadRequest)
                        return@post
                    }
                    val request = envelope.request
                    if (request == null) {
                        val err = IpcEnvelope(response = IpcResponse.Error("Missing 'request' field"))
                        call.respondText(json.encodeToString(IpcEnvelope.serializer(), err), ContentType.Application.Json, HttpStatusCode.BadRequest)
                        return@post
                    }
                    val resp = IpcEnvelope(id = envelope.id, response = handler.handle(request))
                    call.respondText(json.encodeToString(IpcEnvelope.serializer(), resp), ContentType.Application.Json)
                }

                webSocket("/ws") {
                    val sid = nextId.incrementAndGet()
                    sessions[sid] = this
                    println("[VARYNX] WebSocket connected: session $sid")
                    try {
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                val text = frame.readText()
                                val envelope = try {
                                    json.decodeFromString(IpcEnvelope.serializer(), text)
                                } catch (e: Exception) {
                                    send(Frame.Text(json.encodeToString(IpcEnvelope.serializer(), IpcEnvelope(response = IpcResponse.Error("Parse error: ${e.message}")))))
                                    continue
                                }
                                val request = envelope.request
                                if (request == null) {
                                    send(Frame.Text(json.encodeToString(IpcEnvelope.serializer(), IpcEnvelope(id = envelope.id, response = IpcResponse.Error("Missing 'request' field")))))
                                    continue
                                }
                                val resp = IpcEnvelope(id = envelope.id, response = handler.handle(request))
                                send(Frame.Text(json.encodeToString(IpcEnvelope.serializer(), resp)))
                            }
                        }
                    } finally {
                        sessions.remove(sid)
                        println("[VARYNX] WebSocket disconnected: session $sid")
                    }
                }
            }
        }
        server?.start(wait = false)
    }

    fun stop() {
        server?.stop(1000, 2000)
        server = null
    }

    suspend fun pushEvent(event: IpcEvent) {
        val text = json.encodeToString(IpcEnvelope.serializer(), IpcEnvelope(event = event))
        val dead = mutableListOf<Long>()
        sessions.forEach { (id, session) ->
            try {
                session.send(Frame.Text(text))
            } catch (_: Exception) {
                dead.add(id)
            }
        }
        dead.forEach { sessions.remove(it) }
    }

    private companion object {
        fun generateToken(): String {
            val bytes = ByteArray(32)
            SecureRandom().nextBytes(bytes)
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
