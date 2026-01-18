# 🐳 Руководство по настройке VPS для MCP Server

## 📋 Что вам понадобится:

1. **VPS сервер** (любой провайдер):
   - DigitalOcean ($6/месяц)
   - Hetzner Cloud (€4/месяц)
   - Timeweb (от 200₽/месяц)
   - Yandex Cloud (от 400₽/месяц)

2. **Характеристики** (минимум):
   - 1 vCPU
   - 1 GB RAM
   - 10 GB SSD
   - Ubuntu 22.04

---

## 🔧 Шаг 1: Настройка VPS

### Подключение к VPS:
```bash
ssh root@YOUR_VPS_IP
```

### Установка Docker:
```bash
# Обновление системы
apt update && apt upgrade -y

# Установка Docker
curl -fsSL https://get.docker.com -o get-docker.sh
sh get-docker.sh

# Проверка
docker --version
docker run hello-world

# Автозапуск Docker
systemctl enable docker
systemctl start docker
```

### Настройка firewall:
```bash
# Установка UFW
apt install ufw -y

# Разрешаем только нужные порты
ufw allow 22/tcp    # SSH
ufw allow 3000/tcp  # MCP Server
ufw allow 80/tcp    # HTTP (если планируете добавить Nginx)
ufw allow 443/tcp   # HTTPS (если планируете добавить SSL)

# Включаем firewall
ufw enable
ufw status
```

---

## 🔑 Шаг 2: Настройка SSH ключей

### На вашем компьютере:

```bash
# Генерация SSH ключа
ssh-keygen -t ed25519 -C "github-actions-chatbot"

# Сохраните в: ~/.ssh/chatbot_deploy_key
# НЕ УСТАНАВЛИВАЙТЕ ПАРОЛЬ (просто Enter)

# Скопируйте публичный ключ на VPS
ssh-copy-id -i ~/.ssh/chatbot_deploy_key.pub root@YOUR_VPS_IP

# Проверьте подключение
ssh -i ~/.ssh/chatbot_deploy_key root@YOUR_VPS_IP
```

### Добавление ключа в GitHub Secrets:

```bash
# Выведите приватный ключ
cat ~/.ssh/chatbot_deploy_key

# Скопируйте ВСЁ содержимое (включая -----BEGIN и -----END)
# GitHub → Settings → Secrets → New secret
# Name: VPS_SSH_KEY
# Value: (вставьте весь ключ)
```

---

## 🐳 Шаг 3: Настройка Docker Hub

### Создание Docker Hub токена:

1. Зарегистрируйтесь на [hub.docker.com](https://hub.docker.com/)
2. Account Settings → Security → New Access Token
3. Name: `github-actions-chatbot`
4. Permissions: `Read, Write, Delete`
5. Generate → Скопируйте токен

### Добавление в GitHub Secrets:
```
DOCKERHUB_USERNAME = ваш_username
DOCKERHUB_TOKEN = сгенерированный_токен
```

---

## 🔐 Шаг 4: GitHub Secrets - Полный список

Перейдите в **GitHub → Settings → Secrets and variables → Actions**

### Секреты (Secrets):

| Secret | Описание | Пример |
|--------|----------|--------|
| `DOCKERHUB_USERNAME` | Docker Hub username | `ivanivanov` |
| `DOCKERHUB_TOKEN` | Docker Hub access token | `dckr_pat_abc123...` |
| `VPS_HOST` | IP адрес VPS | `195.123.45.67` |
| `VPS_USERNAME` | SSH пользователь | `root` |
| `VPS_SSH_KEY` | Приватный SSH ключ | `-----BEGIN OPENSSH...` |
| `VPS_PORT` | SSH порт | `22` |
| `TODOIST_API_TOKEN` | Todoist API токен | `abc123...` |
| `GH_TOKEN` | GitHub Personal Access Token | `ghp_abc123...` (опционально) |

### Variables:

| Variable | Значение |
|----------|----------|
| `DEPLOY_PLATFORM` | `docker` |

---

## 🧪 Шаг 5: Тестирование

### Ручной тест на VPS:

```bash
# Подключитесь к VPS
ssh root@YOUR_VPS_IP

# Создайте тестовый контейнер
docker run -d \
  --name chatbot-mcp-test \
  --restart unless-stopped \
  -p 3000:3000 \
  -e TODOIST_API_TOKEN=your_token \
  -e GITHUB_TOKEN=your_token \
  python:3.9-slim \
  bash -c "apt-get update && apt-get install -y curl && python3 -m http.server 3000"

# Проверьте
docker ps
curl http://localhost:3000/

# Удалите тестовый контейнер
docker stop chatbot-mcp-test
docker rm chatbot-mcp-test
```

### Тест GitHub Actions (локально):

```bash
# Установите act (для локального тестирования workflows)
brew install act  # macOS
# или
curl https://raw.githubusercontent.com/nektos/act/master/install.sh | sudo bash

# Тест workflow
cd /Users/igorurev/FlutterProjects/ChatBot
act push -W .github/workflows/deploy-mcp-server.yml --secret-file .secrets
```

---

## 📊 Шаг 6: Мониторинг

### Команды для проверки:

```bash
# SSH на VPS
ssh root@YOUR_VPS_IP

# Проверка контейнеров
docker ps

# Логи
docker logs chatbot-mcp-server -f --tail 100

# Статистика ресурсов
docker stats chatbot-mcp-server

# Проверка работы
curl http://localhost:3000/
```

### Настройка логирования:

```bash
# Создайте директорию для логов
mkdir -p /opt/chatbot-logs

# Перезапустите контейнер с логированием
docker run -d \
  --name chatbot-mcp-server \
  --restart unless-stopped \
  -p 3000:3000 \
  -e TODOIST_API_TOKEN=$TODOIST_API_TOKEN \
  -e GITHUB_TOKEN=$GH_TOKEN \
  -v /opt/chatbot-data:/app/data \
  -v /opt/chatbot-logs:/app/logs \
  --log-driver json-file \
  --log-opt max-size=10m \
  --log-opt max-file=3 \
  your-dockerhub-username/chatbot-mcp-server:latest
```

---

## 🔄 Шаг 7: Автоматический деплой

После настройки всех Secrets:

```bash
# На вашем компьютере
cd /Users/igorurev/FlutterProjects/ChatBot
cd mcp-server

# Внесите изменения
vim server.py

# Закоммитьте и запушьте
git add server.py
git commit -m "Update MCP server"
git push origin main

# 🎉 GitHub Actions автоматически:
# 1. Соберет Docker образ
# 2. Запушит в Docker Hub
# 3. SSH подключится к VPS
# 4. Остановит старый контейнер
# 5. Запустит новый контейнер
# 6. Проверит health check
```

---

## 🔒 Безопасность

### Рекомендации:

1. **Отключите пароль SSH**:
```bash
# На VPS
vim /etc/ssh/sshd_config
# Установите: PasswordAuthentication no
systemctl restart sshd
```

2. **Используйте fail2ban**:
```bash
apt install fail2ban -y
systemctl enable fail2ban
systemctl start fail2ban
```

3. **Регулярно обновляйте**:
```bash
# Автоматические обновления безопасности
apt install unattended-upgrades -y
dpkg-reconfigure --priority=low unattended-upgrades
```

4. **Ротация токенов** (раз в 3-6 месяцев):
   - Docker Hub token
   - GitHub token
   - Todoist token

---

## 💰 Стоимость

### Примерные цены VPS:

| Провайдер | Цена/месяц | Характеристики |
|-----------|------------|----------------|
| **DigitalOcean** | $6 | 1 vCPU, 1 GB, 25 GB SSD |
| **Hetzner Cloud** | €4 (~₽400) | 1 vCPU, 2 GB, 20 GB SSD |
| **Timeweb** | от ₽200 | 1 vCPU, 512 MB, 10 GB |
| **Yandex Cloud** | от ₽400 | 2 vCPU, 1 GB, 10 GB |

**+ Дополнительно:**
- Docker Hub: Бесплатно (1 приватный репозиторий)
- GitHub Actions: Бесплатно (2000 минут/месяц)

---

## 🆘 Troubleshooting

### Проблема: Контейнер не запускается
```bash
# Проверьте логи
docker logs chatbot-mcp-server

# Часто причина - отсутствие переменных окружения
docker inspect chatbot-mcp-server | grep -A 20 Env
```

### Проблема: SSH подключение не работает
```bash
# Проверьте формат ключа
head -1 ~/.ssh/chatbot_deploy_key
# Должно быть: -----BEGIN OPENSSH PRIVATE KEY-----

# Проверьте права
chmod 600 ~/.ssh/chatbot_deploy_key
```

### Проблема: Порт 3000 недоступен извне
```bash
# Проверьте firewall
ufw status

# Проверьте что контейнер слушает 0.0.0.0
docker exec chatbot-mcp-server netstat -tuln | grep 3000
```

### Проблема: Docker Hub rate limit
```bash
# Логин в Docker Hub (увеличивает лимит)
docker login
```

---

## 📝 Чеклист настройки

- [ ] VPS создан и настроен
- [ ] Docker установлен на VPS
- [ ] Firewall настроен (порты 22, 3000)
- [ ] SSH ключ сгенерирован
- [ ] Публичный ключ на VPS
- [ ] Приватный ключ в GitHub Secrets
- [ ] Docker Hub аккаунт создан
- [ ] Docker Hub токен в Secrets
- [ ] Все 8 Secrets добавлены в GitHub
- [ ] DEPLOY_PLATFORM = docker в Variables
- [ ] Тестовый деплой выполнен успешно
- [ ] Health check проходит

---

## 🎯 Результат

После настройки:
- ✅ Автоматический деплой при пуше в main
- ✅ Ваш MCP сервер доступен 24/7
- ✅ Полный контроль над сервером
- ✅ Логирование и мониторинг
- ✅ Безопасное хранение секретов

**URL вашего MCP сервера**: `http://YOUR_VPS_IP:3000`

---

**Вопросы?** Создайте Issue на GitHub!
