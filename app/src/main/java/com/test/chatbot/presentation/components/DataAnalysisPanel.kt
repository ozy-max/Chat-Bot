package com.test.chatbot.presentation.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.test.chatbot.ui.theme.AccentYellow
import com.test.chatbot.ui.theme.PureBlack

/**
 * Панель для анализа данных (CSV, JSON, LOG файлы)
 */
@Composable
fun DataAnalysisPanel(
    isVisible: Boolean,
    currentAnalysis: com.test.chatbot.data.DataAnalyzer.AnalysisResult?,
    onFileSelected: (Uri) -> Unit,
    onClose: () -> Unit,
    onAskQuestion: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var questionText by remember { mutableStateOf("") }
    
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onFileSelected(it) }
    }
    
    AnimatedVisibility(
        visible = isVisible,
        enter = expandVertically(),
        exit = shrinkVertically(),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            color = PureBlack,
            shape = RoundedCornerShape(12.dp),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Заголовок
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "📊",
                            fontSize = 24.sp
                        )
                        Text(
                            text = "Анализ данных",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentYellow
                        )
                    }
                    
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1A1A1A))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Закрыть",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                if (currentAnalysis == null) {
                    // Кнопка загрузки файла
                    Button(
                        onClick = { filePickerLauncher.launch("*/*") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentYellow.copy(alpha = 0.2f),
                            contentColor = AccentYellow
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Upload,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Загрузить файл (CSV, JSON, LOG)")
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Загрузите файл для анализа данных с помощью AI",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                } else {
                    // Информация о файле
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1A1A1A))
                            .border(1.dp, AccentYellow.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(
                                text = "📄 ${currentAnalysis.fileName}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Тип: ${currentAnalysis.fileType} • Строк: ${currentAnalysis.rowCount} • ${formatFileSize(currentAnalysis.size)}",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Поле для вопроса
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = questionText,
                            onValueChange = { questionText = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Задайте вопрос о данных...", fontSize = 13.sp) },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentYellow,
                                unfocusedBorderColor = Color(0xFF333333),
                                focusedContainerColor = Color(0xFF0D0D0D),
                                unfocusedContainerColor = Color(0xFF0D0D0D)
                            ),
                            singleLine = true
                        )
                        
                        Button(
                            onClick = {
                                if (questionText.isNotBlank()) {
                                    onAskQuestion(questionText)
                                    questionText = ""
                                }
                            },
                            enabled = questionText.isNotBlank(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentYellow,
                                contentColor = PureBlack
                            )
                        ) {
                            Text("Спросить", fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Кнопка загрузки нового файла
                    TextButton(
                        onClick = { filePickerLauncher.launch("*/*") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Загрузить другой файл",
                            fontSize = 12.sp,
                            color = AccentYellow
                        )
                    }
                }
            }
        }
    }
}

/**
 * Форматирует размер файла в читаемый формат
 */
private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${bytes / (1024 * 1024)} MB"
    }
}
