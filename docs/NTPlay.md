# NTPlay — visão geral do projeto (atualizado em 30/10/2026)

Autor: **Natanael G Silva** · djnatan0@live.com · repositório `NataNpg/NTPlay` (antes `natanpg/NTPlay`; o GitHub redireciona)

Player IPTV pessoal (listas M3U e Xtream Codes) em quatro versões que compartilham a ideia, mas não o código inteiro.

| Versão | Tecnologia | Pasta | Estado |
|---|---|---|---|
| **Android TV** | Kotlin, Compose, Media3 | `tv-native/` | **1.10.6 (código 1037)**, a mais ativa. Exige login. |
| **PC (Windows)** | Electron 31 + interface web | `electron/`, `www/` | 3.3.0, **sem login** e sem as novidades da TV |
| **Web / celular** | mesma interface web (Capacitor) | `www/` | sem login |
| **Painel de contas** | HTML + Supabase | `www/contas.html` | no ar |

Detalhes técnicos: `docs/NTPlay-AndroidTV.md` (changelog completo da TV) e `docs/NTPlay-PC.md`.

---

## 1. Links

- APK da TV (sempre a última): https://github.com/NataNpg/NTPlay/releases/download/tv-latest/NTPlay-TV.apk
- Versão publicada (o app lê este arquivo): `.../releases/download/tv-latest/tv-version.json`
- PC: release `pc-latest` (instalador e portátil)
- Painel de contas: https://natanpg.github.io/NTPlay/contas.html
- Enviar lista pelo celular (QR antigo): https://natanpg.github.io/NTPlay/enviar.html
- Contato exibido na entrada e em ☰ Listas: WhatsApp (42) 92002-8569

## 2. Android TV — o que o app faz hoje

**Acesso e conta**
- Tela de login obrigatória. Contas só são criadas por você, no painel, com validade (7/30/90/365 dias ou data exata).
- Conta vencida, apagada ou com senha trocada trava o app (revalida a cada 90 s; offline vale a última validade confirmada).
- Entrar com outro usuário na mesma TV apaga os dados locais antes de sincronizar.
- Nuvem (☰ Listas): salva listas com link, favoritos, grupos e progresso, **criptografados no aparelho** (a senha nunca sai da TV).
- **Não existe teste automático.** Quem quer experimentar pede pelo WhatsApp: QR code e número aparecem na tela de entrada e em ☰ Listas ("Peça seu teste do aplicativo ou da lista"). Um login de teste local chegou a existir na 1.10.4 e foi removido na 1.10.5, para o controle ficar inteiro no painel.

**Listas**
- Várias listas ao mesmo tempo, por link M3U, QR code (rede local ou GitHub) ou **Xtream Codes** (servidor, usuário e senha, em ☰ Listas).
- Xtream usa a API `player_api.php` (evita o erro 522 do `get.php`); mostra vencimento e telas em uso.
- Atualização automática das listas (ao abrir ou a cada 6/12/24 h, opcional).

**Navegação**
- Abas: Recomendações, TV ao vivo, Filmes, Séries, Infantil, Animes (filtro Crunchyroll/Funimation), Favoritos (com grupos criados por você), Futebol.
- **Busca: só filmes, séries e animes** — canais ao vivo ficam de fora (1.10.6).
- Filtro por ano em Filmes, Séries e Animes. Selos LEG/DUB nos cards. Posição da rolagem lembrada.
- Abertura com logo; espera a lista e os recomendados para não carregar duas vezes.
- Futebol: jogos do dia (ESPN), canal que passa cada jogo pela programação, navegável pelo controle.
- Botão focado: fundo branco com texto escuro, legível mesmo em TV com cor estourada (1.10.4).

**Player**
- ExoPlayer com controles próprios, qualidade máxima, troca de modo de tela para 4K, legenda, áudio, pular abertura e créditos, próximo episódio, reconexão automática.
- **Abertura por série:** o app memoriza onde você pulou a abertura e oferece "Pular abertura" nos próximos episódios.
- **Ao vivo:** agora/próximo (EPG), 📅 Guia, 📺 Canais (lista lateral), ⛶ Tela (ajuste de imagem).
- **EPG:** XMLTV (`xmltv.php` ou `url-tvg` da M3U) com reserva pelo `get_short_epg`; o programa atual aparece também nos cards dos canais.

**Atualização do app**
- Confere a release `tv-latest` **toda vez que o app volta para a frente** (no máximo 1 vez a cada 10 min), 20 s depois de abrir e a cada 6 h. Baixa o APK e pede para instalar (o Android sempre pede a confirmação final).
- Até a 1.10.5 não havia checagem ao reabrir: como o app fica na memória da TV, a versão nova só aparecia até 6 h depois. Corrigido na 1.10.6.
- `tv-native/min_version`: se maior que o `versionCode` instalado, a atualização vira obrigatória.

## 3. Servidor (Supabase, grátis)

- Projeto `baylcjfedxnjnjkvaufw`; tabela `public.ntplay_accounts` (RLS ligado, sem políticas, só a função acessa).
- Colunas: `username, salt, hash, data, ts, fails, locked_until, created_at, expires_at, note, last_seen, devices`.
- Edge Function `ntsync` (v5, `verify_jwt=false`), endpoint `https://baylcjfedxnjnjkvaufw.supabase.co/functions/v1/ntsync`.
  - App: `get`, `put` (cadastro público `reg` fechado). Bloqueio de 15 min após 8 senhas erradas; limite de 500 contas e 2 MB por conta.
  - Administrador (`a=adm`, chave `NT_ADMIN_KEY`): `list, create, renew, setexp, note, setpass, revoke, delete, cleardev`.
- Segredo `NT_ADMIN_KEY`: definido em Supabase → Edge Functions → Secrets (já feito). Não está no código.
- Privacidade: o servidor guarda só hash da chave de login e o blob cifrado (AES-256-GCM, PBKDF2 40.000 iterações). Trocar a senha de uma conta deixa os dados antigos da nuvem ilegíveis.
- O plano grátis pausa o projeto após ~7 dias sem uso (reativar no painel do Supabase).
- **Saúde em 30/10/2026:** 1.429 requisições em 24 h, todas com resposta 200, sem erro e sem conta bloqueada. Folga grande no plano grátis.

## 4. Painel de contas (`contas.html`)

Cartões de resumo, gráficos, filtros (todos/ativos/vencendo/expirados), busca, exportar CSV.
Ações por cliente: renovar +30 dias, nova senha, editar validade e anotação, bloquear, excluir.
**Coluna Aparelhos:** quantos aparelhos usam a conta e quantos estão online (contato nos últimos 5 min); o mouse sobre ela mostra modelo e última vez de cada um. Cada aparelho é identificado por um ID aleatório local; guarda 30 dias, no máximo 20 por conta. Só aparece para apps 1.10.1 ou mais novos.

## 5. Compilação e publicação

- `.github/workflows/android-tv.yml`: roda a cada push que altera `tv-native/**` ou manualmente (`workflow_dispatch`). Compila, publica `NTPlay-TV.apk` e `tv-version.json` na release `tv-latest` (apaga e recria).
- **`versionCode` automático:** `1000 + número da execução` (variável `NT_VERSION_CODE`); `versionName` se muda à mão em `tv-native/app/build.gradle.kts`.
- **Assinatura:** chave `.jks` guardada fora do repositório; o workflow a monta dos GitHub Secrets `NT_KEYSTORE_B64` e `NT_KS_PASS` (alias `ntplay`). **Guarde uma cópia do `.jks`**: perdê-la obriga todo mundo a reinstalar.
- Não existe ambiente local de compilação; tudo é compilado no GitHub Actions. **O Actions garante que compila, não que o defeito sumiu** — todo teste de comportamento é feito na TV.
- Pages (`pages.yml`) publica a pasta `www/`. PC: `build.yml` (manual) gera os `.exe` na release `pc-latest`.

## 6. Histórico recente da TV

| Versão | Principais mudanças |
|---|---|
| 1.6.0–1.6.1 | Novo visual e logo, abertura, Futebol compacto e navegável, LEG/DUB, filtro por ano, QR pela rede local |
| 1.7.0 | Conta na nuvem com criptografia |
| 1.8.0 | Login obrigatório com validade e painel de contas |
| 1.9.0–1.9.2 | Atualização automática, `min_version`, chave de assinatura nos Secrets, WhatsApp na entrada, nuvem em ☰ Listas, novo painel |
| 1.10.0 | Xtream Codes por usuário/senha, EPG XMLTV, memória de abertura por série, painel do ao vivo, `versionCode` automático |
| 1.10.1 | Painel mostra quantos aparelhos usam cada conta |
| 1.10.2 | Filmes duplicados removidos da biblioteca (bug real, mas **não** era o que fechava o app) |
| 1.10.3 | **Corrigido o app fechando na busca**: corrida de threads no `Item.norm`. QR e contato em ☰ Listas |
| 1.10.4 | Teste de 1 dia local (removido depois); botão focado legível |
| 1.10.5 | Teste de 1 dia removido: acesso volta a ser só por conta do painel |
| 1.10.6 | Busca só de filmes, séries e animes; atualização conferida ao reabrir o app |

## 7. O caso do app fechando na busca (1.10.2 → 1.10.3)

Vale guardar, porque a primeira explicação estava errada e o padrão pode voltar.

**Sintoma:** o app fechava ao buscar filme ou série. Navegar por categoria nunca quebrava.

**Primeira tentativa (1.10.2), errada:** filmes entravam na biblioteca sem checagem de repetição, gerando dois `Item` com a mesma chave. Era um bug real — e foi corrigido —, mas não era a causa do fechamento.

**Causa real (1.10.3):** `Item.norm` era `by lazy(LazyThreadSafetyMode.NONE)`, e três threads leem esse campo ao mesmo tempo:

1. a thread principal, nos cards de canal (`UI.PosterCard` → `Epg.nowTitle`);
2. `Dispatchers.Default`, em `AppState.search`;
3. `Dispatchers.IO`, em `Epg.load`.

O `lazy` em modo `NONE` não usa `@Volatile`: uma thread enxerga `initializer` já anulado por outra e `_value` ainda não publicado, e o `initializer!!()` lança `NullPointerException`. Trocado para `PUBLICATION`, que publica com CAS.

**Por que só na busca:** é o único lugar que varre a biblioteca inteira enquanto os cards na tela e o EPG leem o mesmo campo. E nasceu na 1.10.0, quando o EPG passou a aparecer nos cards de canal — antes, só a busca lia `norm`, sem concorrência.

**Lição:** `LazyThreadSafetyMode.NONE` em campo de objeto compartilhado é perigoso. `norm` era o único no projeto; os demais `by lazy` usam o modo padrão, que é seguro.

## 8. Por que "fica carregando" com várias pessoas

Não é o app nem o Supabase. É o `max_connections` do provedor da lista.

Em 30/10/2026 a conta `teste` tinha **20 aparelhos ativos em 24 h** — que é o teto que o `ntsync` guarda, então o número real pode ser maior. Todos sincronizam o mesmo blob, logo recebem a **mesma lista com as mesmas credenciais**, e o provedor corta as conexões excedentes.

Efeito colateral: esses aparelhos gravam no mesmo registro a cada 90 s, então favoritos, grupos e "continuar assistindo" de todos se sobrescrevem.

Saídas:
1. Lista de teste com várias conexões no provedor.
2. Cada testador usa a própria lista (aí cada um gasta as conexões do próprio provedor).
3. Uma conta NTPlay por pessoa, de qualquer forma.

## 9. Pendências e cuidados

1. **PC e web sem login** — porta aberta no controle de acesso: quem não conseguir conta na TV usa essas versões sem nenhuma barreira. Decidir entre adicionar o mesmo login (usando o `ntsync`) ou retirar do ar a release `pc-latest` e a página web.
2. **Limite de aparelhos por conta: não existe** (só contagem). Para prender de verdade, o teste/acesso teria de usar o `Settings.Secure.ANDROID_ID` com hash — o `deviceId` de hoje é um UUID aleatório no SharedPreferences, que morre ao limpar os dados ou reinstalar. O `ANDROID_ID` sobrevive aos dois (muda só em restauração de fábrica ou troca da chave de assinatura). Cuidado: box barato com implementação defeituosa pode repetir o valor e bloquear cliente legítimo — por isso o `cleardev` do painel serve de escape.
3. **Testar na TV** o que ainda não foi visto rodando: Xtream por usuário/senha, EPG, painel Guia/Canais/Tela, "Pular abertura" memorizado, coluna Aparelhos.
4. Listas já em cache não têm `tvg-id`; o EPG casa por nome até a lista ser atualizada.
5. Canais ao vivo entregues em pedaços (`LiveLoopDataSource`) e contas de 1 tela: ainda sujeitos a travar na emenda.
6. `Updater.BASE` ainda aponta para `github.com/natanpg/...` (minúsculo). Funciona pelo redirecionamento do GitHub e foi conferido em 30/10/2026, mas vale corrigir para `NataNpg` e não depender disso.
7. Ativar a verificação em duas etapas (2FA) no GitHub antes de 02/12/2026.
8. Ideias: perfil infantil com PIN, assinar o executável do PC, atualização automática do PC.
