package com.shankaravam.festival.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shankaravam.festival.ShankaRavamApp
import com.shankaravam.festival.di.AppContainer

@Composable
fun rememberContainer(): AppContainer {
    val context = LocalContext.current
    return remember {
        (context.applicationContext as ShankaRavamApp).container
    }
}

/** Manual ViewModel factory — no Hilt in G1–G5 by design. */
inline fun <reified VM : ViewModel> factory(crossinline create: () -> VM): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
    }

@Composable
inline fun <reified VM : ViewModel> containerViewModel(
    noinline create: (AppContainer) -> VM
): VM {
    val container = rememberContainer()
    return viewModel(modelClass = VM::class.java, factory = factory { create(container) })
}
