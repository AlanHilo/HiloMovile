package com.example.hiloapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

sealed class Screen(val route: String) {
    object Index : Screen("index")
    object Login : Screen("login")
    object Register : Screen("register")
    object Pairing : Screen("pairing")
    object Home : Screen("home")
    object ChatDetail : Screen("chat_detail/{chatId}") {
        fun createRoute(chatId: String) = "chat_detail/$chatId"
    }
}

@Composable
fun HiloNavigation(
    authViewModel: AuthViewModel = viewModel(),
    chatsViewModel: ChatsViewModel = viewModel(),
    messagesViewModel: MessagesViewModel = viewModel(),
    aiChatViewModel: AiChatViewModel = viewModel()
) {
    val navController = rememberNavController()
    val isLoggedIn by authViewModel.isLoggedIn.collectAsState()
    val isWhatsAppReady by authViewModel.isWhatsAppReady.collectAsState()
    val isCheckingSession by authViewModel.isCheckingSession.collectAsState()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Centralized routing based on auth state changes
    LaunchedEffect(isCheckingSession, isLoggedIn, isWhatsAppReady) {
        if (!isCheckingSession) {
            if (isLoggedIn) {
                if (isWhatsAppReady) {
                    if (currentRoute != Screen.Home.route && !currentRoute?.startsWith("chat_detail")!!) {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                } else {
                    if (currentRoute != Screen.Pairing.route) {
                        navController.navigate(Screen.Pairing.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }
            } else {
                if (currentRoute == Screen.Home.route || currentRoute == Screen.Pairing.route || currentRoute?.startsWith("chat_detail") == true) {
                    navController.navigate(Screen.Index.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
        }
    }

    NavHost(navController = navController, startDestination = Screen.Index.route) {
        composable(Screen.Index.route) {
            if (isCheckingSession) {
                // Splash / loading screen
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.Black)
                }
            } else {
                IndexScreen(
                    onLoginClick = { navController.navigate(Screen.Login.route) }
                )
            }
        }
        
        composable(Screen.Login.route) {
            LoginScreen(
                authViewModel = authViewModel,
                onLoginSuccess = { ready ->
                    if (ready) {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Index.route) { inclusive = true }
                        }
                    } else {
                        navController.navigate(Screen.Pairing.route) {
                            popUpTo(Screen.Index.route) { inclusive = true }
                        }
                    }
                },
                onRegisterClick = { navController.navigate(Screen.Register.route) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Register.route) {
            RegisterScreen(
                authViewModel = authViewModel,
                onRegisterSuccess = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Register.route) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Pairing.route) {
            PairingScreen(
                authViewModel = authViewModel,
                onPairSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Pairing.route) { inclusive = true }
                    }
                },
                onBack = {
                    authViewModel.logout()
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            val chats by chatsViewModel.chats.collectAsState()
            val allChats by chatsViewModel.allChats.collectAsState()
            val isLoading by chatsViewModel.isLoading.collectAsState()

            // Start and stop polling based on screen lifecycle
            DisposableEffect(Unit) {
                chatsViewModel.startPolling()
                onDispose {
                    chatsViewModel.stopPolling()
                }
            }

            HomeScreen(
                chats = chats,
                allChats = allChats,
                isLoading = isLoading,
                aiChatViewModel = aiChatViewModel,
                onChatClick = { chat ->
                    navController.navigate(Screen.ChatDetail.createRoute(chat.id))
                },
                onToggleMonitor = { chat, isMonitored ->
                    chatsViewModel.toggleMonitoring(chat, isMonitored) { }
                },
                onLoadAllChats = {
                    chatsViewModel.loadAllChats()
                },
                onLogout = {
                    authViewModel.logout()
                    chatsViewModel.clear()
                    messagesViewModel.clear()
                    aiChatViewModel.clear()
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Screen.ChatDetail.route,
            arguments = listOf(navArgument("chatId") { type = NavType.StringType })
        ) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString("chatId") ?: ""
            val chats by chatsViewModel.chats.collectAsState()
            val chat = chats.find { it.id == chatId } ?: Chat(
                id = chatId,
                contactName = "Grupo",
                lastMessage = "",
                timestamp = "",
                unreadCount = 0,
                avatarUrl = HiloApi.getAvatarUrl(chatId),
                isMonitored = false,
                sessionId = null
            )

            DisposableEffect(chatId) {
                onDispose {
                    messagesViewModel.stopPolling()
                }
            }

            ChatDetailScreen(
                chat = chat,
                messagesViewModel = messagesViewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
