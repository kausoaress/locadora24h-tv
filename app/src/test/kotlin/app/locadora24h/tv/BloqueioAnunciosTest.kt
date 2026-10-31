package app.locadora24h.tv

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BloqueioAnunciosTest {

    private fun novo(
        base: List<String> = listOf("ads.exemplo.com", "tracker.net", "doubleclick.net", "# comentario", "", "  ADS.MAIUSCULO.COM  "),
        extras: Set<String> = emptySet(),
        excecoes: Set<String> = emptySet()
    ): BloqueioAnuncios {
        val b = BloqueioAnuncios()
        b.hostCatalogo = "locadora24h.vercel.app"
        b.carregarBase(base.asSequence())
        b.definirListas(extras, excecoes, "rede")
        return b
    }

    // ----- bloqueio de pedidos -----

    @Test fun bloqueiaDominioDaLista() {
        val b = novo()
        assertTrue(b.deveBloquear("ads.exemplo.com"))
        assertTrue(b.deveBloquear("ADS.EXEMPLO.COM"))
        assertTrue(b.deveBloquear("ads.maiusculo.com"))
    }

    @Test fun bloqueiaSubdominiosDeUmDominioDaLista() {
        val b = novo()
        assertTrue(b.deveBloquear("a.b.tracker.net"))
        assertTrue(b.deveBloquear("static.doubleclick.net"))
        assertTrue(b.deveBloquear("www.tracker.net"))
    }

    @Test fun naoBloqueiaOQueNaoEstaNaListaNemOPaiDeUmItemDaLista() {
        val b = novo()
        assertFalse(b.deveBloquear("exemplo.com"))       // só "ads.exemplo.com" está na lista
        assertFalse(b.deveBloquear("www.exemplo.com"))
        assertFalse(b.deveBloquear("outro.net"))
        assertFalse(b.deveBloquear("net"))
        assertFalse(b.deveBloquear("com"))
        assertFalse(b.deveBloquear(null))
        assertFalse(b.deveBloquear(""))
        assertFalse(b.deveBloquear("   "))
    }

    @Test fun naoConfundePedacosDeNome() {
        val b = novo()
        assertFalse(b.deveBloquear("meutracker.net"))      // "tracker.net" não é sufixo "inteiro" de "meutracker.net"
        assertFalse(b.deveBloquear("xdoubleclick.net"))
    }

    @Test fun desligadoNaoBloqueiaNada() {
        val b = novo()
        b.ligado = false
        assertFalse(b.deveBloquear("ads.exemplo.com"))
        assertFalse(b.deveBloquearNavegacao("jogo.com", "ads.exemplo.com", false, false, 10_000))
        assertFalse(b.deveBloquearJanela("ads.exemplo.com", false))
        assertEquals(0, b.bloqueados.get())
        assertEquals(0, b.popups.get())
    }

    @Test fun extrasDoAdminTambemBloqueiam() {
        val b = novo(extras = setOf("novo-anuncio.org"))
        assertTrue(b.deveBloquear("novo-anuncio.org"))
        assertTrue(b.deveBloquear("cdn.novo-anuncio.org"))
        assertEquals(1, b.tamanhoExtras)
    }

    // ----- exceções -----

    @Test fun excecoesVencemSobreAListaEmbutidaEOsExtras() {
        val b = novo(extras = setOf("outro-anuncio.com"), excecoes = setOf("ads.exemplo.com", "outro-anuncio.com"))
        assertFalse(b.deveBloquear("ads.exemplo.com"))
        assertFalse(b.deveBloquear("x.ads.exemplo.com"))   // subdomínio de uma exceção
        assertFalse(b.deveBloquear("outro-anuncio.com"))
        assertTrue(b.deveBloquear("tracker.net"))          // o resto continua bloqueado
    }

    @Test fun excecaoEmDominioPaiLiberaOsFilhos() {
        val b = novo(excecoes = setOf("exemplo.com"))
        assertFalse(b.deveBloquear("ads.exemplo.com"))
    }

    // ----- nunca bloquear o catálogo nem a página do jogo -----

    @Test fun nuncaBloqueiaOCatalogoMesmoSeEstiverNaLista() {
        val b = novo(base = listOf("locadora24h.vercel.app", "vercel.app"))
        assertFalse(b.deveBloquear("locadora24h.vercel.app"))
        assertFalse(b.deveBloquear("outro.vercel.app"))    // mesmo site (vercel.app)
        assertFalse(b.deveBloquearNavegacao("jogo.com", "locadora24h.vercel.app", false, false, 10_000))
    }

    @Test fun nuncaBloqueiaOSitePrincipalDoJogoAberto() {
        val b = novo(base = listOf("ads.exemplo.com", "jogo.com"))
        assertTrue(b.deveBloquear("jogo.com"))             // ainda sem página aberta: a lista vale
        b.hostPagina = "www.jogo.com"
        assertFalse(b.deveBloquear("jogo.com"))
        assertFalse(b.deveBloquear("cdn.jogo.com"))        // irmão do mesmo site
        assertTrue(b.deveBloquear("ads.exemplo.com"))      // anúncio de terceiros continua barrado
        b.hostPagina = null
        assertTrue(b.deveBloquear("jogo.com"))
    }

    @Test fun siteBrasileiroComTerminacaoComBrContaComoUmSite() {
        assertEquals("jogo.com.br", BloqueioAnuncios.dominioBase("cdn.static.jogo.com.br"))
        assertEquals("exemplo.com", BloqueioAnuncios.dominioBase("a.b.exemplo.com"))
        assertEquals("exemplo.co.uk", BloqueioAnuncios.dominioBase("x.exemplo.co.uk"))
        assertEquals("exemplo.com", BloqueioAnuncios.dominioBase("exemplo.com"))
        assertEquals("localhost", BloqueioAnuncios.dominioBase("localhost"))
        assertEquals("127.0.0.1", BloqueioAnuncios.dominioBase("127.0.0.1"))   // IP não é cortado em pedaços
        assertFalse(BloqueioAnuncios.mesmoSite("127.0.0.1", "10.0.0.1"))
        assertEquals("[::1]", BloqueioAnuncios.dominioBase("[::1]"))
        assertTrue(BloqueioAnuncios.mesmoSite("a.jogo.com.br", "www.jogo.com.br"))
        assertFalse(BloqueioAnuncios.mesmoSite("jogo.com.br", "outro.com.br"))
    }

    // ----- contadores -----

    @Test fun contaSoOQueBloqueiaEZeraPorJogo() {
        val b = novo()
        b.deveBloquear("ads.exemplo.com")
        b.deveBloquear("tracker.net")
        b.deveBloquear("naolistado.com")
        assertEquals(2, b.bloqueados.get())
        assertEquals(2, b.bloqueadosNoJogo.get())
        b.zerarContagemDoJogo()
        b.deveBloquear("tracker.net")
        assertEquals(3, b.bloqueados.get())
        assertEquals(1, b.bloqueadosNoJogo.get())
    }

    @Test fun resumoJsonTrazOsNumerosParaODiagnostico() {
        val b = novo(extras = setOf("x.org"), excecoes = setOf("y.org"))
        b.deveBloquear("tracker.net")
        val o = JSONObject(b.resumoJson())
        assertTrue(o.getBoolean("ligado"))
        assertEquals(4, o.getInt("dominios"))   // ads.exemplo.com, tracker.net, doubleclick.net, ads.maiusculo.com
        assertEquals(1, o.getInt("extras"))
        assertEquals(1, o.getInt("excecoes"))
        assertEquals("rede", o.getString("origemExtras"))
        assertEquals(1, o.getInt("bloqueados"))
        assertEquals(0, o.getInt("popups"))
    }

    // ----- pop-ups e redirecionamentos -----

    @Test fun janelaNovaSemCliqueSempreBarra() {
        val b = novo()
        assertTrue(b.deveBloquearJanela("qualquer.com", false))
        assertTrue(b.deveBloquearJanela(null, false))
        assertEquals(2, b.popups.get())
    }

    @Test fun janelaNovaComCliqueSoBarraDominioDeAnuncio() {
        val b = novo()
        assertFalse(b.deveBloquearJanela("jogo-de-verdade.com", true))
        assertTrue(b.deveBloquearJanela("ads.exemplo.com", true))
        assertEquals(1, b.popups.get())
    }

    @Test fun navegacaoComCliqueNuncaBarra() {
        val b = novo()
        assertFalse(b.deveBloquearNavegacao("jogo.com", "outro.com", true, false, 60_000))
        assertFalse(b.deveBloquearNavegacao("jogo.com", "ads.exemplo.com", true, false, 60_000)) // a pessoa clicou: ela decide
    }

    @Test fun navegacaoAutomaticaParaOutroSiteDepoisDaGracaBarra() {
        val b = novo()
        assertTrue(b.deveBloquearNavegacao("jogo.com", "outro.com", false, false, BloqueioAnuncios.GRACA_MS + 1))
        assertEquals(1, b.navegacoes.get())
    }

    @Test fun navegacaoNosPrimeirosSegundosEDoProprioCarregamento() {
        val b = novo()
        assertFalse(b.deveBloquearNavegacao("jogo.com", "espelho.net", false, false, 500))
        // mas para um domínio de anúncio barra até na carência
        assertTrue(b.deveBloquearNavegacao("jogo.com", "ads.exemplo.com", false, false, 500))
    }

    @Test fun redirecionamentoHttpENavegacaoNoMesmoSiteNaoBarram() {
        val b = novo()
        assertFalse(b.deveBloquearNavegacao("jogo.com", "outro.com", false, true, 60_000))       // 301/302 do carregamento
        assertFalse(b.deveBloquearNavegacao("jogo.com", "www.jogo.com", false, false, 60_000))   // mesmo site
        assertFalse(b.deveBloquearNavegacao("a.jogo.com.br", "b.jogo.com.br", false, false, 60_000))
        assertTrue(b.deveBloquearNavegacao("jogo.com", "ads.exemplo.com", false, true, 60_000))  // redirecionamento PARA anúncio barra
    }

    @Test fun excecaoLiberaAnavegacao() {
        val b = novo(excecoes = setOf("espelho.net"))
        assertFalse(b.deveBloquearNavegacao("jogo.com", "espelho.net", false, false, 60_000))
        assertFalse(b.deveBloquearNavegacao("jogo.com", "x.espelho.net", false, false, 60_000))
    }

    // ----- leitura das listas do site -----

    @Test fun leListasDoAdmin() {
        val r = BloqueioAnuncios.lerListas("""{"extras":["A.com","b.org"," "],"excecoes":["c.net"],"atualizadoEm":null}""")
        assertNotNull(r)
        assertEquals(setOf("a.com", "b.org"), r!!.first)
        assertEquals(setOf("c.net"), r.second)
    }

    @Test fun listaInvalidaVirraNull() {
        assertNull(BloqueioAnuncios.lerListas(null))
        assertNull(BloqueioAnuncios.lerListas(""))
        assertNull(BloqueioAnuncios.lerListas("isto não é json"))
        assertNull(BloqueioAnuncios.lerListas("<html>erro</html>"))
    }

    @Test fun jsonSemAsListasViraListasVazias() {
        val r = BloqueioAnuncios.lerListas("{}")
        assertNotNull(r)
        assertTrue(r!!.first.isEmpty())
        assertTrue(r.second.isEmpty())
    }

    // ----- CSS e desempenho -----

    @Test fun cssNaoTocaEmElementosGenericos() {
        val css = BloqueioAnuncios.CSS
        assertTrue(css.contains("ins.adsbygoogle"))
        assertTrue(css.contains("display:none!important"))
        assertFalse("não pode esconder canvas nem body", css.contains("canvas") || css.contains("body"))
        assertFalse("não pode ser seletor largo demais", css.contains("[id*=\"ad\"]") || css.contains("[class*=\"ad\"]"))
        assertTrue(BloqueioAnuncios.JS_CSS.contains("l24h-ads"))
    }

    @Test fun listaGrandeConsultaRapido() {
        val b = BloqueioAnuncios()
        b.carregarBase((0 until 100_000).asSequence().map { "anuncio$it.exemplo-de-rede.com" })
        assertEquals(100_000, b.tamanhoBase)
        val t0 = System.nanoTime()
        var n = 0
        for (i in 0 until 50_000) if (b.deveBloquear("sub.anuncio${i * 2}.exemplo-de-rede.com")) n++
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertEquals(50_000, n.coerceAtMost(50_000))
        assertTrue("50 mil consultas devem levar bem menos de 2 s (levou $ms ms)", ms < 2000)
    }
}
