package com.test.chatbot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.test.chatbot.presentation.ChatScreen
import com.test.chatbot.presentation.ChatViewModel
import com.test.chatbot.ui.theme.ChatBotTheme

class MainActivity : ComponentActivity() {
    
    private lateinit var viewModel: ChatViewModel
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        viewModel = ViewModelProvider(this)[ChatViewModel::class.java]
        
        setContent {
            ChatBotTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val uiState by viewModel.uiState.collectAsState()
                    
                    ChatScreen(
                        uiState = uiState,
                        onUiEvent = viewModel::onUiEvent
                    )
                }
            }
        }
    }
}
