package com.natanael.ntplay.mobile

/**
 * Leitura do registro de conta Xtream que o motor guarda por lista.
 *
 * O formato é `exp|status|maxCon|active|ts`, escrito por `AppState.refreshAccounts`. Isto está
 * fora de um @Composable de propósito: a decisão de avisar sobre conta de 1 tela é a regra que
 * mais importa acertar nesta tela, e assim ela tem teste de unidade.
 */
class AcctInfo(val exp: Long, val status: String, val maxCon: Int, val active: Int, val ts: Long)

/**
 * Monta a URL de uma lista Xtream a partir de servidor, usuário e senha.
 *
 * Tem de sair no formato que `Xtream.creds` reconhece — caminho terminado em `get.php`, com
 * `username` e `password` na query —, senão o motor trata a lista como M3U comum e perde a API.
 */
fun xtreamUrl(host: String, user: String, pass: String): String {
    val h = host.trim().trimEnd('/').let { if (it.contains("://")) it else "http://$it" }
    return h + "/get.php?username=" + enc(user.trim()) +
        "&password=" + enc(pass.trim()) + "&type=m3u_plus&output=ts"
}

/** Escapa para query. Senha com & ou = é comum e quebraria a URL se fosse crua. */
private fun enc(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")

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

/**
 * A dica a acrescentar a um erro de reprodução, ou null quando não há dica honesta a dar.
 *
 * Existe porque o erro cru mente por omissão: numa conta de 1 tela com a TV ligada, o que o
 * usuário vê é "o servidor pode estar fora do ar", e ele vai desconfiar do provedor em vez de
 * desligar a TV. A causa real é o `max_connections` da lista.
 */
fun playbackErrorHint(acctRaw: String): String? {
    val a = parseAccount(acctRaw) ?: return null
    if (a.maxCon != 1) return null
    return "Esta lista é de 1 tela: se a TV estiver assistindo, desligue-a e tente de novo."
}
