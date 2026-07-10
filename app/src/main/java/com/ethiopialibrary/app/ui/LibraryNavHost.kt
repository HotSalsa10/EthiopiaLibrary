package com.ethiopialibrary.app.ui

import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
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
    // Fallback focus holder: the dashboard (start destination) never
    // autofocuses anything by design, so without this there can be no focused
    // node in the tree at all on a cold launch (and again after every Esc back
    // to the dashboard) - a known Compose scenario where onPreviewKeyEvent can
    // fail to fire for lack of a focus path to dispatch along. Purely a
    // fallback: any screen's own autofocused field steals focus from this the
    // moment it mounts, which is the desired behavior.
    val rootFocusRequester = remember { FocusRequester() }

    // Global keyboard scaffold: Escape pops the back stack (no-op at the
    // dashboard root, where there is no previous destination), and Ctrl+O/R/L
    // jump straight to the three most-used desk actions. A Material 3
    // AlertDialog opens in its own Android Dialog window, so key events while
    // one is showing generally won't reach this handler - expected, not a bug.
    // Each Ctrl+ branch first checks it isn't already on the target route:
    // this guards against hardware key-repeat stacking duplicate back-stack
    // entries while the combo is held, and against a stray repeat press
    // silently resetting an in-progress checkout/return once already there.
    val handleShortcut: (KeyEvent) -> Boolean = handler@{ event ->
        if (event.type != KeyEventType.KeyDown) return@handler false
        when {
            event.key == Key.Escape -> {
                if (nav.previousBackStackEntry != null) nav.popBackStack()
                true
            }
            event.isCtrlPressed && event.key == Key.O -> {
                if (nav.currentDestination?.route != "checkout") {
                    nav.navigate("checkout") { launchSingleTop = true }
                }
                true
            }
            event.isCtrlPressed && event.key == Key.R -> {
                if (nav.currentDestination?.route != "return") {
                    nav.navigate("return") { launchSingleTop = true }
                }
                true
            }
            event.isCtrlPressed && event.key == Key.L -> {
                // currentDestination.route is the registered route pattern
                // ("loans?filter={filter}"), not an instantiated path with the
                // actual filter value substituted in.
                if (nav.currentDestination?.route != "loans?filter={filter}") {
                    nav.navigate("loans") { launchSingleTop = true }
                }
                true
            }
            else -> false
        }
    }

    CompositionLocalProvider(LocalCalendarMode provides calendarMode) {
        NavHost(
            navController = nav,
            startDestination = "dashboard",
            modifier = Modifier
                .focusRequester(rootFocusRequester)
                .focusable()
                .onPreviewKeyEvent(handleShortcut),
        ) {
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
        composable(
            "loans?filter={filter}",
            arguments = listOf(navArgument("filter") { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) { entry ->
            val filter = entry.arguments?.getString("filter")
            CurrentlyOutScreen(vm = viewModel(factory = factory), onBack = back, initialFilter = filter)
        }
        composable("settings") {
            SettingsScreen(vm = viewModel(factory = factory), repo = repo, onBack = back)
        }
        composable("statistics") {
            StatisticsScreen(vm = viewModel(factory = factory), onBack = back)
        }
        }
    }
    LaunchedEffect(Unit) {
        rootFocusRequester.requestFocus()
    }
}
