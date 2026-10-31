package app.locadora24h.tv

import android.webkit.JavascriptInterface

/**
 * O que o site enxerga como window.Locadora24hApp.
 * Fica de propósito pequeno e inofensivo: qualquer página aberta no WebView pode chamar estes métodos.
 */
class PonteJs(private val aoMapa: (String?) -> Unit) {

    /** O site manda o mapa do controle do jogo (JSON) antes de abrir o jogo. */
    @JavascriptInterface
    fun setMapa(json: String?) = aoMapa(json)

    /** Volta ao mapa padrão. */
    @JavascriptInterface
    fun limparMapa() = aoMapa(null)

    @JavascriptInterface
    fun versao(): String = BuildConfig.VERSION_NAME
}
