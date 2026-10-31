package app.locadora24h.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class UserAgentsTest {

    @Test
    fun tiraWvEVersion() {
        val wv = "Mozilla/5.0 (Linux; Android 9; AFTMM Build/PS7233; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/100.0.0.0 Mobile Safari/537.36"
        val esperado = "Mozilla/5.0 (Linux; Android 9; AFTMM Build/PS7233) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.0.0 Mobile Safari/537.36"
        assertEquals(esperado, UserAgents.deJogo(wv))
    }

    @Test
    fun naoMexeNoQueJaEDeChromeComum() {
        val chrome = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        assertEquals(chrome, UserAgents.deJogo(chrome))
    }

    @Test
    fun resultadoNaoTemAsMarcasDoWebView() {
        val r = UserAgents.deJogo("Mozilla/5.0 (Linux; Android 5.1; AFTT Build/LVY48F; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/50.0 Safari/537.36")
        assertFalse(r.contains("; wv"))
        assertFalse(r.contains("Version/4.0"))
        assertFalse(r.contains("  ")) // sem espaço duplo sobrando
    }
}
