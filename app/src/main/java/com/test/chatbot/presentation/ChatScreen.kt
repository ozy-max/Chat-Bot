package com.test.chatbot.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.test.chatbot.models.Message
import com.test.chatbot.presentation.components.ApiKeyDialog
import com.test.chatbot.presentation.components.MessageItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    uiState: ChatUiState,
    onUiEvent: (ChatUiEvents) -> Unit,
    modifier: Modifier = Modifier
) {
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(uiState.messages.size - 1)
            }
        }
    }
    
    if (uiState.showApiKeyDialog) {
        ApiKeyDialog(
            currentApiKey = uiState.apiKey,
            onDismiss = { onUiEvent(ChatUiEvents.DismissApiKeyDialog) },
            onSave = { newKey -> 
                onUiEvent(ChatUiEvents.UpdateApiKey(newKey))
                onUiEvent(ChatUiEvents.DismissApiKeyDialog)
            }
        )
    }
    
    uiState.error?.let { error ->
        AlertDialog(
            onDismissRequest = { onUiEvent(ChatUiEvents.DismissError) },
            title = { Text("Ошибка") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = { onUiEvent(ChatUiEvents.DismissError) }) {
                    Text("OK")
                }
            }
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Claude AI Чат-бот",
                        fontWeight = FontWeight.Bold
                    ) 
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    // Кнопка "Новый чат"
                    IconButton(onClick = { onUiEvent(ChatUiEvents.ClearChat) }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Новый чат",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    // Кнопка "Настройки API"
                    IconButton(onClick = { onUiEvent(ChatUiEvents.ShowApiKeyDialog) }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Настройки API",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Список сообщений
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(uiState.messages) { message ->
                        MessageItem(message = message)
                    }
                }
                
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(48.dp)
                    )
                }
            }
            
            Surface(
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        placeholder = { Text("Введите сообщение...") },
                        enabled = !uiState.isLoading,
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 4
                    )
                    
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(
                                if (!uiState.isLoading && messageText.isNotBlank()) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            )
                            .clickable(
                                enabled = !uiState.isLoading && messageText.isNotBlank(),
                                onClick = {
                                    if (messageText.isNotBlank()) {
                                        onUiEvent(ChatUiEvents.SendMessage(messageText))
                                        messageText = ""
                                    }
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Отправить",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}

