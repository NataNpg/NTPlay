# NTPlay — visão geral do projeto (atualizado em 30/10/2026)

Autor: **Natanael G Silva** · djnatan0@live.com · repositório `NataNpg/NTPlay` (antes `natanpg/NTPlay`; o GitHub redireciona)

Player IPTV pessoal (listas M3U e Xtream Codes) em quatro versões que compartilham a ideia, mas não o código inteiro.

| Versão | Tecnologia | Pasta | Estado |
|---|---|---|---|
| **Android TV** | Kotlin, Compose, Media3 | `tv-native/` | **1.10.1 (código 1032)**, a mais ativa. Exige login. |
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
- Contato exibido na tela de login: WhatsApp (42) 92002-8569

## 2. Android TV — o que o app faz hoje

**Acesso e conta**
- Tela de login obrigatória. Contas só são criadas por você, no painel, com validade (7/30/90/365 dias ou data exata).
- Conta vencida, apagada ou com senha trocada trava o app (revalida a cada 90 s; offline vale a última validade confirmada).
- Entrar com outro usuário na mesma TV apaga os dados locais antes de sincronizar.
- Nuvem (☰ Listas): salva listas com link, favoritos, grupos e progresso, **criptografados no aparelho** (a senha nunca sai da TV).

**Listas**
- Várias listas ao mesmo tempo, por link M3U, QR code (rede local ou GitHub) ou **Xtream Codes** (servidor, usuário e senha, em ☰ Listas).
- Xtream usa a API `player_api.php` (evita o erro 522 do `get.php`); mostra vencimento e telas em uso.
- Atualização automática das listas (ao abrir ou a cada 6/12/24 h, opcional).

**Navegação**
- Abas: Recomendações, TV ao vivo, Filmes, Séries, Infantil, Animes (filtro Crunchyroll/Funimation), Favoritos (com grupos criados por você), Futebol.
- Filtro por ano em Filmes, Séries e Animes. Selos LEG/DUB nos cards. Posição da rolagem lembrada.
- Abertura com logo; espera a lista e os recomendados para não carregar duas vezes.
- Futebol: jogos do dia (ESPN), canal que passa cada jogo pela programação, navegável pelo controle.

**Player**
- ExoPlayer com controles próprios, qualidade máxima, troca de modo de tela para 4K, legenda, áudio, pular abertura e créditos, próximo episódio, reconexão automática.
- **Abertura por série:** o app memoriza onde você pulou a abertura e oferece "Pular abertura" nos próximos episódios.
- **Ao vivo:** agora/próximo (EPG), 📅 Guia, 📺 Canais (lista lateral), ⛶ Tela (ajuste de imagem).
- **EPG:** XMLTV (`xmltv.php` ou `url-tvg` da M3U) com reserva pelo `get_short_epg`; o programa atual aparece também nos cards dos canais.

**Atualização do app**
- Verifica a release `tv-latest` ao abrir e a cada 6 h, baixa o APK e pede para instalar (o Android sempre pede a confirmação final).
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

## 4. Painel de contas (`contas.html`)

Cartões de resumo, gráficos, filtros (todos/ativos/vencendo/expirados), busca, exportar CSV.
Ações por cliente: renovar +30 dias, nova senha, editar validade e anotação, bloquear, excluir.
**Coluna Aparelhos:** quantos aparelhos usam a conta e quantos estão online (contato nos últimos 5 min); o mouse sobre ela mostra modelo e última vez de cada um. Cada aparelho é identificado por um ID aleatório local; guarda 30 dias, no máximo 20 por conta. Só aparece para apps 1.10.1 ou mais novos.

## 5. Compilação e publicação

- `.github/workflows/android-tv.yml`: roda a cada push que altera `tv-native/**` ou manualmente (`workflow_dispatch`). Compila, publica `NTPlay-TV.apk` e `tv-version.json` na release `tv-latest` (apaga e recria).
- **`versionCode` automático:** `1000 + número da execução` (variável `NT_VERSION_CODE`); `versionName` se muda à mão em `tv-native/app/build.gradle.kts`.
- **Assinatura:** chave `.jks` guardada fora do repositório; o workflow a monta dos GitHub Secrets `NT_KEYSTORE_B64` e `NT_KS_PASS` (alias `ntplay`). **Guarde uma cópia do `.jks`**: perdê-la obriga todo mundo a reinstalar.
- Não existe ambiente local de compilação; tudo é compilado no GitHub Actions.
- Pages (`pages.yml`) publica a pasta `www/`. PC: `build.yml` (manual) gera os `.exe` na release `pc-latest`.
- Este ambiente do Claude não consegue acessar o Supabase por `curl`, apagar tags nem criar secrets; essas etapas são feitas pelo site.

## 6. Histórico recente da TV

| Versão | Principais mudanças |
|---|---|
| 1.4.0 | 4K real (troca de modo HDMI), Futebol por EPG, rolagem lembrada |
| 1.5.0 | Guia agora/próximo no player, grupos de favoritos, provedores com estatísticas, atualização das listas |
| 1.6.0 | Novo visual (azul-marinho/violeta/ciano) e logo, abertura, Futebol compacto, LEG/DUB, animes por provedor |
| 1.6.1 | Futebol navegável, filtro por ano, QR pela rede local, abertura sem recarregar |
| 1.7.0 | Conta na nuvem com criptografia |
| 1.8.0 | Login obrigatório com validade e painel de contas |
| 1.9.0–1.9.2 | Atualização automática, `min_version`, chave de assinatura nos Secrets, WhatsApp na entrada, nuvem em ☰ Listas, novo painel |
| 1.10.0 | Xtream Codes por usuário/senha, EPG XMLTV, memória de abertura por série, painel do ao vivo, `versionCode` automático |
| 1.10.1 | Painel mostra quantos aparelhos usam cada conta |

## 7. Pendências e cuidados

1. **Reinstalar o app na TV box uma vez** (conflito de assinatura de versões antigas). Depois as atualizações automáticas funcionam.
2. **Testar na TV** tudo da 1.10.0 e 1.10.1: Xtream por usuário/senha, EPG, painel Guia/Canais/Tela, "Pular abertura" memorizado, coluna Aparelhos. Nada disso foi visto rodando por mim.
3. Listas já em cache não têm `tvg-id`; o EPG casa por nome até a lista ser atualizada.
4. Canais ao vivo entregues em pedaços (`LiveLoopDataSource`) e contas de 1 tela: ainda sujeitos a travar na emenda.
5. PC e web **sem login** e sem as novidades. Decidir: adicionar login a eles, ou retirar a release `pc-latest` e a página web para não contornar o acesso.
6. Limite de aparelhos por conta: não existe (só contagem). Pode ser aplicado no servidor se quiser.
7. Ativar a verificação em duas etapas (2FA) no GitHub antes de 02/12/2026.
8. Ideias: perfil infantil com PIN, assinar o executável do PC, atualização automática do PC.
