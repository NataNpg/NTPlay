package com.natanael.ntplay.mobile

/**
 * Leitura do registro de conta Xtream que o motor guarda por lista.
 *
 * O formato é `exp|status|maxCon|active|ts`, escrito por `AppState.refreshAccounts`. Isto está
 * fora de um @Composable de propósito: a decisão de avisar sobre conta de 1 tela é a regra que
 * mais importa acertar nesta tela, e assim ela tem teste de unidade.
 */
class AcctInfo(val exp: Long, val status: String, val maxCon: Int, val active: Int, val ts: Long)

/** Lê o registro, ou null se não houver nada guardado ou o formato não for o esperado. */
fun parseAccount(raw: String): AcctInfo? {
    val p = raw.split('|')
    if (p.size < 5) return null
    return AcctInfo(
        exp = p[0].toLongOrNull() ?: 0L,
        status = p[1],
        maxCon = p[2].toIntOrNull() ?: 0,
        active = p[3].toIntOrNull() ?: 0,
        ts = p[4].toLongOrNull() ?: 0L,
    )
}

/**
 * O aviso de conta de uma tela só, ou null quando não há o que avisar.
 *
 * Por que isto existe: com o celular, o mesmo cliente passa a usar a conta em dois aparelhos, e
 * quem tem lista de 1 tela derruba um ao abrir o outro. Sem o aviso, o sintoma que aparece é
 * "fica carregando", que parece defeito do app e não é — é o `max_connections` do provedor.
 *
 * Só avisa com maxCon exatamente 1: zero significa que o servidor não informou.
 */
fun oneScreenWarning(raw: String): String? {
    val a = parseAccount(raw) ?: return null
    if (a.maxCon != 1) return null
    return "Esta lista permite 1 tela por vez: TV e celular não podem assistir ao mesmo tempo."
}
