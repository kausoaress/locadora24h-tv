package app.locadora24h.tv

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VerificadorLinksTest {

    @Test
    fun lerListaValida() {
        val l = VerificadorLinks.lerLista("""[{"id":"a","url":"https://x.com/"},{"id":"b","url":"http://y.org/jogo"}]""")
        assertEquals(listOf("a" to "https://x.com/", "b" to "http://y.org/jogo"), l)
    }

    @Test
    fun lerListaIgnoraItensInvalidos() {
        val l = VerificadorLinks.lerLista("""[{"id":"a","url":"ftp://x.com"},{"id":"","url":"https://ok.com"},{"url":"https://sem-id.com"},{"id":"c","url":"javascript:alert(1)"},{"id":"d","url":"https://bom.com"},5,null]""")
        assertEquals(listOf("d" to "https://bom.com"), l)
    }

    @Test
    fun lerListaDeJsonQuebradoOuVazio() {
        assertTrue(VerificadorLinks.lerLista(null).isEmpty())
        assertTrue(VerificadorLinks.lerLista("").isEmpty())
        assertTrue(VerificadorLinks.lerLista("isto não é json").isEmpty())
        assertTrue(VerificadorLinks.lerLista("{}").isEmpty())
    }

    @Test
    fun lerListaTemLimiteDeTamanho() {
        val sb = StringBuilder("[")
        for (i in 0 until 500) { if (i > 0) sb.append(","); sb.append("""{"id":"j$i","url":"https://x.com/$i"}""") }
        sb.append("]")
        assertEquals(VerificadorLinks.MAX_POR_CHAMADA, VerificadorLinks.lerLista(sb.toString()).size)
    }

    @Test
    fun statusJsonNoFormatoQueOSiteEspera() {
        val o = JSONObject(VerificadorLinks.statusJson("pes-6", Resultado(true, 123), 1700000000000L))
        val s = o.getJSONObject("pes-6")
        assertEquals(true, s.getBoolean("ok"))
        assertEquals(123, s.getInt("ms"))
        assertEquals(1700000000000L, s.getLong("quando"))
    }

    @Test
    fun limitesCombinadosComOPedido() {
        assertEquals(3, VerificadorLinks.SIMULTANEOS)
        assertEquals(6000, VerificadorLinks.LIMITE_MS)
    }
}
