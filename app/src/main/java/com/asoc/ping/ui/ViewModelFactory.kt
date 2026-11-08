package com.asoc.ping.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/** Tiny manual factory: `viewModel(factory = vmFactory { MyVm(container) })`. */
@Suppress("UNCHECKED_CAST")
fun <T : ViewModel> vmFactory(make: () -> T): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    override fun <V : ViewModel> create(modelClass: Class<V>): V = make() as V
}
