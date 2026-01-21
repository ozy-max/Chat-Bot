#!/usr/bin/env python3
"""
Ollama Chat API Server
======================
Простой REST API для работы с локальной Ollama моделью.

Endpoints:
  POST /chat - отправить сообщение и получить ответ
  GET /health - проверка статуса сервера
  GET /models - список доступных моделей

Автор: ChatBot Team
"""

from flask import Flask, request, jsonify
from flask_cors import CORS
import requests
import os
import logging
from datetime import datetime

# Настройка логирования
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

app = Flask(__name__)
CORS(app)  # Разрешаем CORS для Android приложения

# Конфигурация
OLLAMA_HOST = os.getenv('OLLAMA_HOST', 'http://localhost:11434')
DEFAULT_MODEL = os.getenv('OLLAMA_MODEL', 'llama3')
PORT = int(os.getenv('PORT', 8080))

logger.info(f"🚀 Ollama API Server starting...")
logger.info(f"📡 Ollama Host: {OLLAMA_HOST}")
logger.info(f"🦙 Default Model: {DEFAULT_MODEL}")
logger.info(f"🔌 Server Port: {PORT}")


@app.route('/health', methods=['GET'])
def health():
    """
    Проверка статуса сервера и Ollama
    
    Returns:
        200: Сервер и Ollama работают
        503: Ollama недоступна
    """
    try:
        # Проверяем доступность Ollama
        response = requests.get(f"{OLLAMA_HOST}/api/tags", timeout=5)
        
        if response.status_code == 200:
            models = response.json().get('models', [])
            return jsonify({
                'status': 'healthy',
                'ollama_status': 'connected',
                'ollama_host': OLLAMA_HOST,
                'available_models': len(models),
                'timestamp': datetime.now().isoformat()
            }), 200
        else:
            return jsonify({
                'status': 'degraded',
                'ollama_status': 'error',
                'error': f"Ollama returned status {response.status_code}"
            }), 503
            
    except Exception as e:
        logger.error(f"❌ Health check failed: {e}")
        return jsonify({
            'status': 'unhealthy',
            'ollama_status': 'disconnected',
            'error': str(e),
            'timestamp': datetime.now().isoformat()
        }), 503


@app.route('/models', methods=['GET'])
def list_models():
    """
    Получить список доступных моделей
    
    Returns:
        200: Список моделей
        500: Ошибка при получении списка
    """
    try:
        response = requests.get(f"{OLLAMA_HOST}/api/tags", timeout=5)
        
        if response.status_code == 200:
            data = response.json()
            models = data.get('models', [])
            
            return jsonify({
                'models': [
                    {
                        'name': m['name'],
                        'size': m.get('size', 0),
                        'modified': m.get('modified_at', '')
                    }
                    for m in models
                ],
                'default_model': DEFAULT_MODEL,
                'timestamp': datetime.now().isoformat()
            }), 200
        else:
            return jsonify({
                'error': 'Failed to fetch models',
                'status_code': response.status_code
            }), 500
            
    except Exception as e:
        logger.error(f"❌ Failed to list models: {e}")
        return jsonify({
            'error': str(e)
        }), 500


@app.route('/chat', methods=['POST'])
def chat():
    """
    Отправить сообщение и получить ответ от Ollama
    
    Request Body:
    {
        "message": "Привет! Как дела?",
        "model": "llama3",  // опционально
        "temperature": 0.7,  // опционально
        "max_tokens": 2048,  // опционально
        "history": [  // опционально
            {"role": "user", "content": "..."},
            {"role": "assistant", "content": "..."}
        ]
    }
    
    Returns:
        200: Успешный ответ
        400: Некорректный запрос
        500: Ошибка генерации
    """
    try:
        data = request.get_json()
        
        if not data:
            return jsonify({'error': 'Request body is required'}), 400
        
        # Получаем параметры
        message = data.get('message', '').strip()
        model = data.get('model', DEFAULT_MODEL)
        temperature = data.get('temperature', 0.7)
        max_tokens = data.get('max_tokens', 2048)
        history = data.get('history', [])
        
        if not message:
            return jsonify({'error': 'Message is required'}), 400
        
        logger.info(f"💬 Chat request: model={model}, message_length={len(message)}")
        
        # Формируем промпт с учетом истории
        full_prompt = ""
        
        if history:
            for msg in history:
                role = msg.get('role', 'user')
                content = msg.get('content', '')
                full_prompt += f"{role.capitalize()}: {content}\n"
        
        full_prompt += f"User: {message}\nAssistant: "
        
        # Запрос к Ollama
        ollama_request = {
            "model": model,
            "prompt": full_prompt,
            "stream": False,
            "options": {
                "temperature": temperature,
                "num_predict": max_tokens
            }
        }
        
        logger.info(f"🦙 Sending request to Ollama...")
        start_time = datetime.now()
        
        response = requests.post(
            f"{OLLAMA_HOST}/api/generate",
            json=ollama_request,
            timeout=120  # 2 минуты на генерацию
        )
        
        end_time = datetime.now()
        duration = (end_time - start_time).total_seconds()
        
        if response.status_code != 200:
            logger.error(f"❌ Ollama error: {response.status_code}")
            return jsonify({
                'error': 'Ollama generation failed',
                'status_code': response.status_code,
                'details': response.text
            }), 500
        
        # Парсим ответ
        result = response.json()
        assistant_message = result.get('response', '').strip()
        
        # Оценка токенов (приблизительно)
        input_tokens = len(full_prompt.split())
        output_tokens = len(assistant_message.split())
        
        logger.info(f"✅ Response generated in {duration:.2f}s")
        
        return jsonify({
            'message': assistant_message,
            'model': model,
            'input_tokens': input_tokens,
            'output_tokens': output_tokens,
            'total_tokens': input_tokens + output_tokens,
            'generation_time': duration,
            'timestamp': datetime.now().isoformat()
        }), 200
        
    except requests.Timeout:
        logger.error("⏱️ Request timeout")
        return jsonify({
            'error': 'Request timeout',
            'message': 'Ollama took too long to respond'
        }), 504
        
    except Exception as e:
        logger.error(f"❌ Chat error: {e}")
        return jsonify({
            'error': 'Internal server error',
            'details': str(e)
        }), 500


@app.route('/', methods=['GET'])
def index():
    """
    Информация об API
    """
    return jsonify({
        'service': 'Ollama Chat API',
        'version': '1.0.0',
        'endpoints': {
            'GET /': 'API info',
            'GET /health': 'Health check',
            'GET /models': 'List available models',
            'POST /chat': 'Send message and get response'
        },
        'status': 'running',
        'timestamp': datetime.now().isoformat()
    })


if __name__ == '__main__':
    logger.info("═" * 60)
    logger.info("🦙 OLLAMA CHAT API SERVER")
    logger.info("═" * 60)
    logger.info(f"📡 Ollama: {OLLAMA_HOST}")
    logger.info(f"🔌 Port: {PORT}")
    logger.info(f"🦙 Model: {DEFAULT_MODEL}")
    logger.info("═" * 60)
    
    # Проверяем доступность Ollama при старте
    try:
        response = requests.get(f"{OLLAMA_HOST}/api/tags", timeout=5)
        if response.status_code == 200:
            models = response.json().get('models', [])
            logger.info(f"✅ Ollama connected! Models: {len(models)}")
            for model in models:
                logger.info(f"   - {model['name']}")
        else:
            logger.warning(f"⚠️  Ollama returned status {response.status_code}")
    except Exception as e:
        logger.error(f"❌ Cannot connect to Ollama: {e}")
        logger.warning("⚠️  Server will start anyway, but /chat will fail")
    
    logger.info("═" * 60)
    logger.info("🚀 Starting server...")
    logger.info("═" * 60)
    
    app.run(
        host='0.0.0.0',
        port=PORT,
        debug=False
    )
