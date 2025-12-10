package com.test.chatbot.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.test.chatbot.models.AiProvider
import com.test.chatbot.presentation.components.ApiKeyDialog
import com.test.chatbot.presentation.components.ComparisonDialog
import com.test.chatbot.presentation.components.MessageItem
import com.test.chatbot.presentation.components.SettingsDialog
import com.test.chatbot.presentation.components.TokenStatsBar
import com.test.chatbot.ui.theme.AccentYellow
import com.test.chatbot.ui.theme.PureBlack
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
    
    // Диалог ввода API ключей
    if (uiState.showApiKeyDialog) {
        ApiKeyDialog(
            currentApiKey = uiState.apiKey,
            currentYandexApiKey = uiState.yandexApiKey,
            currentYandexFolderId = uiState.yandexFolderId,
            selectedProvider = uiState.selectedProvider,
            onSave = { claudeKey, yandexKey, yandexFolderId ->
                onUiEvent(ChatUiEvents.UpdateApiKey(claudeKey))
                onUiEvent(ChatUiEvents.UpdateYandexApiKey(yandexKey))
                onUiEvent(ChatUiEvents.UpdateYandexFolderId(yandexFolderId))
                onUiEvent(ChatUiEvents.DismissApiKeyDialog)
            },
            onDismiss = { onUiEvent(ChatUiEvents.DismissApiKeyDialog) }
        )
    }
    
    // Диалог настроек (Temperature + Provider)
    if (uiState.showSettingsDialog) {
        SettingsDialog(
            currentTemperature = uiState.temperature,
            currentMaxTokens = uiState.maxTokens,
            currentProvider = uiState.selectedProvider,
            onTemperatureChange = { onUiEvent(ChatUiEvents.UpdateTemperature(it)) },
            onMaxTokensChange = { onUiEvent(ChatUiEvents.UpdateMaxTokens(it)) },
            onProviderChange = { onUiEvent(ChatUiEvents.UpdateProvider(it)) },
            onDismiss = { onUiEvent(ChatUiEvents.DismissSettingsDialog) }
        )
    }
    
    // Диалог сравнения моделей
    if (uiState.showComparisonDialog) {
        ComparisonDialog(
            isComparing = uiState.isComparing,
            comparisonResult = uiState.comparisonResult,
            onDismiss = { onUiEvent(ChatUiEvents.DismissComparisonDialog) },
            onCompare = { query -> onUiEvent(ChatUiEvents.CompareModels(query)) },
            onClearResult = { onUiEvent(ChatUiEvents.ClearComparisonResult) }
        )
    }
    
    // Диалог ошибки
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
            // Современный жёлто-чёрный тулбар (двухуровневый)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = PureBlack,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    // Всё в одну строку: модель + температура + кнопки
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Левая часть: модель + температура
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Бейдж модели
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(
                                                AccentYellow.copy(alpha = 0.2f),
                                                AccentYellow.copy(alpha = 0.05f)
                                            )
                                        )
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = AccentYellow.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = uiState.selectedProvider.displayName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = AccentYellow
                                )
                            }
                            
                            // Бейдж температуры
                            val tempIcon = when {
                                uiState.temperature <= 0.3 -> "❄️"
                                uiState.temperature <= 0.7 -> "🎯"
                                else -> "🔥"
                            }
                            
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(
                                                AccentYellow.copy(alpha = 0.2f),
                                                AccentYellow.copy(alpha = 0.05f)
                                            )
                                        )
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = AccentYellow.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(text = tempIcon, fontSize = 12.sp)
                                    Text(
                                        text = "${uiState.temperature}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = AccentYellow
                                    )
                                }
                            }
                        }
                        
                        // Правая часть: кнопки
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SmallActionButton(
                                icon = Icons.Default.Compare,
                                tint = AccentYellow,
                                onClick = { onUiEvent(ChatUiEvents.ShowComparisonDialog) }
                            )
                            SmallActionButton(
                                icon = Icons.Default.Refresh,
                                tint = Color.White.copy(alpha = 0.7f),
                                onClick = { onUiEvent(ChatUiEvents.ClearChat) }
                            )
                            SmallActionButton(
                                icon = Icons.Default.Key,
                                tint = Color.White.copy(alpha = 0.7f),
                                onClick = { onUiEvent(ChatUiEvents.ShowApiKeyDialog) }
                            )
                            SmallActionButton(
                                icon = Icons.Default.Settings,
                                tint = AccentYellow,
                                isAccent = true,
                                onClick = { onUiEvent(ChatUiEvents.ShowSettingsDialog) }
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    // Жёлтая линия-акцент внизу
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        AccentYellow,
                                        AccentYellow.copy(alpha = 0.3f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Панель статистики токенов (sticky под TopAppBar)
            TokenStatsBar(stats = uiState.tokenStats)
            
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
            
            // Поле ввода сообщения
            Surface(
                shadowElevation = 8.dp,
                color = PureBlack
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { 
                            Text(
                                "Введите сообщение...",
                                color = Color.White.copy(alpha = 0.3f)
                            ) 
                        },
                        enabled = !uiState.isLoading,
                        shape = RoundedCornerShape(20.dp),
                        maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentYellow,
                            unfocusedBorderColor = Color(0xFF333333),
                            focusedContainerColor = Color(0xFF0D0D0D),
                            unfocusedContainerColor = Color(0xFF0D0D0D),
                            cursorColor = AccentYellow,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(
                                if (!uiState.isLoading && messageText.isNotBlank()) 
                                    AccentYellow
                                else 
                                    AccentYellow.copy(alpha = 0.3f)
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
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Отправить",
                            tint = PureBlack
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onClick: () -> Unit,
    isAccent: Boolean = false
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(
                if (isAccent) AccentYellow.copy(alpha = 0.15f)
                else Color(0xFF1A1A1A)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
    }
}
