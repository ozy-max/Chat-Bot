package com.test.chatbot

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.test.chatbot.presentation.ChatScreen
import com.test.chatbot.presentation.ChatViewModel
import com.test.chatbot.presentation.ChatViewModelFactory
import com.test.chatbot.presentation.SupportChatScreen
import com.test.chatbot.presentation.SupportChatViewModel
import com.test.chatbot.presentation.OnboardingScreen
import com.test.chatbot.ui.theme.ChatBotTheme
import com.test.chatbot.utils.DemoDocsInitializer
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.test.chatbot.data.UserPreferences
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

class MainActivity : ComponentActivity() {
    
    private lateinit var viewModel: ChatViewModel
    private lateinit var supportViewModel: SupportChatViewModel
    private lateinit var userPreferences: UserPreferences
    
    // Launcher для запроса разрешения на уведомления
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Инициализируем UserPreferences
        userPreferences = UserPreferences(applicationContext)
        
        // Запрашиваем разрешение на уведомления для Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        // Используем Factory для передачи PreferencesRepository
        val factory = ChatViewModelFactory(applicationContext)
        viewModel = ViewModelProvider(this, factory)[ChatViewModel::class.java]
        
        // Создаем ViewModel для support чата
        supportViewModel = SupportChatViewModel(applicationContext)
        
        // Загружаем демо-документы при первом запуске
        lifecycleScope.launch {
            val demoDocsInitializer = DemoDocsInitializer(applicationContext)
            demoDocsInitializer.initializeDemoDocsIfNeeded()
        }
        
        setContent {
            ChatBotTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val uiState by viewModel.uiState.collectAsState()
                    val navController = rememberNavController()
                    
                    // Определяем стартовый экран в зависимости от onboarding
                    val startDestination = remember {
                        if (userPreferences.isOnboardingCompleted) "chat" else "onboarding"
                    }
                    
                    // Показываем загрузку пока настройки не загружены
                    if (!uiState.isSettingsLoaded && userPreferences.isOnboardingCompleted) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        // Navigation между экранами
                        NavHost(
                            navController = navController,
                            startDestination = startDestination
                        ) {
                            // Экран онбординга (первый запуск)
                            composable("onboarding") {
                                OnboardingScreen(
                                    onComplete = { firstName, lastName, email ->
                                        // Сохраняем данные пользователя
                                        userPreferences.firstName = firstName
                                        userPreferences.lastName = lastName
                                        userPreferences.email = email
                                        userPreferences.isOnboardingCompleted = true
                                        
                                        // Переходим на главный экран
                                        navController.navigate("chat") {
                                            popUpTo("onboarding") { inclusive = true }
                                        }
                                    }
                                )
                            }
                            
                            // Главный экран чата
                            composable("chat") {
                                ChatScreen(
                                    uiState = uiState,
                                    onUiEvent = viewModel::onUiEvent,
                                    onNavigateToSupport = {
                                        navController.navigate("support_chat")
                                    }
                                )
                            }
                            
                            // Экран чата поддержки
                            composable("support_chat") {
                                SupportChatScreen(
                                    viewModel = supportViewModel,
                                    onNavigateBack = {
                                        navController.popBackStack()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Сохраняем summary при сворачивании приложения
     */
    override fun onPause() {
        super.onPause()
        if (::viewModel.isInitialized) {
            viewModel.onAppPause()
        }
    }
    
    /**
     * Сохраняем summary при закрытии/остановке приложения
     */
    override fun onStop() {
        super.onStop()
        if (::viewModel.isInitialized) {
            viewModel.onAppPause()
        }
    }
}
