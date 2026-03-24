package com.vcemanager

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.snackbar.Snackbar
import com.vcemanager.databinding.ActivityMainBinding
import com.vcemanager.viewmodel.MainViewModel

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHost.navController

        binding.bottomNav.setupWithNavController(navController)

        // Hide bottom nav on detail screens
        navController.addOnDestinationChangedListener { _, dest, _ ->
            val hideOn = setOf(R.id.containerDetailFragment)
            binding.bottomNav.visibility =
                if (dest.id in hideOn) View.GONE else View.VISIBLE
        }

        // Global error observer
        viewModel.error.observe(this) { msg ->
            msg?.let {
                Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG)
                    .setAction("Dismiss") { viewModel.clearError() }
                    .show()
            }
        }

        // Global loading indicator
        viewModel.loading.observe(this) { loading ->
            binding.globalProgress.visibility = if (loading == true) View.VISIBLE else View.GONE
        }

        viewModel.startAutoRefresh()
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.stopAutoRefresh()
    }

    fun showSnack(msg: String, isError: Boolean = false) {
        val snackbar = Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT)
        if (isError) {
            snackbar.setBackgroundTint(
                resources.getColor(com.google.android.material.R.color.design_default_color_error, theme))
        }
        snackbar.show()
    }
}
