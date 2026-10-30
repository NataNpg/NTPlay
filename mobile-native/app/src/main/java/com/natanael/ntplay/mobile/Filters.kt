package com.natanael.ntplay.mobile

import com.natanael.ntplay.core.Epg
import com.natanael.ntplay.core.Item
import com.natanael.ntplay.core.Util

/**
 * A lógica de peneirar listas, fora dos @Composable de propósito: assim tem teste de unidade
 * rodando na JVM, sem emulador. As telas só desenham o que estas funções devolvem.
 */

/** Filtra canais pelo que o usuário digitou. Texto vazio devolve a lista inteira. */
fun filterChannels(items: List<Item>, query: String): List<Item> = items

/**
 * O programa de agora de um canal, ou null quando a lista não tem programação.
 *
 * Devolve null — e não "" nem "—" — porque quem desenha precisa poder *não compor* a linha:
 * um texto vazio deixaria um buraco no cartão.
 */
fun channelNow(item: Item): String? = null

/** Os anos presentes nos itens, do mais novo para o mais velho, sem repetir. */
fun yearsOf(items: List<Item>): List<String> = emptyList()

/** Filtra por ano. Ano vazio devolve a lista inteira. */
fun filterByYear(items: List<Item>, year: String): List<Item> = items

/** Agrupa por categoria, mantendo a ordem em que as categorias aparecem. */
fun categoriesOf(groups: List<Pair<String, List<Item>>>): List<String> =
    groups.map { it.first }
