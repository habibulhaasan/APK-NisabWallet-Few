package com.hasan.nisabwallet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.hasan.nisabwallet.navigation.NisabWalletRootNav
import com.hasan.nisabwallet.ui.theme.NisabWalletTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NisabWalletRoot()
        }
    }
}

@Composable
fun NisabWalletRoot() {
    NisabWalletTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            NisabWalletRootNav()
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun NisabWalletAppPreview() {
    NisabWalletRoot()
}