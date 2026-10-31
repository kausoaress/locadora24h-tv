package app.locadora24h.tv

import android.view.KeyEvent
import org.json.JSONObject

/** Como o direcional (e o analógico esquerdo) viram teclas dentro de um jogo. */
enum class Direcional { SETAS, WASD }

/**
 * O mapa de um jogo: qual tecla do teclado cada botão do controle aperta.
 * [botoes] tem só os botões que têm uso; [nomes] guarda o texto da tecla para mostrar na tela.
 */
data class Mapa(
    val botoes: Map<String, Int>,
    val nomes: Map<String, String>,
    val direcional: Direcional,
    val padrao: Boolean
)

object Botoes {
    /** Botões que o mapa aceita, na ordem em que aparecem no menu. */
    val TODOS = listOf("A", "B", "X", "Y", "LB", "RB", "Start", "Select")
}

object Teclas {
    private val especiais = mapOf(
        "enter" to KeyEvent.KEYCODE_ENTER,
        "return" to KeyEvent.KEYCODE_ENTER,
        "escape" to KeyEvent.KEYCODE_ESCAPE,
        "esc" to KeyEvent.KEYCODE_ESCAPE,
        "space" to KeyEvent.KEYCODE_SPACE,
        "espaco" to KeyEvent.KEYCODE_SPACE,
        "shift" to KeyEvent.KEYCODE_SHIFT_LEFT,
        "ctrl" to KeyEvent.KEYCODE_CTRL_LEFT,
        "control" to KeyEvent.KEYCODE_CTRL_LEFT,
        "alt" to KeyEvent.KEYCODE_ALT_LEFT,
        "tab" to KeyEvent.KEYCODE_TAB,
        "backspace" to KeyEvent.KEYCODE_DEL,
        "up" to KeyEvent.KEYCODE_DPAD_UP,
        "arrowup" to KeyEvent.KEYCODE_DPAD_UP,
        "cima" to KeyEvent.KEYCODE_DPAD_UP,
        "down" to KeyEvent.KEYCODE_DPAD_DOWN,
        "arrowdown" to KeyEvent.KEYCODE_DPAD_DOWN,
        "baixo" to KeyEvent.KEYCODE_DPAD_DOWN,
        "left" to KeyEvent.KEYCODE_DPAD_LEFT,
        "arrowleft" to KeyEvent.KEYCODE_DPAD_LEFT,
        "esquerda" to KeyEvent.KEYCODE_DPAD_LEFT,
        "right" to KeyEvent.KEYCODE_DPAD_RIGHT,
        "arrowright" to KeyEvent.KEYCODE_DPAD_RIGHT,
        "direita" to KeyEvent.KEYCODE_DPAD_RIGHT
    )

    /** "Z" -> KEYCODE_Z, "Enter" -> KEYCODE_ENTER, "F5" -> KEYCODE_F5... ou null se não conhecemos. */
    fun codigo(nome: String?): Int? {
        val n = nome?.trim()?.lowercase() ?: return null
        if (n.isEmpty()) return null
        if (n.length == 1) {
            val c = n[0]
            if (c in 'a'..'z') return KeyEvent.KEYCODE_A + (c - 'a')
            if (c in '0'..'9') return KeyEvent.KEYCODE_0 + (c - '0')
        }
        if (n.length in 2..3 && n[0] == 'f') {
            val num = n.substring(1).toIntOrNull()
            if (num != null && num in 1..12) return KeyEvent.KEYCODE_F1 + (num - 1)
        }
        return especiais[n]
    }
}

object MapaParser {
    /** Sem mapa no jogo: direcional = setas, A = Enter, B = Escape, X = Z, Y = X, Start = Enter. */
    val PADRAO = Mapa(
        botoes = mapOf(
            "A" to KeyEvent.KEYCODE_ENTER,
            "B" to KeyEvent.KEYCODE_ESCAPE,
            "X" to KeyEvent.KEYCODE_Z,
            "Y" to KeyEvent.KEYCODE_X,
            "Start" to KeyEvent.KEYCODE_ENTER
        ),
        nomes = mapOf("A" to "Enter", "B" to "Escape", "X" to "Z", "Y" to "X", "Start" to "Enter"),
        direcional = Direcional.SETAS,
        padrao = true
    )

    /**
     * Lê o JSON que o site manda, por exemplo:
     * {"A":"Z","B":"X","LB":"Q","Start":"Enter","direcional":"setas"}
     * Cada valor pode ser só o texto da tecla ou um objeto {"tecla":"Z","funcao":"Pular"}.
     * JSON vazio ou quebrado: volta o mapa padrão.
     */
    fun parse(json: String?): Mapa {
        if (json.isNullOrBlank()) return PADRAO
        return try {
            val o = JSONObject(json)
            val porNome = HashMap<String, Any?>()
            val chaves = o.keys()
            while (chaves.hasNext()) {
                val k = chaves.next()
                porNome[k.lowercase()] = o.opt(k)
            }
            val botoes = LinkedHashMap<String, Int>()
            val nomes = LinkedHashMap<String, String>()
            for (b in Botoes.TODOS) {
                val v = porNome[b.lowercase()]
                val tecla = when (v) {
                    is JSONObject -> v.optString("tecla")
                    null, JSONObject.NULL -> null
                    else -> v.toString()
                }
                val cod = Teclas.codigo(tecla)
                if (cod != null) {
                    botoes[b] = cod
                    nomes[b] = tecla!!.trim()
                }
            }
            val dir = if (porNome["direcional"]?.toString()?.trim()?.lowercase() == "wasd") Direcional.WASD else Direcional.SETAS
            Mapa(botoes, nomes, dir, false)
        } catch (e: Exception) {
            PADRAO
        }
    }
}
