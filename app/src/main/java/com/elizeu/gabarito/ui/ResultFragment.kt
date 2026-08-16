package com.elizeu.gabarito.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.elizeu.gabarito.GabaritoStore
import com.elizeu.gabarito.ItemProva
import com.elizeu.gabarito.R
import com.elizeu.gabarito.databinding.FragmentResultBinding

class ResultFragment : Fragment() {

    companion object {
        const val ARG_ITEMS = "items"
        private val LETTERS = "ABCDE"
    }

    private var _b: FragmentResultBinding? = null
    private val b get() = _b!!

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _b = FragmentResultBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val items: List<ItemProva> =
            arguments?.getParcelableArrayList(ARG_ITEMS) ?: emptyList()
        val key = GabaritoStore.load(requireContext())

        var acertos = 0
        var erros = 0
        var brancos = 0
        var total = 0
        val rows = ArrayList<Array<Any?>>()

        for (item in items) {
            val correta = key[item.questao] ?: continue
            val expectedCol = LETTERS.indexOf(correta)
            total++
            val respCol = item.resposta
            when {
                respCol < 0 -> {
                    brancos++
                    rows.add(arrayOf(item.questao, null, expectedCol))
                }
                respCol == expectedCol -> {
                    acertos++
                    rows.add(arrayOf(item.questao, respCol, expectedCol))
                }
                else -> {
                    erros++
                    rows.add(arrayOf(item.questao, respCol, expectedCol))
                }
            }
        }

        b.txtNota.text = "$acertos / $total"
        val pct = if (total > 0) (acertos * 100) / total else 0
        b.txtPercent.text = "$pct% de acerto"
        val parts = ArrayList<String>()
        if (acertos > 0) parts.add("$acertos acertos")
        if (erros > 0) parts.add("$erros erros")
        if (brancos > 0) parts.add("$brancos em branco")
        b.txtSummary.text = if (parts.isEmpty()) "Nenhuma questão conferida" else parts.joinToString(" · ")

        buildList(rows)
        b.btnAnother.setOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }
        b.btnHome.setOnClickListener {
            requireActivity().supportFragmentManager.popBackStack(
                null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE
            )
        }
    }

    private fun buildList(rows: List<Array<Any?>>) {
        b.listContainer.removeAllViews()
        val correctColor = ContextCompat.getColor(requireContext(), R.color.correct)
        val wrongColor = ContextCompat.getColor(requireContext(), R.color.wrong)
        val secondaryColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)

        for (row in rows) {
            val questao = row[0] as Int
            val respCol = row[1] as? Int
            val expectedCol = row[2] as Int

            val tv = TextView(requireContext())
            tv.textSize = 14f
            tv.setPadding(dp(4), dp(8), dp(4), dp(8))

            val resp = if (respCol != null && respCol in 0..4) LETTERS[respCol].toString() else "—"
            val corr = LETTERS[expectedCol].toString()
            val ok = respCol != null && respCol == expectedCol
            val blank = respCol == null

            tv.text = buildString {
                append("Q")
                append(questao)
                append("   Aluno: ")
                append(resp)
                append("   Correto: ")
                append(corr)
                append("   ")
                append(
                    when {
                        ok -> "✓"
                        blank -> "em branco"
                        else -> "✗"
                    }
                )
            }
            tv.setTextColor(
                when {
                    ok -> correctColor
                    blank -> secondaryColor
                    else -> wrongColor
                }
            )
            b.listContainer.addView(tv)
        }
    }

    private fun dp(v: Int): Int =
        (v * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
