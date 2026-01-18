#!/bin/bash

# 🚂 Railway Automatic Setup Script
# Автоматизирует весь процесс настройки Railway для деплоя MCP сервера

set -e  # Exit on error

echo "🚂 RAILWAY AUTOMATIC SETUP"
echo "=========================="
echo ""

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Проверка Railway CLI
echo "📦 Шаг 1/4: Проверка Railway CLI..."
if ! command -v railway &> /dev/null; then
    echo -e "${YELLOW}⚠️  Railway CLI не найден${NC}"
    echo "📥 Устанавливаю Railway CLI..."
    
    # Попытка установки через npm
    if command -v npm &> /dev/null; then
        npm install -g @railway/cli
    # Попытка установки через Homebrew (macOS)
    elif command -v brew &> /dev/null; then
        brew install railway
    else
        echo -e "${RED}❌ Не удалось установить Railway CLI автоматически${NC}"
        echo "Установите вручную:"
        echo "  macOS: brew install railway"
        echo "  npm:   npm install -g @railway/cli"
        exit 1
    fi
fi

railway --version
echo -e "${GREEN}✅ Railway CLI установлен${NC}"
echo ""

# Логин в Railway
echo "🔐 Шаг 2/4: Логин в Railway..."
if ! railway whoami &> /dev/null; then
    echo "🌐 Откроется браузер для входа в Railway..."
    railway login
fi

echo -e "${GREEN}✅ Вы вошли в Railway${NC}"
railway whoami
echo ""

# Получение токена
echo "🔑 Шаг 3/4: Получение токена..."
RAILWAY_TOKEN=$(railway token)

if [ -z "$RAILWAY_TOKEN" ]; then
    echo -e "${RED}❌ Не удалось получить токен${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Токен получен${NC}"
echo ""
echo "📋 Ваш Railway Token:"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "$RAILWAY_TOKEN"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# Инструкции для GitHub
echo "🎯 Шаг 4/4: Добавьте токен в GitHub"
echo ""
echo "📝 ИНСТРУКЦИЯ:"
echo ""
echo "1️⃣  Откройте в браузере:"
echo "   ${YELLOW}https://github.com/ozy-max/Chat-Bot/settings/secrets/actions${NC}"
echo ""
echo "2️⃣  Нажмите: ${GREEN}New repository secret${NC}"
echo ""
echo "3️⃣  Заполните:"
echo "   • Name:   ${GREEN}RAILWAY_TOKEN${NC}"
echo "   • Secret: ${GREEN}${RAILWAY_TOKEN}${NC}"
echo ""
echo "4️⃣  Нажмите: ${GREEN}Add secret${NC}"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "5️⃣  Переключитесь на вкладку: ${GREEN}Variables${NC}"
echo ""
echo "6️⃣  Нажмите: ${GREEN}New repository variable${NC}"
echo ""
echo "7️⃣  Заполните:"
echo "   • Name:  ${GREEN}DEPLOY_PLATFORM${NC}"
echo "   • Value: ${GREEN}railway${NC}"
echo ""
echo "8️⃣  Нажмите: ${GREEN}Add variable${NC}"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# Предложение скопировать токен в буфер обмена
if command -v pbcopy &> /dev/null; then
    echo "$RAILWAY_TOKEN" | pbcopy
    echo -e "${GREEN}✅ Токен скопирован в буфер обмена!${NC}"
    echo ""
elif command -v xclip &> /dev/null; then
    echo "$RAILWAY_TOKEN" | xclip -selection clipboard
    echo -e "${GREEN}✅ Токен скопирован в буфер обмена!${NC}"
    echo ""
fi

# Предложение открыть GitHub
echo "🌐 Открыть GitHub Secrets сейчас? (y/n)"
read -r response
if [[ "$response" =~ ^([yY][eE][sS]|[yY])$ ]]; then
    if command -v open &> /dev/null; then
        open "https://github.com/ozy-max/Chat-Bot/settings/secrets/actions"
    elif command -v xdg-open &> /dev/null; then
        xdg-open "https://github.com/ozy-max/Chat-Bot/settings/secrets/actions"
    else
        echo "Откройте вручную: https://github.com/ozy-max/Chat-Bot/settings/secrets/actions"
    fi
fi

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "✅ НАСТРОЙКА ЗАВЕРШЕНА!"
echo ""
echo "📝 СЛЕДУЮЩИЕ ШАГИ:"
echo ""
echo "1. Добавьте токен в GitHub (см. выше)"
echo "2. Запушьте изменения:"
echo "   ${GREEN}git push origin main${NC}"
echo ""
echo "3. Проверьте деплой:"
echo "   ${GREEN}https://github.com/ozy-max/Chat-Bot/actions${NC}"
echo ""
echo "4. После деплоя проверьте Railway:"
echo "   ${GREEN}https://railway.app/${NC}"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "🎉 Ваш MCP сервер будет задеплоен автоматически!"
echo ""
