#!/usr/bin/env python3
"""Тест API ключа Claude"""

import os
import anthropic

ANTHROPIC_API_KEY = os.getenv("ANTHROPIC_API_KEY")

def test_api_key():
    if not ANTHROPIC_API_KEY:
        print("❌ ANTHROPIC_API_KEY не установлен")
        print("\nУстановите:")
        print('export ANTHROPIC_API_KEY="sk-ant-ваш-ключ"')
        return
    
    print(f"🔑 Проверяю API ключ: {ANTHROPIC_API_KEY[:20]}...")
    
    client = anthropic.Anthropic(api_key=ANTHROPIC_API_KEY)
    
    # Список моделей для тестирования
    models_to_test = [
        "claude-3-5-sonnet-20240620",
        "claude-3-opus-20240229",
        "claude-3-sonnet-20240229",
        "claude-3-haiku-20240307",
    ]
    
    print("\n📋 Проверяю доступные модели:\n")
    
    for model in models_to_test:
        try:
            print(f"Тестирую {model}...", end=" ")
            
            message = client.messages.create(
                model=model,
                max_tokens=10,
                messages=[{"role": "user", "content": "Hi"}]
            )
            
            print(f"✅ РАБОТАЕТ")
            print(f"   Ответ: {message.content[0].text}")
            print(f"   ✅ Используйте эту модель!\n")
            break
            
        except anthropic.NotFoundError as e:
            print(f"❌ Модель не найдена")
        except anthropic.AuthenticationError as e:
            print(f"❌ ОШИБКА АУТЕНТИФИКАЦИИ - проверьте API ключ!")
            print(f"   {e}")
            return
        except anthropic.PermissionDeniedError as e:
            print(f"❌ Нет доступа к этой модели")
        except Exception as e:
            print(f"❌ Ошибка: {e}")
    
    print("\n" + "="*60)
    print("Если все модели недоступны:")
    print("1. Проверьте API ключ на https://console.anthropic.com")
    print("2. Убедитесь что есть credits")
    print("3. Проверьте тарифный план")
    print("="*60)

if __name__ == "__main__":
    test_api_key()
