#!/usr/bin/env python3
"""
Скрипт для получения Organization ID через API Яндекс.Трекера
Использование: python3 get_org_id.py YOUR_TOKEN
"""

import sys
import requests
import json

if len(sys.argv) < 2:
    print("Использование: python3 get_org_id.py YOUR_OAUTH_TOKEN")
    sys.exit(1)

token = sys.argv[1]

# Получаем информацию о текущем пользователе
headers = {
    "Authorization": f"OAuth {token}",
    "Content-Type": "application/json"
}

try:
    # Получаем информацию о пользователе
    response = requests.get(
        "https://api.tracker.yandex.net/v2/myself",
        headers=headers,
        timeout=10
    )
    
    if response.status_code == 200:
        data = response.json()
        print("\n✅ Успешное подключение к API!")
        print(f"\nПользователь: {data.get('display', 'N/A')}")
        print(f"Email: {data.get('email', 'N/A')}")
        
        # Получаем список организаций
        org_response = requests.get(
            "https://api.tracker.yandex.net/v2/organizations",
            headers=headers,
            timeout=10
        )
        
        if org_response.status_code == 200:
            orgs = org_response.json()
            if orgs:
                print(f"\n📋 Ваши организации:")
                for org in orgs:
                    print(f"\n  Название: {org.get('name', 'N/A')}")
                    print(f"  Org ID: {org.get('id', 'N/A')}")
                    print(f"  URL: {org.get('url', 'N/A')}")
            else:
                print("\n⚠️ Организации не найдены")
        else:
            print(f"\n⚠️ Не удалось получить список организаций")
            print(f"Статус: {org_response.status_code}")
    
    elif response.status_code == 401:
        print("\n❌ Ошибка авторизации!")
        print("Проверьте правильность токена")
    else:
        print(f"\n❌ Ошибка: {response.status_code}")
        print(response.text)

except requests.exceptions.RequestException as e:
    print(f"\n❌ Ошибка подключения: {e}")
except Exception as e:
    print(f"\n❌ Ошибка: {e}")

