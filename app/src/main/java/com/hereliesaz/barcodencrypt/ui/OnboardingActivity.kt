package com.hereliesaz.barcodencrypt.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.hereliesaz.barcodencrypt.ui.theme.BarcodencryptTheme

/**
 * An activity to host the onboarding flow.
 * This is currently a stub and should be implemented to guide the user through the
 * required permissions and app functionality.
 */
class OnboardingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BarcodencryptTheme {
                OnboardingScreen()
            }
        }
    }
}
