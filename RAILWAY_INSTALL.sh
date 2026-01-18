#!/bin/bash

echo "🚂 RAILWAY SETUP SCRIPT"
echo "======================="
echo ""

# Проверка npm
if command -v npm &> /dev/null; then
    echo "✅ npm найден"
    echo "📦 Устанавливаю Railway CLI..."
    sudo npm install -g @railway/cli
else
    echo "⚠️ npm не найден, использую curl..."
    echo "📦 Устанавливаю Railway CLI..."
    curl -fsSL https://railway.app/install.sh | sh
fi

echo ""
echo "✅ Railway CLI установлен!"
echo ""
echo "📝 Следующие шаги:"
echo "1. railway login    - Войдите в Railway"
echo "2. railway token    - Получите токен"
echo "3. Добавьте токен в GitHub Secrets"
echo ""
echo "🚀 Запустите: bash RAILWAY_INSTALL.sh"
