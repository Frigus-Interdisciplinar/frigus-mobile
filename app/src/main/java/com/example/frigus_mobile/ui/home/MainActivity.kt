package com.example.frigus_mobile.ui.home

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentManager
import com.example.frigus_mobile.R
import com.example.frigus_mobile.ui.profile.PerfilFragment
import com.example.frigus_mobile.ui.recipes.ReceitasFragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        if (savedInstanceState == null) {
            bottomNav.selectedItemId = R.id.nav_receitas
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, ReceitasFragment())
                .commit()
        }

        bottomNav.setOnItemSelectedListener { item ->
            supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
            when (item.itemId) {
                R.id.nav_receitas -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, ReceitasFragment())
                        .commit()
                    true
                }
                R.id.nav_perfil -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, PerfilFragment())
                        .commit()
                    true
                }
                else -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, ReceitasFragment())
                        .commit()
                    true
                }
            }
        }
    }
}
