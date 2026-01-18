# 🚂 Railway - Ручная настройка (из-за CLI ограничений)

## ⚠️ Проблема:
Railway CLI не может создать сервис в неинтерактивном режиме (GitHub Actions).
Нужна **первоначальная настройка через Web UI**.

---

## 📝 ПОШАГОВАЯ ИНСТРУКЦИЯ:

### ✅ ШАГ 1: Откройте Railway Dashboard

```
https://railway.com/project/d5eba09d-3795-4429-a6d2-fff3bfca2ba4
```

---

### ✅ ШАГ 2: Создайте новый сервис

1. Нажмите: **+ New Service**

2. Выберите: **Empty Service**

3. Название сервиса: `mcp-server` (или любое другое)

---

### ✅ ШАГ 3: Подключите GitHub репозиторий

1. В созданном сервисе нажмите: **Settings**

2. Выберите: **Source**

3. Нажмите: **Connect Repo**

4. Выберите репозиторий: **ozy-max/Chat-Bot**

5. **Root Directory**: `mcp-server`

6. **Branch**: `main`

7. Нажмите: **Connect**

---

### ✅ ШАГ 4: Настройте переменные окружения

1. В сервисе откройте: **Variables**

2. Добавьте переменные:

```
PORT=3000
TODOIST_API_TOKEN=ваш_токен_todoist
GITHUB_TOKEN=ваш_токен_github (опционально)
```

3. Нажмите: **Save**

---

### ✅ ШАГ 5: Настройте Deploy Trigger

1. **Settings** → **Deploy**

2. **Deploy Triggers**:
   - ✅ **Watch Paths**: `mcp-server/**`
   - ✅ **Branch**: `main`

---

### ✅ ШАГ 6: Первый деплой

1. Нажмите: **Deploy** (или он запустится автоматически)

2. Дождитесь завершения (2-3 минуты)

3. **Build Logs** покажут прогресс

---

### ✅ ШАГ 7: Получите Deployment URL

1. После успешного деплоя откройте: **Settings** → **Networking**

2. Нажмите: **Generate Domain**

3. Railway создаст URL примерно такой:
   ```
   https://mcp-server-production-XXXX.up.railway.app
   ```

4. **Скопируйте этот URL** - он понадобится для Android приложения

---

### ✅ ШАГ 8: Проверьте деплой

Откройте URL в браузере:
```
https://your-generated-url.up.railway.app
```

Должен вернуться ответ от MCP сервера (возможно "Not Found" для корневого пути - это нормально).

Проверьте эндпоинт `/mcp`:
```
https://your-generated-url.up.railway.app/mcp
```

---

## 🔄 Автоматический деплой (после первоначальной настройки):

После ручной настройки выше, Railway будет **автоматически** деплоить при:
- Push в `main` ветку
- Изменения в `mcp-server/**`

**GitHub Actions больше не нужен!** Railway сам отслеживает изменения в репозитории.

---

## 🎯 Итоговый Checklist:

```
☐ Открыл Railway Dashboard
☐ Создал новый сервис "mcp-server"
☐ Подключил GitHub репозиторий
☐ Указал Root Directory: mcp-server
☐ Настроил переменные окружения:
  - PORT=3000
  - TODOIST_API_TOKEN
  - GITHUB_TOKEN (опционально)
☐ Настроил Deploy Triggers
☐ Выполнил первый деплой
☐ Получил Deployment URL
☐ Проверил работу сервера
☐ Обновил URL в Android приложении
```

---

## 📱 Обновление URL в Android приложении:

После получения Railway URL, обновите в коде:

```kotlin
// Файл: TeamAssistantChatViewModel.kt или McpServer.kt
// Найдите и замените:
val PYTHON_MCP_URL = "http://10.0.2.2:3000/mcp"

// На ваш Railway URL:
val PYTHON_MCP_URL = "https://your-url.up.railway.app/mcp"
```

---

## 🐛 Устранение неполадок:

### ❌ "Build failed"

Проверьте:
1. **Root Directory** = `mcp-server` (правильная папка)
2. **Dockerfile** существует в `mcp-server/`
3. **Build Logs** - посмотрите ошибки

### ❌ "Application crashed"

Проверьте:
1. **Variables** - все переменные установлены
2. **Deploy Logs** - посмотрите ошибки запуска
3. **PORT** = `3000` (Railway требует этот порт)

### ❌ "Domain not found"

1. Подождите 2-3 минуты после деплоя
2. Перегенерируйте домен: Settings → Networking → Generate Domain
3. Проверьте статус деплоя

---

## 🎉 Готово!

После выполнения всех шагов:
- ✅ MCP сервер работает 24/7 на Railway
- ✅ Автоматический деплой при push
- ✅ Android приложение может обращаться к серверу
- ✅ Логи и мониторинг в Railway Dashboard

---

## 📚 Полезные ссылки:

- 🚂 Railway Dashboard: https://railway.app/
- 📖 Railway Docs: https://docs.railway.app/
- 💬 Railway Discord: https://discord.gg/railway
- 🎓 Railway GitHub Integration: https://docs.railway.app/deploy/integrations/github

---

**Удачи! После первой настройки всё будет работать автоматически! 🚀**
