package com.test.chatbot.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SupportAgent
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
import com.test.chatbot.presentation.components.MessageItem
import com.test.chatbot.ui.theme.AccentYellow
import com.test.chatbot.ui.theme.PureBlack
import kotlinx.coroutines.launch

/**
 * Экран чата со службой поддержки
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportChatScreen(
    viewModel: SupportChatViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    // Автоскролл при новых сообщениях
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(uiState.messages.size - 1)
            }
        }
    }
    
    Scaffold(
        topBar = {
            // Шапка чата поддержки
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = PureBlack,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Кнопка назад
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1A1A1A))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
                            tint = AccentYellow
                        )
                    }
                    
                    // Заголовок с иконкой
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.SupportAgent,
                            contentDescription = null,
                            tint = AccentYellow,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Служба поддержки",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = AccentYellow
                            )
                            Text(
                                text = "AI Assistant",
                                fontSize = 12.sp,
                                color = AccentYellow.copy(alpha = 0.6f)
                            )
                        }
                    }
                    
                    // Кнопка очистить чат
                    IconButton(
                        onClick = { viewModel.clearChat() },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1A1A1A))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Очистить чат",
                            tint = AccentYellow
                        )
                    }
                }
            }
        },
        containerColor = PureBlack
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Список сообщений
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(uiState.messages) { message ->
                    MessageItem(message = message)
                }
                
                // Индикатор загрузки
                if (uiState.isLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = AccentYellow,
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = "Ищу ответ...",
                                    color = AccentYellow.copy(alpha = 0.8f),
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
            
            // Подсказка по командам (если чат пустой или только приветствие)
            if (uiState.messages.size <= 1) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    color = Color(0xFF1A1A1A),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "💡 Быстрые команды:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = AccentYellow
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "• /tickets - мои тикеты\n" +
                                   "• /ticket <описание> - создать тикет\n" +
                                   "• /status TICKET-001 - детали тикета\n" +
                                   "• /user - моя информация\n" +
                                   "• /stats - статистика",
                            fontSize = 11.sp,
                            color = Color(0xFFAAAAAA),
                            lineHeight = 16.sp
                        )
                    }
                }
            }
            
            // Поле ввода
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = PureBlack,
                shadowElevation = 12.dp
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
                                "Задайте ваш вопрос...",
                                color = Color(0xFF666666)
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
                            unfocusedTextColor = Color.White,
                            disabledTextColor = Color(0xFF666666)
                        )
                    )
                    
                    // Кнопка отправить
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        AccentYellow,
                                        AccentYellow.copy(alpha = 0.8f)
                                    )
                                )
                            )
                            .then(
                                if (!uiState.isLoading && messageText.isNotBlank()) {
                                    Modifier.clickableWithoutRipple {
                                        viewModel.sendMessage(messageText)
                                        messageText = ""
                                    }
                                } else {
                                    Modifier
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
 * Модификатор для клика без ripple эффекта
 */
private fun Modifier.clickableWithoutRipple(onClick: () -> Unit): Modifier {
    return this.then(
        clickable(
            indication = null,
            interactionSource = MutableInteractionSource(),
            onClick = onClick
        )
    )
}
