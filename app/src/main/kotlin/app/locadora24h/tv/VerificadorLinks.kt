package app.locadora24h.tv

import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class Resultado(val ok: Boolean, val ms: Long)

/**
 * Confere se o site de cada jogo responde ("No ar"). Faz um pedido HEAD (ou GET, se o site não aceitar HEAD)
 * com limite de 6 s, em threads de fundo, no máximo 3 ao mesmo tempo.
 */
class VerificadorLinks(private val aoResultado: (id: String, r: Resultado) -> Unit) {

    companion object {
        const val SIMULTANEOS = 3
        const val LIMITE_MS = 6000
        const val MAX_POR_CHAMADA = 200
        private const val UA = "Mozilla/5.0 (Linux; Android; Locadora24hTV) AppleWebKit/537.36 Chrome/100 Safari/537.36"

        /** O site respondeu mas barrou o robô (401, 403, 405, 429): o site está no ar. */
        private val VIVO_MAS_BARRA = setOf(401, 403, 405, 429)

        /** Lê [{"id":"x","url":"https://..."}]. Itens inválidos são ignorados. */
        fun lerLista(json: String?): List<Pair<String, String>> {
            if (json.isNullOrBlank()) return emptyList()
            return try {
                val arr = JSONArray(json)
                val saida = ArrayList<Pair<String, String>>()
                for (i in 0 until minOf(arr.length(), MAX_POR_CHAMADA)) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optString("id", "")
                    val url = o.optString("url", "")
                    if (id.isNotEmpty() && (url.startsWith("http://") || url.startsWith("https://"))) saida.add(id to url)
                }
                saida
            } catch (e: Exception) {
                emptyList()
            }
        }

        /** O JSON que o site recebe em window.L24HTV.aoStatusLinks: {id:{ok,ms,quando}} */
        fun statusJson(id: String, r: Resultado, quando: Long): String =
            JSONObject().put(id, JSONObject().put("ok", r.ok).put("ms", r.ms).put("quando", quando)).toString()

        fun checar(url: String): Resultado {
            val t0 = SystemClock.elapsedRealtime()
            try {
                var atual = URL(url)
                var metodo = "HEAD"
                for (tentativa in 0..6) {
                    if (SystemClock.elapsedRealtime() - t0 > LIMITE_MS) return Resultado(false, 0)
                    if (atual.protocol != "http" && atual.protocol != "https") return Resultado(false, 0)
                    val c = atual.openConnection() as HttpURLConnection
                    try {
                        c.connectTimeout = LIMITE_MS
                        c.readTimeout = LIMITE_MS
                        c.instanceFollowRedirects = false // seguimos na mão (o Android não segue de http para https)
                        c.requestMethod = metodo
                        c.setRequestProperty("User-Agent", UA)
                        c.setRequestProperty("Accept", "*/*")
                        val codigo = c.responseCode
                        if (codigo in 300..399) {
                            val destino = c.getHeaderField("Location") ?: return Resultado(true, SystemClock.elapsedRealtime() - t0)
                            atual = URL(atual, destino)
                            continue
                        }
                        if (metodo == "HEAD" && (codigo == 403 || codigo == 405 || codigo == 501)) { metodo = "GET"; continue }
                        val ok = codigo in 100..399 || codigo in VIVO_MAS_BARRA
                        return Resultado(ok, SystemClock.elapsedRealtime() - t0)
                    } finally {
                        c.disconnect()
                    }
                }
                return Resultado(false, 0)
            } catch (e: Exception) {
                return Resultado(false, 0)
            }
        }
    }

    private val pool = Executors.newFixedThreadPool(SIMULTANEOS) // no máximo 3 pedidos ao mesmo tempo
    private val emAndamento: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    fun verificar(jsonLista: String?) {
        for ((id, url) in lerLista(jsonLista)) {
            if (!emAndamento.add(id)) continue // já está sendo conferido
            try {
                pool.execute {
                    val r = checar(url)
                    emAndamento.remove(id)
                    aoResultado(id, r)
                }
            } catch (e: Exception) {
                emAndamento.remove(id)
            }
        }
    }

    fun parar() {
        pool.shutdownNow()
    }
}
