#!/usr/bin/env python3
"""Copia o motor do app de Android TV para o app de celular, trocando o pacote.

Por que isto existe: o motor do NTPlay (leitura de M3U, API Xtream, metadados, nuvem) levou
catorze versões para ficar bom e mora em `tv-native/`. O app de celular usa o mesmo motor sem
duplicá-lo e **sem alterar um único arquivo da TV**: na compilação, estes arquivos são copiados
para uma pasta gerada, com a linha `package` trocada e três remendos aplicados.

Os remendos são o ponto delicado. Se a TV mudar e um alvo deixar de bater, este script **para a
compilação** em vez de gerar silenciosamente um app de celular que se identifica como TV ou que
procura atualização na release errada. Por isso cada alvo precisa ocorrer exatamente uma vez.

Uso:
    python3 gen_core.py <pasta-de-origem> <pasta-de-saida>
"""
import os
import shutil
import sys

PKG_FROM = "package com.natanael.ntplay.tv"
PKG_TO = "package com.natanael.ntplay.core"

# Os dez primeiros são neutros de plataforma e vão sem remendo. Os três últimos levam remendo.
# UI.kt, Player.kt, MainActivity.kt (interface de controle remoto), Futebol.kt (tela de TV) e
# LocalServer.kt (QR pela rede local, sem sentido no celular) de propósito ficam de fora.
FILES = [
    "Store.kt", "Library.kt", "Xtream.kt", "Net.kt", "Util.kt",
    "Logos.kt", "Trending.kt", "Epg.kt", "LiveLoop.kt", "Sync.kt",
    "AppState.kt", "Presence.kt", "Updater.kt",
]

# arquivo -> lista de (trecho procurado, substituto). Cada trecho tem de ocorrer UMA vez.
PATCHES = {
    "AppState.kt": [(
        'val TABS = listOf("RECOMENDAÇÕES", "TV AO VIVO", "FUTEBOL", "FILMES", '
        '"SÉRIES", "INFANTIL", "ANIMES", "FAVORITOS")',
        'val TABS = listOf("INÍCIO", "FILMES", "SÉRIES", "TV", "BUSCA")',
    )],
    "Presence.kt": [(
        'put("p", "tv"); put("v", "tv-" + ver)',
        'put("p", "mob"); put("v", "mob-" + ver)',
    )],
    "Updater.kt": [
        (
            # grafia NataNpg de propósito: a da TV usa natanpg e depende do redirecionamento
            # do GitHub. O celular nasce sem essa dívida.
            '"https://github.com/natanpg/NTPlay/releases/download/tv-latest/"',
            '"https://github.com/NataNpg/NTPlay/releases/download/mob-latest/"',
        ),
        ('"tv-version.json?t="', '"mob-version.json?t="'),
    ],
}


class PatchError(Exception):
    """A cópia não pôde ser feita com segurança. Sempre para a compilação."""


def _transform(name, text):
    """Devolve o conteúdo já com o pacote trocado e os remendos aplicados."""
    if PKG_FROM not in text:
        raise PatchError(
            f"{name}: não achei a linha '{PKG_FROM}'. O arquivo deixou de ser do app de TV? "
            f"Confira mobile-native/tools/gen_core.py."
        )
    text = text.replace(PKG_FROM, PKG_TO, 1)

    for needle, repl in PATCHES.get(name, []):
        n = text.count(needle)
        if n != 1:
            raise PatchError(
                f"{name}: esperava 1 ocorrência de {needle!r}, achei {n}. "
                f"O app de TV mudou esse trecho — atualize o remendo em "
                f"mobile-native/tools/gen_core.py antes de seguir."
            )
        text = text.replace(needle, repl, 1)
    return text


def generate(src_dir, out_dir, files=None):
    """Copia `files` de `src_dir` para `out_dir`, transformando cada um.

    Transforma tudo na memória antes de escrever qualquer coisa: se um remendo falhar, a pasta
    de saída não fica pela metade (um build seguinte compilaria uma mistura de novo e velho).
    """
    files = FILES if files is None else files
    done = {}
    for name in files:
        path = os.path.join(src_dir, name)
        if not os.path.isfile(path):
            raise PatchError(f"{name}: não existe em {src_dir}.")
        with open(path, encoding="utf-8") as f:
            done[name] = _transform(name, f.read())

    # só agora mexe no disco; a saída é recriada para não sobrar arquivo de uma lista antiga
    if os.path.isdir(out_dir):
        shutil.rmtree(out_dir)
    os.makedirs(out_dir)
    for name, text in done.items():
        with open(os.path.join(out_dir, name), "w", encoding="utf-8") as f:
            f.write(text)
    return sorted(done)


def main(argv):
    if len(argv) != 3:
        print(__doc__)
        return 2
    try:
        written = generate(argv[1], argv[2])
    except PatchError as e:
        print(f"ERRO: {e}", file=sys.stderr)
        return 1
    print(f"motor gerado: {len(written)} arquivos em {argv[2]}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
