# 🚂 Railway - Автоматический деплой MCP Server

✨ **Полностью автоматизирован!** Просто добавьте токен в GitHub - CI/CD сделает всё остальное!

---

## 📋 Что получим в результате:
- ✅ MCP сервер доступен 24/7 по URL: `https://chatbot-mcp-server.railway.app`
- ✅ **Автоматическое создание проекта** (не нужно вручную!)
- ✅ **Автоматическая настройка переменных окружения**
- ✅ **Автоматический деплой** при каждом push в main
- ✅ Бесплатно 500 часов/месяц (~20 дней)
- ✅ Логи, мониторинг, метрики

---

## 🎯 Быстрый старт (3 шага):

### ✅ ШАГ 1: Получить Railway Token

```bash
# Установка Railway CLI
npm install -g @railway/cli
# или на macOS:
brew install railway

# Логин (откроется браузер)
railway login

# Получить токен
railway token
```

**Скопируйте токен!** Он выглядит примерно так:
```
railway_token_abc123def456...
```

---

### ✅ ШАГ 2: Добавить токен в GitHub

#### 2.1 Добавить RAILWAY_TOKEN в Secrets:

**Открыть в браузере:**
```
https://github.com/ozy-max/Chat-Bot/settings/secrets/actions
```

**Или перейти вручную:**
1. Репозиторий → **Settings**
2. Слева → **Secrets and variables** → **Actions**
3. **New repository secret**

**Добавить:**
- **Name**: `RAILWAY_TOKEN`
- **Secret**: (вставить скопированный токен)
- **Add secret**

#### 2.2 Добавить DEPLOY_PLATFORM в Variables:

На той же странице, вкладка **Variables**:

1. **New repository variable**
2. **Name**: `DEPLOY_PLATFORM`
3. **Value**: `railway`
4. **Add variable**

#### 2.3 (Опционально) Добавить Todoist Token:

Если хотите, чтобы MCP сервер работал с Todoist:

1. **New repository secret**
2. **Name**: `TODOIST_API_TOKEN`
3. **Secret**: (ваш Todoist API токен)
4. **Add secret**

---

### ✅ ШАГ 3: Запустить деплой

```bash
cd /Users/igorurev/FlutterProjects/ChatBot

# Запушить изменения
git push origin main
```

**Или запустить вручную:**
1. Открыть репозиторий на GitHub
2. **Actions** → **🐍 Deploy MCP Server to Cloud**
3. **Run workflow** → **Run workflow**

---

## 🎉 Готово!

После push:

1. **Откройте Actions на GitHub:**
   ```
   https://github.com/ozy-max/Chat-Bot/actions
   ```

2. **Дождитесь завершения** (2-3 минуты)

3. **Проверьте деплой:**
   - 🚂 Railway Dashboard: https://railway.app/
   - 🌐 Ваш сервер: `https://chatbot-mcp-server.railway.app`

---

## 🔧 Что происходит автоматически:

GitHub Actions пайплайн выполнит:

```yaml
✅ Установка Railway CLI
✅ Логин с токеном
✅ Проверка существования проекта
✅ Создание проекта (если не существует)
✅ Настройка переменных окружения:
   - PORT=3000
   - TODOIST_API_TOKEN
   - GITHUB_TOKEN
✅ Деплой кода
✅ Получение URL
```

**Вам ничего не нужно делать вручную!** 🎊

---

## 📊 Мониторинг и управление:

### Railway Dashboard:
```
https://railway.app/
```

**Что можно делать:**
- 📈 Просмотр логов
- 📊 Метрики (CPU, RAM, трафик)
- 🔧 Настройка переменных окружения
- 🌐 Настройка Custom Domain
- 💰 Просмотр использования (бесплатных часов)

### Полезные команды CLI:

```bash
cd mcp-server

# Просмотр статуса
railway status

# Просмотр логов
railway logs

# Просмотр переменных
railway variables

# Открыть в браузере
railway open
```

---

## 🔄 Обновление после деплоя:

**Автоматически:**
```bash
# Внесите изменения в mcp-server/
cd mcp-server
# Отредактируйте файлы

# Закоммитьте и запушьте
cd ..
git add .
git commit -m "Update MCP server"
git push origin main

# Пайплайн автоматически задеплоит! 🚀
```

**Вручную (если нужно):**
```bash
cd mcp-server
railway up
```

---

## 🌐 Настройка Custom Domain (опционально):

1. **Railway Dashboard** → Ваш проект → **Settings**
2. **Domains** → **Generate Domain** (получите бесплатный поддомен)
3. Или **Custom Domain** (если есть свой домен)

---

## 🐛 Устранение неполадок:

### ❌ "Project not found"

Пайплайн автоматически создаст проект при первом запуске. Если ошибка повторяется:

```bash
cd mcp-server
railway init --name chatbot-mcp-server
railway link
```

### ❌ "Invalid token"

Проверьте, что токен правильно добавлен в GitHub Secrets:
```
https://github.com/ozy-max/Chat-Bot/settings/secrets/actions
```

### ❌ "Build failed"

Проверьте логи в GitHub Actions:
```
https://github.com/ozy-max/Chat-Bot/actions
```

### ⚠️ Сервер не отвечает

1. Проверьте логи в Railway Dashboard
2. Убедитесь, что `PORT=3000` установлен в переменных
3. Проверьте, что `requirements.txt` и `Dockerfile` корректны

---

## 💰 Ограничения бесплатного плана:

- ⏰ **500 часов/месяц** (~20 дней)
- 💾 **512 MB RAM**
- 💿 **1 GB диск**
- 🌐 **100 GB трафика**

**Этого достаточно для тестирования и небольших проектов!**

Если нужно больше - можно перейти на платный план ($5/месяц).

---

## 📚 Дополнительно:

- 📖 **Railway Docs**: https://docs.railway.app/
- 💬 **Railway Discord**: https://discord.gg/railway
- 🎓 **Railway Templates**: https://railway.app/templates

---

## ✅ Checklist:

```
☐ Railway CLI установлен
☐ Railway токен получен
☐ RAILWAY_TOKEN добавлен в GitHub Secrets
☐ DEPLOY_PLATFORM='railway' добавлен в GitHub Variables
☐ TODOIST_API_TOKEN добавлен (опционально)
☐ Push в main выполнен
☐ GitHub Actions пайплайн запущен
☐ Деплой завершен успешно
☐ Сервер доступен по URL
☐ URL обновлен в Android приложении
```

---

## 🎊 Поздравляем!

Ваш MCP сервер задеплоен и работает 24/7 на Railway! 🚀

**Теперь ваше Android приложение может обращаться к нему из любой точки мира!** 🌍

---

**Есть вопросы?** Проверьте:
- 📖 `DEPLOYMENT.md` - полная документация по всем пайплайнам
- 📋 `MCP_DEPLOY_CONFIG.md` - сравнение платформ
- 🚀 `PIPELINES_QUICKSTART.md` - быстрый старт
