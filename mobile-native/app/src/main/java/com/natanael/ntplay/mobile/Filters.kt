package com.natanael.ntplay.mobile

import com.natanael.ntplay.core.Epg
import com.natanael.ntplay.core.Item
import com.natanael.ntplay.core.Util

/**
 * A lógica de peneirar listas, fora dos @Composable de propósito: assim tem teste de unidade
 * rodando na JVM, sem emulador. As telas só desenham o que estas funções devolvem.
 */

/** Filtra canais pelo que o usuário digitou. Texto vazio devolve a lista inteira. */
fun filterChannels(items: List<Item>, query: String): List<Item> {
    // Util.norm tira acento, maiúsculas e as palavras de qualidade (HD, 4K...). Se o que sobrou
    // for vazio — alguém digitou só "hd" —, filtrar por "" devolveria tudo de qualquer forma.
    val q = Util.norm(query)
    if (q.isBlank()) return items
    return items.filter { it.norm.contains(q) }
}

/**
 * O programa de agora de um canal, ou null quando a lista não tem programação.
 *
 * Devolve null — e não "" nem "—" — porque quem desenha precisa poder *não compor* a linha:
 * um texto vazio deixaria um buraco no cartão.
 */
fun channelNow(item: Item): String? = Epg.nowTitle(item)?.takeIf { it.isNotBlank() }

/** Os anos presentes nos itens, do mais novo para o mais velho, sem repetir. */
fun yearsOf(items: List<Item>): List<String> = items.asSequence()
    .map { Util.yearOf(it.title) }
    .filter { it.isNotEmpty() }
    .distinct()
    .sortedDescending()
    .toList()

/** Filtra por ano. Ano vazio devolve a lista inteira. */
fun filterByYear(items: List<Item>, year: String): List<Item> {
    if (year.isBlank()) return items
    return items.filter { Util.yearOf(it.title) == year }
}

/** Agrupa por categoria, mantendo a ordem em que as categorias aparecem. */
fun categoriesOf(groups: List<Pair<String, List<Item>>>): List<String> =
    groups.map { it.first }
