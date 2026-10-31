package app.locadora24h.tv

object UserAgents {
    /**
     * O WebView se anuncia com "; wv" e "Version/4.0" no user agent, e muitos sites de jogo recusam isso.
     * Para as páginas de jogo usamos o de um Chrome comum: o mesmo texto, sem essas duas marcas.
     */
    fun deJogo(padrao: String): String = padrao.replace("; wv", "").replace(" Version/4.0", "")
}
