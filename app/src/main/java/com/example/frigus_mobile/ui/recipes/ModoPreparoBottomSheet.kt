package com.example.frigus_mobile.ui.recipes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import com.example.frigus_mobile.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class ModoPreparoBottomSheet : BottomSheetDialogFragment() {

    private var recipeName: String? = null
    private var instructions: String? = null

    companion object {
        private const val ARG_NAME = "recipe_name"
        private const val ARG_INSTRUCTIONS = "recipe_instructions"

        fun newInstance(recipeName: String, instructions: String): ModoPreparoBottomSheet {
            val fragment = ModoPreparoBottomSheet()
            val args = Bundle().apply {
                putString(ARG_NAME, recipeName)
                putString(ARG_INSTRUCTIONS, instructions)
            }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        recipeName = arguments?.getString(ARG_NAME)
        instructions = arguments?.getString(ARG_INSTRUCTIONS)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_modo_preparo, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val txtTitle = view.findViewById<TextView>(R.id.txtSheetRecipeTitle)
        val txtInstructions = view.findViewById<TextView>(R.id.txtSheetInstructions)
        val btnFechar = view.findViewById<Button>(R.id.btnFecharModoPreparo)

        if (!recipeName.isNullOrBlank()) {
            txtTitle.text = "Preparo: $recipeName"
        }

        txtInstructions.text = if (!instructions.isNullOrBlank()) {
            instructions
        } else {
            "Modo de preparo indisponível no momento."
        }

        btnFechar.setOnClickListener {
            dismiss()
        }
    }
}
