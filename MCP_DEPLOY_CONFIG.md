# 🐍 MCP Server - Конфигурация деплоя

## 🎯 Что нужно для каждой платформы

---

## 🚂 Railway (Проще всего)

### GitHub Secrets (1 шт):
```
RAILWAY_TOKEN
```

### GitHub Variables:
```
DEPLOY_PLATFORM = railway
```

### Где взять токен:
```bash
npm install -g @railway/cli
railway login
railway token
```

### Стоимость:
- ✅ Бесплатно: 500 часов/месяц
- 💰 $5/месяц: Безлимит

### Время настройки: ~5 минут

---

## 🎨 Render

### GitHub Secrets (2 шт):
```
RENDER_SERVICE_ID
RENDER_API_KEY
```

### GitHub Variables:
```
DEPLOY_PLATFORM = render
```

### Где взять:
1. Создайте Web Service на render.com
2. SERVICE_ID: из URL (`srv-XXXXX`)
3. API_KEY: Account Settings → API Keys

### Стоимость:
- ✅ Бесплатно: 750 часов/месяц (засыпает через 15 мин)
- 💰 $7/месяц: Всегда активен

### Время настройки: ~10 минут

---

## 🐳 Docker + VPS (Полный контроль)

### GitHub Secrets (8 шт):
```
DOCKERHUB_USERNAME          # Docker Hub username
DOCKERHUB_TOKEN             # Docker Hub access token
VPS_HOST                    # IP адрес (195.123.45.67)
VPS_USERNAME                # SSH user (root)
VPS_SSH_KEY                 # Приватный SSH ключ
VPS_PORT                    # SSH порт (22)
TODOIST_API_TOKEN           # Todoist token
GH_TOKEN                    # GitHub token (опционально)
```

### GitHub Variables:
```
DEPLOY_PLATFORM = docker
```

### Что нужно:
1. **VPS** ($6/месяц на DigitalOcean)
2. **Docker Hub** аккаунт (бесплатно)
3. **SSH ключ** (сгенерировать)

### Стоимость:
- 💰 VPS: $4-6/месяц
- ✅ Docker Hub: Бесплатно
- ✅ GitHub Actions: Бесплатно

### Время настройки: ~30 минут

### Подробная инструкция:
➡️ [VPS_SETUP_GUIDE.md](./VPS_SETUP_GUIDE.md)

---

## 📊 Сравнение платформ

| Критерий | Railway | Render | VPS |
|----------|---------|--------|-----|
| **Сложность** | ⭐ Легко | ⭐⭐ Средне | ⭐⭐⭐ Сложно |
| **Настройка** | 5 мин | 10 мин | 30 мин |
| **Бесплатно** | 500 ч/мес | 750 ч/мес | ❌ |
| **Всегда активен** | ✅ | ❌ (засыпает) | ✅ |
| **Контроль** | ⭐⭐ | ⭐⭐ | ⭐⭐⭐⭐⭐ |
| **Цена** | $5/мес | $7/мес | $4-6/мес |

---

## 🎯 Рекомендации

### Для начала:
👉 **Railway** - проще всего настроить, бесплатно на старте

### Для production:
👉 **VPS** - полный контроль, лучшая цена, всегда активен

### Для небольших проектов:
👉 **Render** - хорошо для редко используемых сервисов

---

## ⚡ Быстрый старт

### Railway (5 минут):

```bash
# 1. Установите CLI
npm install -g @railway/cli

# 2. Логин и получите токен
railway login
railway token

# 3. Добавьте в GitHub:
#    Secrets: RAILWAY_TOKEN
#    Variables: DEPLOY_PLATFORM = railway

# 4. Деплой
git push origin main
# 🎉 Готово!
```

---

### Render (10 минут):

```bash
# 1. Зарегистрируйтесь на render.com
# 2. New → Web Service → Connect GitHub repo
# 3. Получите SERVICE_ID из URL
# 4. Account Settings → API Keys → Create
# 5. Добавьте в GitHub:
#    Secrets: RENDER_SERVICE_ID, RENDER_API_KEY
#    Variables: DEPLOY_PLATFORM = render
# 6. Деплой
git push origin main
# 🎉 Готово!
```

---

### VPS (30 минут):

```bash
# 1. Купите VPS (DigitalOcean, Hetzner, etc)
# 2. Установите Docker на VPS:
ssh root@YOUR_VPS_IP
curl -fsSL https://get.docker.com | sh

# 3. Создайте SSH ключ:
ssh-keygen -t ed25519 -C "chatbot"
ssh-copy-id root@YOUR_VPS_IP

# 4. Создайте Docker Hub аккаунт
# 5. Добавьте 8 Secrets в GitHub (см. выше)
# 6. Деплой:
git push origin main
# 🎉 Готово!
```

📘 **Полная инструкция VPS**: [VPS_SETUP_GUIDE.md](./VPS_SETUP_GUIDE.md)

---

## 🔍 Проверка деплоя

### Railway:
```bash
curl https://mcp-server.railway.app/
```

### Render:
```bash
curl https://mcp-server.onrender.com/
```

### VPS:
```bash
curl http://YOUR_VPS_IP:3000/
```

---

## 📚 Дополнительная документация

- 🚀 **Быстрый старт**: [PIPELINES_QUICKSTART.md](./PIPELINES_QUICKSTART.md)
- 📖 **Полное руководство**: [DEPLOYMENT.md](./DEPLOYMENT.md)
- 🐳 **Настройка VPS**: [VPS_SETUP_GUIDE.md](./VPS_SETUP_GUIDE.md)

---

## ❓ FAQ

**Q: Можно ли использовать бесплатно?**  
A: Да! Railway дает 500 часов бесплатно, Render - 750 часов.

**Q: Какая платформа лучше?**  
A: Для начала - Railway. Для production - VPS (дешевле и больше контроля).

**Q: Нужно ли что-то менять в коде?**  
A: Нет! Код уже готов, нужно только настроить Secrets.

**Q: Сколько времени занимает деплой?**  
A: Railway/Render: ~2 минуты. VPS: ~5 минут.

**Q: Как обновить сервер?**  
A: Просто сделайте `git push origin main` - деплой автоматический!

---

## ✅ Чеклист

### Минимальная конфигурация (Railway):
- [ ] Установлен Railway CLI
- [ ] Получен RAILWAY_TOKEN
- [ ] Добавлен в GitHub Secrets
- [ ] DEPLOY_PLATFORM = railway в Variables
- [ ] Сделан push в main

### Полная конфигурация (VPS):
- [ ] VPS куплен и настроен
- [ ] Docker установлен
- [ ] SSH ключ создан и добавлен
- [ ] Docker Hub аккаунт создан
- [ ] Все 8 Secrets в GitHub
- [ ] DEPLOY_PLATFORM = docker
- [ ] Firewall настроен
- [ ] Тестовый деплой успешен

---

**Готовы начать? Выберите платформу и следуйте инструкциям! 🚀**
