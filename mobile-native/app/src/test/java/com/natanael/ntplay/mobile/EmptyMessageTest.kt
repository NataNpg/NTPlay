package com.natanael.ntplay.mobile

import com.natanael.ntplay.core.Ep
import com.natanael.ntplay.core.Item
import com.natanael.ntplay.core.Library
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testes da regra que decide entre mostrar conteúdo e mostrar orientação.
 *
 * Rodam na JVM, sem emulador: é por isso que a regra é uma função pura e não está enterrada
 * dentro de um @Composable.
 */
class EmptyMessageTest {

    private fun item(title: String, kind: Char) =
        Item(kind, "k-$title", title, "grupo", "", arrayListOf(Ep(title, "http://x/$title.ts", 1, 1)), false)

    private fun lib(
        live: List<Item> = emptyList(),
        movies: List<Item> = emptyList(),
        series: List<Item> = emptyList(),
    ) = Library(live, movies, series)

    // ------------------------------------------------ primeira abertura, nenhuma lista cadastrada

    @Test
    fun semNenhumaListaCadastradaOrientaAAdicionar() {
        val msg = emptyMessage("FILMES", null, busy = false, listCount = 0)
        assertEquals("Adicione uma lista para começar", msg)
    }

    @Test
    fun semListaAOrientacaoEIgualEmTodasAsAbas() {
        for (tab in listOf("INÍCIO", "FILMES", "SÉRIES", "TV", "BUSCA")) {
            assertEquals(
                "a aba $tab deveria orientar a adicionar lista",
                "Adicione uma lista para começar",
                emptyMessage(tab, null, busy = false, listCount = 0),
            )
        }
    }

    // ------------------------------------------------ carregando

    @Test
    fun enquantoCarregaNaoMostraOrientacao() {
        // quem aparece nesse momento é o indicador de progresso, não um texto de "vazio"
        assertNull(emptyMessage("FILMES", null, busy = true, listCount = 0))
        assertNull(emptyMessage("FILMES", null, busy = true, listCount = 1))
    }

    // ------------------------------------------------ lista cadastrada mas biblioteca ainda nula

    @Test
    fun comListaCadastradaEBibliotecaNulaOrientaAAdicionar() {
        // aconteceu de a lista existir e não carregar (erro de rede, servidor fora)
        val msg = emptyMessage("FILMES", null, busy = false, listCount = 1)
        assertTrue("deveria dizer algo ao usuário, veio: $msg", !msg.isNullOrBlank())
    }

    // ------------------------------------------------ biblioteca carregada

    @Test
    fun comFilmesNaBibliotecaNaoMostraNada() {
        val l = lib(movies = listOf(item("Filme A", 'm')))
        assertNull(emptyMessage("FILMES", l, busy = false, listCount = 1))
    }

    @Test
    fun abaSemConteudoNaquelaListaDizQueNaoTemNada() {
        // a lista tem filmes mas nenhuma série: a aba Séries não pode ficar em branco e calada
        val l = lib(movies = listOf(item("Filme A", 'm')))
        val msg = emptyMessage("SÉRIES", l, busy = false, listCount = 1)
        assertTrue("deveria explicar que não há séries, veio: $msg", !msg.isNullOrBlank())
        assertTrue(
            "a mensagem deveria citar a aba, veio: $msg",
            msg!!.contains("série", ignoreCase = true),
        )
    }

    @Test
    fun abaTvSemCanaisDizQueNaoTemCanal() {
        val l = lib(movies = listOf(item("Filme A", 'm')))
        val msg = emptyMessage("TV", l, busy = false, listCount = 1)
        assertTrue("deveria explicar que não há canais, veio: $msg", !msg.isNullOrBlank())
        assertTrue(
            "a mensagem deveria citar canal, veio: $msg",
            msg!!.contains("canal", ignoreCase = true),
        )
    }

    @Test
    fun abaTvComCanaisNaoMostraNada() {
        val l = lib(live = listOf(item("Canal A", 'l')))
        assertNull(emptyMessage("TV", l, busy = false, listCount = 1))
    }

    @Test
    fun inicioComQualquerConteudoNaoMostraNada() {
        val l = lib(movies = listOf(item("Filme A", 'm')))
        assertNull(emptyMessage("INÍCIO", l, busy = false, listCount = 1))
    }

    @Test
    fun buscaNuncaMostraOrientacaoDeVazioQuandoHaBiblioteca() {
        // a aba Busca tem o próprio estado ("digite algo"), que não é assunto desta regra
        val l = lib(movies = listOf(item("Filme A", 'm')))
        assertNull(emptyMessage("BUSCA", l, busy = false, listCount = 1))
    }

    @Test
    fun bibliotecaCompletamenteVaziaOrientaAAdicionar() {
        // a lista carregou, mas não veio nada dentro dela
        val msg = emptyMessage("INÍCIO", lib(), busy = false, listCount = 1)
        assertTrue("deveria dizer algo ao usuário, veio: $msg", !msg.isNullOrBlank())
    }
}
