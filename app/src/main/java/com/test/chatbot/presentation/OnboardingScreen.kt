package com.test.chatbot.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun OnboardingScreen(
    onComplete: (firstName: String, lastName: String, email: String?) -> Unit
) {
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val isFormValid = firstName.isNotBlank() && lastName.isNotBlank()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A1A)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Иконка приветствия
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = Color(0xFFFFD700)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Заголовок
            Text(
                text = "Добро пожаловать!",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Подзаголовок
            Text(
                text = "Представьтесь, пожалуйста",
                fontSize = 16.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Поле: Имя
            OutlinedTextField(
                value = firstName,
                onValueChange = { 
                    firstName = it
                    showError = false
                },
                label = { Text("Имя *") },
                placeholder = { Text("Введите ваше имя") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFFFFD700),
                    unfocusedBorderColor = Color.Gray,
                    focusedLabelColor = Color(0xFFFFD700),
                    unfocusedLabelColor = Color.Gray,
                    cursorColor = Color(0xFFFFD700)
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                isError = showError && firstName.isBlank()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Поле: Фамилия
            OutlinedTextField(
                value = lastName,
                onValueChange = { 
                    lastName = it
                    showError = false
                },
                label = { Text("Фамилия *") },
                placeholder = { Text("Введите вашу фамилию") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFFFFD700),
                    unfocusedBorderColor = Color.Gray,
                    focusedLabelColor = Color(0xFFFFD700),
                    unfocusedLabelColor = Color.Gray,
                    cursorColor = Color(0xFFFFD700)
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                isError = showError && lastName.isBlank()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Поле: Email (опционально)
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email (необязательно)") },
                placeholder = { Text("example@mail.com") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFFFFD700),
                    unfocusedBorderColor = Color.Gray,
                    focusedLabelColor = Color(0xFFFFD700),
                    unfocusedLabelColor = Color.Gray,
                    cursorColor = Color(0xFFFFD700)
                ),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { 
                        if (isFormValid) {
                            onComplete(firstName.trim(), lastName.trim(), email.trim().takeIf { it.isNotBlank() })
                        } else {
                            showError = true
                        }
                    }
                )
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Кнопка продолжить
            Button(
                onClick = {
                    if (isFormValid) {
                        onComplete(firstName.trim(), lastName.trim(), email.trim().takeIf { it.isNotBlank() })
                    } else {
                        showError = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFD700),
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(12.dp),
                enabled = isFormValid
            ) {
                Text(
                    text = "Продолжить",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Сообщение об ошибке
            if (showError && !isFormValid) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Пожалуйста, заполните имя и фамилию",
                    color = Color.Red,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Примечание
            Text(
                text = "* Обязательные поля",
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
    }
}
