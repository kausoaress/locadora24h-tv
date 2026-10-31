package app.locadora24h.tv

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import org.json.JSONArray
import org.json.JSONObject

/** Handler do Android como Agenda da ponte do controle. */
private class AgendaHandler(private val handler: Handler) : Agenda {
    override fun depois(ms: Long, r: Runnable) { handler.postDelayed(r, ms) }
    override fun cancelar(r: Runnable) { handler.removeCallbacks(r) }
}

/** Uma tela só, em tela cheia, com um WebView aberto na Locadora 24h. */
class MainActivity : Activity(), Alvo, AcoesApp {

    companion object {
        /** Tag do Logcat: no PC, `adb logcat -s L24H` mostra cada passo. */
        const val TAG = "L24H"
        private const val ABRIR_JOGO_MS = 15_000L   // se o jogo não carregar em 15 s, volta ao catálogo
        private const val MEDIR_FPS_APOS_MS = 8_000L // mede os quadros por segundo 8 s depois do jogo abrir
        private const val SPLASH_MAX_MS = 8_000L
        private const val FUNDO = "#0E0F14"
    }

    private fun l(msg: String) { Log.i(TAG, msg) }

    private lateinit var raiz: FrameLayout
    private lateinit var webView: WebView
    private lateinit var splash: ImageView
    private lateinit var menu: MenuApp
    private lateinit var controle: Controle
    private lateinit var verificador: VerificadorLinks
    private lateinit var prefs: SharedPreferences
    private lateinit var chrome: WebChromeClient
    private lateinit var uaCatalogo: String
    private lateinit var uaJogo: String
    private val handler = Handler(Looper.getMainLooper())
    private val catalogoHost: String? = Uri.parse(BuildConfig.START_URL).host

    @Volatile private var mapaAtual: Mapa = MapaParser.PADRAO
    @Volatile private var jogoId: String? = null
    private var jogoForcado = false          // true entre abrirJogo e a volta ao catálogo
    private var jogoCarregou = false
    private var tokenJogo = 0
    private var avisoPendente: String? = null
    private var telaCheia: View? = null
    private var telaCheiaCb: WebChromeClient.CustomViewCallback? = null

    // ----- ciclo de vida -----

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        l("onCreate: app ${BuildConfig.VERSION_NAME} (código ${BuildConfig.VERSION_CODE}), Android ${Build.VERSION.SDK_INT}, ${Build.MODEL}, abrindo ${BuildConfig.START_URL}")
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) // tela sempre acesa com o app aberto
        val matchParent = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        raiz = FrameLayout(this).apply { setBackgroundColor(Color.parseColor(FUNDO)) }
        webView = WebView(this)
        raiz.addView(webView, matchParent)
        splash = ImageView(this).apply { // tela de abertura escura com o selo "24h": nada de flash branco
            setBackgroundColor(Color.parseColor(FUNDO))
            setImageResource(R.drawable.splash_logo)
            scaleType = ImageView.ScaleType.CENTER
        }
        raiz.addView(splash, FrameLayout.LayoutParams(matchParent))
        menu = MenuApp(this)
        raiz.addView(menu, FrameLayout.LayoutParams(matchParent))
        setContentView(raiz)

        prefs = getSharedPreferences("locadora24h", MODE_PRIVATE)
        controle = Controle(this, AgendaHandler(handler))
        verificador = VerificadorLinks { id, r -> runOnUiThread { entregarStatus(id, r) } }
        configurarWebView()
        handler.postDelayed({ esconderSplash() }, SPLASH_MAX_MS)
        if (savedInstanceState != null) webView.restoreState(savedInstanceState) else {
            l("loadUrl (catálogo): ${BuildConfig.START_URL}")
            webView.loadUrl(BuildConfig.START_URL)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configurarWebView() {
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true                  // localStorage (favoritos, recentes...)
            @Suppress("DEPRECATION")
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false  // o som não precisa de toque
            setSupportMultipleWindows(true)           // window.open / target=_blank: tratados em onCreateWindow
            javaScriptCanOpenWindowsAutomatically = true
            allowFileAccess = false
            allowContentAccess = false
            useWideViewPort = true
            loadWithOverviewMode = true
            cacheMode = WebSettings.LOAD_DEFAULT      // cache padrão
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }
        // User agent: o WebView se anuncia com "; wv" e "Version/4.0", e muitos sites de jogo recusam isso.
        // No catálogo mantemos o original; nas páginas de jogo usamos o de um Chrome comum.
        uaCatalogo = webView.settings.userAgentString
        uaJogo = UserAgents.deJogo(uaCatalogo)
        l("user agent do catálogo: $uaCatalogo")
        l("user agent dos jogos:   $uaJogo")

        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null) // aceleração por hardware
        webView.overScrollMode = View.OVER_SCROLL_NEVER      // sem efeito de "puxar" nas bordas
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false
        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        webView.setBackgroundColor(Color.parseColor(FUNDO))
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true) // jogos que guardam o progresso em outro domínio
        }
        webView.addJavascriptInterface(PonteJs(this), "Locadora24hApp")
        webView.addJavascriptInterface(PonteInterna(this), "LocadoraApp")

        webView.webViewClient = object : WebViewClient() {
            // Todos os links abrem dentro do próprio app; esquemas estranhos (intent:, market:...) são bloqueados.
            @Deprecated("Usado no Android 6 e anteriores")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                l("shouldOverrideUrlLoading (api<24): $url")
                if (!url.startsWith("http")) return true
                ajustarUserAgent(url)
                return false
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val esquema = request.url.scheme ?: return true
                l("shouldOverrideUrlLoading: ${request.url} (quadro principal: ${request.isForMainFrame})")
                if (esquema != "http" && esquema != "https") return true
                if (request.isForMainFrame) ajustarUserAgent(request.url.toString())
                return false
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                l("onPageStarted: $url")
                ajustarUserAgent(url)
                controle.soltarTudo()
                handler.removeCallbacks(medirFps)
                if (hostDe(url) == catalogoHost) { jogoForcado = false; jogoId = null } // voltou ao catálogo
            }

            override fun onPageFinished(view: WebView, url: String?) {
                l("onPageFinished: $url")
                esconderSplash()
                view.requestFocus()
                if (hostDe(url) == catalogoHost) aoCatalogoCarregar() else aoJogoCarregar()
            }

            @Deprecated("Usado no Android 5.x")
            override fun onReceivedError(view: WebView, errorCode: Int, description: String?, failingUrl: String?) {
                l("onReceivedError (api<23): código $errorCode, $description, url $failingUrl")
                if (failingUrl != null && failingUrl == view.url) aoFalhaDePagina(getString(R.string.motivo_rede, description ?: errorCode.toString()))
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                l("onReceivedError: quadro principal=${request.isForMainFrame}, código ${error.errorCode}, ${error.description}, url ${request.url}")
                if (request.isForMainFrame) aoFalhaDePagina(getString(R.string.motivo_rede, error.description?.toString() ?: error.errorCode.toString()))
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                val codigo = errorResponse.statusCode
                l("onReceivedHttpError: quadro principal=${request.isForMainFrame}, HTTP $codigo ${errorResponse.reasonPhrase}, url ${request.url}")
                if (request.isForMainFrame && codigo >= 400) aoFalhaDePagina(motivoHttp(codigo))
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                l("onReceivedSslError: erro ${error.primaryError}, url ${error.url}")
                handler.cancel() // NÃO ignora: nunca aceita certificado inválido
                aoFalhaDePagina(getString(R.string.motivo_ssl))
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                l("onRenderProcessGone: travou=${!detail.didCrash()} (crash=${detail.didCrash()})")
                handler.post { recreate() } // o WebView morreu: recria a tela do app
                return true
            }
        }

        // Vídeo / jogo em tela cheia pedido pela página e janelas novas
        chrome = object : WebChromeClient() {
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                l("onShowCustomView (tela cheia)")
                if (telaCheia != null) { callback.onCustomViewHidden(); return }
                telaCheia = view
                telaCheiaCb = callback
                webView.visibility = View.GONE
                raiz.addView(view, 0, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                view.requestFocus()
            }

            override fun onHideCustomView() {
                l("onHideCustomView")
                val v = telaCheia ?: return
                raiz.removeView(v)
                telaCheia = null
                telaCheiaCb?.onCustomViewHidden()
                telaCheiaCb = null
                webView.visibility = View.VISIBLE
                webView.requestFocus()
            }

            // window.open / target=_blank: a URL nova carrega NESTA MESMA WebView (nunca abre outra janela)
            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                l("onCreateWindow: isDialog=$isDialog, gesto=$isUserGesture")
                val transporte = resultMsg.obj as? WebView.WebViewTransport ?: return false
                val temp = WebView(this@MainActivity)
                temp.webViewClient = object : WebViewClient() {
                    private var feito = false
                    private fun destino(url: String?) {
                        if (feito || url.isNullOrEmpty() || url == "about:blank") return
                        if (!(url.startsWith("http://") || url.startsWith("https://"))) return
                        feito = true
                        l("janela nova -> mesma WebView: $url")
                        handler.post {
                            ajustarUserAgent(url)
                            webView.loadUrl(url)
                            temp.stopLoading()
                            temp.destroy()
                        }
                    }

                    @Deprecated("Usado no Android 6 e anteriores")
                    override fun shouldOverrideUrlLoading(v: WebView, url: String): Boolean { destino(url); return true }
                    override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean { destino(request.url.toString()); return true }
                    override fun onPageStarted(v: WebView, url: String?, favicon: Bitmap?) { destino(url) }
                }
                transporte.webView = temp
                resultMsg.sendToTarget()
                return true
            }
        }
        webView.webChromeClient = chrome
    }

    private fun hostDe(url: String?): String? = url?.let { try { Uri.parse(it).host } catch (e: Exception) { null } }

    /** Catálogo: user agent original. Páginas de jogo: user agent de um Chrome comum (sem "; wv"). */
    private fun ajustarUserAgent(url: String?) {
        val h = hostDe(url) ?: return
        val alvo = if (h == catalogoHost) uaCatalogo else uaJogo
        if (webView.settings.userAgentString != alvo) {
            webView.settings.userAgentString = alvo
            l("user agent trocado para ${if (h == catalogoHost) "catálogo" else "jogo"} ($h)")
        }
    }

    private fun esconderSplash() {
        if (splash.visibility != View.GONE) splash.visibility = View.GONE
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        webView.requestFocus()
    }

    override fun onPause() {
        controle.soltarTudo()
        webView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        verificador.parar()
        raiz.removeAllViews()
        webView.destroy()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
    }

    // ----- entrada: o controle passa pela ponte antes de chegar no WebView -----

    override fun dispatchKeyEvent(event: KeyEvent): Boolean = controle.aoTecla(event) || super.dispatchKeyEvent(event)

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean = controle.aoMovimento(ev) || super.dispatchGenericMotionEvent(ev)

    @Deprecated("Voltar do controle remoto")
    override fun onBackPressed() = voltar()

    // ----- Alvo (o que a ponte do controle pede) -----

    override fun emJogo(): Boolean {
        if (jogoForcado) return true
        val url = webView.url ?: return false
        return hostDe(url) != catalogoHost
    }

    override fun mapa(): Mapa = mapaAtual

    override fun enviar(codigo: Int, acao: Int, repeticao: Int) {
        val agora = SystemClock.uptimeMillis()
        // jogo em tela cheia (Fullscreen API): a tecla vai para a vista de tela cheia
        (telaCheia ?: webView).dispatchKeyEvent(KeyEvent(agora, agora, acao, codigo, repeticao, 0,
            KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD))
    }

    /**
     * Voltar (B no catálogo, ou o voltar do controle remoto):
     *  - menu do app aberto: fecha;
     *  - dentro de um jogo: NÃO sai direto, abre o menu do app;
     *  - no catálogo: manda Escape ao site (o site decide: volta uma tela ou pergunta se quer sair).
     */
    override fun voltar() {
        if (menu.aberto) { menu.fechar(); return }
        if (telaCheia != null) { chrome.onHideCustomView(); return }
        if (emJogo()) { abrirMenu(); return }
        webView.evaluateJavascript("(function(){return typeof window.L24HTV==='object'})()") { r ->
            if (r == "true") {
                enviar(KeyEvent.KEYCODE_ESCAPE, KeyEvent.ACTION_DOWN, 0)
                enviar(KeyEvent.KEYCODE_ESCAPE, KeyEvent.ACTION_UP, 0)
            } else {
                voltarSemSite()
            }
        }
    }

    /** O site ainda não carregou (ou deu erro): volta uma página ou pergunta se quer sair. */
    private fun voltarSemSite() {
        if (webView.canGoBack()) webView.goBack() else confirmarSaida()
    }

    override fun abrirMenu() {
        if (menu.aberto) return
        l("menu do app aberto (em jogo)")
        menu.mostrar(getString(R.string.menu_titulo), null, listOf(
            getString(R.string.menu_continuar) to { },
            getString(R.string.menu_ver_mapa) to { mostrarMapa() },
            getString(R.string.menu_voltar_catalogo) to { voltarAoCatalogo(null) }
        ))
    }

    override fun menuAberto(): Boolean = menu.aberto
    override fun menuMover(delta: Int) = menu.mover(delta)
    override fun menuEscolher() = menu.escolher()
    override fun menuFechar() = menu.fechar()

    // ----- AcoesApp (o que o site pede pela ponte) -----

    private fun noCatalogo(): Boolean = hostDe(webView.url) == catalogoHost

    override fun abrirJogo(url: String?, mapaJson: String?, id: String?) {
        l("abrirJogo recebido: id=$id, url=$url")
        runOnUiThread {
            if (!noCatalogo()) { l("abrirJogo ignorado: a página atual não é o catálogo (${webView.url})"); return@runOnUiThread }
            if (url.isNullOrBlank() || !(url.startsWith("https://") || url.startsWith("http://"))) { l("abrirJogo ignorado: URL inválida"); return@runOnUiThread }
            mapaAtual = MapaParser.parse(mapaJson)
            jogoForcado = true
            jogoCarregou = false
            jogoId = id
            val token = ++tokenJogo
            ajustarUserAgent(url)
            l("loadUrl (jogo): $url")
            webView.loadUrl(url)
            // Se a página não carregar em 15 s, volta ao catálogo e avisa o site (com o motivo)
            handler.postDelayed({
                if (token == tokenJogo && jogoForcado && !jogoCarregou) {
                    l("tempo esgotado: o jogo não carregou em 15 s ($url)")
                    aoFalhaDePagina(getString(R.string.motivo_tempo))
                }
            }, ABRIR_JOGO_MS)
        }
    }

    override fun definirMapa(json: String?) {
        l("mapa do controle recebido: ${json?.take(80)}")
        mapaAtual = MapaParser.parse(json)
    }

    override fun verificarLinks(json: String?) {
        runOnUiThread {
            if (noCatalogo()) { l("verificarLinks: ${VerificadorLinks.lerLista(json).size} link(s)"); verificador.verificar(json) }
        }
    }

    override fun infoAparelho(): String {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return JSONObject()
            .put("memoriaMB", mi.totalMem / (1024L * 1024L))
            .put("modelo", Build.MODEL ?: "")
            .put("androidApi", Build.VERSION.SDK_INT)
            .toString()
    }

    override fun controles(): String {
        val nomes = JSONArray()
        for (id in InputDevice.getDeviceIds()) {
            val d = InputDevice.getDevice(id) ?: continue
            val fontes = d.sources
            val ehControle = (fontes and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                (fontes and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
            if (ehControle && !d.isVirtual) nomes.put(d.name)
        }
        return nomes.toString()
    }

    override fun desempenho(): String = prefs.getString("fps", "{}") ?: "{}"

    override fun sair() {
        l("sair() pedido pelo site")
        runOnUiThread { if (noCatalogo()) finish() }
    }

    override fun salvarFps(id: String?, fps: Double) {
        if (id.isNullOrBlank() || id != jogoId || fps.isNaN() || fps.isInfinite() || fps < 0 || fps > 240) return
        l("fps medido: $id = $fps")
        synchronized(prefs) {
            val o = try { JSONObject(prefs.getString("fps", "{}") ?: "{}") } catch (e: Exception) { JSONObject() }
            o.put(id, JSONObject().put("fps", Math.round(fps * 10) / 10.0).put("quando", System.currentTimeMillis()))
            prefs.edit().putString("fps", o.toString()).apply()
        }
    }

    override fun mostrarTeclado() {
        runOnUiThread {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(webView, 0)
        }
    }

    // ----- páginas: catálogo x jogo -----

    private val medirFps = Runnable {
        val id = jogoId ?: return@Runnable
        if (!emJogo()) return@Runnable
        // conta requestAnimationFrame por 3 s e devolve para o app (LocadoraApp.salvarFps)
        webView.evaluateJavascript(
            "(function(){try{var id=${JSONObject.quote(id)},n=0,t0=0;" +
                "function f(t){if(!t0)t0=t;n++;if(t-t0<3000){requestAnimationFrame(f)}else{LocadoraApp.salvarFps(id,n*1000/(t-t0))}}" +
                "requestAnimationFrame(f)}catch(e){}})();", null)
    }

    private fun aoJogoCarregar() {
        jogoCarregou = true
        // site de jogo com campo de texto: ao receber foco, abre o teclado do Android
        webView.evaluateJavascript(
            "(function(){if(window.__l24k)return;window.__l24k=1;document.addEventListener('focusin',function(e){" +
                "var t=e.target;if(t&&(t.tagName==='INPUT'||t.tagName==='TEXTAREA'||t.isContentEditable)){try{LocadoraApp.mostrarTeclado()}catch(x){}}},true)})();", null)
        if (jogoId != null) {
            handler.removeCallbacks(medirFps)
            handler.postDelayed(medirFps, MEDIR_FPS_APOS_MS)
        }
    }

    private fun aoCatalogoCarregar() {
        jogoForcado = false
        jogoCarregou = false
        val aviso = avisoPendente
        avisoPendente = null
        handler.postDelayed({
            // o site atualiza os selos com o desempenho medido e mostra o aviso (com o motivo), se houver
            webView.evaluateJavascript("(function(){var s=window.L24HTV;if(!s)return;try{s.aoDesempenho&&s.aoDesempenho()}catch(e){}" +
                (if (aviso != null) "try{s.toast(${JSONObject.quote(aviso)})}catch(e){}" else "") + "})();", null)
        }, 900)
    }

    /** Falha ao carregar uma página. Jogo: volta ao catálogo avisando o motivo. Catálogo: mostra "sem conexão". */
    private fun aoFalhaDePagina(motivo: String) {
        l("falha de página: $motivo (jogoForcado=$jogoForcado, jogoId=$jogoId)")
        if (jogoForcado || (jogoId != null && !noCatalogo())) voltarAoCatalogo(motivo)
        else mostrarErro()
    }

    /** O motivo, em texto simples, para o site mostrar no aviso: "o site do jogo respondeu erro 503". */
    private fun motivoHttp(codigo: Int): String =
        if (codigo == 401 || codigo == 403 || codigo == 429) getString(R.string.motivo_recusou, codigo) else getString(R.string.motivo_http, codigo)

    private fun voltarAoCatalogo(motivo: String?) {
        l("voltando ao catálogo" + (if (motivo != null) " (motivo: $motivo)" else ""))
        jogoForcado = false
        jogoId = null
        tokenJogo++
        handler.removeCallbacks(medirFps)
        avisoPendente = if (motivo != null) getString(R.string.nao_abriu_motivo, motivo) else null
        webView.stopLoading()
        ajustarUserAgent(BuildConfig.START_URL)
        webView.loadUrl(BuildConfig.START_URL)
    }

    private fun entregarStatus(id: String, r: Resultado) {
        val json = VerificadorLinks.statusJson(id, r, System.currentTimeMillis())
        webView.evaluateJavascript("(function(){var s=window.L24HTV;if(s&&s.aoStatusLinks)s.aoStatusLinks($json)})();", null)
    }

    // ----- janelas (o menu do app, sem janela separada) -----

    private fun confirmarSaida() {
        if (menu.aberto) return
        menu.mostrar(getString(R.string.sair_titulo), null, listOf(
            getString(R.string.nao) to { },
            getString(R.string.sim) to { finish() }
        ))
    }

    private fun mostrarMapa() {
        val m = mapaAtual
        val sb = StringBuilder()
        sb.append(getString(R.string.mapa_direcional)).append(":  ")
            .append(if (m.direcional == Direcional.WASD) "W A S D" else "↑ ← ↓ →").append("\n")
        for (b in Botoes.TODOS) {
            sb.append(b).append("  →  ").append(m.nomes[b] ?: getString(R.string.mapa_sem_uso)).append("\n")
        }
        menu.mostrar(getString(R.string.mapa_titulo), sb.toString().trimEnd(), listOf(getString(R.string.mapa_ok) to { }))
    }

    private fun mostrarErro() {
        if (menu.aberto) return
        menu.mostrar(getString(R.string.erro_titulo), getString(R.string.erro_texto), listOf(
            getString(R.string.erro_tentar) to { webView.loadUrl(BuildConfig.START_URL) },
            getString(R.string.erro_sair) to { finish() }
        ))
    }
}
