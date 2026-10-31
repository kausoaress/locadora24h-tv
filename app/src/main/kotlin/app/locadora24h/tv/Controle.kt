package app.locadora24h.tv

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent

/** Marca "daqui a X ms, rode isto". No app é um Handler; nos testes é um relógio de mentira. */
interface Agenda {
    fun depois(ms: Long, r: Runnable)
    fun cancelar(r: Runnable)
}

/** O que a ponte do controle precisa da tela do app. */
interface Alvo {
    /** true quando o WebView está num site de jogo (e não no catálogo). */
    fun emJogo(): Boolean
    fun mapa(): Mapa

    /** Entrega uma tecla ao WebView, como se viesse de um teclado de verdade. */
    fun enviar(codigo: Int, acao: Int, repeticao: Int)

    /** Catálogo: B / voltar. */
    fun voltar()

    /** Dentro de um jogo: abre o menu do app (Continuar / Ver mapa / Voltar ao catálogo). */
    fun abrirMenu()

    // Enquanto o menu do app está aberto, o controle navega nele (setas ou analógico, A escolhe, B fecha)
    fun menuAberto(): Boolean
    fun menuMover(delta: Int)
    fun menuEscolher()
    fun menuFechar()
}

/**
 * Ponte do controle: recebe os botões do controle (e o analógico) e os transforma em teclas.
 *  - Menu do app aberto: setas/analógico andam no menu, A escolhe, B fecha.
 *  - Catálogo: direcional e analógico = setas, A = Enter, B = voltar (o site recebe Escape), X = X, Y = Y,
 *    LB = Page Up, RB = Page Down, Select = C (celular como teclado), Start = M (menu do site).
 *  - Dentro de um jogo: cada botão vira a tecla do mapa do jogo. Segurar Start por 2 s abre o menu do app.
 *    (O "voltar" do controle remoto, que não é botão de controle, também abre o menu: isso fica na tela do app.)
 */
class Controle(private val alvo: Alvo, private val agenda: Agenda) {

    companion object {
        const val ZONA_MORTA = 0.5f
        const val MENU_MS = 2000L
        const val TOQUE_MS = 60L
        const val CAT_PRIMEIRA = 400L   // catálogo: primeira repetição ao segurar...
        const val CAT_SEGUINTES = 120L  // ...e as seguintes
        const val JOGO_PRIMEIRA = 500L  // jogo: repetição parecida com a de um teclado
        const val JOGO_SEGUINTES = 40L

        private const val DOWN = KeyEvent.ACTION_DOWN
        private const val UP = KeyEvent.ACTION_UP

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

    private var menuAbriuPeloStart = false
    private val abrirMenu = Runnable { menuAbriuPeloStart = true; alvo.abrirMenu() }

    // ----- botões -----

    /** true = o app tratou a tecla (o WebView não deve vê-la). */
    fun aoTecla(e: KeyEvent): Boolean = aoBotao(e.keyCode, e.action, e.repeatCount)

    fun aoBotao(k: Int, acao: Int, repeticao: Int): Boolean {
        if (alvo.menuAberto()) return noMenu(k, acao)
        if (k !in DOS_BOTOES) return false
        return if (alvo.emJogo()) noJogo(k, acao, repeticao) else noCatalogo(k, acao, repeticao)
    }

    private fun noMenu(k: Int, acao: Int): Boolean {
        when (k) {
            KeyEvent.KEYCODE_DPAD_UP -> { if (acao == DOWN) alvo.menuMover(-1); return true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { if (acao == DOWN) alvo.menuMover(1); return true }
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { if (acao == UP) alvo.menuEscolher(); return true }
            KeyEvent.KEYCODE_BUTTON_B -> { if (acao == UP) alvo.menuFechar(); return true }
            KeyEvent.KEYCODE_BUTTON_START -> { if (acao == UP) menuAbriuPeloStart = false; return true }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y,
            KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_BUTTON_SELECT -> return true
        }
        return false
    }

    private fun noCatalogo(k: Int, acao: Int, repeticao: Int): Boolean {
        when (k) {
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER -> return repassar(KeyEvent.KEYCODE_ENTER, acao, repeticao, soPrimeira = true)
            KeyEvent.KEYCODE_BUTTON_B -> { if (acao == UP) alvo.voltar(); return true }
            KeyEvent.KEYCODE_BUTTON_X -> return repassar(KeyEvent.KEYCODE_X, acao, repeticao, soPrimeira = true)
            KeyEvent.KEYCODE_BUTTON_Y -> return repassar(KeyEvent.KEYCODE_Y, acao, repeticao, soPrimeira = true)
            KeyEvent.KEYCODE_BUTTON_L1 -> return repassar(KeyEvent.KEYCODE_PAGE_UP, acao, repeticao, soPrimeira = true)
            KeyEvent.KEYCODE_BUTTON_R1 -> return repassar(KeyEvent.KEYCODE_PAGE_DOWN, acao, repeticao, soPrimeira = true)
            KeyEvent.KEYCODE_BUTTON_SELECT -> return repassar(KeyEvent.KEYCODE_C, acao, repeticao, soPrimeira = true) // abre o celular como teclado
            KeyEvent.KEYCODE_BUTTON_START -> return repassar(KeyEvent.KEYCODE_M, acao, repeticao, soPrimeira = true)  // abre o menu do site
        }
        return false // direcional: o WebView já entende como setas (e o sistema repete ao segurar)
    }

    private fun noJogo(k: Int, acao: Int, repeticao: Int): Boolean {
        val mapa = alvo.mapa()
        when (k) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT ->
                return repassar(teclaDoDirecional(k, mapa.direcional), acao, repeticao, soPrimeira = false)
            KeyEvent.KEYCODE_BUTTON_START -> return start(acao, repeticao, mapa)
        }
        val botao = nomeDoBotao(k) ?: return false
        val cod = mapa.botoes[botao] ?: return true // botão sem uso neste jogo
        return repassar(cod, acao, repeticao, soPrimeira = false)
    }

    /** Start curto = a tecla do mapa. Start segurado por 2 s = menu do app. */
    private fun start(acao: Int, repeticao: Int, mapa: Mapa): Boolean {
        if (acao == DOWN) {
            if (repeticao == 0) { menuAbriuPeloStart = false; agenda.depois(MENU_MS, abrirMenu) }
            return true
        }
        agenda.cancelar(abrirMenu)
        if (!menuAbriuPeloStart) mapa.botoes["Start"]?.let { toque(it) }
        menuAbriuPeloStart = false
        return true
    }

    private fun toque(codigo: Int) {
        alvo.enviar(codigo, DOWN, 0)
        agenda.depois(TOQUE_MS, Runnable { alvo.enviar(codigo, UP, 0) })
    }

    private fun repassar(codigo: Int, acao: Int, repeticao: Int, soPrimeira: Boolean): Boolean {
        if (soPrimeira && acao == DOWN && repeticao > 0) return true
        alvo.enviar(codigo, acao, repeticao)
        return true
    }

    // ----- analógico esquerdo -----
    // 0 = esquerda, 1 = direita, 2 = cima, 3 = baixo
    private val ligada = BooleanArray(4)
    private val codigoPressionado = IntArray(4)
    private val enviouDown = BooleanArray(4) // só soltamos a tecla se ela foi mesmo apertada
    private val repetidores = Array(4) { Repetidor(it) }

    private inner class Repetidor(private val i: Int) : Runnable {
        var n = 0
        override fun run() {
            n++
            if (alvo.menuAberto()) {
                if (i == 2) alvo.menuMover(-1) else if (i == 3) alvo.menuMover(1)
            } else {
                alvo.enviar(codigoPressionado[i], DOWN, n)
            }
            agenda.depois(if (alvo.emJogo() && !alvo.menuAberto()) JOGO_SEGUINTES else CAT_SEGUINTES, this)
        }
    }

    private fun direcao(i: Int, ligar: Boolean) {
        if (ligada[i] == ligar) return
        ligada[i] = ligar
        val noMenu = alvo.menuAberto()
        if (ligar) {
            val base = when (i) {
                0 -> KeyEvent.KEYCODE_DPAD_LEFT
                1 -> KeyEvent.KEYCODE_DPAD_RIGHT
                2 -> KeyEvent.KEYCODE_DPAD_UP
                else -> KeyEvent.KEYCODE_DPAD_DOWN
            }
            val jogo = alvo.emJogo() && !noMenu
            codigoPressionado[i] = if (jogo) teclaDoDirecional(base, alvo.mapa().direcional) else base
            if (noMenu) { if (i == 2) alvo.menuMover(-1) else if (i == 3) alvo.menuMover(1) }
            else alvo.enviar(codigoPressionado[i], DOWN, 0)
            enviouDown[i] = !noMenu
            repetidores[i].n = 0
            agenda.depois(if (jogo) JOGO_PRIMEIRA else CAT_PRIMEIRA, repetidores[i])
        } else {
            agenda.cancelar(repetidores[i])
            if (enviouDown[i]) { alvo.enviar(codigoPressionado[i], UP, 0); enviouDown[i] = false }
        }
    }

    /** Posição do analógico esquerdo (-1 a 1). */
    fun aoAnalogico(x: Float, y: Float) {
        direcao(0, x < -ZONA_MORTA)
        direcao(1, x > ZONA_MORTA)
        direcao(2, y < -ZONA_MORTA)
        direcao(3, y > ZONA_MORTA)
    }

    /** true = tratou o movimento do analógico. */
    fun aoMovimento(e: MotionEvent): Boolean {
        if ((e.source and InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK || e.action != MotionEvent.ACTION_MOVE) return false
        aoAnalogico(e.getAxisValue(MotionEvent.AXIS_X), e.getAxisValue(MotionEvent.AXIS_Y))
        return true
    }

    /** Solta tudo (ao sair do app ou trocar de tela), para nenhuma tecla ficar "presa". */
    fun soltarTudo() {
        agenda.cancelar(abrirMenu)
        menuAbriuPeloStart = false
        for (i in 0..3) direcao(i, false)
    }
}
