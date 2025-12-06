package com.example.billingapp_2.ui.components

import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.*

@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    val backCallback = remember {
        object : OnBackPressedCallback(enabled) {
            override fun handleOnBackPressed() { onBack() }
        }
    }
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    DisposableEffect(backDispatcher) {
        backDispatcher?.addCallback(backCallback)
        onDispose { backCallback.remove() }
    }
}