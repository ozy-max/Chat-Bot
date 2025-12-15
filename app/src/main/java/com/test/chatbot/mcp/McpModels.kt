package com.test.chatbot.mcp

import com.google.gson.annotations.SerializedName

/**
 * MCP (Model Context Protocol) модели данных
 * Основан на JSON-RPC 2.0
 */

// ===== JSON-RPC базовые модели =====

data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val id: Int,
    val method: String,
    val params: Any? = null
)

data class JsonRpcResponse<T>(
    val jsonrpc: String,
    val id: Int?,
    val result: T?,
    val error: JsonRpcError?
)

data class JsonRpcError(
    val code: Int,
    val message: String,
    val data: Any? = null
)

// ===== MCP Инициализация =====

data class InitializeParams(
    val protocolVersion: String = "2024-11-05",
    val capabilities: ClientCapabilities = ClientCapabilities(),
    val clientInfo: ClientInfo = ClientInfo()
)

data class ClientCapabilities(
    val roots: RootsCapability? = null,
    val sampling: SamplingCapability? = null
)

data class RootsCapability(
    val listChanged: Boolean = false
)

data class SamplingCapability(
    val enabled: Boolean = false
)

data class ClientInfo(
    val name: String = "ChatBot-Android",
    val version: String = "1.0.0"
)

data class InitializeResult(
    val protocolVersion: String,
    val capabilities: ServerCapabilities,
    val serverInfo: ServerInfo?
)

data class ServerCapabilities(
    val tools: ToolsCapability? = null,
    val resources: ResourcesCapability? = null,
    val prompts: PromptsCapability? = null
)

data class ToolsCapability(
    val listChanged: Boolean = false
)

data class ResourcesCapability(
    val subscribe: Boolean = false,
    val listChanged: Boolean = false
)

data class PromptsCapability(
    val listChanged: Boolean = false
)

data class ServerInfo(
    val name: String,
    val version: String?
)

// ===== MCP Tools =====

data class ListToolsResult(
    val tools: List<McpTool>
)

data class McpTool(
    val name: String,
    val description: String?,
    val inputSchema: ToolInputSchema?
)

data class ToolInputSchema(
    val type: String = "object",
    val properties: Map<String, PropertySchema>? = null,
    val required: List<String>? = null
)

data class PropertySchema(
    val type: String,
    val description: String? = null,
    @SerializedName("enum")
    val enumValues: List<String>? = null
)

// ===== MCP Tool Call =====

data class CallToolParams(
    val name: String,
    val arguments: Map<String, Any>? = null
)

data class CallToolResult(
    val content: List<ToolContent>,
    val isError: Boolean = false
)

data class ToolContent(
    val type: String, // "text", "image", "resource"
    val text: String? = null,
    val data: String? = null,
    val mimeType: String? = null
)

// ===== MCP Resources =====

data class ListResourcesResult(
    val resources: List<McpResource>
)

data class McpResource(
    val uri: String,
    val name: String,
    val description: String? = null,
    val mimeType: String? = null
)

// ===== MCP Prompts =====

data class ListPromptsResult(
    val prompts: List<McpPrompt>
)

data class McpPrompt(
    val name: String,
    val description: String? = null,
    val arguments: List<PromptArgument>? = null
)

data class PromptArgument(
    val name: String,
    val description: String? = null,
    val required: Boolean = false
)

