package com.natanael.ntplay.tv

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder

/** Servidor local (só na rede Wi‑Fi): o celular abre o endereço do QR e envia a lista direto para a TV, sem internet/GitHub. */
object LocalServer {
    private var ss: ServerSocket? = null
    @Volatile private var token = ""
    @Volatile private var onAdd: ((String, String) -> Unit)? = null

    fun ip(): String? {
        try {
            for (ni in NetworkInterface.getNetworkInterfaces()) {
                if (!ni.isUp || ni.isLoopback) continue
                for (a in ni.inetAddresses) if (a is Inet4Address && !a.isLoopbackAddress && a.isSiteLocalAddress) return a.hostAddress
            }
        } catch (e: Throwable) { }
        return null
    }

    /** inicia e devolve o endereço para o QR (ou null se não houver rede local) */
    @Synchronized
    fun start(cb: (String, String) -> Unit): String? {
        stop()
        val ip = ip() ?: return null
        val s = try { ServerSocket(8765) } catch (e: Throwable) { try { ServerSocket(0) } catch (x: Throwable) { return null } }
        ss = s
        token = java.lang.Long.toString(java.security.SecureRandom().nextLong() and Long.MAX_VALUE, 36).take(8)
        onAdd = cb
        val tk = token
        Thread {
            while (!s.isClosed) {
                val c = try { s.accept() } catch (e: Throwable) { break }
                Thread { handle(c, tk) }.start()
            }
        }.start()
        return "http://$ip:${s.localPort}/$tk"
    }

    @Synchronized
    fun stop() { try { ss?.close() } catch (e: Throwable) { }; ss = null; onAdd = null }

    private fun readHead(i: InputStream): String {
        val b = ByteArrayOutputStream()
        var last = 0
        while (b.size() < 16384) {
            val x = i.read(); if (x < 0) break
            b.write(x)
            last = (last shl 8) or x
            if (last == 0x0D0A0D0A) break
        }
        return b.toString("UTF-8")
    }

    private fun handle(c: Socket, tk: String) {
        try {
            c.soTimeout = 20000
            val i = c.getInputStream(); val o = c.getOutputStream()
            val head = readHead(i)
            val lines = head.split("\r\n")
            val rl = lines.firstOrNull()?.split(" ") ?: return
            if (rl.size < 2) return
            val method = rl[0]; val path = rl[1].substringBefore('?')
            fun send(code: String, body: String) {
                val bytes = body.toByteArray(Charsets.UTF_8)
                o.write(("HTTP/1.1 $code\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray())
                o.write(bytes); o.flush()
            }
            if (path != "/$tk") { send("404 Not Found", page("Endereço inválido. Escaneie o QR code de novo na TV.", false)); return }
            if (method == "GET") { send("200 OK", page("", true)); return }
            if (method == "POST") {
                val len = lines.firstOrNull { it.startsWith("content-length:", true) }?.substringAfter(':')?.trim()?.toIntOrNull() ?: 0
                if (len <= 0 || len > 40 * 1024 * 1024) { send("400 Bad Request", page("Conteúdo vazio ou grande demais.", true)); return }
                val buf = ByteArray(len); var n = 0
                while (n < len) { val r = i.read(buf, n, len - n); if (r < 0) break; n += r }
                val f = HashMap<String, String>()
                for (kv in String(buf, 0, n, Charsets.UTF_8).split('&')) {
                    val k = kv.substringBefore('='); val v = kv.substringAfter('=', "")
                    f[URLDecoder.decode(k, "UTF-8")] = URLDecoder.decode(v, "UTF-8")
                }
                val content = (f["url"] ?: "").trim().ifEmpty { (f["text"] ?: "").trim() }
                if (content.isEmpty()) { send("200 OK", page("Cole o link ou o conteúdo da lista.", true)); return }
                onAdd?.invoke(content, f["name"]?.trim()?.ifEmpty { null } ?: "")
                send("200 OK", page("✅ Enviado! Veja a TV: a lista está sendo carregada.", true))
            }
        } catch (e: Throwable) {
        } finally { try { c.close() } catch (e: Throwable) { } }
    }

    private fun page(msg: String, form: Boolean): String =
        "<!doctype html><html><head><meta charset=utf-8><meta name=viewport content='width=device-width,initial-scale=1'><title>NTPlay</title>" +
        "<style>body{font-family:sans-serif;background:#070b1a;color:#fff;margin:0;padding:18px}h2{color:#3ce0e6;margin:0 0 4px}" +
        "input,textarea{width:100%;box-sizing:border-box;margin:6px 0;padding:12px;border-radius:10px;border:1px solid #3b3f86;background:#111838;color:#fff;font-size:16px}" +
        "button{width:100%;padding:14px;border:0;border-radius:10px;background:#8b5cf6;color:#fff;font-size:17px;font-weight:bold;margin-top:8px}.m{margin:10px 0;color:#9fb0d6}</style></head><body>" +
        "<h2>NTPlay</h2><div class=m>Enviar lista para a TV</div>" + (if (msg.isNotEmpty()) "<p>$msg</p>" else "") +
        (if (form) "<form method=post><input name=name placeholder='Nome da lista (opcional)'><input name=url placeholder='Link da lista (http://...)' autocapitalize=off>" +
            "<div class=m>ou cole o conteúdo M3U:</div><textarea name=text rows=5 placeholder='#EXTM3U ...'></textarea><button type=submit>Enviar para a TV</button></form>" else "") +
        "</body></html>"
}
