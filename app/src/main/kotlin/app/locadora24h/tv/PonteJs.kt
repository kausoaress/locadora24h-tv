package app.locadora24h.tv

import android.webkit.JavascriptInterface

/** O que a tela do app faz quando o site pede algo pela ponte. */
interface AcoesApp {
    fun abrirJogo(url: String?, mapaJson: String?, id: String?)
    fun definirMapa(json: String?)
    fun verificarLinks(json: String?)
    fun infoAparelho(): String
    fun controles(): String
    fun desempenho(): String
    fun sair()
    fun salvarFps(id: String?, fps: Double)
    fun mostrarTeclado()
}

/**
 * O que o site enxerga como window.Locadora24hApp.
 * Tudo devolve texto ou JSON e nunca lança erro (um erro aqui travaria o site).
 * Qualquer página aberta no WebView pode chamar estes métodos, por isso a tela do app só obedece
 * abrirJogo / verificarLinks / sair quando quem chama está no catálogo.
 */
class PonteJs(private val acoes: AcoesApp) {

    private inline fun <T> seguro(padrao: T, f: () -> T): T = try { f() } catch (e: Throwable) { padrao }

    /** Guarda o mapa do jogo, marca "em jogo" e abre a URL. Se não carregar em 15 s, volta ao catálogo e avisa o site. */
    @JavascriptInterface
    fun abrirJogo(url: String?, mapaJson: String?, id: String?) = seguro(Unit) { acoes.abrirJogo(url, mapaJson, id) }

    /** O site manda o mapa do controle do jogo (JSON). */
    @JavascriptInterface
    fun setMapa(json: String?) = seguro(Unit) { acoes.definirMapa(json) }

    /** Volta ao mapa padrão. */
    @JavascriptInterface
    fun limparMapa() = seguro(Unit) { acoes.definirMapa(null) }

    /** Recebe [{id,url}] e devolve o resultado por window.L24HTV.aoStatusLinks({id:{ok,ms,quando}}). */
    @JavascriptInterface
    fun verificarLinks(jsonLista: String?) = seguro(Unit) { acoes.verificarLinks(jsonLista) }

    /** {memoriaMB, modelo, androidApi} */
    @JavascriptInterface
    fun infoAparelho(): String = seguro("{}") { acoes.infoAparelho() }

    /** ["nome do controle", ...] dos controles de jogo conectados. */
    @JavascriptInterface
    fun controles(): String = seguro("[]") { acoes.controles() }

    /** {id:{fps,quando}} medido de verdade nesta TV. */
    @JavascriptInterface
    fun desempenho(): String = seguro("{}") { acoes.desempenho() }

    @JavascriptInterface
    fun sair() = seguro(Unit) { acoes.sair() }

    @JavascriptInterface
    fun versao(): String = BuildConfig.VERSION_NAME
}

/** window.LocadoraApp: usado só pelos scripts que o próprio app injeta nas páginas de jogo. */
class PonteInterna(private val acoes: AcoesApp) {
    @JavascriptInterface
    fun salvarFps(id: String?, fps: Double) {
        try { acoes.salvarFps(id, fps) } catch (e: Throwable) { /* ignora */ }
    }

    @JavascriptInterface
    fun mostrarTeclado() {
        try { acoes.mostrarTeclado() } catch (e: Throwable) { /* ignora */ }
    }
}
