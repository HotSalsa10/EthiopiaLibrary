package com.ethiopialibrary.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ethiopialibrary.app.data.LibraryRepository
import com.ethiopialibrary.app.dates.CalendarMode
import com.ethiopialibrary.app.ui.screens.BookDetailScreen
import com.ethiopialibrary.app.ui.screens.BooksScreen
import com.ethiopialibrary.app.ui.screens.CheckoutScreen
import com.ethiopialibrary.app.ui.screens.CurrentlyOutScreen
import com.ethiopialibrary.app.ui.screens.DashboardScreen
import com.ethiopialibrary.app.ui.screens.MemberDetailScreen
import com.ethiopialibrary.app.ui.screens.MembersScreen
import com.ethiopialibrary.app.ui.screens.ReturnScreen
import com.ethiopialibrary.app.ui.screens.SettingsScreen
import com.ethiopialibrary.app.ui.screens.StatisticsScreen

@Composable
fun LibraryNavHost(repo: LibraryRepository) {
    val nav = rememberNavController()
    val factory = remember(repo) { LibraryVmFactory(repo) }
    val back: () -> Unit = { nav.popBackStack() }
    val calendarMode by repo.calendarModeFlow().collectAsStateWithLifecycle(CalendarMode.DUAL)

    CompositionLocalProvider(LocalCalendarMode provides calendarMode) {
        NavHost(navController = nav, startDestination = "dashboard") {
        composable("dashboard") {
            DashboardScreen(
                vm = viewModel(factory = factory),
                repo = repo,
                onNavigate = { route -> nav.navigate(route) },
            )
        }
        composable("books") {
            BooksScreen(
                vm = viewModel(factory = factory),
                repo = repo,
                onOpenBook = { id -> nav.navigate("book/$id") },
                onBack = back,
            )
        }
        composable("book/{id}") { entry ->
            BookDetailScreen(
                repo = repo,
                bookId = requireNotNull(entry.arguments?.getString("id")),
                onBack = back,
            )
        }
        composable("members") {
            MembersScreen(
                vm = viewModel(factory = factory),
                repo = repo,
                onOpenMember = { id -> nav.navigate("member/$id") },
                onBack = back,
            )
        }
        composable("member/{id}") { entry ->
            MemberDetailScreen(
                repo = repo,
                memberId = requireNotNull(entry.arguments?.getString("id")),
                onBack = back,
            )
        }
        composable("checkout") {
            CheckoutScreen(vm = viewModel(factory = factory), onBack = back)
        }
        composable("return") {
            ReturnScreen(vm = viewModel(factory = factory), onBack = back)
        }
        composable("loans") {
            CurrentlyOutScreen(vm = viewModel(factory = factory), onBack = back)
        }
        composable("settings") {
            SettingsScreen(vm = viewModel(factory = factory), repo = repo, onBack = back)
        }
        composable("statistics") {
            StatisticsScreen(vm = viewModel(factory = factory), onBack = back)
        }
        }
    }
}
