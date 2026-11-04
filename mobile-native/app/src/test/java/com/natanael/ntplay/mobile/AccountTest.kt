package com.natanael.ntplay.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountTest {

    /** Como AppState.refreshAccounts grava: exp|status|maxCon|active|ts */
    private fun raw(maxCon: Int, active: Int = 0, status: String = "Active") =
        "1800000000000|$status|$maxCon|$active|1700000000000"

    // ------------------------------------------------------------------ leitura do registro

    @Test
    fun leOsCincoCampos() {
        val a = parseAccount(raw(maxCon = 2, active = 1))
        assertNotNull(a)
        assertEquals(1800000000000L, a!!.exp)
        assertEquals("Active", a.status)
        assertEquals(2, a.maxCon)
        assertEquals(1, a.active)
        assertEquals(1700000000000L, a.ts)
    }

    @Test
    fun registroVazioNaoLe() {
        assertNull(parseAccount(""))
    }

    @Test
    fun registroCurtoNaoLe() {
        // lista M3U comum não tem conta Xtream, então o registro fica vazio ou incompleto
        assertNull(parseAccount("1800000000000|Active|1"))
    }

    @Test
    fun campoNumericoLixoViraZeroSemQuebrar() {
        val a = parseAccount("abc|Active|xyz|?|-")
        assertNotNull(a)
        assertEquals(0L, a!!.exp)
        assertEquals(0, a.maxCon)
        assertEquals(0, a.active)
    }

    // ------------------------------------------------------------------ aviso de 1 tela

    @Test
    fun contaDeUmaTelaAvisa() {
        val msg = oneScreenWarning(raw(maxCon = 1))
        assertNotNull("deveria avisar com maxCon 1", msg)
        assertTrue(
            "o aviso deveria dizer que TV e celular não tocam juntos, veio: $msg",
            msg!!.contains("1 tela"),
        )
    }

    @Test
    fun contaDeDuasTelasNaoAvisa() {
        assertNull(oneScreenWarning(raw(maxCon = 2)))
    }

    @Test
    fun contaDeMuitasTelasNaoAvisa() {
        assertNull(oneScreenWarning(raw(maxCon = 20)))
    }

    @Test
    fun maxConZeroNaoAvisa() {
        // zero significa que o servidor não informou — avisar aí seria inventar um problema
        assertNull(oneScreenWarning(raw(maxCon = 0)))
    }

    @Test
    fun listaSemContaXtreamNaoAvisa() {
        assertNull(oneScreenWarning(""))
    }

    @Test
    fun registroIlegivelNaoAvisa() {
        assertNull(oneScreenWarning("lixo"))
    }

    // ------------------------------------------------------------------ dica no erro de reprodução

    @Test
    fun erroEmContaDeUmaTelaSugereDesligarATv() {
        // sem isto, o usuário lê "o servidor pode estar fora do ar" e desconfia do provedor,
        // quando a causa é a TV ocupando a única conexão
        val hint = playbackErrorHint(raw(maxCon = 1))
        assertNotNull("deveria dar a dica com maxCon 1", hint)
        assertTrue(
            "a dica deveria mandar desligar a TV, veio: $hint",
            hint!!.contains("TV", ignoreCase = false),
        )
    }

    // ------------------------------------------------------------------ montagem da URL Xtream

    @Test
    fun montaAUrlNoFormatoQueOMotorReconhece() {
        // Xtream.creds só aceita caminho terminado em get.php ou player_api.php
        val u = xtreamUrl("http://servidor.com:8080", "joao", "123456")
        assertTrue("deveria terminar o caminho em get.php, veio: $u", u.contains("/get.php?"))
        assertTrue(u.contains("username=joao"))
        assertTrue(u.contains("password=123456"))
        assertTrue(u.contains("type=m3u_plus"))
    }

    @Test
    fun poeHttpQuandoOUsuarioNaoDigita() {
        // o provedor manda "servidor.com:8080" e o usuário cola exatamente isso
        val u = xtreamUrl("servidor.com:8080", "joao", "123456")
        assertTrue("deveria assumir http://, veio: $u", u.startsWith("http://servidor.com:8080/"))
    }

    @Test
    fun respeitaHttpsQuandoJaVem() {
        val u = xtreamUrl("https://servidor.com", "joao", "123456")
        assertTrue(u.startsWith("https://servidor.com/"))
    }

    @Test
    fun naoDuplicaABarraFinal() {
        val u = xtreamUrl("http://servidor.com:8080/", "joao", "123456")
        assertTrue("barra duplicada em $u", !u.contains("//get.php"))
        assertTrue(u.contains("servidor.com:8080/get.php"))
    }

    @Test
    fun tiraEspacoSobrandoDoQueFoiColado() {
        val u = xtreamUrl("  http://servidor.com  ", " joao ", " 123456 ")
        assertTrue(u.startsWith("http://servidor.com/get.php"))
        assertTrue(u.contains("username=joao"))
        assertTrue(u.contains("password=123456"))
    }

    @Test
    fun escapaCaractereEspecialNaSenha() {
        // senha com & ou = quebraria a query se entrasse crua
        val u = xtreamUrl("http://servidor.com", "joao", "a&b=c")
        assertTrue("a senha precisa ir escapada, veio: $u", !u.contains("password=a&b=c"))
        assertTrue(u.contains("password=a%26b%3Dc"))
    }

    @Test
    fun erroEmContaDeVariasTelasNaoDaDicaFalsa() {
        // inventar a dica aqui mandaria o usuário caçar um problema que não existe
        assertNull(playbackErrorHint(raw(maxCon = 2)))
        assertNull(playbackErrorHint(raw(maxCon = 0)))
        assertNull(playbackErrorHint(""))
    }
}
