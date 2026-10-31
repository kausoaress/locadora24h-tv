package app.locadora24h.tv

import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

/**
 * Decide o que o bloqueador de anúncios barra. Sem nada do Android aqui dentro: dá para testar no computador.
 *
 * Regras:
 *  - desligado = nunca bloqueia;
 *  - nunca bloqueia o endereço do catálogo nem o da página principal do jogo que está aberto (nem os "irmãos" do mesmo site);
 *  - nunca bloqueia o que está na lista de EXCEÇÕES (editada no /admin);
 *  - bloqueia o domínio (ou um domínio "pai" dele) que estiver na lista embutida (StevenBlack/hosts) ou nos EXTRAS do /admin.
 */
class BloqueioAnuncios {

    @Volatile var ligado: Boolean = true
    @Volatile var hostCatalogo: String? = null
    @Volatile var hostPagina: String? = null

    @Volatile private var base: Set<String> = emptySet()
    @Volatile private var extras: Set<String> = emptySet()
    @Volatile private var excecoes: Set<String> = emptySet()
    @Volatile var origemListasExtras: String = "nenhuma" // "rede", "cache" ou "nenhuma"

    /** Quantos pedidos foram barrados desde que o app abriu / no jogo atual / janelas novas e navegações barradas. */
    val bloqueados = AtomicInteger(0)
    val bloqueadosNoJogo = AtomicInteger(0)
    val popups = AtomicInteger(0)
    val navegacoes = AtomicInteger(0)

    val tamanhoBase: Int get() = base.size
    val tamanhoExtras: Int get() = extras.size
    val tamanhoExcecoes: Int get() = excecoes.size

    // ----- listas -----

    /** Uma linha por domínio. Linhas vazias e comentários (#) são ignorados. */
    fun carregarBase(linhas: Sequence<String>) {
        val novo = HashSet<String>(100_000)
        for (linha in linhas) {
            val l = linha.trim().lowercase()
            if (l.isEmpty() || l.startsWith("#")) continue
            novo.add(l.trimEnd('.'))
        }
        base = novo
    }

    fun definirListas(novosExtras: Set<String>, novasExcecoes: Set<String>, origem: String) {
        extras = novosExtras.map { limpar(it) }.filter { it.isNotEmpty() }.toSet()
        excecoes = novasExcecoes.map { limpar(it) }.filter { it.isNotEmpty() }.toSet()
        origemListasExtras = origem
    }

    // ----- decisões -----

    /** Este endereço (host de um pedido de imagem, script, quadro...) deve ser barrado? Conta quando barra. */
    fun deveBloquear(host: String?): Boolean {
        if (!ligado || host.isNullOrBlank()) return false
        val h = limpar(host)
        if (h.isEmpty() || protegido(h)) return false
        if (pertence(h, excecoes)) return false
        val barrar = pertence(h, extras) || pertence(h, base)
        if (barrar) { bloqueados.incrementAndGet(); bloqueadosNoJogo.incrementAndGet() }
        return barrar
    }

    /** Só pergunta (não conta nada): este domínio está na lista de anúncios? */
    fun ehDeAnuncio(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        val h = limpar(host)
        if (h.isEmpty() || protegido(h) || pertence(h, excecoes)) return false
        return pertence(h, extras) || pertence(h, base)
    }

    /**
     * Uma navegação do quadro principal (a página trocando de endereço sozinha) deve ser barrada?
     *  - com clique de verdade (comGesto) ou desligado: nunca;
     *  - indo para um domínio de anúncio: barra;
     *  - indo para outro site, sem clique, e fora dos primeiros [GRACA_MS] da página: barra (pop-under / redirecionamento automático);
     *  - redirecionamento HTTP do carregamento normal e navegação dentro do mesmo site: deixa passar.
     */
    fun deveBloquearNavegacao(hostAtual: String?, hostDestino: String?, comGesto: Boolean, ehRedirecionamentoHttp: Boolean, msDesdeInicio: Long): Boolean {
        if (!ligado || comGesto || hostDestino.isNullOrBlank()) return false
        val destino = limpar(hostDestino)
        if (destino.isEmpty() || protegido(destino) || pertence(destino, excecoes)) return false
        val barrar = when {
            ehDeAnuncio(destino) -> true
            ehRedirecionamentoHttp -> false
            msDesdeInicio < GRACA_MS -> false
            hostAtual.isNullOrBlank() -> false
            mesmoSite(limpar(hostAtual), destino) -> false
            else -> true
        }
        if (barrar) navegacoes.incrementAndGet()
        return barrar
    }

    /** Uma janela nova (window.open / target=_blank) deve ser barrada? Sem clique: sempre. Com clique: só se for domínio de anúncio. */
    fun deveBloquearJanela(hostDestino: String?, comGesto: Boolean): Boolean {
        if (!ligado) return false
        val barrar = if (!comGesto) true else ehDeAnuncio(hostDestino)
        if (barrar) popups.incrementAndGet()
        return barrar
    }

    fun zerarContagemDoJogo() { bloqueadosNoJogo.set(0) }

    /** Resumo para o site (diagnóstico) e para o menu. */
    fun resumoJson(): String = JSONObject()
        .put("ligado", ligado)
        .put("dominios", tamanhoBase)
        .put("extras", tamanhoExtras)
        .put("excecoes", tamanhoExcecoes)
        .put("origemExtras", origemListasExtras)
        .put("bloqueados", bloqueados.get())
        .put("noJogo", bloqueadosNoJogo.get())
        .put("popups", popups.get())
        .put("navegacoes", navegacoes.get())
        .toString()

    // ----- apoio -----

    /** Catálogo, página do jogo e "irmãos" do mesmo site nunca são barrados. */
    private fun protegido(h: String): Boolean {
        val c = hostCatalogo?.let { limpar(it) }
        val p = hostPagina?.let { limpar(it) }
        return (c != null && mesmoSite(h, c)) || (p != null && mesmoSite(h, p))
    }

    companion object {
        /** Navegações sem clique logo depois de a página começar a carregar são do próprio carregamento do jogo. */
        const val GRACA_MS = 3_000L

        /** Tira espaços e o ponto final, e deixa em minúsculas. */
        fun limpar(host: String): String = host.trim().lowercase().trimEnd('.')

        private val SEGUNDO_NIVEL = setOf("com", "co", "org", "net", "gov", "edu", "ac", "ne", "or")

        /** "a.b.exemplo.com" -> "exemplo.com"; "jogo.com.br" -> "jogo.com.br". */
        private val IPV4 = Regex("""\d{1,3}(\.\d{1,3}){3}""")

        fun dominioBase(host: String): String {
            if (host.contains(':') || IPV4.matches(host)) return host // endereço IP: o próprio endereço é o "site"
            val p = host.split('.')
            if (p.size <= 2) return host
            val ultimo = p[p.size - 1]
            val penultimo = p[p.size - 2]
            val n = if (ultimo.length == 2 && penultimo in SEGUNDO_NIVEL) 3 else 2
            return p.takeLast(minOf(n, p.size)).joinToString(".")
        }

        fun mesmoSite(a: String, b: String): Boolean = dominioBase(a) == dominioBase(b)

        /** O host ou algum domínio "pai" dele (sem chegar ao TLD) está no conjunto? */
        fun pertence(host: String, conjunto: Set<String>): Boolean {
            if (conjunto.isEmpty()) return false
            var h = host
            while (true) {
                if (conjunto.contains(h)) return true
                val i = h.indexOf('.')
                if (i < 0) return false
                h = h.substring(i + 1)
                if (h.indexOf('.') < 0) return conjunto.contains(h) // o último pedaço só vale se estiver exatamente na lista
            }
        }

        /** Lê {"extras":[...],"excecoes":[...]} do /api/bloqueio. Devolve null se não for um JSON válido. */
        fun lerListas(json: String?): Pair<Set<String>, Set<String>>? {
            if (json.isNullOrBlank()) return null
            return try {
                val o = JSONObject(json)
                fun conjunto(nome: String): Set<String> {
                    val a = o.optJSONArray(nome) ?: return emptySet()
                    val s = HashSet<String>()
                    for (i in 0 until minOf(a.length(), 5000)) {
                        val v = a.optString(i, "").trim().lowercase()
                        if (v.isNotEmpty() && v.length <= 253) s.add(v)
                    }
                    return s
                }
                conjunto("extras") to conjunto("excecoes")
            } catch (e: Exception) { null }
        }

        /** CSS que esconde os blocos de anúncio mais comuns, sem encostar no jogo. */
        const val CSS = "ins.adsbygoogle,[id^=\"google_ads_\"],[id^=\"div-gpt-ad\"],[id^=\"aswift_\"],[class~=\"adsbygoogle\"]," +
            "iframe[src*=\"doubleclick.net\"],iframe[src*=\"googlesyndication.com\"],iframe[src*=\"adservice.google\"]," +
            "iframe[id^=\"google_ads_iframe\"],a[href*=\"doubleclick.net/\"]{display:none!important}"

        /** JavaScript que põe o CSS na página (só uma vez por página). */
        val JS_CSS: String = "(function(){try{if(document.getElementById('l24h-ads'))return;var s=document.createElement('style');" +
            "s.id='l24h-ads';s.textContent=" + JSONObject.quote(CSS) + ";(document.head||document.documentElement).appendChild(s)}catch(e){}})();"
    }
}
