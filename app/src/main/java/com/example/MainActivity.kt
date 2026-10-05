package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.ui.screens.HostMainScreen
import com.example.ui.screens.SetupWizardScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.util.CrashProtector
import com.example.viewmodel.HostViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: HostViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashProtector.install()
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.White
                ) {
                    val isSetupCompleted by viewModel.isSetupCompleted.collectAsState()
                    val currentStep by viewModel.currentWizardStep.collectAsState()

                    BackHandler(enabled = !isSetupCompleted && currentStep > 0) {
                        viewModel.previousWizardStep()
                    }

                    if (!isSetupCompleted) {
                        SetupWizardScreen(viewModel = viewModel)
                    } else {
                        HostMainScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshAll(this)
    }
}
