package com.ethiopialibrary.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ethiopialibrary.app.data.LibraryRepository

/** Manual DI: one repository, six ViewModels - no framework needed at this scale. */
class LibraryVmFactory(private val repo: LibraryRepository) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(DashboardViewModel::class.java) -> DashboardViewModel(repo)
        modelClass.isAssignableFrom(BooksViewModel::class.java) -> BooksViewModel(repo)
        modelClass.isAssignableFrom(MembersViewModel::class.java) -> MembersViewModel(repo)
        modelClass.isAssignableFrom(CheckoutViewModel::class.java) -> CheckoutViewModel(repo)
        modelClass.isAssignableFrom(ReturnViewModel::class.java) -> ReturnViewModel(repo)
        modelClass.isAssignableFrom(SettingsViewModel::class.java) -> SettingsViewModel(repo)
        else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    } as T
}
