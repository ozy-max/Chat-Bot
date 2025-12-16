#!/usr/bin/env python3
"""
MCP HTTP Test Server с реальным API погоды
Запуск: python3 server.py
"""

from http.server import HTTPServer, BaseHTTPRequestHandler
import json
import socket
import random
from datetime import datetime
import urllib.request
import urllib.parse

# Список инструментов
TOOLS = [
    {
        "name": "get_weather",
        "description": "Получить текущую погоду для города",
        "inputSchema": {
            "type": "object",
            "properties": {
                "city": {"type": "string", "description": "Название города"}
            },
            "required": ["city"]
        }
    },
    {
        "name": "get_time",
        "description": "Получить текущее время",
        "inputSchema": {
            "type": "object",
            "properties": {
                "timezone": {"type": "string", "description": "Часовой пояс"}
            },
            "required": []
        }
    },
    {
        "name": "calculate",
        "description": "Выполнить математические вычисления",
        "inputSchema": {
            "type": "object",
            "properties": {
                "expression": {"type": "string", "description": "Выражение (2+2*2)"}
            },
            "required": ["expression"]
        }
    },
    {
        "name": "random_number",
        "description": "Сгенерировать случайное число",
        "inputSchema": {
            "type": "object",
            "properties": {
                "min": {"type": "integer", "description": "Минимум"},
                "max": {"type": "integer", "description": "Максимум"}
            },
            "required": []
        }
    }
]

def get_real_weather(city):
    """Получить реальную погоду через wttr.in API"""
    try:
        # wttr.in - бесплатное API без ключа
        city_encoded = urllib.parse.quote(city)
        url = f"https://wttr.in/{city_encoded}?format=j1"
        
        req = urllib.request.Request(url)
        req.add_header('User-Agent', 'Mozilla/5.0')
        
        with urllib.request.urlopen(req, timeout=15) as response:
            data = json.loads(response.read().decode())
            
            current = data['current_condition'][0]
            temp = current['temp_C']
            feels_like = current['FeelsLikeC']
            humidity = current['humidity']
            weather_desc = current['weatherDesc'][0]['value']
            wind_speed = current['windspeedKmph']
            
            return f"""🌍 Реальная погода в {city}:
🌡️ Температура: {temp}°C (ощущается как {feels_like}°C)
☁️ Условия: {weather_desc}
💧 Влажность: {humidity}%
💨 Ветер: {wind_speed} км/ч
✅ Данные получены с wttr.in API"""
    except Exception as e:
        # Fallback на демо данные
        return f"""🌍 Демо погода для {city}:
🌡️ Температура: {random.randint(15, 25)}°C
☁️ Условия: Переменная облачность
💧 Влажность: {random.randint(40, 70)}%
⚠️ Примечание: Реальное API недоступно ({str(e)[:50]})"""

def handle_tool_call(name, args):
    """Обработка вызова инструмента"""
    args = args or {}
    
    if name == "get_weather":
        city = args.get("city", "Moscow")
        weather_info = get_real_weather(city)
        return {"content": [{"type": "text", "text": weather_info}]}
    
    elif name == "get_time":
        now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        return {"content": [{"type": "text", "text": f"Текущее время: {now}"}]}
    
    elif name == "calculate":
        expr = args.get("expression", "0")
        try:
            # Безопасное вычисление
            allowed = set("0123456789+-*/().% ")
            if all(c in allowed for c in expr):
                result = eval(expr)
                return {"content": [{"type": "text", "text": f"{expr} = {result}"}]}
            else:
                return {"content": [{"type": "text", "text": "Недопустимые символы"}], "isError": True}
        except Exception as e:
            return {"content": [{"type": "text", "text": f"Ошибка: {e}"}], "isError": True}
    
    elif name == "random_number":
        min_val = args.get("min", 1)
        max_val = args.get("max", 100)
        num = random.randint(min_val, max_val)
        return {"content": [{"type": "text", "text": f"Случайное число [{min_val}-{max_val}]: {num}"}]}
    
    return {"content": [{"type": "text", "text": f"Unknown tool: {name}"}], "isError": True}


class MCPHandler(BaseHTTPRequestHandler):
    def do_POST(self):
        if self.path != "/mcp":
            self.send_error(404)
            return
        
        # Читаем тело запроса
        content_length = int(self.headers.get('Content-Length', 0))
        body = self.rfile.read(content_length).decode('utf-8')
        
        try:
            request = json.loads(body)
        except json.JSONDecodeError:
            self.send_error(400, "Invalid JSON")
            return
        
        method = request.get("method", "")
        params = request.get("params")
        req_id = request.get("id")
        
        print(f"📨 {method}", params if params else "")
        
        # Обработка методов
        result = None
        error = None
        
        if method == "initialize":
            result = {
                "protocolVersion": "2024-11-05",
                "capabilities": {
                    "tools": {"listChanged": False},
                    "resources": {"subscribe": False, "listChanged": False},
                    "prompts": {"listChanged": False}
                },
                "serverInfo": {
                    "name": "MCP Python Test Server",
                    "version": "1.0.0"
                }
            }
        
        elif method == "notifications/initialized":
            result = {}
        
        elif method == "tools/list":
            result = {"tools": TOOLS}
        
        elif method == "tools/call":
            name = params.get("name", "") if params else ""
            args = params.get("arguments") if params else None
            result = handle_tool_call(name, args)
        
        elif method == "resources/list":
            result = {"resources": []}
        
        elif method == "prompts/list":
            result = {"prompts": []}
        
        else:
            error = {"code": -32601, "message": f"Method not found: {method}"}
        
        # Формируем ответ
        response = {"jsonrpc": "2.0", "id": req_id}
        if error:
            response["error"] = error
            print(f"❌ Error: {error['message']}")
        else:
            response["result"] = result
            print(f"✅ OK")
        
        # Отправляем ответ
        response_body = json.dumps(response).encode('utf-8')
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Content-Length', len(response_body))
        self.end_headers()
        self.wfile.write(response_body)
    
    def do_OPTIONS(self):
        """CORS preflight"""
        self.send_response(200)
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Access-Control-Allow-Methods', 'POST, OPTIONS')
        self.send_header('Access-Control-Allow-Headers', 'Content-Type')
        self.end_headers()
    
    def log_message(self, format, *args):
        pass  # Отключаем стандартные логи


def get_local_ip():
    """Получить локальный IP"""
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except:
        return "localhost"


if __name__ == "__main__":
    PORT = 3000
    IP = get_local_ip()
    
    print()
    print("🚀 MCP Test Server запущен!")
    print()
    print("📱 Для подключения с Android используйте:")
    print(f"   http://{IP}:{PORT}/mcp")
    print()
    print("🔧 Доступные инструменты:")
    for t in TOOLS:
        print(f"   - {t['name']}: {t['description']}")
    print()
    print("Нажмите Ctrl+C для остановки")
    print()
    
    server = HTTPServer(("0.0.0.0", PORT), MCPHandler)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n👋 Сервер остановлен")
        server.shutdown()

