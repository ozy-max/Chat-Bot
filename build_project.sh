#!/bin/bash

###############################################################################
# BUILD PROJECT SCRIPT
###############################################################################
# Автоматическая сборка Android проекта с проверкой зависимостей
###############################################################################

set -e

# Цвета
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo "═══════════════════════════════════════════════════════════════"
echo "🔨 СБОРКА ANDROID ПРОЕКТА"
echo "═══════════════════════════════════════════════════════════════"
echo ""

# 1. Проверка Java
echo "🔍 Проверка Java..."
if command -v java &> /dev/null; then
    JAVA_VERSION=$(java -version 2>&1 | head -1 | cut -d'"' -f2)
    echo -e "${GREEN}✅ Java найдена: $JAVA_VERSION${NC}"
else
    echo -e "${RED}❌ Java не установлена!${NC}"
    echo ""
    echo "Установите Java 17:"
    echo "  brew install openjdk@17"
    echo ""
    echo "Или установите через Android Studio:"
    echo "  Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK"
    exit 1
fi

# 2. Проверка ANDROID_HOME
echo "🔍 Проверка Android SDK..."
if [ -z "$ANDROID_HOME" ]; then
    # Пытаемся найти Android SDK автоматически
    if [ -d "$HOME/Library/Android/sdk" ]; then
        export ANDROID_HOME="$HOME/Library/Android/sdk"
        echo -e "${YELLOW}⚠️  ANDROID_HOME не установлен, использую: $ANDROID_HOME${NC}"
    else
        echo -e "${RED}❌ Android SDK не найден!${NC}"
        echo ""
        echo "Установите Android Studio и SDK:"
        echo "  https://developer.android.com/studio"
        echo ""
        echo "Или установите через Homebrew:"
        echo "  brew install android-platform-tools"
        exit 1
    fi
else
    echo -e "${GREEN}✅ Android SDK: $ANDROID_HOME${NC}"
fi

# 3. Настройка PATH
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/tools:$PATH"

# 4. Проверка Gradle
echo "🔍 Проверка Gradle..."
if [ -f "./gradlew" ]; then
    chmod +x ./gradlew
    echo -e "${GREEN}✅ Gradle wrapper найден${NC}"
else
    echo -e "${RED}❌ gradlew не найден!${NC}"
    exit 1
fi

# 5. Очистка предыдущей сборки
echo ""
echo "🧹 Очистка предыдущей сборки..."
./gradlew clean

# 6. Сборка Debug APK
echo ""
echo "🔨 Сборка Debug APK..."
echo "   (Это может занять несколько минут при первой сборке)"
echo ""
./gradlew assembleDebug

# 7. Проверка результата
if [ -f "app/build/outputs/apk/debug/app-debug.apk" ]; then
    APK_SIZE=$(du -h app/build/outputs/apk/debug/app-debug.apk | cut -f1)
    echo ""
    echo "═══════════════════════════════════════════════════════════════"
    echo -e "${GREEN}✅ СБОРКА УСПЕШНА!${NC}"
    echo "═══════════════════════════════════════════════════════════════"
    echo ""
    echo "📦 APK создан:"
    echo "   Путь: app/build/outputs/apk/debug/app-debug.apk"
    echo "   Размер: $APK_SIZE"
    echo ""
    echo "📱 Установка на устройство/эмулятор:"
    echo "   ./gradlew installDebug"
    echo ""
    echo "   или вручную:"
    echo "   adb install -r app/build/outputs/apk/debug/app-debug.apk"
    echo ""
else
    echo ""
    echo "═══════════════════════════════════════════════════════════════"
    echo -e "${RED}❌ СБОРКА ЗАВЕРШИЛАСЬ С ОШИБКАМИ${NC}"
    echo "═══════════════════════════════════════════════════════════════"
    echo ""
    echo "Проверьте логи выше для деталей ошибки"
    echo ""
    exit 1
fi
