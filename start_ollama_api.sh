#!/bin/bash

###############################################################################
# Ollama Chat API Server Launcher
###############################################################################
# Запускает локальный API сервер для работы с Ollama
# Использование: ./start_ollama_api.sh
###############################################################################

set -e

echo "═══════════════════════════════════════════════════════════════"
echo "🚀 ЗАПУСК OLLAMA CHAT API SERVER"
echo "═══════════════════════════════════════════════════════════════"
echo ""

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 1. Проверка Ollama
echo "🔍 Проверка Ollama..."
if pgrep -x "ollama" > /dev/null; then
    echo -e "${GREEN}✅ Ollama запущен${NC}"
else
    echo -e "${RED}❌ Ollama не запущен!${NC}"
    echo ""
    echo "Запустите Ollama:"
    echo "  ./start_ollama.sh"
    exit 1
fi

# 2. Проверка доступности Ollama API
echo "🔍 Проверка Ollama API..."
if curl -s http://localhost:11434/api/tags > /dev/null 2>&1; then
    echo -e "${GREEN}✅ Ollama API доступен${NC}"
    
    # Получаем список моделей
    MODELS=$(curl -s http://localhost:11434/api/tags | python3 -c "import sys, json; data=json.load(sys.stdin); print(len(data.get('models', [])))")
    echo "   📦 Установлено моделей: $MODELS"
else
    echo -e "${RED}❌ Ollama API недоступен!${NC}"
    exit 1
fi

# 3. Проверка Python
echo "🔍 Проверка Python..."
if command -v python3 &> /dev/null; then
    PYTHON_VERSION=$(python3 --version | cut -d' ' -f2)
    echo -e "${GREEN}✅ Python $PYTHON_VERSION${NC}"
else
    echo -e "${RED}❌ Python3 не найден!${NC}"
    exit 1
fi

# 4. Установка зависимостей
echo "📦 Установка зависимостей..."
cd mcp-server

if [ ! -d "venv" ]; then
    echo "   Создание виртуального окружения..."
    python3 -m venv venv
fi

source venv/bin/activate
pip install -q --upgrade pip
pip install -q -r requirements.txt

echo -e "${GREEN}✅ Зависимости установлены${NC}"

# 5. Настройка переменных окружения
export OLLAMA_HOST="http://localhost:11434"
export OLLAMA_MODEL="llama3"
export PORT="8080"

# 6. Получаем локальный IP
LOCAL_IP=$(ipconfig getifaddr en0 2>/dev/null || echo "127.0.0.1")

echo ""
echo "═══════════════════════════════════════════════════════════════"
echo "🔧 КОНФИГУРАЦИЯ"
echo "═══════════════════════════════════════════════════════════════"
echo "  Ollama Host:    $OLLAMA_HOST"
echo "  Default Model:  $OLLAMA_MODEL"
echo "  API Port:       $PORT"
echo "  Local IP:       $LOCAL_IP"
echo "═══════════════════════════════════════════════════════════════"
echo ""

# 7. Запуск сервера
echo "🚀 Запуск API сервера..."
echo ""
echo "═══════════════════════════════════════════════════════════════"
echo "📡 API ENDPOINTS:"
echo "═══════════════════════════════════════════════════════════════"
echo "  Health Check:   http://localhost:$PORT/health"
echo "  Models List:    http://localhost:$PORT/models"
echo "  Chat:           http://localhost:$PORT/chat"
echo ""
echo "  Android (эмулятор): http://10.0.2.2:$PORT/chat"
echo "  Android (устройство): http://$LOCAL_IP:$PORT/chat"
echo "═══════════════════════════════════════════════════════════════"
echo ""
echo -e "${YELLOW}⏹  Остановка: Ctrl+C${NC}"
echo ""

# 8. Настройка ADB reverse для эмулятора (опционально)
if command -v adb &> /dev/null; then
    if adb devices 2>/dev/null | grep -q "device$"; then
        echo "🔧 Настройка ADB reverse..."
        adb reverse tcp:$PORT tcp:$PORT 2>/dev/null && echo -e "${GREEN}✅ ADB reverse настроен (localhost:$PORT работает в эмуляторе)${NC}" || echo -e "${YELLOW}⚠️  ADB reverse не удался${NC}"
        echo ""
    fi
fi

# 9. Запуск
python3 ollama_api.py
