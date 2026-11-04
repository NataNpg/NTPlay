package com.natanael.ntplay.mobile

import com.natanael.ntplay.core.Ep
import com.natanael.ntplay.core.Item
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FiltersTest {

    private fun item(title: String, kind: Char = 'l', group: String = "Geral") =
        Item(kind, "k-$title", title, group, "", arrayListOf(Ep(title, "http://x/a.ts", 1, 1)), false)

    // ------------------------------------------------------------------ filtro de canais

    @Test
    fun textoVazioDevolveTodosOsCanais() {
        val l = listOf(item("Globo SP"), item("SBT"))
        assertEquals(2, filterChannels(l, "").size)
        assertEquals(2, filterChannels(l, "   ").size)
    }

    @Test
    fun achaCanalPorParteDoNome() {
        val l = listOf(item("Globo SP"), item("SBT"), item("Record News"))
        assertEquals(listOf("Globo SP"), filterChannels(l, "glo").map { it.title })
    }

    @Test
    fun ignoraMaiusculasEMinusculas() {
        val l = listOf(item("Globo SP"))
        assertEquals(1, filterChannels(l, "GLOBO").size)
        assertEquals(1, filterChannels(l, "globo").size)
    }

    @Test
    fun ignoraAcentoNaBuscaDeCanal() {
        // quem digita no celular raramente põe acento
        val l = listOf(item("Canal Brasília HD"), item("SBT"))
        assertEquals(listOf("Canal Brasília HD"), filterChannels(l, "brasilia").map { it.title })
    }

    @Test
    fun semCorrespondenciaDevolveListaVazia() {
        val l = listOf(item("Globo SP"), item("SBT"))
        assertTrue(filterChannels(l, "hbo").isEmpty())
    }

    // ------------------------------------------------------------------ programa de agora

    @Test
    fun semProgramacaoCarregadaOProgramaDeAgoraEhNulo() {
        // é isto que faz a linha do programa não ser composta, em vez de aparecer vazia
        assertNull(channelNow(item("Canal Teste")))
    }

    // ------------------------------------------------------------------ anos

    @Test
    fun anosVemDoNomeDoItemSemRepetirEDoMaisNovoParaOMaisVelho() {
        val l = listOf(
            item("Filme A (2019)", 'm'),
            item("Filme B (2023)", 'm'),
            item("Filme C (2019)", 'm'),
        )
        assertEquals(listOf("2023", "2019"), yearsOf(l))
    }

    @Test
    fun itemSemAnoNaoEntraNaListaDeAnos() {
        val l = listOf(item("Filme Sem Ano", 'm'), item("Filme B (2023)", 'm'))
        assertEquals(listOf("2023"), yearsOf(l))
    }

    @Test
    fun listaSemNenhumAnoDevolveVazio() {
        assertTrue(yearsOf(listOf(item("Filme Sem Ano", 'm'))).isEmpty())
    }

    // ------------------------------------------------------------------ filtro por ano

    @Test
    fun filtraPeloAnoEscolhido() {
        val l = listOf(item("Filme A (2019)", 'm'), item("Filme B (2023)", 'm'))
        assertEquals(listOf("Filme B (2023)"), filterByYear(l, "2023").map { it.title })
    }

    @Test
    fun anoVazioDevolveTudo() {
        val l = listOf(item("Filme A (2019)", 'm'), item("Filme B (2023)", 'm'))
        assertEquals(2, filterByYear(l, "").size)
    }

    @Test
    fun anoSemItensDevolveVazio() {
        val l = listOf(item("Filme A (2019)", 'm'))
        assertTrue(filterByYear(l, "2001").isEmpty())
    }

    // ------------------------------------------------------------------ categorias

    @Test
    fun categoriasMantemAOrdemDosGrupos() {
        val g = listOf(
            "Ação" to listOf(item("A", 'm')),
            "Comédia" to listOf(item("B", 'm')),
        )
        assertEquals(listOf("Ação", "Comédia"), categoriesOf(g))
    }
}
