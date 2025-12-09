package com.test.chatbot.api

import com.test.chatbot.models.YandexGptRequest
import com.test.chatbot.models.YandexGptResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

interface YandexGptApiService {
    @POST("foundationModels/v1/completion")
    @Headers("Content-Type: application/json")
    suspend fun sendMessage(
        @Header("Authorization") authorization: String,
        @Body request: YandexGptRequest
    ): Response<YandexGptResponse>
}

