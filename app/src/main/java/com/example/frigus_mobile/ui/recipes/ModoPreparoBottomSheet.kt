package com.example.frigus_mobile.ui.recipes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.example.frigus_mobile.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class ModoPreparoBottomSheet : BottomSheetDialogFragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.bottom_sheet_modo_preparo, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<TextView>(R.id.txtSheetRecipeTitle).text =
            getString(R.string.recipe_browser_preparation_title, arguments?.getString(ARG_NAME).orEmpty())
        view.findViewById<TextView>(R.id.txtSheetInstructions).text =
            arguments?.getString(ARG_INSTRUCTIONS)?.takeIf(String::isNotBlank)
                ?: getString(R.string.recipe_browser_no_instructions)
        view.findViewById<View>(R.id.btnFecharModoPreparo).setOnClickListener { dismiss() }
    }

    companion object {
        private const val ARG_NAME = "recipe_name"
        private const val ARG_INSTRUCTIONS = "recipe_instructions"
        fun newInstance(recipeName: String, instructions: String) = ModoPreparoBottomSheet().apply {
            arguments = Bundle().apply {
                putString(ARG_NAME, recipeName)
                putString(ARG_INSTRUCTIONS, instructions)
            }
        }
    }
}
