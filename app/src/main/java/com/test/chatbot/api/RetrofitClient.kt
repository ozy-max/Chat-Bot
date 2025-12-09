package com.test.chatbot.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private const val CLAUDE_BASE_URL = "https://api.anthropic.com/"
    private const val YANDEX_BASE_URL = "https://llm.api.cloud.yandex.net/"
    
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }
    
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    
    // Claude API
    private val claudeRetrofit = Retrofit.Builder()
        .baseUrl(CLAUDE_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    
    val claudeApiService: ClaudeApiService = claudeRetrofit.create(ClaudeApiService::class.java)
    
    // YandexGPT API
    private val yandexRetrofit = Retrofit.Builder()
        .baseUrl(YANDEX_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    
    val yandexGptApiService: YandexGptApiService = yandexRetrofit.create(YandexGptApiService::class.java)
}

