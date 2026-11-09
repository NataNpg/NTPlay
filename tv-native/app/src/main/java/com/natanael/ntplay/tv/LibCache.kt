package com.natanael.ntplay.tv

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Guarda a biblioteca já processada (canais/filmes/séries) em um arquivo binário.
 * Ao abrir o app, se as listas não mudaram (mesmo tamanho e data) e a versão do app é a mesma,
 * lê este arquivo em vez de analisar o M3U inteiro de novo. Qualquer problema → cai no caminho normal.
 */
object LibCache {
    private const val MAGIC = 0x4E544C42 // "NTLB"
    private const val FORMAT = 1

    fun sig(files: List<File>, showAdult: Boolean, b64: Boolean, adultByName: Boolean): String =
        files.joinToString("|") { it.name + ":" + it.length() + ":" + it.lastModified() } + "#" + showAdult + b64 + adultByName + "#" + BuildConfig.VERSION_NAME + "#" + FORMAT

    private fun file(dir: File) = File(dir, "lib.bin")

    private fun DataOutputStream.str(s: String) {
        val b = s.toByteArray(Charsets.UTF_8); writeInt(b.size); write(b)
    }
    private fun DataInputStream.str(): String {
        val n = readInt(); if (n < 0 || n > 1_000_000) throw java.io.IOException("cache inválido")
        val b = ByteArray(n); readFully(b); return String(b, Charsets.UTF_8)
    }

    fun save(dir: File, sig: String, lib: Library) {
        val tmp = File(dir, "lib.bin.tmp")
        try {
            DataOutputStream(BufferedOutputStream(FileOutputStream(tmp), 1 shl 16)).use { o ->
                o.writeInt(MAGIC); o.str(sig)
                o.writeInt(lib.live.size)
                for (i in lib.live) { o.str(i.key); o.str(i.title); o.str(i.group); o.str(i.logo); o.writeBoolean(i.adult); o.str(i.tvgId); o.str(i.eps[0].url) }
                o.writeInt(lib.movies.size)
                for (i in lib.movies) { o.str(i.key); o.str(i.title); o.str(i.group); o.str(i.logo); o.writeBoolean(i.adult); o.writeInt(i.pos); o.str(i.eps[0].name); o.str(i.eps[0].url) }
                o.writeInt(lib.series.size)
                for (i in lib.series) {
                    o.str(i.key); o.str(i.title); o.str(i.group); o.str(i.logo); o.writeBoolean(i.adult); o.writeInt(i.pos); o.writeInt(i.ns)
                    o.writeInt(i.eps.size)
                    for (e in i.eps) { o.str(e.name); o.str(e.url); o.writeInt(e.s); o.writeInt(e.e) }
                }
            }
            val f = file(dir); f.delete(); tmp.renameTo(f)
        } catch (e: Throwable) { try { tmp.delete() } catch (x: Throwable) { } }
    }

    fun load(dir: File, sig: String): Library? {
        val f = file(dir)
        if (!f.exists()) return null
        return try {
            DataInputStream(BufferedInputStream(FileInputStream(f), 1 shl 16)).use { r ->
                if (r.readInt() != MAGIC || r.str() != sig) return null
                val nl = r.readInt(); val live = ArrayList<Item>(nl)
                repeat(nl) {
                    val key = r.str(); val title = r.str(); val group = r.str(); val logo = r.str(); val adult = r.readBoolean(); val tvg = r.str(); val url = r.str()
                    live.add(Item('l', key, title, group, logo, arrayListOf(Ep(title, url, 0, 0)), adult).also { it.tvgId = tvg })
                }
                val nm = r.readInt(); val mv = ArrayList<Item>(nm)
                repeat(nm) {
                    val key = r.str(); val title = r.str(); val group = r.str(); val logo = r.str(); val adult = r.readBoolean(); val pos = r.readInt(); val en = r.str(); val url = r.str()
                    mv.add(Item('m', key, title, group, logo, arrayListOf(Ep(en, url, 0, 0)), adult).also { it.pos = pos })
                }
                val ns = r.readInt(); val sr = ArrayList<Item>(ns)
                repeat(ns) {
                    val key = r.str(); val title = r.str(); val group = r.str(); val logo = r.str(); val adult = r.readBoolean(); val pos = r.readInt(); val nseas = r.readInt()
                    val ne = r.readInt(); val eps = ArrayList<Ep>(ne)
                    repeat(ne) { eps.add(Ep(r.str(), r.str(), r.readInt(), r.readInt())) }
                    sr.add(Item('s', key, title, group, logo, eps, adult).also { it.pos = pos; it.ns = nseas })
                }
                Library(live, mv, sr)
            }
        } catch (e: Throwable) { try { f.delete() } catch (x: Throwable) { }; null }
    }
}
