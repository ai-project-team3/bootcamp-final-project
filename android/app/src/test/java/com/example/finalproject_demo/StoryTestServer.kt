package com.example.finalproject_demo

import org.json.JSONObject
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

/** Real HTTP boundary with deterministic responses; no model keys or external services. */
class StoryTestServer(private val respond: (String, JSONObject) -> JSONObject) : AutoCloseable {
    private val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    val base = "http://127.0.0.1:${server.localPort}"
    val requests = CopyOnWriteArrayList<Pair<String, JSONObject>>()

    init {
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = try { server.accept() } catch (_: Exception) { break }
                thread(isDaemon = true) { socket.use { connection ->
                    connection.soTimeout = 5_000
                    val input = connection.getInputStream()
                    val header = StringBuilder()
                    while (!header.endsWith("\r\n\r\n")) {
                        val next = input.read()
                        if (next == -1) return@use
                        header.append(next.toChar())
                    }
                    val path = header.lineSequence().first().split(" ")[1]
                    val length = Regex("Content-Length: (\\d+)", RegexOption.IGNORE_CASE)
                        .find(header)?.groupValues?.get(1)?.toInt() ?: 0
                    val raw = ByteArray(length)
                    var offset = 0
                    while (offset < length) {
                        val read = input.read(raw, offset, length - offset)
                        if (read < 0) return@use
                        offset += read
                    }
                    val body = runCatching { JSONObject(raw.decodeToString()) }.getOrDefault(JSONObject())
                    requests += path to body
                    // This fixture implements JSON routes, not audio synthesis. A prior
                    // Activity test may attach Voice; never pretend JSON bytes are audio.
                    val status = if (path == "/tts") "503 Service Unavailable" else "200 OK"
                    val response = if (path == "/tts") "".toByteArray() else respond(path, body).toString().toByteArray()
                    connection.getOutputStream().apply {
                        write("HTTP/1.1 $status\r\nContent-Length: ${response.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        write(response)
                        flush()
                    }
                } }
            }
        }
    }

    override fun close() = server.close()
}
