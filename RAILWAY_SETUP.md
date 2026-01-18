# 🚂 Railway - Пошаговая настройка MCP Server

## 📋 Что получим в результате:
- ✅ MCP сервер доступен 24/7 по URL: `https://chatbot-mcp.railway.app`
- ✅ Автоматический деплой при каждом push в main
- ✅ Бесплатно 500 часов/месяц (~20 дней)
- ✅ Логи, мониторинг, метрики

---

## 🚀 ШАГ 1: Установка Railway CLI

### macOS:
```bash
brew install railway
```

### Windows:
```powershell
npm install -g @railway/cli
```

### Linux:
```bash
npm install -g @railway/cli
# или
curl -fsSL https://railway.app/install.sh | sh
```

### Проверка установки:
```bash
railway --version
```

---

## 🔑 ШАГ 2: Получение Railway Token

```bash
# 1. Логин в Railway (откроется браузер)
railway login

# 2. Получение токена
railway token
```

**Скопируйте токен!** Он выглядит примерно так:
```
railway_token_abc123def456...
```

⚠️ **Важно**: Токен показывается только один раз! Сохраните его.

---

## 🔐 ШАГ 3: Добавление в GitHub Secrets

### Через веб-интерфейс:

1. Откройте ваш репозиторий: `https://github.com/ozy-max/Chat-Bot`
2. Нажмите **Settings** (⚙️)
3. Слева выберите **Secrets and variables** → **Actions**
4. Нажмите **New repository secret**
5. Заполните:
   - **Name**: `RAILWAY_TOKEN`
   - **Secret**: (вставьте скопированный токен)
6. Нажмите **Add secret**

### Через GitHub CLI (опционально):
```bash
gh secret set RAILWAY_TOKEN
# Вставьте токен и нажмите Enter
```

---

## ⚙️ ШАГ 4: Установка DEPLOY_PLATFORM

### Через веб-интерфейс:

1. В том же разделе (Settings → Secrets and variables → Actions)
2. Перейдите на вкладку **Variables**
3. Нажмите **New repository variable**
4. Заполните:
   - **Name**: `DEPLOY_PLATFORM`
   - **Value**: `railway`
5. Нажмите **Add variable**

### Через GitHub CLI:
```bash
gh variable set DEPLOY_PLATFORM --body "railway"
```

---

## 🌐 ШАГ 5: Создание проекта в Railway (через CLI)

```bash
# Перейдите в директорию MCP сервера
cd /Users/igorurev/FlutterProjects/ChatBot/mcp-server

# Инициализация Railway проекта
railway init

# Следуйте инструкциям:
# 1. Create new project? → Yes
# 2. Project name → chatbot-mcp (или любое другое)
# 3. Select environment → production

# Линк проекта с текущей директорией
railway link
```

---

## 🔧 ШАГ 6: Настройка переменных окружения в Railway

### Через веб-интерфейс (рекомендуется):

1. Откройте [Railway Dashboard](https://railway.app/dashboard)
2. Выберите ваш проект **chatbot-mcp**
3. Перейдите в **Variables**
4. Добавьте переменные:

| Variable | Value |
|----------|-------|
| `PORT` | `3000` |
| `TODOIST_API_TOKEN` | ваш Todoist токен |
| `GITHUB_TOKEN` | ваш GitHub token (опционально) |

### Через CLI:
```bash
cd /Users/igorurev/FlutterProjects/ChatBot/mcp-server

# Добавление переменных
railway variables set PORT=3000
railway variables set TODOIST_API_TOKEN=your_todoist_token_here
railway variables set GITHUB_TOKEN=your_github_token_here  # опционально

# Проверка
railway variables
```

---

## 🧪 ШАГ 7: Тестовый деплой (локально из CLI)

```bash
# Убедитесь что вы в mcp-server/
cd /Users/igorurev/FlutterProjects/ChatBot/mcp-server

# Деплой
railway up

# Railway покажет:
# ✓ Deployment successful
# ✓ URL: https://chatbot-mcp.railway.app
```

### Проверка работы:
```bash
# Замените URL на ваш
curl https://chatbot-mcp.railway.app/

# Или откройте в браузере
open https://chatbot-mcp.railway.app/
```

Если видите страницу "MCP Server" - **всё работает!** ✅

---

## 🔄 ШАГ 8: Проверка автоматического деплоя через GitHub Actions

```bash
# Вернитесь в корень проекта
cd /Users/igorurev/FlutterProjects/ChatBot

# Внесите небольшое изменение
echo "# Updated $(date)" >> mcp-server/README_RAILWAY.md

# Закоммитьте и запушьте
git add .
git commit -m "Test Railway auto-deploy"
git push origin main

# 🎉 GitHub Actions автоматически задеплоит на Railway!
```

### Проверка деплоя:

1. **GitHub Actions**:
   - Откройте `https://github.com/ozy-max/Chat-Bot/actions`
   - Найдите workflow "🐍 Deploy MCP Server to Cloud"
   - Проверьте что он успешно выполнился ✅

2. **Railway Dashboard**:
   - Откройте [Railway Dashboard](https://railway.app/dashboard)
   - Выберите проект **chatbot-mcp**
   - В разделе **Deployments** увидите новый деплой

3. **Логи**:
```bash
railway logs
```

---

## 📊 ШАГ 9: Мониторинг и логи

### Просмотр логов:
```bash
# Через CLI
railway logs

# С отслеживанием в реальном времени
railway logs --follow
```

### Railway Dashboard:
1. Откройте проект в [Railway Dashboard](https://railway.app/dashboard)
2. **Metrics** - графики CPU, RAM, Network
3. **Deployments** - история деплоев
4. **Settings** - настройки проекта

---

## 🔗 ШАГ 10: Получение публичного URL

### Через Dashboard:
1. Railway Dashboard → Ваш проект
2. **Settings** → **Networking**
3. **Generate Domain** (если еще не создан)
4. Скопируйте URL: `https://chatbot-mcp.railway.app`

### Через CLI:
```bash
railway open
# Откроет проект в браузере с URL
```

### Использование URL в Android приложении:

Теперь обновите адрес MCP сервера в Android приложении:

```kotlin
// Было:
val mcpServerUrl = "http://10.0.2.2:3000"

// Стало:
val mcpServerUrl = "https://chatbot-mcp.railway.app"
```

---

## ✅ ШАГ 11: Проверка полной интеграции

### 1. Проверка MCP сервера:
```bash
curl https://chatbot-mcp.railway.app/
```

### 2. Проверка GitHub Actions:
```bash
# В корне проекта
git add .
git commit -m "Final Railway test"
git push origin main

# Проверьте Actions на GitHub
```

### 3. Проверка в Android приложении:
- Запустите приложение
- Попробуйте команды:
  - `/project index`
  - `/git_status`
  - `/tasks`

---

## 💰 Бесплатный лимит Railway

### Что включено бесплатно:
- ✅ **500 часов/месяц** исполнения (~20 дней)
- ✅ **100 GB** исходящего трафика
- ✅ **1 GB** RAM
- ✅ **1 vCPU**
- ✅ Неограниченное количество деплоев
- ✅ Автоматический HTTPS

### Когда нужно платить:
- Если превышен лимит 500 часов
- **$5/месяц** за Hobby Plan (безлимитные часы)

### Мониторинг использования:
Railway Dashboard → Account → Usage

---

## 🔄 Обновление MCP сервера

### Автоматическое (рекомендуется):
```bash
cd /Users/igorurev/FlutterProjects/ChatBot/mcp-server
vim server.py  # Внесите изменения

git add server.py
git commit -m "Update MCP server logic"
git push origin main

# 🎉 GitHub Actions автоматически задеплоит!
```

### Ручное (через CLI):
```bash
cd /Users/igorurev/FlutterProjects/ChatBot/mcp-server
railway up
```

---

## 🛠️ Полезные команды Railway CLI

```bash
# Статус проекта
railway status

# Логи
railway logs
railway logs --follow

# Переменные окружения
railway variables
railway variables set KEY=VALUE

# Открыть dashboard
railway open

# Информация о проекте
railway whoami

# Список проектов
railway list

# Удаление проекта (осторожно!)
railway delete
```

---

## 🐛 Troubleshooting

### Проблема: "No project linked"
```bash
cd mcp-server
railway link
# Выберите нужный проект
```

### Проблема: "Deployment failed"
```bash
# Проверьте логи
railway logs

# Частые причины:
# 1. Нет Dockerfile
# 2. Нет railway.json
# 3. Неверные переменные окружения
```

### Проблема: "GitHub Actions не видит RAILWAY_TOKEN"
```bash
# Проверьте что секрет добавлен:
gh secret list

# Должен быть: RAILWAY_TOKEN

# Если нет, добавьте:
gh secret set RAILWAY_TOKEN
```

### Проблема: "Port already in use"
```bash
# В Railway переменных проверьте:
railway variables

# Должна быть: PORT=3000
```

---

## 📝 Чеклист готовности

### Перед деплоем:
- [ ] Railway CLI установлен
- [ ] `railway login` выполнен
- [ ] RAILWAY_TOKEN получен
- [ ] RAILWAY_TOKEN добавлен в GitHub Secrets
- [ ] DEPLOY_PLATFORM = railway в GitHub Variables
- [ ] Railway проект создан (`railway init`)
- [ ] Переменные окружения настроены (PORT, TODOIST_API_TOKEN)
- [ ] `railway.json` существует в mcp-server/
- [ ] Dockerfile существует в mcp-server/

### После деплоя:
- [ ] `railway up` выполнен успешно
- [ ] URL доступен (curl проверка)
- [ ] GitHub Actions workflow прошел успешно
- [ ] Логи не показывают ошибок
- [ ] Android приложение подключается к Railway URL

---

## 🎉 Поздравляю!

Теперь ваш MCP сервер:
- ✅ Работает 24/7 на Railway
- ✅ Автоматически деплоится при push
- ✅ Доступен по HTTPS
- ✅ Имеет мониторинг и логи
- ✅ Бесплатно (500 часов/месяц)

**URL вашего сервера**: `https://chatbot-mcp.railway.app`

---

## 📚 Что дальше?

1. **Обновите Android приложение**: замените `http://10.0.2.2:3000` на Railway URL
2. **Настройте custom domain** (опционально): Railway Dashboard → Settings → Domains
3. **Мониторьте использование**: Railway Dashboard → Usage

---

**Вопросы или проблемы?** Создайте Issue на GitHub!
