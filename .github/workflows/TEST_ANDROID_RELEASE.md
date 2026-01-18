# 🧪 Тестовый запуск Android Release Pipeline

## Этот файл создан для триггера пайплайна

**Дата создания:** 18 января 2026  
**Цель:** Проверить работу Android Release Pipeline без ключей

## Как запустить вручную:

1. Откройте: https://github.com/ozy-max/Chat-Bot/actions
2. Выберите workflow: **📱 Android Release to Play Store**
3. Нажмите: **Run workflow** (справа)
4. Выберите ветку: **main**
5. Нажмите: **Run workflow** (зеленая кнопка)

## Ожидаемый результат:

✅ Workflow запустится  
✅ Checkout code  
✅ Setup Java 17  
✅ Gradle cache  
⚠️ Decode Keystore - Предупреждение (keystore не найден)  
⚠️ Create keystore.properties - Предупреждение (credentials не найдены)  
✅ Build Release APK - **Соберет unsigned APK**  
✅ Build Release AAB - **Соберет unsigned AAB**  
✅ Upload APK as Artifact - **APK доступен для скачивания**  
✅ Upload AAB as Artifact - **AAB доступен для скачивания**  
⏭️ Upload to Google Play Console - **ПРОПУЩЕН** (нет тега)  
⏭️ Create GitHub Release - **ПРОПУЩЕН** (нет тега)  

**Итог:** 🟢 **SUCCESS** - Пайплайн завершится успешно!

---

## Альтернативный способ (через тег):

Если хотите увидеть полный запуск с попыткой загрузки в Play Store:

```bash
git tag v0.0.1-test
git push origin v0.0.1-test
```

**Результат:** Упадет на шаге "Upload to Google Play" (ожидаемо, ключей нет).

---

**Этот тест докажет, что пайплайн работает и готов к использованию!**
