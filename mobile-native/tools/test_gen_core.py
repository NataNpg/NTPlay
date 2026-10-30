"""Testes do gerador do motor (gen_core.py).

Rodam em qualquer máquina com python3, sem SDK do Android e sem Gradle — é por isso que a
cópia-com-remendo vive aqui e não dentro do build.gradle.kts: é a lógica mais arriscada do
projeto do celular e precisa ser testável.
"""
import os
import shutil
import tempfile
import unittest

import gen_core


class Base(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.src = os.path.join(self.tmp, "tv")
        self.out = os.path.join(self.tmp, "out")
        os.makedirs(self.src)

    def tearDown(self):
        shutil.rmtree(self.tmp, ignore_errors=True)

    def write(self, name, body):
        with open(os.path.join(self.src, name), "w", encoding="utf-8") as f:
            f.write(body)

    def read_out(self, name):
        with open(os.path.join(self.out, name), encoding="utf-8") as f:
            return f.read()

    def plain(self, name="Store.kt", extra=""):
        """Um arquivo do motor sem remendo, com o package da TV."""
        self.write(name, "package com.natanael.ntplay.tv\n\nclass X\n" + extra)


class TestPackageRewrite(Base):
    def test_troca_o_package_para_core(self):
        self.plain()
        gen_core.generate(self.src, self.out, ["Store.kt"])
        out = self.read_out("Store.kt")
        self.assertIn("package com.natanael.ntplay.core", out)
        self.assertNotIn("package com.natanael.ntplay.tv", out)

    def test_mantem_o_resto_do_arquivo_intacto(self):
        self.plain(extra="\n// comentário com acento e emoji ★\nval y = 1\n")
        gen_core.generate(self.src, self.out, ["Store.kt"])
        out = self.read_out("Store.kt")
        self.assertIn("// comentário com acento e emoji ★", out)
        self.assertIn("val y = 1", out)

    def test_arquivo_sem_a_linha_de_package_falha(self):
        self.write("Store.kt", "class SemPackage\n")
        with self.assertRaises(gen_core.PatchError) as e:
            gen_core.generate(self.src, self.out, ["Store.kt"])
        self.assertIn("Store.kt", str(e.exception))


class TestPatches(Base):
    def test_remendo_das_abas(self):
        self.write("AppState.kt",
                   'package com.natanael.ntplay.tv\n'
                   'val TABS = listOf("RECOMENDAÇÕES", "TV AO VIVO", "FUTEBOL", "FILMES", '
                   '"SÉRIES", "INFANTIL", "ANIMES", "FAVORITOS")\n')
        gen_core.generate(self.src, self.out, ["AppState.kt"])
        out = self.read_out("AppState.kt")
        self.assertIn('val TABS = listOf("INÍCIO", "FILMES", "SÉRIES", "TV", "BUSCA")', out)
        self.assertNotIn("RECOMENDAÇÕES", out)
        self.assertNotIn("FUTEBOL", out)

    def test_remendo_da_plataforma_da_presenca(self):
        self.write("Presence.kt",
                   'package com.natanael.ntplay.tv\n'
                   'fun f() { put("p", "tv"); put("v", "tv-" + ver) }\n')
        gen_core.generate(self.src, self.out, ["Presence.kt"])
        out = self.read_out("Presence.kt")
        self.assertIn('put("p", "mob"); put("v", "mob-" + ver)', out)
        self.assertNotIn('"tv"', out)

    def test_remendo_do_atualizador(self):
        self.write("Updater.kt",
                   'package com.natanael.ntplay.tv\n'
                   'const val BASE = "https://github.com/natanpg/NTPlay/releases/download/tv-latest/"\n'
                   'val u = BASE + "tv-version.json?t=" + now\n')
        gen_core.generate(self.src, self.out, ["Updater.kt"])
        out = self.read_out("Updater.kt")
        self.assertIn("https://github.com/NataNpg/NTPlay/releases/download/mob-latest/", out)
        self.assertIn('"mob-version.json?t="', out)
        self.assertNotIn("tv-latest", out)
        self.assertNotIn("tv-version.json", out)
        # a grafia correta do dono, para não depender do redirecionamento do GitHub
        self.assertNotIn("github.com/natanpg", out)


class TestPatchVerification(Base):
    """O ponto do exercício: um remendo que não bate tem de parar a compilação."""

    def test_alvo_ausente_falha_nomeando_arquivo_e_trecho(self):
        self.write("Presence.kt",
                   'package com.natanael.ntplay.tv\n'
                   'fun f() { put("p", "celular") }\n')  # a TV mudou: alvo não existe mais
        with self.assertRaises(gen_core.PatchError) as e:
            gen_core.generate(self.src, self.out, ["Presence.kt"])
        msg = str(e.exception)
        self.assertIn("Presence.kt", msg)
        self.assertIn('put("p", "tv")', msg)
        self.assertIn("0", msg)

    def test_alvo_duplicado_falha(self):
        self.write("Presence.kt",
                   'package com.natanael.ntplay.tv\n'
                   'fun a() { put("p", "tv"); put("v", "tv-" + ver) }\n'
                   'fun b() { put("p", "tv"); put("v", "tv-" + ver) }\n')
        with self.assertRaises(gen_core.PatchError) as e:
            gen_core.generate(self.src, self.out, ["Presence.kt"])
        self.assertIn("2", str(e.exception))

    def test_nada_e_escrito_quando_um_remendo_falha(self):
        """Falha no meio não pode deixar saída pela metade: o próximo build usaria cópia velha."""
        self.plain("Store.kt")
        self.write("Presence.kt", 'package com.natanael.ntplay.tv\nfun f() { }\n')
        with self.assertRaises(gen_core.PatchError):
            gen_core.generate(self.src, self.out, ["Store.kt", "Presence.kt"])
        self.assertFalse(
            os.path.exists(os.path.join(self.out, "Store.kt")),
            "Store.kt foi escrito mesmo com Presence.kt falhando",
        )

    def test_arquivo_ausente_na_origem_falha(self):
        with self.assertRaises(gen_core.PatchError) as e:
            gen_core.generate(self.src, self.out, ["NaoExiste.kt"])
        self.assertIn("NaoExiste.kt", str(e.exception))


class TestOutputHygiene(Base):
    def test_saida_antiga_e_removida(self):
        """Arquivo que deixou de ser copiado não pode ficar para trás e ser compilado."""
        os.makedirs(self.out)
        with open(os.path.join(self.out, "Fantasma.kt"), "w") as f:
            f.write("package com.natanael.ntplay.core\nclass Fantasma\n")
        self.plain("Store.kt")
        gen_core.generate(self.src, self.out, ["Store.kt"])
        self.assertFalse(os.path.exists(os.path.join(self.out, "Fantasma.kt")))
        self.assertTrue(os.path.exists(os.path.join(self.out, "Store.kt")))

    def test_nenhuma_mencao_a_tv_sobra_na_saida(self):
        self.plain("Store.kt")
        gen_core.generate(self.src, self.out, ["Store.kt"])
        self.assertNotIn("ntplay.tv", self.read_out("Store.kt"))


class TestLeakScan(Base):
    """Os remendos provam que os alvos conhecidos bateram. Esta varredura prova o que sobrou.

    Sem ela, uma versão futura da TV que introduza uma identificação nova — outro put("p","tv"),
    um segundo literal de release — passaria pelos três remendos e geraria, em silêncio, um APK de
    celular que se identifica como TV.
    """

    def test_identificacao_de_tv_nova_no_motor_falha(self):
        self.write("Sync.kt",
                   'package com.natanael.ntplay.tv\n'
                   'fun f() { put("p", "tv") }\n')  # a TV passou a se identificar aqui também
        with self.assertRaises(gen_core.PatchError) as e:
            gen_core.generate(self.src, self.out, ["Sync.kt"])
        self.assertIn("Sync.kt", str(e.exception))

    def test_release_de_tv_nova_no_motor_falha(self):
        self.write("Store.kt",
                   'package com.natanael.ntplay.tv\n'
                   'const val X = "https://github.com/NataNpg/NTPlay/releases/download/tv-latest/"\n')
        with self.assertRaises(gen_core.PatchError) as e:
            gen_core.generate(self.src, self.out, ["Store.kt"])
        self.assertIn("tv-latest", str(e.exception))

    def test_referencia_ao_pacote_da_tv_no_corpo_falha(self):
        # import ou nome totalmente qualificado apontando de volta para o app de TV
        self.write("Store.kt",
                   'package com.natanael.ntplay.tv\n'
                   'val x = com.natanael.ntplay.tv.Util.UA\n')
        with self.assertRaises(gen_core.PatchError):
            gen_core.generate(self.src, self.out, ["Store.kt"])

    def test_a_palavra_tv_em_texto_comum_nao_falha(self):
        # "TV AO VIVO", "Smart TV", tvgId: nada disso é identificação de plataforma
        self.write("Store.kt",
                   'package com.natanael.ntplay.tv\n'
                   'val a = "TV AO VIVO"\nval b = "Smart TV"\nvar tvgId = ""\n')
        gen_core.generate(self.src, self.out, ["Store.kt"])
        out = self.read_out("Store.kt")
        self.assertIn("TV AO VIVO", out)
        self.assertIn("tvgId", out)

    def test_os_tres_remendados_passam_pela_varredura(self):
        """O caso que não pode ter falso positivo: os arquivos remendados têm de sair limpos."""
        self.write("Presence.kt",
                   'package com.natanael.ntplay.tv\n'
                   'fun f() { put("p", "tv"); put("v", "tv-" + ver) }\n')
        self.write("Updater.kt",
                   'package com.natanael.ntplay.tv\n'
                   'const val BASE = "https://github.com/natanpg/NTPlay/releases/download/tv-latest/"\n'
                   'val u = BASE + "tv-version.json?t=" + now\n')
        gen_core.generate(self.src, self.out, ["Presence.kt", "Updater.kt"])
        self.assertIn('put("p", "mob")', self.read_out("Presence.kt"))
        self.assertIn("mob-latest", self.read_out("Updater.kt"))


class TestRealFileList(Base):
    def test_a_lista_tem_os_13_arquivos_do_motor(self):
        self.assertEqual(13, len(gen_core.FILES))
        for f in ("Store.kt", "Library.kt", "Xtream.kt", "Net.kt", "Util.kt", "Logos.kt",
                  "Trending.kt", "Epg.kt", "LiveLoop.kt", "Sync.kt",
                  "AppState.kt", "Presence.kt", "Updater.kt"):
            self.assertIn(f, gen_core.FILES)

    def test_a_lista_nao_tem_os_arquivos_de_tv(self):
        for f in ("UI.kt", "Player.kt", "MainActivity.kt", "Futebol.kt", "LocalServer.kt"):
            self.assertNotIn(f, gen_core.FILES, f + " é de TV e não deve ser copiado")


if __name__ == "__main__":
    unittest.main()
