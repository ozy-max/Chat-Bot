# 🚀 Быстрый старт: Пайплайны ChatBot

## 📱 Пайплайн 1: Android → Google Play Store

### Что делает:
- ✅ Автоматически собирает signed APK/AAB при создании тега
- ✅ Загружает в Google Play Console (Internal Testing)
- ✅ Создает GitHub Release с артефактами
- ✅ Работает без локальной машины

### Как использовать:

#### 1️⃣ **Настройка (один раз)**
```bash
# 1. Создайте keystore
keytool -genkey -v -keystore release-keystore.jks \
  -keyalg RSA -keysize 2048 -validity 10000 -alias chatbot-release

# 2. Конвертируйте в Base64
base64 -i release-keystore.jks | tr -d '\n' > keystore_base64.txt

# 3. Добавьте в GitHub Secrets:
#    - KEYSTORE_BASE64 (содержимое keystore_base64.txt)
#    - KEYSTORE_PASSWORD
#    - KEY_ALIAS
#    - KEY_PASSWORD
#    - GOOGLE_PLAY_SERVICE_ACCOUNT_JSON (JSON из Google Cloud)
```

#### 2️⃣ **Создание релиза**
```bash
# Обновите версию в app/build.gradle.kts
versionCode = 2
versionName = "1.1.0"

# Закоммитьте и создайте тег
git add app/build.gradle.kts
git commit -m "Release v1.1.0"
git tag v1.1.0
git push origin v1.1.0

# 🎉 Готово! Пайплайн запустится автоматически
```

#### 3️⃣ **Результат**
- 📦 APK/AAB в **GitHub Releases**
- 🏪 Новая версия в **Google Play Console** → **Internal Testing**
- 📊 Логи сборки в **Actions**

---

## 🐍 Пайплайн 2: Python MCP Server → Cloud

### Что делает:
- ✅ Автоматически деплоит MCP сервер при изменениях
- ✅ Поддерживает Railway / Render / Docker+VPS
- ✅ Создает Docker образ и пушит в Docker Hub
- ✅ SSH деплой на VPS

### Как использовать:

#### 1️⃣ **Выбор платформы**

##### **Вариант A: Railway (проще всего)**
```bash
# 1. Создайте аккаунт на railway.app
# 2. Создайте новый проект из GitHub repo
# 3. Добавьте переменные:
#    - TODOIST_API_TOKEN
#    - GITHUB_TOKEN
# 4. Получите RAILWAY_TOKEN:
railway login
railway token

# 5. Добавьте в GitHub:
#    Secrets: RAILWAY_TOKEN
#    Variables: DEPLOY_PLATFORM = railway
```

##### **Вариант B: Docker + VPS**
```bash
# 1. Создайте Docker Hub аккаунт
# 2. Настройте VPS (Ubuntu)
sudo apt install docker.io
sudo systemctl start docker

# 3. Создайте SSH ключ
ssh-keygen -t ed25519
# Добавьте публичный ключ на VPS: ~/.ssh/authorized_keys

# 4. Добавьте в GitHub Secrets:
#    - DOCKERHUB_USERNAME
#    - DOCKERHUB_TOKEN
#    - VPS_HOST
#    - VPS_USERNAME
#    - VPS_SSH_KEY (приватный ключ)
#    Variables: DEPLOY_PLATFORM = docker
```

#### 2️⃣ **Деплой**
```bash
# Автоматический (при пуше в main)
cd mcp-server
# Внесите изменения в server.py
git add server.py
git commit -m "Update MCP server"
git push origin main

# 🎉 Пайплайн задеплоит автоматически!

# Ручной
GitHub → Actions → "🐍 Deploy MCP Server" → Run workflow
```

#### 3️⃣ **Проверка**
```bash
# Railway
curl https://mcp-server.railway.app/

# VPS
curl http://YOUR_VPS_IP:3000/
```

---

## 🎯 Какой пайплайн когда запускается?

| Пайплайн | Триггер | Когда использовать |
|----------|---------|-------------------|
| **📱 Android Release** | Создание тега `v*.*.*` | Релиз новой версии приложения |
| **🐍 MCP Server Deploy** | Пуш в `main` с изменениями в `mcp-server/**` | Обновление MCP сервера |
| **🔍 AI PR Review** | Открытие/обновление PR | Автоматический код-ревью |

---

## ⚡ Чит-шит команд

### Android Release
```bash
# Быстрый релиз
./gradlew clean assembleRelease
git tag v1.2.0 && git push origin v1.2.0

# Проверка статуса
gh workflow view "📱 Android Release to Play Store"
gh run list --workflow=android-release.yml
```

### MCP Server
```bash
# Локальный тест Docker
cd mcp-server
docker build -t chatbot-mcp .
docker run -p 3000:3000 -e TODOIST_API_TOKEN=xxx chatbot-mcp

# Проверка деплоя
gh workflow view "🐍 Deploy MCP Server to Cloud"
gh run list --workflow=deploy-mcp-server.yml
```

---

## 🔧 Troubleshooting

### ❌ "Workflow not found"
```bash
# Убедитесь что файлы в правильных местах:
ls -la .github/workflows/
# android-release.yml
# deploy-mcp-server.yml
# ai-pr-review.yml
```

### ❌ "Secret not found"
```bash
# Проверьте Secrets в GitHub:
Settings → Secrets and variables → Actions
```

### ❌ "Build failed"
```bash
# Проверьте логи:
GitHub → Actions → выберите workflow → посмотрите красный шаг
```

---

## 📚 Подробная документация

- **Полная инструкция**: [DEPLOYMENT.md](./DEPLOYMENT.md)
- **README**: [README.md](./README.md)
- **Issues**: [GitHub Issues](https://github.com/ozy-max/Chat-Bot/issues)

---

## ✅ Чеклист готовности

### Перед первым Android релизом:
- [ ] Создан keystore
- [ ] Добавлены все 5 Secrets (KEYSTORE_*, GOOGLE_PLAY_*)
- [ ] Service Account привязан в Google Play Console
- [ ] Обновлен versionCode и versionName

### Перед первым MCP деплоем:
- [ ] Выбрана платформа (Railway/Render/VPS)
- [ ] Добавлены необходимые Secrets
- [ ] Установлен DEPLOY_PLATFORM в Variables
- [ ] Локально протестирован Docker образ

---

**Удачи с деплоем! 🚀**
