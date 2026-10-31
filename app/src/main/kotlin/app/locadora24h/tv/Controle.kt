package app.locadora24h.tv

import android.os.Handler
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent

/** O que o Controle precisa da tela do app. */
interface Alvo {
    /** true quando o WebView está num site de jogo (e não no catálogo). */
    fun emJogo(): Boolean
    fun mapa(): Mapa
    /** Entrega uma tecla ao WebView, como se viesse de um teclado de verdade. */
    fun enviar(ev: KeyEvent)
    fun voltar()
    fun abrirMenu()
}

/**
 * Ponte do controle: recebe os botões do controle (e o analógico) e os transforma em teclas.
 *  - No catálogo: direcional e analógico = setas, A = Enter, B = voltar (X = X, Y = Y, LB = Page Up, RB = Page Down,
 *    que são as teclas que o modo TV do site entende).
 *  - Dentro de um jogo: cada botão vira a tecla do mapa do jogo. Segurar Start por 2 s abre o menu do app.
 */
class Controle(private val alvo: Alvo, private val handler: Handler) {

    companion object {
        const val ZONA_MORTA = 0.5f
        const val MENU_MS = 2000L
        const val TOQUE_MS = 60L
        const val CAT_PRIMEIRA = 400L   // catálogo: primeira repetição ao segurar...
        const val CAT_SEGUINTES = 120L  // ...e as seguintes
        const val JOGO_PRIMEIRA = 500L  // jogo: repetição parecida com a de um teclado
        const val JOGO_SEGUINTES = 40L

        private val DOS_BOTOES = setOf(
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y,
            KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_SELECT,
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER
        )

        /** Nome do botão no mapa para o código do Android, ou null se não for um dos 8. */
        fun nomeDoBotao(k: Int): String? = when (k) {
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER -> "A"
            KeyEvent.KEYCODE_BUTTON_B -> "B"
            KeyEvent.KEYCODE_BUTTON_X -> "X"
            KeyEvent.KEYCODE_BUTTON_Y -> "Y"
            KeyEvent.KEYCODE_BUTTON_L1 -> "LB"
            KeyEvent.KEYCODE_BUTTON_R1 -> "RB"
            KeyEvent.KEYCODE_BUTTON_START -> "Start"
            KeyEvent.KEYCODE_BUTTON_SELECT -> "Select"
            else -> null
        }

        /** Direcional do controle -> tecla que o jogo recebe (setas ou WASD). */
        fun teclaDoDirecional(k: Int, d: Direcional): Int = if (d == Direcional.WASD) when (k) {
            KeyEvent.KEYCODE_DPAD_UP -> KeyEvent.KEYCODE_W
            KeyEvent.KEYCODE_DPAD_DOWN -> KeyEvent.KEYCODE_S
            KeyEvent.KEYCODE_DPAD_LEFT -> KeyEvent.KEYCODE_A
            else -> KeyEvent.KEYCODE_D
        } else k
    }

    private var menuAbriu = false
    private val abrirMenu = Runnable { menuAbriu = true; alvo.abrirMenu() }

    // ----- botões -----

    /** true = o app tratou a tecla (o WebView não deve vê-la). */
    fun aoTecla(e: KeyEvent): Boolean {
        val k = e.keyCode
        if (k !in DOS_BOTOES) return false
        return if (alvo.emJogo()) noJogo(e) else noCatalogo(e)
    }

    private fun noCatalogo(e: KeyEvent): Boolean {
        when (e.keyCode) {
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER -> return repassar(e, KeyEvent.KEYCODE_ENTER, soPrimeira = true)
            KeyEvent.KEYCODE_BUTTON_B -> { if (e.action == KeyEvent.ACTION_UP) alvo.voltar(); return true }
            KeyEvent.KEYCODE_BUTTON_X -> return repassar(e, KeyEvent.KEYCODE_X, soPrimeira = true)
            KeyEvent.KEYCODE_BUTTON_Y -> return repassar(e, KeyEvent.KEYCODE_Y, soPrimeira = true)
            KeyEvent.KEYCODE_BUTTON_L1 -> return repassar(e, KeyEvent.KEYCODE_PAGE_UP, soPrimeira = true)
            KeyEvent.KEYCODE_BUTTON_R1 -> return repassar(e, KeyEvent.KEYCODE_PAGE_DOWN, soPrimeira = true)
            KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_SELECT -> return true
        }
        return false // direcional: o WebView já entende como setas (e o sistema repete ao segurar)
    }

    private fun noJogo(e: KeyEvent): Boolean {
        val mapa = alvo.mapa()
        val k = e.keyCode
        when (k) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT ->
                return repassar(e, teclaDoDirecional(k, mapa.direcional), soPrimeira = false)
            KeyEvent.KEYCODE_BUTTON_START -> return start(e, mapa)
        }
        val botao = nomeDoBotao(k) ?: return false
        val cod = mapa.botoes[botao] ?: return true // botão sem uso neste jogo
        return repassar(e, cod, soPrimeira = false)
    }

    /** Start curto = a tecla do mapa. Start segurado por 2 s = menu do app. */
    private fun start(e: KeyEvent, mapa: Mapa): Boolean {
        if (e.action == KeyEvent.ACTION_DOWN) {
            if (e.repeatCount == 0) { menuAbriu = false; handler.postDelayed(abrirMenu, MENU_MS) }
            return true
        }
        handler.removeCallbacks(abrirMenu)
        if (!menuAbriu) mapa.botoes["Start"]?.let { toque(it) }
        menuAbriu = false
        return true
    }

    private fun toque(codigo: Int) {
        alvo.enviar(tecla(KeyEvent.ACTION_DOWN, codigo, 0))
        handler.postDelayed({ alvo.enviar(tecla(KeyEvent.ACTION_UP, codigo, 0)) }, TOQUE_MS)
    }

    private fun repassar(e: KeyEvent, codigo: Int, soPrimeira: Boolean): Boolean {
        if (soPrimeira && e.action == KeyEvent.ACTION_DOWN && e.repeatCount > 0) return true
        alvo.enviar(KeyEvent(e.downTime, e.eventTime, e.action, codigo, e.repeatCount, e.metaState,
            KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD))
        return true
    }

    private fun tecla(acao: Int, codigo: Int, repeticao: Int): KeyEvent {
        val agora = SystemClock.uptimeMillis()
        return KeyEvent(agora, agora, acao, codigo, repeticao, 0,
            KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD)
    }

    // ----- analógico esquerdo -----
    // 0 = esquerda, 1 = direita, 2 = cima, 3 = baixo
    private val ligada = BooleanArray(4)
    private val codigoPressionado = IntArray(4)
    private val repetidores = Array(4) { Repetidor(it) }

    private inner class Repetidor(private val i: Int) : Runnable {
        var n = 0
        override fun run() {
            n++
            alvo.enviar(tecla(KeyEvent.ACTION_DOWN, codigoPressionado[i], n))
            handler.postDelayed(this, if (alvo.emJogo()) JOGO_SEGUINTES else CAT_SEGUINTES)
        }
    }

    private fun direcao(i: Int, ligar: Boolean) {
        if (ligada[i] == ligar) return
        ligada[i] = ligar
        if (ligar) {
            val base = when (i) {
                0 -> KeyEvent.KEYCODE_DPAD_LEFT
                1 -> KeyEvent.KEYCODE_DPAD_RIGHT
                2 -> KeyEvent.KEYCODE_DPAD_UP
                else -> KeyEvent.KEYCODE_DPAD_DOWN
            }
            codigoPressionado[i] = if (alvo.emJogo()) teclaDoDirecional(base, alvo.mapa().direcional) else base
            alvo.enviar(tecla(KeyEvent.ACTION_DOWN, codigoPressionado[i], 0))
            repetidores[i].n = 0
            handler.postDelayed(repetidores[i], if (alvo.emJogo()) JOGO_PRIMEIRA else CAT_PRIMEIRA)
        } else {
            handler.removeCallbacks(repetidores[i])
            alvo.enviar(tecla(KeyEvent.ACTION_UP, codigoPressionado[i], 0))
        }
    }

    /** true = tratou o movimento do analógico. */
    fun aoMovimento(e: MotionEvent): Boolean {
        if ((e.source and InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK || e.action != MotionEvent.ACTION_MOVE) return false
        val x = e.getAxisValue(MotionEvent.AXIS_X)
        val y = e.getAxisValue(MotionEvent.AXIS_Y)
        direcao(0, x < -ZONA_MORTA)
        direcao(1, x > ZONA_MORTA)
        direcao(2, y < -ZONA_MORTA)
        direcao(3, y > ZONA_MORTA)
        return true
    }

    /** Solta tudo (ao sair do app ou trocar de tela), para nenhuma tecla ficar "presa". */
    fun soltarTudo() {
        handler.removeCallbacks(abrirMenu)
        menuAbriu = false
        for (i in 0..3) direcao(i, false)
    }
}
