package app.locadora24h.tv

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * O menu do app (por cima do WebView). Não é uma janela separada: por isso não rouba o foco e o controle
 * continua passando pela ponte (setas ou analógico andam, A escolhe, B fecha).
 */
class MenuApp(contexto: Context) : FrameLayout(contexto) {

    private class Item(val texto: String, val acao: () -> Unit)

    private val densidade = resources.displayMetrics.density
    private val titulo = TextView(contexto)
    private val mensagem = TextView(contexto)
    private val lista = LinearLayout(contexto)
    private var itens: List<Item> = emptyList()
    private var indice = 0

    val aberto: Boolean get() = visibility == View.VISIBLE

    init {
        visibility = View.GONE
        isFocusable = false
        isClickable = false
        setBackgroundColor(Color.argb(210, 14, 15, 20))

        val cartao = LinearLayout(contexto).apply {
            orientation = LinearLayout.VERTICAL
            val p = dp(36)
            setPadding(p, p, p, p)
            background = GradientDrawable().apply { setColor(Color.parseColor("#181A22")); cornerRadius = dp(24).toFloat() }
        }
        titulo.apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 34f); setTextColor(Color.parseColor("#F2F3F5")); setTypeface(typeface, Typeface.BOLD)
        }
        mensagem.apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f); setTextColor(Color.parseColor("#A9AFBA")); setPadding(0, dp(12), 0, dp(8))
        }
        cartao.addView(titulo)
        cartao.addView(mensagem)
        cartao.addView(lista, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(cartao, LayoutParams(dp(640), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
    }

    private fun dp(v: Int) = (v * densidade).toInt()

    /** Mostra o menu. [texto] é uma mensagem opcional (pode ter várias linhas). */
    fun mostrar(tituloMenu: String, texto: String?, opcoes: List<Pair<String, () -> Unit>>) {
        titulo.text = tituloMenu
        mensagem.text = texto ?: ""
        mensagem.visibility = if (texto.isNullOrEmpty()) View.GONE else View.VISIBLE
        itens = opcoes.map { Item(it.first, it.second) }
        indice = 0
        lista.removeAllViews()
        itens.forEach { item ->
            lista.addView(TextView(context).apply {
                text = item.texto
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
                setTypeface(typeface, Typeface.BOLD)
                setPadding(dp(24), dp(16), dp(24), dp(16))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
        }
        pintar()
        visibility = View.VISIBLE
        bringToFront()
    }

    fun mover(delta: Int) {
        if (itens.isEmpty()) return
        indice = (indice + delta + itens.size) % itens.size
        pintar()
    }

    /** Fecha o menu e roda a ação do item escolhido. */
    fun escolher() {
        if (!aberto || itens.isEmpty()) return
        val item = itens[indice]
        fechar()
        item.acao()
    }

    fun fechar() {
        visibility = View.GONE
    }

    private fun pintar() {
        for (i in 0 until lista.childCount) {
            val v = lista.getChildAt(i) as TextView
            val sel = i == indice
            v.setTextColor(if (sel) Color.parseColor("#1C1500") else Color.parseColor("#F2F3F5"))
            v.background = GradientDrawable().apply {
                setColor(if (sel) Color.parseColor("#FFC21A") else Color.parseColor("#262A35"))
                cornerRadius = dp(14).toFloat()
            }
        }
    }
}
