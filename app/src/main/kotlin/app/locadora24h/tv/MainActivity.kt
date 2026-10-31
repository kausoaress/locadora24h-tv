package app.locadora24h.tv

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout

/** Uma tela só, em tela cheia, com um WebView aberto na Locadora 24h. */
class MainActivity : Activity(), Alvo {

    private lateinit var raiz: FrameLayout
    private lateinit var webView: WebView
    private lateinit var controle: Controle
    private val handler = Handler(Looper.getMainLooper())
    private val catalogoHost: String? = Uri.parse(BuildConfig.START_URL).host

    @Volatile
    private var mapaAtual: Mapa = MapaParser.PADRAO
    private var erroAberto = false
    private var telaCheia: View? = null
    private var telaCheiaCb: WebChromeClient.CustomViewCallback? = null
    private lateinit var chrome: WebChromeClient

    // ----- ciclo de vida -----

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) // tela sempre acesa com o app aberto
        raiz = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#0E0F14")) }
        webView = WebView(this)
        raiz.addView(webView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(raiz)
        controle = Controle(this, handler)
        configurarWebView()
        if (savedInstanceState != null) webView.restoreState(savedInstanceState) else webView.loadUrl(BuildConfig.START_URL)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configurarWebView() {
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true                  // localStorage (favoritos, recentes...)
            @Suppress("DEPRECATION")
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            setSupportMultipleWindows(false)          // links com target=_blank abrem na mesma tela
            javaScriptCanOpenWindowsAutomatically = false
            allowFileAccess = false
            allowContentAccess = false
            useWideViewPort = true
            loadWithOverviewMode = true
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null) // aceleração por hardware
        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        webView.setBackgroundColor(Color.parseColor("#0E0F14"))
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }
        webView.addJavascriptInterface(PonteJs { json -> mapaAtual = MapaParser.parse(json) }, "Locadora24hApp")

        webView.webViewClient = object : WebViewClient() {
            // Todos os links abrem dentro do próprio app; esquemas estranhos (intent:, market:...) são bloqueados.
            @Deprecated("Usado no Android 6 e anteriores")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = !url.startsWith("http")

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val esquema = request.url.scheme ?: return true
                return esquema != "http" && esquema != "https"
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                controle.soltarTudo()
            }

            override fun onPageFinished(view: WebView, url: String?) {
                view.requestFocus()
            }

            @Deprecated("Usado no Android 5.x")
            override fun onReceivedError(view: WebView, errorCode: Int, description: String?, failingUrl: String?) {
                if (failingUrl != null && failingUrl == view.url) mostrarErro()
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) mostrarErro()
            }
        }

        // Vídeo / jogo em tela cheia pedido pela página
        chrome = object : WebChromeClient() {
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (telaCheia != null) { callback.onCustomViewHidden(); return }
                telaCheia = view
                telaCheiaCb = callback
                webView.visibility = View.GONE
                raiz.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }

            override fun onHideCustomView() {
                val v = telaCheia ?: return
                raiz.removeView(v)
                telaCheia = null
                telaCheiaCb?.onCustomViewHidden()
                telaCheiaCb = null
                webView.visibility = View.VISIBLE
                webView.requestFocus()
            }
        }
        webView.webChromeClient = chrome
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
        val host = webView.url?.let { Uri.parse(it).host } ?: return false
        return host != catalogoHost
    }

    override fun mapa(): Mapa = mapaAtual

    override fun enviar(ev: KeyEvent) {
        webView.dispatchKeyEvent(ev)
    }

    /** Voltar: volta uma página; na tela inicial, pergunta "Sair do app?". */
    override fun voltar() {
        if (telaCheia != null) { chrome.onHideCustomView(); return }
        if (webView.canGoBack()) webView.goBack() else confirmarSaida()
    }

    override fun abrirMenu() {
        AlertDialog.Builder(this)
            .setTitle(R.string.menu_titulo)
            .setItems(arrayOf(getString(R.string.menu_ver_mapa), getString(R.string.menu_voltar_catalogo), getString(R.string.menu_continuar))) { _, i ->
                when (i) {
                    0 -> mostrarMapa()
                    1 -> webView.loadUrl(BuildConfig.START_URL)
                }
            }
            .show()
    }

    // ----- janelas -----

    private fun confirmarSaida() {
        AlertDialog.Builder(this)
            .setTitle(R.string.sair_titulo)
            .setPositiveButton(R.string.sim) { _, _ -> finish() }
            .setNegativeButton(R.string.nao, null)
            .show()
    }

    private fun mostrarMapa() {
        val m = mapaAtual
        val sb = StringBuilder()
        sb.append(getString(R.string.mapa_direcional)).append(":  ")
            .append(if (m.direcional == Direcional.WASD) "W A S D" else "↑ ← ↓ →").append("\n\n")
        for (b in Botoes.TODOS) {
            sb.append(b).append("  →  ").append(m.nomes[b] ?: getString(R.string.mapa_sem_uso)).append("\n")
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.mapa_titulo)
            .setMessage(sb.toString())
            .setPositiveButton(R.string.mapa_ok, null)
            .show()
    }

    private fun mostrarErro() {
        if (erroAberto) return
        erroAberto = true
        AlertDialog.Builder(this)
            .setTitle(R.string.erro_titulo)
            .setMessage(R.string.erro_texto)
            .setCancelable(false)
            .setPositiveButton(R.string.erro_tentar) { _, _ -> erroAberto = false; webView.loadUrl(BuildConfig.START_URL) }
            .setNegativeButton(R.string.erro_sair) { _, _ -> erroAberto = false; finish() }
            .show()
    }
}
