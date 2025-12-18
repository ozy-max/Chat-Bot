package com.test.chatbot.mcp.server

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class SearchResult(
    val title: String,
    val snippet: String,
    val url: String
)

class WebSearchService {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    
    companion object {
        private const val TAG = "WebSearchService"
        private const val SEARCH_API = "https://html.duckduckgo.com/html/"
    }
    
    suspend fun search(query: String, maxResults: Int = 3): List<SearchResult> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "🔍 Поиск: $query")
            
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val request = Request.Builder()
                .url("$SEARCH_API?q=$encodedQuery")
                .addHeader("User-Agent", "Mozilla/5.0 (Android)")
                .build()
            
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "❌ Ошибка поиска: ${response.code}")
                return@withContext emptyList()
            }
            
            val html = response.body?.string() ?: return@withContext emptyList()
            
            val results = parseSearchResults(html, maxResults)
            Log.i(TAG, "✅ Найдено результатов: ${results.size}")
            
            results
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка поиска: ${e.message}", e)
            emptyList()
        }
    }
    
    private fun parseSearchResults(html: String, maxResults: Int): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        
        try {
            val resultPattern = Regex(
                """<div class="result.*?">.*?<a rel="nofollow" class="result__a" href="(.*?)">(.*?)</a>.*?<a class="result__snippet".*?>(.*?)</a>""",
                RegexOption.DOT_MATCHES_ALL
            )
            
            val matches = resultPattern.findAll(html).take(maxResults)
            
            matches.forEach { match ->
                val url = match.groupValues[1].trim()
                val title = match.groupValues[2]
                    .replace(Regex("<.*?>"), "")
                    .trim()
                val snippet = match.groupValues[3]
                    .replace(Regex("<.*?>"), "")
                    .replace("&quot;", "\"")
                    .replace("&amp;", "&")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .trim()
                
                if (url.isNotEmpty() && title.isNotEmpty()) {
                    results.add(SearchResult(title, snippet, url))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка парсинга: ${e.message}", e)
        }
        
        return results
    }
    
    fun formatResults(results: List<SearchResult>): String {
        if (results.isEmpty()) {
            return "❌ Результаты не найдены"
        }
        
        return buildString {
            append("🔍 Найдено результатов: ${results.size}\n\n")
            results.forEachIndexed { index, result ->
                append("${index + 1}. ${result.title}\n")
                append("   ${result.snippet}\n")
                append("   🔗 ${result.url}\n")
                if (index < results.size - 1) append("\n")
            }
        }
    }
}

