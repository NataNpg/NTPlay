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
}
