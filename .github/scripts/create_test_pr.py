#!/usr/bin/env python3
"""Создать тестовый PR для демонстрации AI Review"""

import os
from github import Github

GITHUB_TOKEN = os.getenv("GITHUB_TOKEN", "")
REPO_NAME = "ozy-max/Chat-Bot"

def create_pr():
    if not GITHUB_TOKEN:
        print("❌ Установите GITHUB_TOKEN")
        print("Или создайте PR вручную: https://github.com/ozy-max/Chat-Bot/pull/new/test/ai-pr-review-demo")
        return
    
    try:
        github = Github(GITHUB_TOKEN)
        repo = github.get_repo(REPO_NAME)
        
        pr = repo.create_pull(
            title="🧪 Test: Demo AI PR Review System",
            body="""## 🎯 Цель
Тестовый PR для демонстрации автоматического AI Code Review

## 📝 Изменения
- Добавлен `TestUtils.kt` с несколькими намеренными проблемами:
  - ❌ Слишком простая валидация email
  - ❌ Отсутствуют проверки на null
  - ❌ Рекурсивный factorial без защиты от StackOverflow
  - ❌ Нет валидации входных параметров

## 🤖 Ожидаемое поведение
AI должен обнаружить эти проблемы и предложить улучшения.

## ✅ Чеклист
- [x] Код компилируется
- [ ] Добавлены тесты
- [ ] Обновлена документация
""",
            head="test/ai-pr-review-demo",
            base="main"
        )
        
        print(f"✅ PR создан: {pr.html_url}")
        print(f"📋 Номер PR: #{pr.number}")
        
    except Exception as e:
        print(f"❌ Ошибка: {e}")
        print("\nСоздайте PR вручную:")
        print("https://github.com/ozy-max/Chat-Bot/pull/new/test/ai-pr-review-demo")

if __name__ == "__main__":
    create_pr()
