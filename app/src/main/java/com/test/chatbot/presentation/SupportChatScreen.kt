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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.test.chatbot.presentation.components.MessageItem
import com.test.chatbot.ui.theme.AccentYellow
import com.test.chatbot.ui.theme.PureBlack
import kotlinx.coroutines.launch
import com.test.chatbot.utils.MessageBridge

/**
 * Главный экран с двумя вкладками: Поддержка и Командный ассистент
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportChatScreen(
    viewModel: SupportChatViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToScan: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Поддержка", "Ассистент")
    
    Scaffold(
        topBar = {
            // Шапка с табами
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = PureBlack,
                shadowElevation = 8.dp
            ) {
                Column {
                    // Верхняя панель с кнопкой назад и заголовком
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
                                imageVector = if (selectedTabIndex == 0) Icons.Default.SupportAgent else Icons.Default.SmartToy,
                                contentDescription = null,
                                tint = AccentYellow,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = if (selectedTabIndex == 0) "Служба поддержки" else "Командный ассистент",
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
                            onClick = { 
                                if (selectedTabIndex == 0) {
                                    viewModel.clearChat()
                                }
                            },
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
                    
                    // TabRow
                    TabRow(
                        selectedTabIndex = selectedTabIndex,
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = PureBlack,
                        contentColor = AccentYellow,
                        divider = {}
                    ) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTabIndex == index,
                                onClick = { selectedTabIndex = index },
                                modifier = Modifier
                                    .height(48.dp)
                                    .background(
                                        if (selectedTabIndex == index) Color(0xFF1A1A1A) else PureBlack
                                    )
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (index == 0) Icons.Default.SupportAgent else Icons.Default.SmartToy,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = if (selectedTabIndex == index) AccentYellow else Color(0xFF666666)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = title,
                                        fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 14.sp,
                                        color = if (selectedTabIndex == index) AccentYellow else Color(0xFF666666)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        containerColor = PureBlack
    ) { paddingValues ->
        // Контент выбранной вкладки
        when (selectedTabIndex) {
            0 -> SupportTabContent(
                viewModel = viewModel,
                modifier = Modifier.padding(paddingValues)
            )
            1 -> TeamAssistantTabContent(
                modifier = Modifier.padding(paddingValues),
                onNavigateToScan = onNavigateToScan
            )
        }
    }
}

/**
 * Контент вкладки "Поддержка"
 */
@Composable
fun SupportTabContent(
    viewModel: SupportChatViewModel,
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
    
    Column(
        modifier = modifier.fillMaxSize()
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

/**
 * Контент вкладки "Командный ассистент"
 */
@Composable
fun TeamAssistantTabContent(
    modifier: Modifier = Modifier,
    onNavigateToScan: () -> Unit = {}
) {
    // Получаем context до factory
    val context = LocalContext.current
    
    // Создаем отдельный ViewModel для командного ассистента
    val viewModel: TeamAssistantChatViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return TeamAssistantChatViewModel(
                    context = context
                ) as T
            }
        }
    )
    
    val uiState by viewModel.uiState.collectAsState()
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    // Прослушиваем сообщения из MessageBridge (для возврата с экрана сканирования)
    LaunchedEffect(Unit) {
        MessageBridge.messages.collect { message ->
            viewModel.addBotMessage(message)
        }
    }
    
    // Автоскролл при новых сообщениях
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(uiState.messages.size - 1)
            }
        }
    }
    
    Column(
        modifier = modifier.fillMaxSize()
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
                                text = "Анализирую...",
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
                        text = "• /tasks - все задачи\n" +
                               "• /tasks high - высокий приоритет\n" +
                               "• /create_task <описание> - создать задачу\n" +
                               "• /sync - синхронизация с Todoist\n" +
                               "• /project_status - статус проекта\n" +
                               "• /recommend - рекомендации\n" +
                               "• /help - полная справка",
                        fontSize = 11.sp,
                        color = Color(0xFFAAAAAA),
                        lineHeight = 16.sp
                    )
                }
            }
        }
        
        // Кнопка сканирования проекта
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF1A1A1A),
            shadowElevation = 4.dp
        ) {
            Button(
                onClick = onNavigateToScan,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentYellow,
                    contentColor = PureBlack
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Сканировать",
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    "🔍 Сканировать проект",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
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
                            "Спросите о проекте или задайте команду...",
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
