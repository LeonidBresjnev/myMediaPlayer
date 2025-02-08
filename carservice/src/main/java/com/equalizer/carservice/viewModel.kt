package com.equalizer.carservice

import androidx.car.app.Screen
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStoreOwner
import org.koin.androidx.viewmodel.ext.android.getLazyViewModelForClass
import org.koin.core.parameter.ParametersDefinition

//import org.koin.androidx.viewmodel.ext.android.getLazyViewModelForClass
//import org.koin.core.parameter.ParametersDefinition

inline fun <reified T : ViewModel> Screen.viewModel(
    viewModelStoreOwner: ViewModelStoreOwner = getViewModelStoreOwner(),
    noinline parameters: ParametersDefinition? = null,
): Lazy<T> {
    return getLazyViewModelForClass(
        clazz = T::class,
        owner = viewModelStoreOwner,
        parameters = parameters,
    )
}