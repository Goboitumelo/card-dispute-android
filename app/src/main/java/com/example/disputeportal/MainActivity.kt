package com.example.disputeportal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.disputeportal.ui.DisputeApp
import com.example.disputeportal.ui.MainViewModel
import com.example.disputeportal.ui.theme.DisputeTheme

class MainActivity : ComponentActivity() {
    /**
     * Enables edge-to-edge display and sets the Compose content.
     *
     * @param savedInstanceState the saved state, if any
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as DisputeApplication).container
        setContent {
            val viewModel: MainViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return MainViewModel(container.session, container.repository) as T
                    }
                },
            )
            DisputeTheme {
                DisputeApp(viewModel)
            }
        }
    }
}
