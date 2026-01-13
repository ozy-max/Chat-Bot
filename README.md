# 🤖 ChatBot - AI Assistant with RAG, MCP & Automated PR Review

> Мощный Android чат-бот с интеграцией RAG (Retrieval-Augmented Generation), встроенным MCP сервером и автоматическим AI Code Review для Pull Request

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.0-purple.svg)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Latest-green.svg)](https://developer.android.com/jetpack/compose)
[![Claude AI](https://img.shields.io/badge/Claude-3%20Haiku-orange.svg)](https://www.anthropic.com)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

---

## 📋 Содержание

- [Основные возможности](#-основные-возможности)
- [Архитектура](#-архитектура)
- [Установка](#-установка)
- [Быстрый старт](#-быстрый-старт)
- [Доступные команды](#-доступные-команды)
- [AI PR Review](#-ai-pr-review)
- [RAG система](#-rag-система)
- [MCP интеграция](#-mcp-интеграция)
- [Git интеграция](#-git-интеграция)
- [Конфигурация](#-конфигурация)
- [Технологический стек](#-технологический-стек)

---

## ✨ Основные возможности

### 🧠 RAG (Retrieval-Augmented Generation)
- **Индексация документации проекта** - README, API docs, код
- **Семантический поиск** - находит релевантную информацию по смыслу
- **Гибридный поиск** - комбинирует векторный и keyword-based поиск
- **ChromaDB** для хранения эмбеддингов
- **Ollama** для локальной генерации эмбеддингов

### 🔧 MCP (Model Context Protocol)
- **Встроенный Kotlin MCP сервер** внутри Android приложения
- **Python MCP сервер** для Git команд на хост-машине
- **20+ инструментов**: погода, задачи, файлы, поиск, Git
- **Двунаправленная интеграция** между Android и Python

### 🤖 AI Ассистенты
- **Claude 3 Haiku** - быстрый и эффективный
- **YandexGPT** - российский AI
- **Ollama** - локальные модели (Llama 3, Mistral)
- **Автоматическое переключение** между моделями

### 🔍 AI Code Review
- **Автоматический анализ PR** при каждом Pull Request
- **GitHub Actions CI/CD** - полностью автоматизировано
- **RAG контекст** - использует знания всей кодовой базы
- **Структурированное ревью** - находит баги, дает рекомендации

### 🎯 Git интеграция
- Информация о текущей ветке
- Git статус и история коммитов
- Поиск в коде через `git grep`
- Анализ изменений в файлах

### 📝 Управление задачами
- **Todoist интеграция**
- Создание, просмотр, завершение задач
- Ежедневные сводки
- Автоматическая синхронизация

### 🌐 Интернет поиск
- Поиск информации в реальном времени
- Интеграция с web sources
- Pipeline анализ результатов

---

## 🏗️ Архитектура

```
┌─────────────────────────────────────────────────────────────────┐
│                    Android App (Kotlin)                          │
│                                                                   │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │           UI Layer (Jetpack Compose)                     │  │
│  │  • ChatScreen                                            │  │
│  │  • MessageList                                           │  │
│  │  • AIFeatures Panel                                      │  │
│  └────────────────┬─────────────────────────────────────────┘  │
│                   │                                              │
│  ┌────────────────▼─────────────────────────────────────────┐  │
│  │          ChatViewModel (State Management)                │  │
│  │  • Command parsing (/project, /git, /help)              │  │
│  │  • AI provider selection                                │  │
│  │  • Memory management                                     │  │
│  └────────┬──────────────────┬──────────────────────────────┘  │
│           │                  │                                  │
│  ┌────────▼────────┐  ┌─────▼──────────────────────────────┐  │
│  │  RAG System     │  │    Kotlin MCP Server               │  │
│  │                 │  │                                      │  │
│  │ • VectorStorage │  │ • 20+ Tools                         │  │
│  │ • OllamaRAG     │  │ • Todoist, Weather, Files          │  │
│  │ • DocumentIndex │  │ • ProjectDocs, WebSearch           │  │
│  │ • ChromaDB      │  │ • Calls Python MCP for Git         │  │
│  └─────────────────┘  └───────────┬──────────────────────────┘  │
└────────────────────────────────────┼──────────────────────────────┘
                                     │ HTTP
                                     │
┌────────────────────────────────────▼──────────────────────────────┐
│              Python MCP Server (Host Machine)                      │
│                                                                     │
│  • Git commands (status, branch, search)                          │
│  • Project info (files, commits)                                  │
│  • File system access                                             │
└────────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────────┐
│                     GitHub Actions CI/CD                            │
│                                                                     │
│  Trigger: Pull Request                                             │
│     ↓                                                               │
│  1. Checkout code                                                  │
│  2. Index codebase with RAG (ChromaDB)                            │
│  3. Get PR diff and changed files                                 │
│  4. Search similar code for context                               │
│  5. Send to Claude 3 Haiku for analysis                           │
│  6. Post review comment to PR                                     │
└────────────────────────────────────────────────────────────────────┘
```

---

## 📦 Установка

### Требования

- **Android Studio** Electric Eel или новее
- **Kotlin** 1.9.0+
- **Android SDK** минимум API 26 (Android 8.0)
- **Python** 3.9+ (для Python MCP сервера и AI PR Review)
- **Ollama** (опционально, для локальных моделей)

### 1. Клонирование репозитория

```bash
git clone https://github.com/ozy-max/Chat-Bot.git
cd Chat-Bot
```

### 2. Настройка API ключей

Создайте файл `local.properties` в корне проекта:

```properties
CLAUDE_API_KEY=sk-ant-ваш-ключ
YANDEX_API_KEY=ваш-ключ-yandex-gpt
ANTHROPIC_API_KEY=sk-ant-ваш-ключ
TODOIST_API_TOKEN=ваш-токен-todoist
```

### 3. Сборка проекта

```bash
./gradlew assembleDebug
```

### 4. Установка зависимостей Python (опционально)

Для AI PR Review и Python MCP сервера:

```bash
cd mcp-server
pip3 install -r requirements.txt
```

---

## 🚀 Быстрый старт

### Запуск Android приложения

1. Откройте проект в Android Studio
2. Подключите устройство или запустите эмулятор
3. Нажмите **Run** (▶️)

### Запуск Python MCP сервера (для Git команд)

```bash
cd mcp-server
python3 server.py
```

Сервер запустится на `http://localhost:3000`

### Запуск Ollama (для RAG)

```bash
# Установка Ollama (macOS)
brew install ollama

# Запуск сервиса
ollama serve

# Загрузка модели для эмбеддингов
ollama pull nomic-embed-text

# Загрузка модели для генерации (опционально)
ollama pull llama3
```

---

## 📝 Доступные команды

### RAG команды

```bash
/project index          # Проиндексировать документацию проекта
/project search <query> # Поиск в коде через RAG
/project stats          # Статистика индекса

/ask <вопрос>          # Вопрос с использованием RAG
/rag <вопрос>          # Алиас для /ask

/help                  # Список всех команд
/help <тема>           # Помощь по конкретной теме через RAG
```

### Git команды

```bash
/git status            # Git статус проекта (через Python MCP)
/git search <query>    # Поиск в коде через git grep
/project info          # Информация о проекте (ветка, файлы, коммиты)
```

### Ollama команды

```bash
/ollama status         # Проверить подключение к Ollama
/ollama models         # Список доступных моделей
/ollama url <url>      # Изменить URL Ollama сервера
```

### Команды для RAG экспериментов

```bash
/index <путь>          # Индексировать файл/папку
/load <имя>            # Загрузить демо документы
/compare <вопрос>      # Сравнить RAG vs обычный ответ
/filter <вопрос>       # RAG с улучшенной фильтрацией
/docs                  # Список проиндексированных документов
/search <запрос>       # Семантический поиск
```

### Todoist команды

```bash
/task add <название>   # Добавить задачу
/task list             # Список задач
/task complete <id>    # Завершить задачу
/summary               # Сводка за сегодня
/sync                  # Синхронизировать с Todoist
```

### Другие команды

```bash
/weather <город>       # Погода в городе
/pipeline <запрос>     # Web поиск + анализ
/files                 # Список файлов в хранилище
```

---

## 🤖 AI PR Review

### Настройка GitHub Actions

1. **Добавьте API ключ в Secrets:**
   - Откройте: `https://github.com/ваш-repo/settings/secrets/actions`
   - Создайте секрет: `ANTHROPIC_API_KEY` = ваш ключ Claude

2. **Workflow запустится автоматически** при создании PR

### Что делает AI Review:

✅ **Анализирует изменения:**
- Получает diff PR
- Индексирует всю кодовую базу через RAG
- Ищет похожий код для контекста

✅ **Генерирует ревью:**
- Положительные моменты
- Замечания и проблемы (с указанием файлов и строк)
- Рекомендации по улучшению
- Общая оценка (можно ли мержить)

✅ **Публикует комментарий** в PR автоматически

### Пример ревью:

```markdown
🤖 AI Code Review

## ✅ Положительные моменты
- Хорошее использование корутин для асинхронных операций
- Правильная обработка ошибок через Result type

## ⚠️ Замечания
1. **TestUtils.kt:12** - Email валидация слишком простая
   Рекомендация: Использовать Patterns.EMAIL_ADDRESS

2. **TestUtils.kt:25** - Рекурсия может привести к StackOverflow
   Рекомендация: Добавить итеративную версию или ограничение глубины

## 💡 Рекомендации
- Добавить unit тесты для новых функций
- Обновить документацию в README

## 📊 Общая оценка
Требуются небольшие изменения перед мержем
```

---

## 🧠 RAG система

### Как это работает

1. **Индексация документов:**
   ```kotlin
   documentIndexService.indexDocument(
       name = "README",
       content = readmeText,
       type = "markdown"
   )
   ```

2. **Создание эмбеддингов:**
   - Ollama генерирует векторные представления
   - Хранятся в ChromaDB (векторная база данных)

3. **Поиск:**
   ```kotlin
   val results = ollamaRAGService.queryWithRAG(
       question = "Как работает hybrid search?",
       modelName = "llama3"
   )
   ```

4. **Гибридный поиск:**
   - **Векторный поиск** - семантическая близость
   - **Keyword boost** - точные совпадения терминов
   - **Fuzzy matching** - похожие слова
   - **Reranking** - переупорядочивание результатов

### Улучшения RAG

- ✅ Keyword boost для технических терминов
- ✅ Term mapping (RAG → retrieval augmented generation)
- ✅ Fuzzy matching для опечаток
- ✅ Chunk selection - выбор наиболее релевантного чанка на документ
- ✅ Relevance threshold - фильтрация нерелевантных результатов

---

## 🔧 MCP интеграция

### Встроенный Kotlin MCP Server

Работает внутри Android приложения на порту 3000:

```kotlin
val mcpServer = McpServer(context, port = 3000)
mcpServer.start()
```

**Доступные инструменты:**
- `project_info` - информация о проекте
- `git_status` - Git статус (через Python MCP)
- `git_search` - поиск в коде
- `project_index` - индексация документации
- `project_help` - помощь через RAG
- `get_weather` - погода
- `add_task` - добавить задачу Todoist
- `web_search` - поиск в интернете
- И многое другое...

### Python MCP Server

Работает на Mac/Linux для Git команд:

```python
# mcp-server/server.py
# Инструменты:
# - project_info: git branch, status, commits
# - git_status: полный статус репозитория
# - git_search: git grep поиск
```

**Запуск:**
```bash
cd mcp-server
python3 server.py
```

**Интеграция:** Android приложение вызывает Python сервер по HTTP для Git команд.

---

## 🔍 Git интеграция

### Архитектура

```
Android App → HTTP → Python MCP Server → Git Commands → Repository
```

### Доступные операции

**Через команды в приложении:**
```bash
/project info    # Ветка, файлы, коммиты
/git status      # Статус репозитория
/git search RAG  # Найти "RAG" в коде
```

**Что возвращается:**
- Текущая ветка
- Измененные файлы
- История коммитов (последние 5)
- Результаты поиска по коду
- Количество Kotlin файлов

---

## ⚙️ Конфигурация

### RAG конфигурация

```kotlin
// RagConfig.kt
data class RagConfig(
    val topK: Int = 5,                    // Количество результатов
    val minRelevance: Float = 0.8f,       // Минимальная релевантность
    val keywordBoost: Float = 1.5f,       // Буст для keyword-match
    val enableReranking: Boolean = true,   // Переранжирование
    val fuzzyThreshold: Int = 2            // Fuzzy matching threshold
)
```

### Ollama конфигурация

```kotlin
// OllamaClient.kt
val ollamaClient = OllamaClient(
    baseUrl = "http://192.168.1.100:11434"  // Ваш Ollama URL
)
```

### MCP Server конфигурация

```kotlin
// McpServer.kt
class McpServer(
    private val context: Context,
    private val port: Int = 3000  // Порт для HTTP сервера
)
```

---

## 🛠️ Технологический стек

### Android App

| Технология | Версия | Назначение |
|------------|--------|------------|
| **Kotlin** | 1.9.0 | Основной язык |
| **Jetpack Compose** | Latest | UI framework |
| **Coroutines** | 1.7.3 | Асинхронность |
| **Room** | 2.6.0 | База данных |
| **Retrofit** | 2.9.0 | HTTP клиент |
| **OkHttp** | 4.12.0 | Сетевой слой |
| **Gson** | 2.10.1 | JSON парсинг |
| **NanoHTTPD** | 2.3.1 | Встроенный HTTP сервер |

### RAG & AI

| Технология | Назначение |
|------------|------------|
| **Ollama** | Локальные LLM и эмбеддинги |
| **ChromaDB** | Векторная база данных |
| **Claude 3 Haiku** | AI для code review |
| **YandexGPT** | Российский AI assistant |
| **SentenceTransformers** | Эмбеддинги (Python) |

### CI/CD & Automation

| Технология | Назначение |
|------------|------------|
| **GitHub Actions** | CI/CD пайплайн |
| **Python 3.9+** | Скрипты автоматизации |
| **PyGithub** | GitHub API |
| **Anthropic API** | Claude интеграция |

---

## 📚 Дополнительные ресурсы

### Документация

- [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-guide.html)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Ollama Documentation](https://github.com/ollama/ollama)
- [Claude API](https://docs.anthropic.com/)
- [MCP Protocol](https://modelcontextprotocol.io/)

### Туториалы

1. **Настройка RAG:**
   - Запустите Ollama
   - Выполните `/ollama status` в приложении
   - Проиндексируйте документы: `/project index`
   - Попробуйте поиск: `/project search RAG`

2. **Настройка AI PR Review:**
   - Добавьте ANTHROPIC_API_KEY в GitHub Secrets
   - Создайте тестовый PR
   - Проверьте комментарий от AI бота

3. **Git интеграция:**
   - Запустите Python MCP сервер: `python3 mcp-server/server.py`
   - В приложении: `/project info`
   - Проверьте Git статус: `/git status`

---

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request
6. **AI будет автоматически ревьюить ваш PR!** 🤖

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## 👨‍💻 Author

**Igor Urev (ozy-max)**

- GitHub: [@ozy-max](https://github.com/ozy-max)

---

## 🙏 Acknowledgments

- [Anthropic](https://www.anthropic.com/) за Claude API
- [Ollama](https://ollama.ai/) за локальные LLM
- [JetBrains](https://www.jetbrains.com/) за Kotlin
- [Google](https://developer.android.com/) за Android & Jetpack Compose

---

## 📊 Статистика проекта

- **Строк кода:** ~20,000+
- **Kotlin файлов:** 63
- **Компонентов UI:** 30+
- **MCP инструментов:** 20+
- **Проиндексированных документов:** Настраиваемо

---

**⭐ Если проект понравился - поставьте звезду!**

Made with ❤️ and 🤖 AI
