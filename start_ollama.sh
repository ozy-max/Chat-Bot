#!/bin/bash

# ════════════════════════════════════════════════════════════════════════════
# 🚀 START OLLAMA SERVER FOR ANDROID CHATBOT
# ════════════════════════════════════════════════════════════════════════════
#
# Этот скрипт запускает Ollama с доступом по локальной сети,
# чтобы Android эмулятор/устройство могло подключиться к нему.
#
# Использование:
#   ./start_ollama.sh
#
# ════════════════════════════════════════════════════════════════════════════

echo "═══════════════════════════════════════════════════════════════"
echo "🚀 ЗАПУСК OLLAMA ДЛЯ ANDROID CHATBOT"
echo "═══════════════════════════════════════════════════════════════"
echo ""

# 1. Проверка установки Ollama
if ! command -v ollama &> /dev/null; then
    echo "❌ Ollama не установлен!"
    echo ""
    echo "Установите Ollama:"
    echo "  brew install ollama"
    echo "  или скачайте с https://ollama.com"
    exit 1
fi

echo "✅ Ollama установлен: $(ollama --version 2>&1 | head -1)"
echo ""

# 2. Остановка существующего Ollama
echo "🔄 Остановка существующего Ollama сервера..."
pkill -f "ollama serve" 2>/dev/null
sleep 2
echo "✅ Ollama остановлен"
echo ""

# 3. Получение локального IP
LOCAL_IP=$(ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null || echo "localhost")
echo "📡 Локальный IP: $LOCAL_IP"
echo ""

# 4. Запуск Ollama с сетевым доступом
echo "🚀 Запуск Ollama на 0.0.0.0:11434..."
echo "   (Будет доступен по $LOCAL_IP:11434)"
echo ""

export OLLAMA_HOST=0.0.0.0:11434
nohup ollama serve > /tmp/ollama_server.log 2>&1 &
OLLAMA_PID=$!

echo "✅ Ollama запущен (PID: $OLLAMA_PID)"
echo ""

# 5. Ожидание запуска
echo "⏳ Ожидание запуска сервера..."
sleep 3

# 6. Проверка работоспособности
if curl -s http://localhost:11434/api/tags > /dev/null; then
    echo "✅ Ollama работает!"
    echo ""
    
    # Получение списка моделей
    MODELS=$(curl -s http://localhost:11434/api/tags | python3 -c "import sys, json; data=json.load(sys.stdin); print('\n'.join([m['name'] for m in data['models']]))" 2>/dev/null)
    
    if [ -n "$MODELS" ]; then
        echo "📦 Установленные модели:"
        echo "$MODELS" | sed 's/^/   - /'
        echo ""
    else
        echo "⚠️  Модели не найдены!"
        echo ""
        echo "Установите модели:"
        echo "   ollama pull llama3"
        echo "   ollama pull nomic-embed-text"
        echo ""
    fi
    
    echo "═══════════════════════════════════════════════════════════════"
    echo "✅ OLLAMA ГОТОВ К ИСПОЛЬЗОВАНИЮ!"
    echo "═══════════════════════════════════════════════════════════════"
    echo ""
    
    # Настройка ADB reverse для эмулятора
    echo "🔧 Настройка ADB reverse для эмулятора..."
    if command -v adb &> /dev/null; then
        adb reverse tcp:11434 tcp:11434 2>/dev/null && echo "✅ ADB reverse настроен (localhost:11434)" || echo "⚠️  ADB reverse не настроен (эмулятор не запущен?)"
    else
        echo "⚠️  ADB не найден (установите Android SDK)"
    fi
    echo ""
    
    echo "🌐 URL для Android приложения:"
    echo "   http://localhost:11434 (через ADB reverse)"
    echo ""
    echo "📝 Логи сервера:"
    echo "   tail -f /tmp/ollama_server.log"
    echo ""
    echo "🛑 Остановка сервера:"
    echo "   pkill -f 'ollama serve'"
    echo ""
else
    echo "❌ Ollama не запустился!"
    echo ""
    echo "Проверьте логи:"
    echo "   cat /tmp/ollama_server.log"
    exit 1
fi
