package com.natanael.ntplay.tv

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.IOException

/**
 * Canais ao vivo de alguns servidores IPTV chegam em pedaços (~20 s) e a conexão fecha.
 * Esta fonte pede o próximo pedaço sozinha e entrega tudo emendado ao player,
 * como o VLC faz — sem travar nem reiniciar o canal.
 */
class LiveLoopDataSource(private val up: DataSource, private val mainUrl: String) : DataSource {
    private var loopOn = false
    private var spec: DataSpec? = null
    private var fails = 0
    private var opened = false

    override fun addTransferListener(transferListener: TransferListener) = up.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long {
        spec = dataSpec
        // só emenda o endereço principal do canal (nunca listas HLS nem segmentos)
        loopOn = dataSpec.uri.toString() == mainUrl && !mainUrl.lowercase().contains(".m3u8")
        val n = up.open(dataSpec)
        opened = true
        return if (loopOn) C.LENGTH_UNSET.toLong() else n
    }

    private fun reopen(): Boolean {
        val s = spec ?: return false
        while (fails < 12) {
            try { up.close() } catch (e: Exception) { }
            opened = false
            try {
                up.open(DataSpec.Builder().setUri(s.uri).setHttpRequestHeaders(s.httpRequestHeaders).build())
                opened = true
                return true
            } catch (e: IOException) {
                fails++
                try { Thread.sleep(800) } catch (ie: InterruptedException) { return false }
            }
        }
        return false
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        while (true) {
            val n = try { up.read(buffer, offset, length) } catch (e: IOException) { fails++; if (fails >= 12) throw e; C.RESULT_END_OF_INPUT }
            if (n != C.RESULT_END_OF_INPUT) {
                if (n > 0) fails = 0
                return n
            }
            if (!loopOn || !reopen()) return C.RESULT_END_OF_INPUT
        }
    }

    override fun getUri(): Uri? = up.uri

    override fun getResponseHeaders(): Map<String, List<String>> = up.responseHeaders

    override fun close() { opened = false; up.close() }
}
