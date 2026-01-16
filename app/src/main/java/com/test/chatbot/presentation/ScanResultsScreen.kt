package com.test.chatbot.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Экран результатов сканирования проекта
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanResultsScreen(
    onBackClick: () -> Unit,
    onTasksCreated: (Int) -> Unit // Callback с количеством задач
) {
    val context = LocalContext.current
    val viewModel: ScanResultsViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return ScanResultsViewModel(context) as T
            }
        }
    )
    
    val uiState by viewModel.uiState.collectAsState()
    
    // Автоматически запускаем сканирование при открытии
    LaunchedEffect(Unit) {
        viewModel.startScan()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Результаты сканирования") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isScanning -> {
                    // Индикатор загрузки
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            uiState.scanningStatus.ifEmpty { "🔍 Сканирование проекта..." },
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            when {
                                uiState.scanningStatus.contains("Индексация") -> "Шаг 1/2: Индексируем Kotlin файлы..."
                                uiState.scanningStatus.contains("Сканирование") -> "Шаг 2/2: Анализируем код через RAG..."
                                else -> "Это может занять 60-90 секунд"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                uiState.error != null -> {
                    // Ошибка
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "❌ Ошибка",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            uiState.error ?: "Неизвестная ошибка",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.startScan() }) {
                            Text("Попробовать снова")
                        }
                    }
                }
                
                uiState.tasks.isEmpty() && !uiState.isScanning -> {
                    // Нет задач
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "✅",
                            fontSize = 64.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Проект в отличном состоянии!",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            "Проблем не найдено",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                else -> {
                    // Список задач
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Заголовок со статистикой
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    "📊 Найдено проблем: ${uiState.tasks.size}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                val selectedCount = uiState.tasks.count { it.isSelected }
                                val highCount = uiState.tasks.count { it.priority == "high" }
                                val mediumCount = uiState.tasks.count { it.priority == "medium" }
                                val lowCount = uiState.tasks.count { it.priority == "low" }
                                
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    if (highCount > 0) {
                                        Text("🔴 $highCount", fontSize = 12.sp)
                                    }
                                    if (mediumCount > 0) {
                                        Text("🟡 $mediumCount", fontSize = 12.sp)
                                    }
                                    if (lowCount > 0) {
                                        Text("⚪ $lowCount", fontSize = 12.sp)
                                    }
                                }
                                
                                Text(
                                    "Выбрано: $selectedCount из ${uiState.tasks.size}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        
                        // Список задач
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            itemsIndexed(uiState.tasks) { index, task ->
                                TaskCard(
                                    task = task,
                                    onToggleSelection = { viewModel.toggleTaskSelection(index) },
                                    onPriorityChange = { newPriority -> 
                                        viewModel.changeTaskPriority(index, newPriority)
                                    }
                                )
                            }
                        }
                        
                        // Кнопка создания
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shadowElevation = 8.dp
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Button(
                                    onClick = { 
                                        viewModel.createSelectedTasks { totalTasks ->
                                            onTasksCreated(totalTasks)
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp),
                                    enabled = !uiState.isCreating && uiState.tasks.any { it.isSelected },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    if (uiState.isCreating) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    } else {
                                        val selectedCount = uiState.tasks.count { it.isSelected }
                                        Text(
                                            "Создать задачи ($selectedCount)",
                                            fontSize = 18.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskCard(
    task: ScanTask,
    onToggleSelection: () -> Unit,
    onPriorityChange: (String) -> Unit
) {
    var showPriorityMenu by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (task.isSelected) 
                MaterialTheme.colorScheme.surfaceVariant 
            else 
                MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Checkbox
            Checkbox(
                checked = task.isSelected,
                onCheckedChange = { onToggleSelection() },
                modifier = Modifier.padding(end = 8.dp)
            )
            
            // Контент
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Заголовок
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val priorityIcon = when (task.priority) {
                        "high" -> "🔴"
                        "medium" -> "🟡"
                        else -> "⚪"
                    }
                    val categoryIcon = when (task.category) {
                        "bug" -> "🐛"
                        "security" -> "🔒"
                        "refactor" -> "♻️"
                        "docs" -> "📝"
                        else -> "💡"
                    }
                    
                    Text(
                        "$priorityIcon $categoryIcon",
                        fontSize = 16.sp
                    )
                    
                    Text(
                        task.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Описание
                Text(
                    task.description.take(150) + if (task.description.length > 150) "..." else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                if (task.file != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "📄 ${task.file}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Приоритет (кликабельный)
                Box {
                    Surface(
                        modifier = Modifier.clickable { showPriorityMenu = true },
                        shape = RoundedCornerShape(4.dp),
                        color = when (task.priority) {
                            "high" -> Color(0xFFFFEBEE)
                            "medium" -> Color(0xFFFFF9C4)
                            else -> Color(0xFFF5F5F5)
                        }
                    ) {
                        Text(
                            task.priority.uppercase(),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (task.priority) {
                                "high" -> Color(0xFFC62828)
                                "medium" -> Color(0xFFF57F17)
                                else -> Color(0xFF616161)
                            }
                        )
                    }
                    
                    DropdownMenu(
                        expanded = showPriorityMenu,
                        onDismissRequest = { showPriorityMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("🔴 HIGH") },
                            onClick = {
                                onPriorityChange("high")
                                showPriorityMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("🟡 MEDIUM") },
                            onClick = {
                                onPriorityChange("medium")
                                showPriorityMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("⚪ LOW") },
                            onClick = {
                                onPriorityChange("low")
                                showPriorityMenu = false
                            }
                        )
                    }
                }
            }
        }
    }
}
