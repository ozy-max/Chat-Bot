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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.*
import androidx.compose.material3.FabPosition
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
import com.test.chatbot.presentation.components.AiFeaturesBottomSheet
import com.test.chatbot.presentation.components.ApiKeyDialog
import com.test.chatbot.presentation.components.ComparisonDialog
import com.test.chatbot.presentation.components.CompressionInfoDialog
import com.test.chatbot.presentation.components.MessageItem
import com.test.chatbot.presentation.components.SettingsDialog
import com.test.chatbot.ui.theme.AccentYellow
import com.test.chatbot.ui.theme.PureBlack
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    uiState: ChatUiState,
    onUiEvent: (ChatUiEvents) -> Unit,
    onCheckOllamaAvailability: (suspend () -> Boolean)? = null,
    onNavigateToSupport: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    // Bottom Sheet state
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )
    
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(uiState.messages.size - 1)
            }
        }
    }
    
    // Отслеживание lifecycle для сохранения памяти при выходе
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    // Сохраняем summary при уходе в фон или закрытии
                    onUiEvent(ChatUiEvents.OnAppPause)
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    // Диалог ввода API ключей
    if (uiState.showApiKeyDialog) {
        ApiKeyDialog(
            currentApiKey = uiState.apiKey,
            currentYandexApiKey = uiState.yandexApiKey,
            currentYandexFolderId = uiState.yandexFolderId,
            currentTodoistToken = uiState.todoistToken,
            selectedProvider = uiState.selectedProvider,
            onSave = { claudeKey, yandexKey, yandexFolderId, todoistToken ->
                onUiEvent(ChatUiEvents.UpdateApiKey(claudeKey))
                onUiEvent(ChatUiEvents.UpdateYandexApiKey(yandexKey))
                onUiEvent(ChatUiEvents.UpdateYandexFolderId(yandexFolderId))
                onUiEvent(ChatUiEvents.UpdateTodoistToken(todoistToken))
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
            currentTaskType = uiState.taskType,
            currentOllamaModel = uiState.ollamaModel,
            currentContextWindow = uiState.contextWindow,
            onTemperatureChange = { onUiEvent(ChatUiEvents.UpdateTemperature(it)) },
            onMaxTokensChange = { onUiEvent(ChatUiEvents.UpdateMaxTokens(it)) },
            onProviderChange = { onUiEvent(ChatUiEvents.UpdateProvider(it)) },
            onTaskTypeChange = { onUiEvent(ChatUiEvents.UpdateTaskType(it)) },
            onOllamaModelChange = { onUiEvent(ChatUiEvents.UpdateOllamaModel(it)) },
            onContextWindowChange = { onUiEvent(ChatUiEvents.UpdateContextWindow(it)) },
            onDismiss = { onUiEvent(ChatUiEvents.DismissSettingsDialog) },
            onCheckOllamaAvailability = onCheckOllamaAvailability
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
    
    // Диалог информации о компрессии
    if (uiState.showCompressionInfo) {
        CompressionInfoDialog(
            compressionState = uiState.compressionState,
            onDismiss = { onUiEvent(ChatUiEvents.DismissCompressionInfo) }
        )
    }
    
    
    // AI Features Bottom Sheet
    if (uiState.showAiFeaturesSheet) {
        AiFeaturesBottomSheet(
            sheetState = sheetState,
            // Компрессия
            compressionSettings = uiState.compressionSettings,
            compressionState = uiState.compressionState,
            isCompressing = uiState.isCompressing,
            onToggleCompression = { onUiEvent(ChatUiEvents.ToggleCompression(it)) },
            onUpdateThreshold = { onUiEvent(ChatUiEvents.UpdateCompressionThreshold(it)) },
            onManualCompress = { onUiEvent(ChatUiEvents.ManualCompress) },
            // Память
            memoryState = uiState.memoryState,
            onToggleMemory = { onUiEvent(ChatUiEvents.ToggleMemory(it)) },
            onClearAllMemories = { onUiEvent(ChatUiEvents.ClearAllMemories) },
            // Статистика токенов
            tokenStats = uiState.tokenStats,
            onDismiss = { onUiEvent(ChatUiEvents.DismissAiFeaturesSheet) }
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
            // Современный жёлто-чёрный тулбар
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
                            // AI Features кнопка с badge
                            AiFeaturesButton(
                                hasActiveFeatures = uiState.compressionState.isEnabled || uiState.memoryState.isEnabled,
                                hasSavedMemory = uiState.memoryState.hasSummary,
                                onClick = { onUiEvent(ChatUiEvents.ShowAiFeaturesSheet) }
                            )
                            // Кнопка анализа данных
                            DataAnalysisButton(
                                isActive = uiState.showDataAnalysisPanel,
                                hasData = uiState.currentDataAnalysis != null,
                                onClick = { 
                                    if (uiState.showDataAnalysisPanel) {
                                        onUiEvent(ChatUiEvents.DismissDataAnalysisPanel)
                                    } else {
                                        onUiEvent(ChatUiEvents.ShowDataAnalysisPanel)
                                    }
                                }
                            )
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
        },
        floatingActionButton = {
            // FAB для перехода в чат поддержки
            FloatingActionButton(
                onClick = onNavigateToSupport,
                containerColor = AccentYellow,
                contentColor = PureBlack,
                modifier = Modifier
                    .padding(bottom = 80.dp) // Над кнопкой отправки
            ) {
                Icon(
                    imageVector = Icons.Default.SupportAgent,
                    contentDescription = "Служба поддержки",
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        floatingActionButtonPosition = FabPosition.End
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Список сообщений - занимает весь экран
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
            
            // Панель анализа данных
            com.test.chatbot.presentation.components.DataAnalysisPanel(
                isVisible = uiState.showDataAnalysisPanel,
                currentAnalysis = uiState.currentDataAnalysis,
                onFileSelected = { uri -> onUiEvent(ChatUiEvents.AnalyzeFile(uri)) },
                onClose = { onUiEvent(ChatUiEvents.DismissDataAnalysisPanel) },
                onAskQuestion = { question -> onUiEvent(ChatUiEvents.AskDataQuestion(question)) }
            )
            
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

/**
 * Кнопка AI Features с индикатором активных функций
 */
@Composable
private fun AiFeaturesButton(
    hasActiveFeatures: Boolean,
    hasSavedMemory: Boolean,
    onClick: () -> Unit
) {
    Box {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    if (hasActiveFeatures)
                        Color(0xFF4CAF50).copy(alpha = 0.2f)
                    else
                        Color(0xFF1A1A1A)
                )
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = "AI Функции",
                tint = if (hasActiveFeatures) Color(0xFF4CAF50) else Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp)
            )
        }
        
        // Badge если есть сохранённая память
        if (hasSavedMemory) {
            Badge(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp),
                containerColor = Color(0xFF4CAF50)
            ) {
                Text(
                    text = "💾",
                    fontSize = 8.sp
                )
            }
        }
    }
}

@Composable
private fun DataAnalysisButton(
    isActive: Boolean,
    hasData: Boolean,
    onClick: () -> Unit
) {
    Box {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    if (isActive)
                        Color(0xFF2196F3).copy(alpha = 0.2f)
                    else
                        Color(0xFF1A1A1A)
                )
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "📊",
                fontSize = 18.sp,
                color = if (isActive) Color(0xFF2196F3) else Color.White.copy(alpha = 0.7f)
            )
        }
        
        // Badge если есть загруженные данные
        if (hasData) {
            Badge(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp),
                containerColor = Color(0xFF4CAF50)
            ) {
                Text(
                    text = "✓",
                    fontSize = 8.sp
                )
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
