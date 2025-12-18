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
import com.test.chatbot.presentation.ChatScreen
import com.test.chatbot.presentation.ChatViewModel
import com.test.chatbot.presentation.ChatViewModelFactory
import com.test.chatbot.ui.theme.ChatBotTheme

class MainActivity : ComponentActivity() {
    
    private lateinit var viewModel: ChatViewModel
    
    // Launcher для запроса разрешения на уведомления
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
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
        
        setContent {
            ChatBotTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val uiState by viewModel.uiState.collectAsState()
                    
                    // Показываем загрузку пока настройки не загружены
                    if (!uiState.isSettingsLoaded) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        ChatScreen(
                            uiState = uiState,
                            onUiEvent = viewModel::onUiEvent
                        )
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
