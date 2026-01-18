# 🚀 Руководство по деплою ChatBot AI Assistant

## 📋 Содержание
1. [Android → Google Play Store](#android--google-play-store)
2. [Python MCP Server → Cloud](#python-mcp-server--cloud)
3. [Локальная разработка](#локальная-разработка)

---

## 📱 Android → Google Play Store

### Требования
- Google Play Console аккаунт
- Android Keystore для подписи APK
- Google Play API Service Account

### Шаг 1: Создание Keystore

```bash
keytool -genkey -v -keystore release-keystore.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias chatbot-release
```

**Сохраните:**
- Пароль keystore
- Alias ключа
- Пароль ключа

### Шаг 2: Конвертация Keystore в Base64

```bash
base64 -i release-keystore.jks | tr -d '\n' > keystore_base64.txt
```

### Шаг 3: Создание Google Play Service Account

1. Откройте [Google Cloud Console](https://console.cloud.google.com/)
2. Создайте новый проект или выберите существующий
3. Перейдите в **APIs & Services** → **Credentials**
4. Создайте **Service Account**
5. Скачайте JSON ключ
6. В Google Play Console: **Setup** → **API access** → свяжите Service Account
7. Выдайте права: **Admin (all permissions)**

### Шаг 4: Настройка GitHub Secrets

Перейдите в **Settings** → **Secrets and variables** → **Actions** и добавьте:

| Secret | Описание | Пример |
|--------|----------|--------|
| `KEYSTORE_BASE64` | Base64 содержимое keystore | `MIIKEQIBAzCCCc...` |
| `KEYSTORE_PASSWORD` | Пароль keystore | `my_secure_password` |
| `KEY_ALIAS` | Alias ключа | `chatbot-release` |
| `KEY_PASSWORD` | Пароль ключа | `my_key_password` |
| `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` | JSON ключ Service Account | `{"type":"service_account",...}` |

### Шаг 5: Создание релиза

#### Автоматический релиз (рекомендуется):
```bash
# 1. Обновите versionCode и versionName в app/build.gradle.kts
versionCode = 2
versionName = "1.1.0"

# 2. Закоммитьте изменения
git add app/build.gradle.kts
git commit -m "Bump version to 1.1.0"

# 3. Создайте и запушьте тег
git tag v1.1.0
git push origin v1.1.0

# 4. GitHub Actions автоматически:
#    - Соберет signed APK/AAB
#    - Загрузит в Google Play Console (Internal Testing)
#    - Создаст GitHub Release
```

#### Ручной релиз:
```bash
# Запустите workflow вручную
GitHub → Actions → "📱 Android Release to Play Store" → Run workflow
```

### Шаг 6: Проверка результата

1. **GitHub**: Проверьте **Releases** → найдите новый релиз с APK/AAB
2. **Google Play Console**: Перейдите в **Release** → **Internal testing** → проверьте новую версию
3. **Артефакты**: Скачайте APK/AAB из Actions → Artifacts

---

## 🐍 Python MCP Server → Cloud

### Опции деплоя:
1. **Railway** (рекомендуется для начала) - бесплатно 500 часов/месяц
2. **Render** - бесплатно с ограничениями
3. **Docker + VPS** - полный контроль

---

### Вариант 1: Railway 🚂

#### Шаг 1: Подготовка
1. Создайте аккаунт на [Railway.app](https://railway.app/)
2. Установите Railway CLI (опционально):
```bash
npm install -g @railway/cli
railway login
```

#### Шаг 2: Создание проекта
```bash
# Через CLI
cd mcp-server
railway init
railway up

# Или через веб-интерфейс:
# Railway → New Project → Deploy from GitHub repo
```

#### Шаг 3: Настройка переменных окружения
Railway Dashboard → Variables:
- `PORT` = `3000`
- `TODOIST_API_TOKEN` = ваш токен
- `GITHUB_TOKEN` = ваш GitHub token

#### Шаг 4: GitHub Actions
Добавьте в **GitHub Secrets**:
- `RAILWAY_TOKEN` - получите через `railway login`
- Установите `DEPLOY_PLATFORM` в **Variables**: `railway`

#### Шаг 5: Деплой
```bash
# Автоматический (при пуше в main, изменения в mcp-server/*)
git add .
git commit -m "Update MCP server"
git push origin main

# Ручной
GitHub → Actions → "🐍 Deploy MCP Server to Cloud" → Run workflow
```

---

### Вариант 2: Render 🎨

#### Шаг 1: Создание Web Service
1. Зарегистрируйтесь на [Render.com](https://render.com/)
2. Dashboard → **New** → **Web Service**
3. Connect your GitHub repository
4. Settings:
   - **Build Command**: `cd mcp-server && pip install -r requirements.txt`
   - **Start Command**: `cd mcp-server && python server.py`
   - **Port**: `3000`

#### Шаг 2: Environment Variables
- `TODOIST_API_TOKEN`
- `GITHUB_TOKEN`

#### Шаг 3: GitHub Actions
Добавьте в **GitHub Secrets**:
- `RENDER_SERVICE_ID` - ID вашего сервиса
- `RENDER_API_KEY` - API ключ из Render Account Settings
- Установите `DEPLOY_PLATFORM` в **Variables**: `render`

---

### Вариант 3: Docker + VPS 🐳

#### Шаг 1: Подготовка VPS
```bash
# На вашем VPS (Ubuntu/Debian)
sudo apt update
sudo apt install docker.io docker-compose
sudo systemctl start docker
sudo systemctl enable docker
```

#### Шаг 2: Docker Hub
1. Создайте аккаунт на [Docker Hub](https://hub.docker.com/)
2. Создайте репозиторий: `your-username/chatbot-mcp-server`

#### Шаг 3: GitHub Secrets
Добавьте:
- `DOCKERHUB_USERNAME` - ваш Docker Hub username
- `DOCKERHUB_TOKEN` - Docker Hub access token
- `VPS_HOST` - IP адрес VPS
- `VPS_USERNAME` - SSH username (обычно `root`)
- `VPS_SSH_KEY` - приватный SSH ключ
- `VPS_PORT` - SSH порт (по умолчанию `22`)
- Установите `DEPLOY_PLATFORM` в **Variables**: `docker`

#### Шаг 4: Генерация SSH ключа
```bash
# На вашем компьютере
ssh-keygen -t ed25519 -C "github-actions"
# Сохраните приватный ключ в GitHub Secret: VPS_SSH_KEY
# Добавьте публичный ключ на VPS: ~/.ssh/authorized_keys
```

#### Шаг 5: Деплой
```bash
# Автоматический
git push origin main

# Ручной
GitHub → Actions → "🐍 Deploy MCP Server to Cloud" → Run workflow
```

#### Шаг 6: Проверка на VPS
```bash
ssh user@your-vps-ip
docker ps
curl http://localhost:3000/
```

---

## 🛠️ Локальная разработка

### Docker Compose

```bash
cd mcp-server

# Создайте .env файл
cat > .env << EOF
TODOIST_API_TOKEN=your_token
GITHUB_TOKEN=your_github_token
EOF

# Запустите
docker-compose up -d

# Логи
docker-compose logs -f

# Остановка
docker-compose down
```

### Прямой запуск
```bash
cd mcp-server
pip install -r requirements.txt

export TODOIST_API_TOKEN=your_token
export GITHUB_TOKEN=your_github_token

python server.py
```

---

## 📊 Мониторинг

### Healthcheck эндпоинты:
- `GET /` - основной health check
- `GET /health` - детальный статус

### Railway:
- Railway Dashboard → Deployments → Logs
- Metrics автоматически собираются

### Render:
- Render Dashboard → Logs
- Metrics доступны в платной версии

### VPS:
```bash
# Логи контейнера
docker logs chatbot-mcp-server -f

# Статистика
docker stats chatbot-mcp-server

# Проверка работы
curl http://your-vps-ip:3000/
```

---

## 🔒 Безопасность

### Рекомендации:
1. **Никогда** не коммитьте ключи в репозиторий
2. Используйте **GitHub Secrets** для чувствительных данных
3. Регулярно ротируйте токены и ключи
4. Ограничьте права Service Accounts минимально необходимыми
5. Используйте HTTPS для production деплоя
6. Настройте firewall на VPS (разрешить только 22, 80, 443, 3000)

### Проверка секретов в коде:
```bash
# Установите git-secrets
brew install git-secrets  # macOS
# или
apt-get install git-secrets  # Linux

# Настройте
git secrets --install
git secrets --register-aws
git secrets --scan
```

---

## ❓ Troubleshooting

### Android Build Issues

**Проблема**: `Execution failed for task ':app:packageRelease'`
```bash
# Решение: Проверьте keystore.properties
cat keystore.properties
# Убедитесь что все пути и пароли корректны
```

**Проблема**: `Google Play API error: 403`
```bash
# Решение: Проверьте права Service Account
# Google Play Console → Setup → API access
# Убедитесь что Service Account имеет права "Admin"
```

### MCP Server Issues

**Проблема**: `Container exits immediately`
```bash
# Проверьте логи
docker logs chatbot-mcp-server

# Часто причина - отсутствие переменных окружения
docker run -e TODOIST_API_TOKEN=xxx -e GITHUB_TOKEN=yyy ...
```

**Проблема**: `Port already in use`
```bash
# Найдите процесс
lsof -i :3000
# Остановите
kill -9 <PID>
```

---

## 📞 Поддержка

Возникли проблемы? Создайте [Issue на GitHub](https://github.com/ozy-max/Chat-Bot/issues)

---

## 📝 Чеклист деплоя

### Android Release
- [ ] Обновлен versionCode
- [ ] Обновлен versionName
- [ ] Созданы GitHub Secrets (keystore, Google Play)
- [ ] Создан тег версии
- [ ] Проверен артефакт в Actions
- [ ] Проверен релиз в Google Play Console

### MCP Server
- [ ] Выбрана платформа (Railway/Render/VPS)
- [ ] Созданы необходимые Secrets
- [ ] Установлены переменные окружения
- [ ] Проверен health check после деплоя
- [ ] Настроен мониторинг
- [ ] Документирован URL сервера

---

**Последнее обновление**: Январь 2026
