package com.example.finalproject_demo

import java.io.BufferedInputStream
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * The smallest HTTP server the app's `Server` client can talk to, for tests that need the real
 * request path (`Server.base` → `HttpURLConnection`). `com.sun.net.httpserver` is not on the
 * Android unit-test classpath. [handle] gets (path, body) and returns the JSON to send, or null for 502.
 */
class FakeHttp(private val handle: (path: String, body: String) -> String?) : AutoCloseable {
    private val socket = ServerSocket(0)
    val base: String get() = "http://127.0.0.1:${socket.localPort}"

    init {
        thread(isDaemon = true) {
            while (!socket.isClosed) {
                val c = try { socket.accept() } catch (_: Exception) { break }
                thread(isDaemon = true) { serve(c) }
            }
        }
    }

    private fun serve(c: Socket): Unit = c.use {
        val input = BufferedInputStream(c.getInputStream())
        val head = StringBuilder()
        while (!head.endsWith("\r\n\r\n")) {
            val b = input.read()
            if (b < 0) return
            head.append(b.toChar())
        }
        val lines = head.lines()
        val path = lines.first().split(" ").getOrNull(1).orEmpty()
        val length = lines.firstOrNull { it.startsWith("Content-Length:", ignoreCase = true) }
            ?.substringAfter(':')?.trim()?.toIntOrNull() ?: 0
        val body = ByteArray(length).also { var n = 0; while (n < length) { val r = input.read(it, n, length - n); if (r < 0) break; n += r } }
        val reply = handle(path, body.decodeToString())
        val bytes = (reply ?: "{}").toByteArray()
        val status = if (reply != null) "200 OK" else "502 Bad Gateway"
        c.getOutputStream().apply {
            write("HTTP/1.1 $status\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
            write(bytes)
            flush()
        }
        Unit
    }

    override fun close() = socket.close()
}
