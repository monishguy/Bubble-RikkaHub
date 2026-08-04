package com.bubble.rikkahub.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bubble.rikkahub.data.preferences.AppPreferences
import com.bubble.rikkahub.di.AppContainer
import com.bubble.rikkahub.domain.model.ListTheme
import com.bubble.rikkahub.domain.model.NavTransitionMode
import com.bubble.rikkahub.ui.screens.chat.ChatScreen
import com.bubble.rikkahub.ui.screens.chat.ChatViewModel
import com.bubble.rikkahub.ui.screens.conversations.ConversationListScreen
import com.bubble.rikkahub.ui.screens.conversations.ConversationListViewModel
import com.bubble.rikkahub.ui.screens.settings.SettingsScreen
import com.bubble.rikkahub.ui.screens.settings.SettingsViewModel

object Routes {
    const val CONVERSATIONS = "conversations"
    const val SETTINGS = "settings"
    const val CHAT = "chat/{conversationId}"
    fun chat(id: String) = "chat/$id"
}

private data class BottomNavItem(
    val route: String, val label: String,
    val selectedIcon: ImageVector, val unselectedIcon: ImageVector
)

private val bottomNavItems = listOf(
    BottomNavItem(Routes.CONVERSATIONS, "聊天", Icons.AutoMirrored.Filled.Chat, Icons.AutoMirrored.Outlined.Chat),
    BottomNavItem(Routes.SETTINGS, "设置", Icons.Filled.Settings, Icons.Outlined.Settings)
)

@Composable
fun MainNavGraph(appContainer: AppContainer, initialConversationId: String? = null) {
    val navController = rememberNavController()
    val listTheme by appContainer.appPreferences.listTheme.collectAsStateWithLifecycle(ListTheme.FLAT)
    val navMode by appContainer.appPreferences.navTransitionMode
        .collectAsStateWithLifecycle(NavTransitionMode.SLIDE)
    val navDuration by appContainer.appPreferences.navTransitionDurationMs
        .collectAsStateWithLifecycle(AppPreferences.DEFAULT_NAV_TRANSITION_DURATION)
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isOnChat = currentRoute == Routes.CHAT

    val enter = when (navMode) {
        NavTransitionMode.SLIDE -> slideInHorizontally(tween(navDuration)) { it }
        NavTransitionMode.FADE -> fadeIn(tween(navDuration))
        NavTransitionMode.NONE -> EnterTransition.None
    }
    val exit = when (navMode) {
        NavTransitionMode.SLIDE -> slideOutHorizontally(tween(navDuration)) { -it / 3 }
        NavTransitionMode.FADE -> fadeOut(tween(navDuration))
        NavTransitionMode.NONE -> ExitTransition.None
    }
    val popEnter = when (navMode) {
        NavTransitionMode.SLIDE -> slideInHorizontally(tween(navDuration)) { -it / 3 }
        NavTransitionMode.FADE -> fadeIn(tween(navDuration))
        NavTransitionMode.NONE -> EnterTransition.None
    }
    val popExit = when (navMode) {
        NavTransitionMode.SLIDE -> slideOutHorizontally(tween(navDuration)) { it }
        NavTransitionMode.FADE -> fadeOut(tween(navDuration))
        NavTransitionMode.NONE -> ExitTransition.None
    }

    // When launched from a notification, jump straight into the conversation.
    LaunchedEffect(initialConversationId) {
        if (initialConversationId != null) {
            navController.navigate(Routes.chat(initialConversationId)) {
                popUpTo(Routes.CONVERSATIONS) { saveState = true }
                launchSingleTop = true
            }
        }
    }

    Scaffold(
        bottomBar = {
            if (!isOnChat) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = {
                                if (currentRoute != item.route) {
                                    navController.navigate(item.route) {
                                        popUpTo(Routes.CONVERSATIONS) { saveState = true }
                                        launchSingleTop = true; restoreState = true
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    if (currentRoute == item.route) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.label
                                )
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.CONVERSATIONS,
            modifier = Modifier.padding(
                // Don't pad the top: each screen owns its own TopAppBar (and the status-bar
                // inset). Applying the main scaffold's top inset here caused a large empty
                // strip above every screen's top bar.
                start = innerPadding.calculateStartPadding(LocalLayoutDirection.current),
                end = innerPadding.calculateEndPadding(LocalLayoutDirection.current),
                bottom = innerPadding.calculateBottomPadding()
            ),
            enterTransition = { enter },
            exitTransition = { exit },
            popEnterTransition = { popEnter },
            popExitTransition = { popExit }
        ) {
            composable(Routes.CONVERSATIONS) {
                val listViewModel: ConversationListViewModel = viewModel(
                    factory = viewModelFactory { initializer { appContainer.conversationListViewModel() } }
                )
                ConversationListScreen(
                    viewModel = listViewModel,
                    listTheme = listTheme,
                    onConversationClick = { id -> navController.navigate(Routes.chat(id)) }
                )
            }
            composable(Routes.SETTINGS) {
                val settingsViewModel: SettingsViewModel = viewModel(
                    factory = viewModelFactory { initializer { appContainer.settingsViewModel() } }
                )
                SettingsScreen(
                    viewModel = settingsViewModel,
                    onBack = null
                )
            }
            composable(
                route = Routes.CHAT,
                arguments = listOf(navArgument("conversationId") { type = NavType.StringType })
            ) { entry ->
                val conversationId = entry.arguments?.getString("conversationId") ?: return@composable
                // Scoped to this NavBackStackEntry: survives recompositions, and onCleared()
                // (cancelling the SSE stream) fires when the entry is popped.
                val chatViewModel: ChatViewModel = viewModel(
                    key = "chat_$conversationId",
                    factory = viewModelFactory { initializer { appContainer.chatViewModel() } }
                )
                ChatScreen(
                    viewModel = chatViewModel,
                    conversationId = conversationId,
                    customizationRepository = appContainer.customizationRepository,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
