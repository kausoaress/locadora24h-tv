package app.locadora24h.tv

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapaTest {

    @Test
    fun teclasComuns() {
        assertEquals(KeyEvent.KEYCODE_Z, Teclas.codigo("Z"))
        assertEquals(KeyEvent.KEYCODE_Z, Teclas.codigo(" z "))
        assertEquals(KeyEvent.KEYCODE_5, Teclas.codigo("5"))
        assertEquals(KeyEvent.KEYCODE_ENTER, Teclas.codigo("Enter"))
        assertEquals(KeyEvent.KEYCODE_ESCAPE, Teclas.codigo("Escape"))
        assertEquals(KeyEvent.KEYCODE_SPACE, Teclas.codigo("Space"))
        assertEquals(KeyEvent.KEYCODE_SHIFT_LEFT, Teclas.codigo("Shift"))
        assertEquals(KeyEvent.KEYCODE_F5, Teclas.codigo("F5"))
        assertEquals(KeyEvent.KEYCODE_DPAD_UP, Teclas.codigo("ArrowUp"))
    }

    @Test
    fun teclaDesconhecidaOuVazia() {
        assertNull(Teclas.codigo("Foo"))
        assertNull(Teclas.codigo(""))
        assertNull(Teclas.codigo(null))
        assertNull(Teclas.codigo("F99"))
    }

    @Test
    fun mapaDoSite() {
        val m = MapaParser.parse(
            """{ "A": "Z", "B": "X", "X": "C", "Y": "V", "LB": "Q", "RB": "E", "Start": "Enter", "Select": "Escape", "direcional": "setas" }"""
        )
        assertFalse(m.padrao)
        assertEquals(KeyEvent.KEYCODE_Z, m.botoes["A"])
        assertEquals(KeyEvent.KEYCODE_X, m.botoes["B"])
        assertEquals(KeyEvent.KEYCODE_C, m.botoes["X"])
        assertEquals(KeyEvent.KEYCODE_V, m.botoes["Y"])
        assertEquals(KeyEvent.KEYCODE_Q, m.botoes["LB"])
        assertEquals(KeyEvent.KEYCODE_E, m.botoes["RB"])
        assertEquals(KeyEvent.KEYCODE_ENTER, m.botoes["Start"])
        assertEquals(KeyEvent.KEYCODE_ESCAPE, m.botoes["Select"])
        assertEquals(Direcional.SETAS, m.direcional)
        assertEquals("Z", m.nomes["A"])
    }

    @Test
    fun mapaComWasdEBotaoSemUso() {
        val m = MapaParser.parse("""{ "A": "Space", "direcional": "WASD" }""")
        assertEquals(Direcional.WASD, m.direcional)
        assertEquals(KeyEvent.KEYCODE_SPACE, m.botoes["A"])
        assertNull(m.botoes["B"]) // sem uso
    }

    @Test
    fun valorComoObjetoENomesSemDiferenciarMaiusculas() {
        val m = MapaParser.parse("""{ "a": { "tecla": "Z", "funcao": "Pular" }, "START": "Enter" }""")
        assertEquals(KeyEvent.KEYCODE_Z, m.botoes["A"])
        assertEquals(KeyEvent.KEYCODE_ENTER, m.botoes["Start"])
    }

    @Test
    fun semMapaOuMapaQuebradoUsaOPadrao() {
        for (entrada in listOf(null, "", "   ", "isto não é json", "[1,2]")) {
            val m = MapaParser.parse(entrada)
            assertTrue("entrada: $entrada", m.padrao)
        }
        val p = MapaParser.PADRAO
        assertEquals(Direcional.SETAS, p.direcional)
        assertEquals(KeyEvent.KEYCODE_ENTER, p.botoes["A"])
        assertEquals(KeyEvent.KEYCODE_ESCAPE, p.botoes["B"])
        assertEquals(KeyEvent.KEYCODE_Z, p.botoes["X"])
        assertEquals(KeyEvent.KEYCODE_X, p.botoes["Y"])
        assertEquals(KeyEvent.KEYCODE_ENTER, p.botoes["Start"])
    }

    @Test
    fun direcionalViraSetasOuWasd() {
        assertEquals(KeyEvent.KEYCODE_DPAD_LEFT, Controle.teclaDoDirecional(KeyEvent.KEYCODE_DPAD_LEFT, Direcional.SETAS))
        assertEquals(KeyEvent.KEYCODE_A, Controle.teclaDoDirecional(KeyEvent.KEYCODE_DPAD_LEFT, Direcional.WASD))
        assertEquals(KeyEvent.KEYCODE_D, Controle.teclaDoDirecional(KeyEvent.KEYCODE_DPAD_RIGHT, Direcional.WASD))
        assertEquals(KeyEvent.KEYCODE_W, Controle.teclaDoDirecional(KeyEvent.KEYCODE_DPAD_UP, Direcional.WASD))
        assertEquals(KeyEvent.KEYCODE_S, Controle.teclaDoDirecional(KeyEvent.KEYCODE_DPAD_DOWN, Direcional.WASD))
    }

    @Test
    fun nomesDosBotoes() {
        assertEquals("A", Controle.nomeDoBotao(KeyEvent.KEYCODE_BUTTON_A))
        assertEquals("A", Controle.nomeDoBotao(KeyEvent.KEYCODE_DPAD_CENTER)) // botão do meio do controle remoto
        assertEquals("LB", Controle.nomeDoBotao(KeyEvent.KEYCODE_BUTTON_L1))
        assertEquals("RB", Controle.nomeDoBotao(KeyEvent.KEYCODE_BUTTON_R1))
        assertEquals("Start", Controle.nomeDoBotao(KeyEvent.KEYCODE_BUTTON_START))
        assertEquals("Select", Controle.nomeDoBotao(KeyEvent.KEYCODE_BUTTON_SELECT))
        assertNull(Controle.nomeDoBotao(KeyEvent.KEYCODE_A))
    }
}
