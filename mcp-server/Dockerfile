# Базовый образ Python
FROM python:3.9-slim

# Метаданные
LABEL maintainer="ChatBot AI Assistant"
LABEL description="MCP Server для ChatBot - Git, GitHub, Support, Todoist"

# Рабочая директория
WORKDIR /app

# Установка системных зависимостей
RUN apt-get update && apt-get install -y \
    git \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Копируем файл зависимостей
COPY requirements.txt .

# Создаем requirements.txt если его нет
RUN if [ ! -f requirements.txt ]; then \
    echo "PyGithub==2.1.1" > requirements.txt && \
    echo "requests==2.31.0" >> requirements.txt && \
    echo "schedule==1.2.0" >> requirements.txt; \
    fi

# Устанавливаем Python зависимости
RUN pip install --no-cache-dir -r requirements.txt

# Копируем код сервера
COPY server.py .
COPY *.json ./
COPY *.db ./

# Создаем директории для данных
RUN mkdir -p /app/data /app/logs

# Переменные окружения (будут переопределены при запуске)
ENV PORT=3000
ENV TODOIST_API_TOKEN=""
ENV GITHUB_TOKEN=""

# Экспонируем порт
EXPOSE 3000

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
  CMD curl -f http://localhost:3000/ || exit 1

# Запуск сервера
CMD ["python", "-u", "server.py"]
