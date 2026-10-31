package app.locadora24h.tv

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Relógio de mentira: guarda o que foi agendado e roda quando o teste manda o tempo andar. */
class AgendaFalsa : Agenda {
    class Tarefa(val ms: Long, val r: Runnable, var quando: Long)

    var agora = 0L
    val tarefas = ArrayList<Tarefa>()
    override fun depois(ms: Long, r: Runnable) { tarefas.add(Tarefa(ms, r, agora + ms)) }
    override fun cancelar(r: Runnable) { tarefas.removeAll { it.r === r } }

    fun avancar(ms: Long) {
        val fim = agora + ms
        while (true) {
            val prox = tarefas.filter { it.quando <= fim }.minByOrNull { it.quando } ?: break
            tarefas.remove(prox)
            agora = prox.quando
            prox.r.run()
        }
        agora = fim
    }
}

class AlvoFalso(var jogo: Boolean = false, var mapa: Mapa = MapaParser.PADRAO) : Alvo {
    class Tecla(val codigo: Int, val acao: Int, val repeticao: Int)

    val teclas = ArrayList<Tecla>()
    var voltou = 0
    var menuAbriu = 0
    var menu = false
    val menuLog = ArrayList<String>()

    override fun emJogo() = jogo
    override fun mapa() = mapa
    override fun enviar(codigo: Int, acao: Int, repeticao: Int) { teclas.add(Tecla(codigo, acao, repeticao)) }
    override fun voltar() { voltou++ }
    override fun abrirMenu() { menuAbriu++; menu = true }
    override fun menuAberto() = menu
    override fun menuMover(delta: Int) { menuLog.add("mover $delta") }
    override fun menuEscolher() { menuLog.add("escolher"); menu = false }
    override fun menuFechar() { menuLog.add("fechar"); menu = false }

    fun codigos() = teclas.filter { it.acao == KeyEvent.ACTION_DOWN }.map { it.codigo }
}

class ControleTest {
    private lateinit var alvo: AlvoFalso
    private lateinit var agenda: AgendaFalsa
    private lateinit var c: Controle

    private val DOWN = KeyEvent.ACTION_DOWN
    private val UP = KeyEvent.ACTION_UP

    @Before
    fun preparar() {
        alvo = AlvoFalso()
        agenda = AgendaFalsa()
        c = Controle(alvo, agenda)
    }

    private fun apertar(k: Int): Boolean {
        val tratou = c.aoBotao(k, DOWN, 0)
        c.aoBotao(k, UP, 0)
        return tratou
    }

    // ---------- catálogo ----------

    @Test
    fun catalogoAViraEnter() {
        assertTrue(apertar(KeyEvent.KEYCODE_BUTTON_A))
        assertEquals(listOf(KeyEvent.KEYCODE_ENTER), alvo.codigos())
        assertEquals(2, alvo.teclas.size) // desceu e subiu
    }

    @Test
    fun catalogoBVolta() {
        c.aoBotao(KeyEvent.KEYCODE_BUTTON_B, DOWN, 0)
        assertEquals(0, alvo.voltou) // só ao soltar
        c.aoBotao(KeyEvent.KEYCODE_BUTTON_B, UP, 0)
        assertEquals(1, alvo.voltou)
        assertTrue(alvo.teclas.isEmpty())
    }

    @Test
    fun catalogoSelectViraC() {
        assertTrue(apertar(KeyEvent.KEYCODE_BUTTON_SELECT))
        assertEquals(listOf(KeyEvent.KEYCODE_C), alvo.codigos())
    }

    @Test
    fun catalogoStartViraM() {
        assertTrue(apertar(KeyEvent.KEYCODE_BUTTON_START))
        assertEquals(listOf(KeyEvent.KEYCODE_M), alvo.codigos())
    }

    @Test
    fun catalogoXYLbRb() {
        apertar(KeyEvent.KEYCODE_BUTTON_X)
        apertar(KeyEvent.KEYCODE_BUTTON_Y)
        apertar(KeyEvent.KEYCODE_BUTTON_L1)
        apertar(KeyEvent.KEYCODE_BUTTON_R1)
        assertEquals(listOf(KeyEvent.KEYCODE_X, KeyEvent.KEYCODE_Y, KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_PAGE_DOWN), alvo.codigos())
    }

    @Test
    fun catalogoDirecionalPassaDireto() {
        assertFalse(c.aoBotao(KeyEvent.KEYCODE_DPAD_LEFT, DOWN, 0)) // o WebView já entende como seta
        assertTrue(alvo.teclas.isEmpty())
    }

    @Test
    fun catalogoSegurarAnaoRepeteEnter() {
        c.aoBotao(KeyEvent.KEYCODE_BUTTON_A, DOWN, 0)
        c.aoBotao(KeyEvent.KEYCODE_BUTTON_A, DOWN, 1)
        c.aoBotao(KeyEvent.KEYCODE_BUTTON_A, DOWN, 2)
        assertEquals(1, alvo.codigos().size)
    }

    @Test
    fun teclaQueNaoEDeControleNaoEMexida() {
        assertFalse(c.aoBotao(KeyEvent.KEYCODE_A, DOWN, 0))
        assertFalse(c.aoBotao(KeyEvent.KEYCODE_BACK, DOWN, 0))
    }

    // ---------- dentro de um jogo ----------

    @Test
    fun jogoBotaoViraATeclaDoMapa() {
        alvo.jogo = true
        alvo.mapa = MapaParser.parse("""{"A":"Z","B":"X","LB":"Q","direcional":"setas"}""")
        apertar(KeyEvent.KEYCODE_BUTTON_A)
        apertar(KeyEvent.KEYCODE_BUTTON_B)
        apertar(KeyEvent.KEYCODE_BUTTON_L1)
        assertEquals(listOf(KeyEvent.KEYCODE_Z, KeyEvent.KEYCODE_X, KeyEvent.KEYCODE_Q), alvo.codigos())
        assertEquals(0, alvo.voltou) // B dentro do jogo é uma tecla do jogo, não "voltar"
    }

    @Test
    fun jogoBotaoSemUsoNaoFazNada() {
        alvo.jogo = true
        alvo.mapa = MapaParser.parse("""{"A":"Z"}""")
        assertTrue(apertar(KeyEvent.KEYCODE_BUTTON_Y)) // tratado (o WebView não vê), mas nada é enviado
        assertTrue(alvo.teclas.isEmpty())
    }

    @Test
    fun jogoDirecionalWasd() {
        alvo.jogo = true
        alvo.mapa = MapaParser.parse("""{"direcional":"wasd"}""")
        apertar(KeyEvent.KEYCODE_DPAD_UP)
        apertar(KeyEvent.KEYCODE_DPAD_LEFT)
        assertEquals(listOf(KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_A), alvo.codigos())
    }

    @Test
    fun jogoSemMapaUsaOPadrao() {
        alvo.jogo = true
        apertar(KeyEvent.KEYCODE_BUTTON_X)
        apertar(KeyEvent.KEYCODE_DPAD_RIGHT)
        assertEquals(listOf(KeyEvent.KEYCODE_Z, KeyEvent.KEYCODE_DPAD_RIGHT), alvo.codigos())
    }

    @Test
    fun jogoStartCurtoViraATeclaDoMapa() {
        alvo.jogo = true
        c.aoBotao(KeyEvent.KEYCODE_BUTTON_START, DOWN, 0)
        agenda.avancar(500)
        c.aoBotao(KeyEvent.KEYCODE_BUTTON_START, UP, 0)
        agenda.avancar(100)
        assertEquals(listOf(KeyEvent.KEYCODE_ENTER), alvo.codigos())
        assertEquals(0, alvo.menuAbriu)
    }

    @Test
    fun jogoStartSegurado2sAbreOMenuENaoMandaTecla() {
        alvo.jogo = true
        c.aoBotao(KeyEvent.KEYCODE_BUTTON_START, DOWN, 0)
        agenda.avancar(1999)
        assertEquals(0, alvo.menuAbriu)
        agenda.avancar(2)
        assertEquals(1, alvo.menuAbriu)
        c.aoBotao(KeyEvent.KEYCODE_BUTTON_START, UP, 0) // soltar com o menu aberto
        agenda.avancar(200)
        assertTrue(alvo.teclas.isEmpty()) // o Start do menu não vira tecla no jogo
    }

    // ---------- menu do app: o controle navega nele em vez de mexer no jogo ----------

    @Test
    fun menuAbertoSetasAndamAEscolheBFecha() {
        alvo.jogo = true
        alvo.menu = true
        c.aoBotao(KeyEvent.KEYCODE_DPAD_DOWN, DOWN, 0)
        c.aoBotao(KeyEvent.KEYCODE_DPAD_UP, DOWN, 0)
        assertEquals(listOf("mover 1", "mover -1"), alvo.menuLog)
        assertTrue(c.aoBotao(KeyEvent.KEYCODE_BUTTON_A, DOWN, 0))
        c.aoBotao(KeyEvent.KEYCODE_BUTTON_A, UP, 0)
        assertEquals("escolher", alvo.menuLog.last())
        assertTrue(alvo.teclas.isEmpty()) // nada chegou ao jogo

        alvo.menu = true
        c.aoBotao(KeyEvent.KEYCODE_BUTTON_B, DOWN, 0)
        c.aoBotao(KeyEvent.KEYCODE_BUTTON_B, UP, 0)
        assertEquals("fechar", alvo.menuLog.last())
        assertEquals(0, alvo.voltou)
        assertTrue(alvo.teclas.isEmpty())
    }

    @Test
    fun menuAbertoNoCatalogoTambemNaoVazaTecla() {
        alvo.menu = true
        assertTrue(apertar(KeyEvent.KEYCODE_BUTTON_X))
        assertTrue(alvo.teclas.isEmpty())
    }

    // ---------- analógico ----------

    @Test
    fun analogicoNoCatalogoViraSetaERepeteAoSegurar() {
        c.aoAnalogico(0.9f, 0f) // direita
        assertEquals(listOf(KeyEvent.KEYCODE_DPAD_RIGHT), alvo.codigos())
        agenda.avancar(399)
        assertEquals(1, alvo.codigos().size) // ainda não repetiu
        agenda.avancar(2) // 400 ms: primeira repetição
        assertEquals(2, alvo.codigos().size)
        agenda.avancar(120) // depois, a cada 120 ms
        assertEquals(3, alvo.codigos().size)
        agenda.avancar(120)
        assertEquals(4, alvo.codigos().size)
        c.aoAnalogico(0f, 0f) // soltou
        assertEquals(KeyEvent.ACTION_UP, alvo.teclas.last().acao)
        val antes = alvo.teclas.size
        agenda.avancar(1000)
        assertEquals(antes, alvo.teclas.size) // parou de repetir
    }

    @Test
    fun analogicoDentroDoJogoUsaOMapaDoDirecional() {
        alvo.jogo = true
        alvo.mapa = MapaParser.parse("""{"direcional":"wasd"}""")
        c.aoAnalogico(0f, -1f) // cima
        assertEquals(listOf(KeyEvent.KEYCODE_W), alvo.codigos())
        c.aoAnalogico(0f, 0f)
        assertEquals(KeyEvent.ACTION_UP, alvo.teclas.last().acao)
        assertEquals(KeyEvent.KEYCODE_W, alvo.teclas.last().codigo)
    }

    @Test
    fun analogicoPertoDoCentroNaoFazNada() {
        c.aoAnalogico(0.3f, -0.4f)
        assertTrue(alvo.teclas.isEmpty())
    }

    @Test
    fun analogicoAndaNoMenuAberto() {
        alvo.menu = true
        c.aoAnalogico(0f, 1f) // baixo
        assertEquals(listOf("mover 1"), alvo.menuLog)
        assertTrue(alvo.teclas.isEmpty())
        c.aoAnalogico(0f, 0f)
        assertTrue(alvo.teclas.isEmpty()) // nenhuma tecla "presa"
    }

    @Test
    fun menuAbrindoComAnalogicoPressionadoNaoDeixaTeclaPresa() {
        alvo.jogo = true
        c.aoAnalogico(1f, 0f) // aperta a direita no jogo (manda DOWN)
        alvo.menu = true      // o menu abre
        c.aoAnalogico(0f, 0f) // solta com o menu aberto
        assertEquals(KeyEvent.ACTION_UP, alvo.teclas.last().acao) // a tecla foi solta
    }

    @Test
    fun soltarTudoSoltaTeclasPresas() {
        c.aoAnalogico(-1f, 0f)
        c.soltarTudo()
        assertEquals(KeyEvent.ACTION_UP, alvo.teclas.last().acao)
        agenda.avancar(2000)
        assertEquals(2, alvo.teclas.size)
    }
}
