const express = require('express');
const cors = require('cors');
const os = require('os');

const app = express();
app.use(cors());
app.use(express.json());

// Хранилище для request ID
let requestId = 0;

// Список инструментов MCP сервера
const tools = [
    {
        name: "get_weather",
        description: "Получить текущую погоду для города",
        inputSchema: {
            type: "object",
            properties: {
                city: {
                    type: "string",
                    description: "Название города"
                }
            },
            required: ["city"]
        }
    },
    {
        name: "get_time",
        description: "Получить текущее время",
        inputSchema: {
            type: "object",
            properties: {
                timezone: {
                    type: "string",
                    description: "Часовой пояс (например: Europe/Moscow)"
                }
            },
            required: []
        }
    },
    {
        name: "calculate",
        description: "Выполнить математические вычисления",
        inputSchema: {
            type: "object",
            properties: {
                expression: {
                    type: "string",
                    description: "Математическое выражение (например: 2+2*2)"
                }
            },
            required: ["expression"]
        }
    },
    {
        name: "translate",
        description: "Перевести текст (демо)",
        inputSchema: {
            type: "object",
            properties: {
                text: {
                    type: "string",
                    description: "Текст для перевода"
                },
                to: {
                    type: "string",
                    description: "Целевой язык",
                    enum: ["en", "ru", "de", "fr", "es"]
                }
            },
            required: ["text", "to"]
        }
    }
];

// Обработка MCP запросов
app.post('/mcp', (req, res) => {
    const { jsonrpc, id, method, params } = req.body;
    
    console.log(`📨 Request: ${method}`, params ? JSON.stringify(params) : '');
    
    let result;
    let error;
    
    switch (method) {
        case 'initialize':
            result = {
                protocolVersion: "2024-11-05",
                capabilities: {
                    tools: { listChanged: false },
                    resources: { subscribe: false, listChanged: false },
                    prompts: { listChanged: false }
                },
                serverInfo: {
                    name: "MCP Test Server",
                    version: "1.0.0"
                }
            };
            break;
            
        case 'notifications/initialized':
            // Уведомление, не требует ответа
            res.json({ jsonrpc: "2.0", id, result: {} });
            return;
            
        case 'tools/list':
            result = { tools };
            break;
            
        case 'tools/call':
            result = handleToolCall(params);
            break;
            
        case 'resources/list':
            result = { resources: [] };
            break;
            
        case 'prompts/list':
            result = { prompts: [] };
            break;
            
        default:
            error = {
                code: -32601,
                message: `Method not found: ${method}`
            };
    }
    
    const response = {
        jsonrpc: "2.0",
        id
    };
    
    if (error) {
        response.error = error;
        console.log(`❌ Error: ${error.message}`);
    } else {
        response.result = result;
        console.log(`✅ Response sent`);
    }
    
    res.json(response);
});

// Обработка вызовов инструментов
function handleToolCall(params) {
    const { name, arguments: args } = params;
    
    switch (name) {
        case 'get_weather':
            const city = args?.city || 'Unknown';
            const temp = Math.floor(Math.random() * 30) - 5;
            const conditions = ['☀️ Солнечно', '☁️ Облачно', '🌧️ Дождь', '❄️ Снег'][Math.floor(Math.random() * 4)];
            return {
                content: [{
                    type: "text",
                    text: `Погода в ${city}: ${temp}°C, ${conditions}`
                }],
                isError: false
            };
            
        case 'get_time':
            const now = new Date();
            const tz = args?.timezone || 'Europe/Moscow';
            return {
                content: [{
                    type: "text",
                    text: `Текущее время (${tz}): ${now.toLocaleString('ru-RU', { timeZone: tz })}`
                }],
                isError: false
            };
            
        case 'calculate':
            try {
                const expr = args?.expression || '0';
                // Безопасное вычисление (только числа и операторы)
                const safeExpr = expr.replace(/[^0-9+\-*/().%\s]/g, '');
                const result = eval(safeExpr);
                return {
                    content: [{
                        type: "text",
                        text: `${expr} = ${result}`
                    }],
                    isError: false
                };
            } catch (e) {
                return {
                    content: [{
                        type: "text",
                        text: `Ошибка вычисления: ${e.message}`
                    }],
                    isError: true
                };
            }
            
        case 'translate':
            const text = args?.text || '';
            const to = args?.to || 'en';
            return {
                content: [{
                    type: "text",
                    text: `[Демо перевод на ${to}]: ${text} → [translated text]`
                }],
                isError: false
            };
            
        default:
            return {
                content: [{
                    type: "text",
                    text: `Unknown tool: ${name}`
                }],
                isError: true
            };
    }
}

// Получить IP адрес
function getLocalIP() {
    const interfaces = os.networkInterfaces();
    for (const name of Object.keys(interfaces)) {
        for (const iface of interfaces[name]) {
            if (iface.family === 'IPv4' && !iface.internal) {
                return iface.address;
            }
        }
    }
    return 'localhost';
}

const PORT = 3000;
const IP = getLocalIP();

app.listen(PORT, '0.0.0.0', () => {
    console.log('');
    console.log('🚀 MCP Test Server запущен!');
    console.log('');
    console.log('📱 Для подключения с Android используйте:');
    console.log(`   http://${IP}:${PORT}/mcp`);
    console.log('');
    console.log('🔧 Доступные инструменты:');
    tools.forEach(t => console.log(`   - ${t.name}: ${t.description}`));
    console.log('');
    console.log('Нажмите Ctrl+C для остановки');
    console.log('');
});

