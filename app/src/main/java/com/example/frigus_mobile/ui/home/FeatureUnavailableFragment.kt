package com.example.frigus_mobile.ui.home

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.frigus_mobile.R
import com.google.android.material.button.MaterialButton

class FeatureUnavailableFragment : Fragment(R.layout.fragment_feature_unavailable) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val title = arguments?.getInt(TITLE) ?: R.string.home_other_features
        view.findViewById<TextView>(R.id.unavailableMessage).text =
            if (title == R.string.home_qr) getString(R.string.home_qr_unavailable)
            else getString(R.string.home_feature_unavailable, getString(title))
        view.findViewById<MaterialButton>(R.id.btnUnavailableBack).apply {
            setText(R.string.home_back)
            setOnClickListener { (requireActivity() as MainActivity).showHome() }
        }
    }

    companion object {
        private const val TITLE = "title"
        fun newInstance(title: Int) = FeatureUnavailableFragment().apply {
            arguments = Bundle().apply { putInt(TITLE, title) }
        }
    }
}
