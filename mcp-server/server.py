#!/usr/bin/env python3
"""
MCP HTTP Server с системой напоминаний и агентом 24/7
Запуск: python3 server.py
"""

import sys
import os
# Добавляем пользовательские пакеты Python в путь
sys.path.insert(0, os.path.expanduser('~/Library/Python/3.9/lib/python/site-packages'))

from http.server import HTTPServer, BaseHTTPRequestHandler
import json
import socket
import random
import sqlite3
import threading
import time
from datetime import datetime, timedelta
import urllib.request
import urllib.parse
import os

# ============================================
# CONFIGURATION
# ============================================

# Todoist API
TODOIST_API_TOKEN = os.getenv("TODOIST_API_TOKEN", "")
TODOIST_PROJECT_ID = os.getenv("TODOIST_PROJECT_ID", "")  # ID проекта (необязательно)

# ============================================
# DATABASE
# ============================================

DB_FILE = "tasks.db"

def init_database():
    """Инициализация базы данных"""
    conn = sqlite3.connect(DB_FILE)
    cursor = conn.cursor()
    
    cursor.execute('''
        CREATE TABLE IF NOT EXISTS tasks (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            title TEXT NOT NULL,
            description TEXT,
            created_at TEXT NOT NULL,
            completed_at TEXT,
            status TEXT DEFAULT 'pending',
            user_token TEXT
        )
    ''')
    
    cursor.execute('''
        CREATE TABLE IF NOT EXISTS daily_summaries (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            date TEXT NOT NULL,
            summary TEXT NOT NULL,
            tasks_completed INTEGER,
            created_at TEXT NOT NULL
        )
    ''')
    
    conn.commit()
    conn.close()
    print("✅ База данных инициализирована")

# ============================================
# TASK MANAGEMENT
# ============================================

def add_task(title, description="", user_token=None):
    """Добавить новую задачу"""
    conn = sqlite3.connect(DB_FILE)
    cursor = conn.cursor()
    
    now = datetime.now().isoformat()
    cursor.execute('''
        INSERT INTO tasks (title, description, created_at, user_token)
        VALUES (?, ?, ?, ?)
    ''', (title, description, now, user_token))
    
    task_id = cursor.lastrowid
    conn.commit()
    conn.close()
    
    return task_id

def list_tasks(status=None):
    """Получить список задач"""
    conn = sqlite3.connect(DB_FILE)
    cursor = conn.cursor()
    
    if status:
        cursor.execute('SELECT * FROM tasks WHERE status = ? ORDER BY created_at DESC', (status,))
    else:
        cursor.execute('SELECT * FROM tasks ORDER BY created_at DESC')
    
    tasks = []
    for row in cursor.fetchall():
        tasks.append({
            'id': row[0],
            'title': row[1],
            'description': row[2],
            'created_at': row[3],
            'completed_at': row[4],
            'status': row[5]
        })
    
    conn.close()
    return tasks

def complete_task(task_id):
    """Отметить задачу как выполненную"""
    conn = sqlite3.connect(DB_FILE)
    cursor = conn.cursor()
    
    now = datetime.now().isoformat()
    cursor.execute('''
        UPDATE tasks 
        SET status = 'completed', completed_at = ?
        WHERE id = ?
    ''', (now, task_id))
    
    conn.commit()
    conn.close()

def get_today_summary():
    """Получить сводку задач за сегодня"""
    conn = sqlite3.connect(DB_FILE)
    cursor = conn.cursor()
    
    today = datetime.now().date().isoformat()
    
    # Задачи, созданные сегодня
    cursor.execute('''
        SELECT COUNT(*) FROM tasks 
        WHERE DATE(created_at) = ?
    ''', (today,))
    created_today = cursor.fetchone()[0]
    
    # Задачи, завершенные сегодня
    cursor.execute('''
        SELECT * FROM tasks 
        WHERE DATE(completed_at) = ?
    ''', (today,))
    
    completed_tasks = []
    for row in cursor.fetchall():
        completed_tasks.append({
            'id': row[0],
            'title': row[1],
            'description': row[2]
        })
    
    # Всего активных задач
    cursor.execute('SELECT COUNT(*) FROM tasks WHERE status = "pending"')
    pending_count = cursor.fetchone()[0]
    
    conn.close()
    
    return {
        'date': today,
        'created_today': created_today,
        'completed_today': len(completed_tasks),
        'completed_tasks': completed_tasks,
        'pending_count': pending_count
    }

def save_daily_summary(summary_data):
    """Сохранить ежедневную сводку"""
    conn = sqlite3.connect(DB_FILE)
    cursor = conn.cursor()
    
    now = datetime.now().isoformat()
    summary_text = format_summary(summary_data)
    
    cursor.execute('''
        INSERT INTO daily_summaries (date, summary, tasks_completed, created_at)
        VALUES (?, ?, ?, ?)
    ''', (summary_data['date'], summary_text, summary_data['completed_today'], now))
    
    conn.commit()
    conn.close()

def format_summary(summary_data):
    """Форматировать сводку для отображения"""
    text = f"📊 Сводка за {summary_data['date']}\n\n"
    text += f"✅ Выполнено задач: {summary_data['completed_today']}\n"
    text += f"📝 Создано задач: {summary_data['created_today']}\n"
    text += f"⏳ Осталось активных: {summary_data['pending_count']}\n\n"
    
    if summary_data['completed_tasks']:
        text += "Завершенные задачи:\n"
        for i, task in enumerate(summary_data['completed_tasks'], 1):
            text += f"{i}. {task['title']}\n"
    else:
        text += "Сегодня не было завершено ни одной задачи 😔\n"
    
    return text

# ============================================
# TODOIST INTEGRATION
# ============================================

def sync_with_todoist():
    """
    Синхронизация задач с Todoist
    Возвращает количество импортированных задач
    """
    if not TODOIST_API_TOKEN:
        print("⚠️  Todoist не настроен (нет API токена)")
        return 0
    
    try:
        import requests  # Импортируем только когда нужно
    except ImportError:
        print("❌ Модуль 'requests' не установлен")
        print("   Установите: pip3 install requests")
        return 0
    
    try:
        print(f"\n🔄 Синхронизация с Todoist...")
        
        headers = {
            "Authorization": f"Bearer {TODOIST_API_TOKEN}",
            "Content-Type": "application/json"
        }
        
        # Получаем все активные задачи
        response = requests.get(
            "https://api.todoist.com/rest/v2/tasks",
            headers=headers,
            timeout=10
        )
        
        if response.status_code != 200:
            print(f"❌ Ошибка API Todoist: {response.status_code}")
            print(f"   {response.text}")
            return 0
        
        tasks_data = response.json()
        imported_count = 0
        updated_count = 0
        deleted_count = 0
        
        # Собираем ID всех задач из Todoist
        todoist_task_ids = set()
        for task in tasks_data:
            todoist_task_ids.add(str(task.get("id", "")))
        
        # Импортируем задачи в нашу БД
        conn = sqlite3.connect(DB_FILE)
        cursor = conn.cursor()
        
        for task in tasks_data:
            task_id = task.get("id", "")
            content = task.get("content", "")
            description = task.get("description", "")
            is_completed = task.get("is_completed", False)
            created_at = task.get("created_at", "")
            
            # Проверяем, есть ли уже такая задача (по ID Todoist)
            cursor.execute(
                'SELECT id, status FROM tasks WHERE description LIKE ?',
                (f"%[TODOIST-{task_id}]%",)
            )
            existing = cursor.fetchone()
            
            if existing:
                # Обновляем статус существующей задачи
                task_status = "completed" if is_completed else "pending"
                if existing[1] != task_status:
                    cursor.execute(
                        'UPDATE tasks SET status = ? WHERE id = ?',
                        (task_status, existing[0])
                    )
                    updated_count += 1
                    print(f"  🔄 Обновлено: {content}")
            else:
                # Добавляем новую задачу
                now = datetime.now().isoformat()
                full_description = f"[TODOIST-{task_id}] {description}" if description else f"[TODOIST-{task_id}]"
                task_status = "completed" if is_completed else "pending"
                
                cursor.execute('''
                    INSERT INTO tasks (title, description, created_at, status)
                    VALUES (?, ?, ?, ?)
                ''', (content, full_description, created_at or now, task_status))
                
                imported_count += 1
                print(f"  ✅ Импортировано: {content}")
        
        # Удаляем задачи которых больше нет в Todoist
        cursor.execute('SELECT id, title, description FROM tasks WHERE description LIKE "%[TODOIST-%"')
        local_tasks = cursor.fetchall()
        
        for local_task in local_tasks:
            task_id, title, desc = local_task
            # Извлекаем Todoist ID из описания
            if "[TODOIST-" in desc:
                todoist_id = desc.split("[TODOIST-")[1].split("]")[0]
                
                # Если этой задачи нет в Todoist - удаляем из локальной БД
                if todoist_id not in todoist_task_ids:
                    cursor.execute('DELETE FROM tasks WHERE id = ?', (task_id,))
                    deleted_count += 1
                    print(f"  🗑️ Удалено (не найдено в Todoist): {title}")
        
        conn.commit()
        conn.close()
        
        print(f"\n✅ Синхронизация завершена:")
        print(f"   📥 Импортировано новых: {imported_count}")
        print(f"   🔄 Обновлено: {updated_count}")
        print(f"   🗑️ Удалено: {deleted_count}\n")
        
        return imported_count + updated_count + deleted_count
        
    except requests.exceptions.RequestException as e:
        print(f"❌ Ошибка подключения к Todoist: {e}")
        return 0
    except Exception as e:
        print(f"❌ Ошибка синхронизации: {e}")
        return 0

# ============================================
# PUSH NOTIFICATIONS
# ============================================

def send_push_notification(title, body, data=None):
    """Отправить пуш-уведомление через FCM"""
    # TODO: Интеграция с Firebase Cloud Messaging
    # Пока что просто логируем
    print(f"📨 Push Notification:")
    print(f"   Title: {title}")
    print(f"   Body: {body}")
    if data:
        print(f"   Data: {data}")
    
    # В реальной реализации здесь будет запрос к FCM API
    # import requests
    # fcm_url = "https://fcm.googleapis.com/fcm/send"
    # headers = {
    #     "Authorization": "key=YOUR_SERVER_KEY",
    #     "Content-Type": "application/json"
    # }
    # payload = {
    #     "to": device_token,
    #     "notification": {"title": title, "body": body},
    #     "data": data
    # }
    # requests.post(fcm_url, json=payload, headers=headers)

# ============================================
# BACKGROUND SCHEDULER
# ============================================

class DailyScheduler:
    """Планировщик для ежедневных сводок"""
    
    def __init__(self, hour=18, minute=0):
        self.hour = hour
        self.minute = minute
        self.running = False
        self.thread = None
    
    def start(self):
        """Запустить планировщик"""
        self.running = True
        self.thread = threading.Thread(target=self._run, daemon=True)
        self.thread.start()
        print(f"⏰ Планировщик запущен: ежедневная сводка в {self.hour:02d}:{self.minute:02d}")
    
    def stop(self):
        """Остановить планировщик"""
        self.running = False
        if self.thread:
            self.thread.join()
    
    def _run(self):
        """Основной цикл планировщика"""
        while self.running:
            now = datetime.now()
            target_time = now.replace(hour=self.hour, minute=self.minute, second=0, microsecond=0)
            
            # Если целевое время уже прошло сегодня, планируем на завтра
            if now >= target_time:
                target_time += timedelta(days=1)
            
            # Вычисляем время ожидания
            wait_seconds = (target_time - now).total_seconds()
            
            print(f"⏰ Следующая сводка: {target_time.strftime('%Y-%m-%d %H:%M:%S')} (через {wait_seconds/3600:.1f} часов)")
            
            # Ждем до целевого времени (проверяем каждую минуту)
            while self.running and datetime.now() < target_time:
                time.sleep(60)  # Проверяем каждую минуту
            
            # Отправляем сводку
            if self.running:
                self._send_daily_summary()
    
    def _send_daily_summary(self):
        """Отправить ежедневную сводку"""
        print("\n" + "="*50)
        print("📊 Генерация ежедневной сводки...")
        
        # Сначала синхронизируем задачи из Todoist
        synced_count = sync_with_todoist()
        
        # Затем получаем сводку (уже с импортированными задачами)
        summary_data = get_today_summary()
        summary_text = format_summary(summary_data)
        
        # Сохраняем сводку
        save_daily_summary(summary_data)
        
        # Отправляем пуш-уведомление
        send_push_notification(
            title="📊 Ежедневная сводка задач",
            body=f"Выполнено: {summary_data['completed_today']}, Осталось: {summary_data['pending_count']}",
            data={"type": "daily_summary", "summary": summary_text}
        )
        
        print(summary_text)
        print("="*50 + "\n")

# ============================================
# PERIODIC SYNC SCHEDULER
# ============================================

class PeriodicSyncScheduler:
    """Планировщик периодической синхронизации с Todoist"""
    
    def __init__(self, interval_minutes=30):
        self.interval_minutes = interval_minutes
        self.running = False
        self.thread = None
        self.last_sync_time = None
        self.known_task_ids = set()  # ID задач которые уже видели
        self.interval_changed = False  # Флаг изменения интервала
        
    def start(self):
        """Запустить планировщик"""
        self.running = True
        # Загружаем существующие задачи в known_task_ids
        self._load_existing_tasks()
        self.thread = threading.Thread(target=self._run, daemon=True)
        self.thread.start()
        print(f"🔄 Периодическая синхронизация запущена: каждые {self.interval_minutes} минут")
    
    def stop(self):
        """Остановить планировщик"""
        self.running = False
        if self.thread:
            self.thread.join()
    
    def set_interval(self, minutes):
        """Изменить интервал синхронизации"""
        if minutes < 1:
            print("⚠️  Минимальный интервал: 1 минута")
            return False
        
        old_interval = self.interval_minutes
        self.interval_minutes = minutes
        self.interval_changed = True
        print(f"✅ Интервал изменен: {old_interval} → {minutes} минут")
        return True
    
    def _load_existing_tasks(self):
        """Загрузить ID существующих задач из БД"""
        try:
            conn = sqlite3.connect(DB_FILE)
            cursor = conn.cursor()
            cursor.execute('SELECT description FROM tasks')
            rows = cursor.fetchall()
            conn.close()
            
            # Извлекаем Todoist ID из описаний вида [TODOIST-123456]
            for row in rows:
                desc = row[0] or ""
                if "[TODOIST-" in desc:
                    todoist_id = desc.split("[TODOIST-")[1].split("]")[0]
                    self.known_task_ids.add(todoist_id)
            
            print(f"   Загружено {len(self.known_task_ids)} существующих задач")
        except Exception as e:
            print(f"⚠️  Ошибка загрузки задач: {e}")
    
    def _run(self):
        """Основной цикл планировщика"""
        while self.running:
            # Ждем interval_minutes
            wait_seconds = self.interval_minutes * 60
            print(f"⏰ Следующая синхронизация через {self.interval_minutes} минут")
            
            # Ждем с проверкой изменения интервала
            elapsed = 0
            while elapsed < wait_seconds and self.running:
                if self.interval_changed:
                    # Интервал изменился - перезапускаем ожидание
                    self.interval_changed = False
                    print(f"🔄 Перезапуск таймера с новым интервалом: {self.interval_minutes} минут")
                    break
                time.sleep(1)
                elapsed += 1
            
            # Если интервал не был изменен и таймер истек - проверяем задачи
            if not self.interval_changed and elapsed >= wait_seconds and self.running:
                self._check_for_new_tasks()
    
    def _check_for_new_tasks(self):
        """Проверить новые задачи в Todoist"""
        if not TODOIST_API_TOKEN:
            return
        
        try:
            import requests
        except ImportError:
            print("❌ Модуль 'requests' не установлен")
            return
        
        try:
            print(f"\n🔍 Проверка новых задач в Todoist...")
            
            headers = {
                "Authorization": f"Bearer {TODOIST_API_TOKEN}",
                "Content-Type": "application/json"
            }
            
            # Получаем все активные задачи
            response = requests.get(
                "https://api.todoist.com/rest/v2/tasks",
                headers=headers,
                timeout=10
            )
            
            if response.status_code != 200:
                print(f"❌ Ошибка API Todoist: {response.status_code}")
                return
            
            tasks_data = response.json()
            new_tasks = []
            
            # Проверяем какие задачи новые
            for task in tasks_data:
                task_id = str(task.get("id", ""))
                if task_id not in self.known_task_ids:
                    new_tasks.append(task)
                    self.known_task_ids.add(task_id)
            
            if new_tasks:
                print(f"✨ Найдено новых задач: {len(new_tasks)}")
                
                # Синхронизируем с БД
                self._import_new_tasks(new_tasks)
                
                # Отправляем уведомления
                for task in new_tasks:
                    content = task.get("content", "")
                    send_push_notification(
                        title="📥 Новая задача из Todoist",
                        body=content,
                        data={"type": "new_task", "task_id": str(task.get("id", ""))}
                    )
                    print(f"   📬 Уведомление: {content}")
            else:
                print("   ℹ️  Новых задач нет")
            
            self.last_sync_time = datetime.now()
            
        except Exception as e:
            print(f"❌ Ошибка проверки новых задач: {e}")
    
    def _import_new_tasks(self, tasks):
        """Импортировать новые задачи в БД"""
        try:
            conn = sqlite3.connect(DB_FILE)
            cursor = conn.cursor()
            
            for task in tasks:
                task_id = task.get("id", "")
                content = task.get("content", "")
                description = task.get("description", "")
                is_completed = task.get("is_completed", False)
                created_at = task.get("created_at", "")
                
                now = datetime.now().isoformat()
                full_description = f"[TODOIST-{task_id}] {description}" if description else f"[TODOIST-{task_id}]"
                task_status = "completed" if is_completed else "pending"
                
                cursor.execute('''
                    INSERT INTO tasks (title, description, created_at, status)
                    VALUES (?, ?, ?, ?)
                ''', (content, full_description, created_at or now, task_status))
                
                print(f"   ✅ Импортировано: {content}")
            
            conn.commit()
            conn.close()
            
        except Exception as e:
            print(f"❌ Ошибка импорта задач: {e}")

# ============================================
# WEATHER API
# ============================================

def get_real_weather(city):
    """Получить реальную погоду через wttr.in API"""
    try:
        city_encoded = urllib.parse.quote(city)
        url = f"https://wttr.in/{city_encoded}?format=j1"
        
        req = urllib.request.Request(url)
        req.add_header('User-Agent', 'Mozilla/5.0')
        
        with urllib.request.urlopen(req, timeout=15) as response:
            data = json.loads(response.read().decode())
            
            current = data['current_condition'][0]
            temp = current['temp_C']
            feels_like = current['FeelsLikeC']
            humidity = current['humidity']
            weather_desc = current['weatherDesc'][0]['value']
            wind_speed = current['windspeedKmph']
            
            return f"""🌍 Реальная погода в {city}:
🌡️ Температура: {temp}°C (ощущается как {feels_like}°C)
☁️ Условия: {weather_desc}
💧 Влажность: {humidity}%
💨 Ветер: {wind_speed} км/ч
✅ Данные получены с wttr.in API"""
    except Exception as e:
        return f"""🌍 Демо погода для {city}:
🌡️ Температура: {random.randint(15, 25)}°C
☁️ Условия: Переменная облачность
💧 Влажность: {random.randint(40, 70)}%
⚠️ Примечание: Реальное API недоступно ({str(e)[:50]})"""

# ============================================
# SUPPORT FUNCTIONS
# ============================================

# Пути к данным (относительно MCP сервера или абсолютные)
SUPPORT_DATA_PATH = "/Users/igorurev/FlutterProjects/ChatBot/app/src/main/assets/support_data"
PRODUCT_DOCS_PATH = "/Users/igorurev/FlutterProjects/ChatBot/app/src/main/assets/product_docs"

# Месяцы на русском
RUSSIAN_MONTHS = {
    1: "января", 2: "февраля", 3: "марта", 4: "апреля",
    5: "мая", 6: "июня", 7: "июля", 8: "августа",
    9: "сентября", 10: "октября", 11: "ноября", 12: "декабря"
}

def format_date_russian(iso_date_string):
    """Форматировать дату в формат 'dd месяц YYYY'"""
    try:
        # Парсим ISO дату
        if isinstance(iso_date_string, str):
            # Убираем Z в конце если есть
            date_str = iso_date_string.replace('Z', '+00:00')
            dt = datetime.fromisoformat(date_str)
        else:
            dt = iso_date_string
        
        # Форматируем: "15 января 2026"
        day = dt.day
        month = RUSSIAN_MONTHS[dt.month]
        year = dt.year
        
        return f"{day} {month} {year}"
    except Exception as e:
        # Если не удалось распарсить, возвращаем как есть
        return iso_date_string[:10] if isinstance(iso_date_string, str) else str(iso_date_string)

def load_users():
    """Загрузить данные пользователей"""
    try:
        with open(f"{SUPPORT_DATA_PATH}/users.json", "r", encoding="utf-8") as f:
            data = json.load(f)
            return data.get("users", [])
    except Exception as e:
        print(f"❌ Ошибка загрузки users.json: {e}")
        return []

def load_tickets():
    """Загрузить данные тикетов"""
    try:
        with open(f"{SUPPORT_DATA_PATH}/tickets.json", "r", encoding="utf-8") as f:
            data = json.load(f)
            return data.get("tickets", [])
    except Exception as e:
        print(f"❌ Ошибка загрузки tickets.json: {e}")
        return []

def save_tickets(tickets):
    """Сохранить данные тикетов"""
    try:
        with open(f"{SUPPORT_DATA_PATH}/tickets.json", "w", encoding="utf-8") as f:
            json.dump({"tickets": tickets}, f, ensure_ascii=False, indent=2)
        return True
    except Exception as e:
        print(f"❌ Ошибка сохранения tickets.json: {e}")
        return False

def load_faq():
    """Загрузить FAQ"""
    try:
        with open(f"{PRODUCT_DOCS_PATH}/faq_common.txt", "r", encoding="utf-8") as f:
            return f.read()
    except Exception as e:
        print(f"❌ Ошибка загрузки FAQ: {e}")
        return ""

def search_faq(question):
    """Простой поиск в FAQ по ключевым словам"""
    faq = load_faq()
    if not faq:
        return "❌ FAQ не найден"
    
    # Разбиваем FAQ на блоки Q&A
    blocks = faq.split("\n\nQ:")
    
    # Ключевые слова из вопроса
    keywords = question.lower().split()
    
    best_match = None
    best_score = 0
    
    for block in blocks:
        if not block.strip():
            continue
        
        block_lower = block.lower()
        score = sum(1 for keyword in keywords if keyword in block_lower)
        
        if score > best_score:
            best_score = score
            best_match = block
    
    if best_match and best_score > 0:
        # Форматируем ответ
        if not best_match.startswith("Q:"):
            best_match = "Q:" + best_match
        return best_match.strip()
    
    return None

def get_user_info(user_id):
    """Получить информацию о пользователе"""
    users = load_users()
    for user in users:
        if user.get("id") == user_id:
            return user
    return None

def get_user_tickets(user_id, status_filter=None):
    """Получить тикеты пользователя"""
    tickets = load_tickets()
    user_tickets = [t for t in tickets if t.get("user_id") == user_id]
    
    if status_filter:
        user_tickets = [t for t in user_tickets if t.get("status") == status_filter]
    
    return user_tickets

def get_ticket_by_id(ticket_id):
    """Получить тикет по ID"""
    tickets = load_tickets()
    for ticket in tickets:
        if ticket.get("id") == ticket_id:
            return ticket
    return None

def create_support_ticket(user_id, subject, description, category="other", priority="medium"):
    """Создать новый тикет"""
    tickets = load_tickets()
    
    # Генерируем ID
    ticket_count = len(tickets) + 1
    new_id = f"TICKET-{ticket_count:03d}"
    
    # Создаем тикет
    now = datetime.now().isoformat() + "Z"
    new_ticket = {
        "id": new_id,
        "user_id": user_id,
        "status": "open",
        "priority": priority,
        "category": category,
        "subject": subject,
        "description": description,
        "created_at": now,
        "updated_at": now,
        "assigned_to": "support_team",
        "messages": []
    }
    
    tickets.append(new_ticket)
    save_tickets(tickets)
    
    return new_ticket

def get_support_stats():
    """Статистика по тикетам"""
    tickets = load_tickets()
    
    total = len(tickets)
    open_tickets = len([t for t in tickets if t.get("status") == "open"])
    in_progress = len([t for t in tickets if t.get("status") == "in_progress"])
    closed = len([t for t in tickets if t.get("status") == "closed"])
    
    # Статистика по категориям
    categories = {}
    for ticket in tickets:
        cat = ticket.get("category", "other")
        categories[cat] = categories.get(cat, 0) + 1
    
    return {
        "total": total,
        "open": open_tickets,
        "in_progress": in_progress,
        "closed": closed,
        "categories": categories
    }

# ============================================
# MCP TOOLS
# ============================================

TOOLS = [
    {
        "name": "get_weather",
        "description": "Получить текущую погоду для города",
        "inputSchema": {
            "type": "object",
            "properties": {
                "city": {"type": "string", "description": "Название города"}
            },
            "required": ["city"]
        }
    },
    {
        "name": "add_task",
        "description": "Добавить новую задачу в список",
        "inputSchema": {
            "type": "object",
            "properties": {
                "title": {"type": "string", "description": "Название задачи"},
                "description": {"type": "string", "description": "Описание задачи (необязательно)"}
            },
            "required": ["title"]
        }
    },
    {
        "name": "list_tasks",
        "description": "Получить список задач",
        "inputSchema": {
            "type": "object",
            "properties": {
                "status": {
                    "type": "string", 
                    "description": "Фильтр по статусу: pending (активные) или completed (завершенные)",
                    "enum": ["pending", "completed"]
                }
            },
            "required": []
        }
    },
    {
        "name": "complete_task",
        "description": "Отметить задачу как выполненную",
        "inputSchema": {
            "type": "object",
            "properties": {
                "task_id": {"type": "integer", "description": "ID задачи"}
            },
            "required": ["task_id"]
        }
    },
    {
        "name": "get_summary",
        "description": "Получить сводку задач за сегодня",
        "inputSchema": {
            "type": "object",
            "properties": {},
            "required": []
        }
    },
    {
        "name": "sync_todoist",
        "description": "Синхронизировать задачи с Todoist прямо сейчас",
        "inputSchema": {
            "type": "object",
            "properties": {},
            "required": []
        }
    },
    {
        "name": "get_time",
        "description": "Получить текущее время",
        "inputSchema": {
            "type": "object",
            "properties": {},
            "required": []
        }
    },
    {
        "name": "project_info",
        "description": "Получить информацию о проекте (Git ветка, файлы, коммиты)",
        "inputSchema": {
            "type": "object",
            "properties": {},
            "required": []
        }
    },
    {
        "name": "git_status",
        "description": "Получить Git статус проекта",
        "inputSchema": {
            "type": "object",
            "properties": {},
            "required": []
        }
    },
    {
        "name": "git_search",
        "description": "Поиск в файлах проекта через git grep",
        "inputSchema": {
            "type": "object",
            "properties": {
                "query": {"type": "string", "description": "Поисковый запрос"}
            },
            "required": ["query"]
        }
    },
    {
        "name": "github_list_kotlin_files",
        "description": "Получить список всех .kt файлов из GitHub репозитория",
        "inputSchema": {
            "type": "object",
            "properties": {
                "repo_url": {"type": "string", "description": "URL репозитория (https://github.com/user/repo)"},
                "branch": {"type": "string", "description": "Ветка (по умолчанию: main)"}
            },
            "required": ["repo_url"]
        }
    },
    {
        "name": "github_get_file_content",
        "description": "Получить содержимое файла из GitHub репозитория",
        "inputSchema": {
            "type": "object",
            "properties": {
                "repo_url": {"type": "string", "description": "URL репозитория"},
                "file_path": {"type": "string", "description": "Путь к файлу (app/src/main/java/MainActivity.kt)"},
                "branch": {"type": "string", "description": "Ветка (по умолчанию: main)"}
            },
            "required": ["repo_url", "file_path"]
        }
    },
    {
        "name": "local_list_kotlin_files",
        "description": "Получить список всех .kt файлов из локальной директории проекта",
        "inputSchema": {
            "type": "object",
            "properties": {
                "project_path": {"type": "string", "description": "Путь к проекту (по умолчанию: /Users/igorurev/FlutterProjects/ChatBot)"}
            },
            "required": []
        }
    },
    {
        "name": "local_get_file_content",
        "description": "Получить содержимое локального файла",
        "inputSchema": {
            "type": "object",
            "properties": {
                "file_path": {"type": "string", "description": "Полный путь к файлу"}
            },
            "required": ["file_path"]
        }
    },
    {
        "name": "support_answer",
        "description": "Ответить на вопрос пользователя о продукте используя FAQ и контекст тикетов",
        "inputSchema": {
            "type": "object",
            "properties": {
                "question": {"type": "string", "description": "Вопрос пользователя"},
                "user_id": {"type": "string", "description": "ID пользователя"},
                "user_name": {"type": "string", "description": "Имя пользователя"},
                "device_model": {"type": "string", "description": "Модель устройства"},
                "android_version": {"type": "string", "description": "Версия Android"}
            },
            "required": ["question"]
        }
    },
    {
        "name": "support_user_info",
        "description": "Получить информацию о пользователе",
        "inputSchema": {
            "type": "object",
            "properties": {
                "user_id": {"type": "string", "description": "ID пользователя"}
            },
            "required": ["user_id"]
        }
    },
    {
        "name": "support_tickets",
        "description": "Получить список тикетов пользователя",
        "inputSchema": {
            "type": "object",
            "properties": {
                "user_id": {"type": "string", "description": "ID пользователя"},
                "status": {"type": "string", "description": "Фильтр по статусу: open, in_progress, closed"}
            },
            "required": ["user_id"]
        }
    },
    {
        "name": "support_ticket_details",
        "description": "Получить детали конкретного тикета",
        "inputSchema": {
            "type": "object",
            "properties": {
                "ticket_id": {"type": "string", "description": "ID тикета"}
            },
            "required": ["ticket_id"]
        }
    },
    {
        "name": "support_create_ticket",
        "description": "Создать новый тикет поддержки",
        "inputSchema": {
            "type": "object",
            "properties": {
                "user_id": {"type": "string", "description": "ID пользователя"},
                "subject": {"type": "string", "description": "Тема тикета"},
                "description": {"type": "string", "description": "Описание проблемы"},
                "category": {"type": "string", "description": "Категория: authorization, rag, performance, mcp, feature_request, other"},
                "priority": {"type": "string", "description": "Приоритет: low, medium, high"}
            },
            "required": ["user_id", "subject", "description"]
        }
    },
    {
        "name": "support_stats",
        "description": "Получить статистику по тикетам поддержки",
        "inputSchema": {
            "type": "object",
            "properties": {},
            "required": []
        }
    }
]

def handle_tool_call(name, args):
    """Обработка вызова инструмента"""
    args = args or {}
    
    if name == "get_weather":
        city = args.get("city", "Moscow")
        weather_info = get_real_weather(city)
        return {"content": [{"type": "text", "text": weather_info}]}
    
    elif name == "add_task":
        title = args.get("title", "")
        description = args.get("description", "")
        
        if not title:
            return {"content": [{"type": "text", "text": "❌ Ошибка: название задачи не может быть пустым"}], "isError": True}
        
        task_id = add_task(title, description)
        return {"content": [{"type": "text", "text": f"✅ Задача #{task_id} добавлена: {title}"}]}
    
    elif name == "list_tasks":
        status = args.get("status")
        tasks = list_tasks(status)
        
        if not tasks:
            msg = "📋 Нет задач"
            if status == "pending":
                msg = "✅ Нет активных задач"
            elif status == "completed":
                msg = "📋 Нет завершенных задач"
            return {"content": [{"type": "text", "text": msg}]}
        
        text = f"📋 Список задач ({len(tasks)}):\n\n"
        for task in tasks:
            status_icon = "✅" if task['status'] == "completed" else "⏳"
            text += f"{status_icon} #{task['id']}: {task['title']}\n"
            if task['description']:
                text += f"   {task['description']}\n"
            text += f"   Создана: {task['created_at'][:10]}\n"
            if task['completed_at']:
                text += f"   Завершена: {task['completed_at'][:10]}\n"
            text += "\n"
        
        return {"content": [{"type": "text", "text": text}]}
    
    elif name == "complete_task":
        task_id = args.get("task_id")
        
        if not task_id:
            return {"content": [{"type": "text", "text": "❌ Ошибка: укажите ID задачи"}], "isError": True}
        
        complete_task(task_id)
        return {"content": [{"type": "text", "text": f"✅ Задача #{task_id} отмечена как выполненная"}]}
    
    elif name == "get_summary":
        summary_data = get_today_summary()
        summary_text = format_summary(summary_data)
        return {"content": [{"type": "text", "text": summary_text}]}
    
    elif name == "sync_todoist":
        synced_count = sync_with_todoist()
        
        if synced_count > 0:
            text = f"✅ Синхронизация завершена!\n\n📥 Синхронизировано задач с Todoist: {synced_count}\n\nИспользуйте /task list для просмотра всех задач."
        elif TODOIST_API_TOKEN:
            text = "ℹ️ Синхронизация завершена. Новых задач не найдено."
        else:
            text = "⚠️ Todoist не настроен.\n\nУстановите переменную окружения:\n- TODOIST_API_TOKEN\n\nПолучить токен: https://todoist.com/app/settings/integrations"
        
        return {"content": [{"type": "text", "text": text}]}
    
    elif name == "get_time":
        now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        return {"content": [{"type": "text", "text": f"🕐 Текущее время: {now}"}]}
    
    # ============================================
    # PROJECT & GIT COMMANDS
    # ============================================
    
    elif name == "project_info":
        try:
            import subprocess
            import os
            
            project_path = "/Users/igorurev/FlutterProjects/ChatBot"
            os.chdir(project_path)
            
            # Получить текущую ветку
            branch = subprocess.check_output(['git', 'branch', '--show-current'], text=True).strip()
            
            # Получить измененные файлы
            status_output = subprocess.check_output(['git', 'status', '--short'], text=True)
            changed_files = [line[3:].strip() for line in status_output.split('\n') if line.strip()]
            
            # Получить Kotlin файлы
            kotlin_files = subprocess.check_output(['git', 'ls-files', '*.kt'], text=True)
            kotlin_count = len([f for f in kotlin_files.split('\n') if f.strip()])
            
            # Получить последние коммиты
            commits = subprocess.check_output(['git', 'log', '--oneline', '-n', '3'], text=True)
            
            text = f"""📁 Информация о проекте
━━━━━━━━━━━━━━━━━━━━

🌿 Ветка: {branch}
📝 Изменённых файлов: {len(changed_files)}
📄 Kotlin файлов: {kotlin_count}

"""
            
            if changed_files:
                text += "📝 Изменённые файлы:\n"
                for f in changed_files[:10]:  # Максимум 10 файлов
                    text += f"   • {f}\n"
                text += "\n"
            
            text += f"📜 Последние коммиты:\n{commits}"
            
            return {"content": [{"type": "text", "text": text}]}
        except Exception as e:
            return {"content": [{"type": "text", "text": f"❌ Ошибка: {str(e)}"}], "isError": True}
    
    elif name == "git_status":
        try:
            import subprocess
            import os
            
            project_path = "/Users/igorurev/FlutterProjects/ChatBot"
            os.chdir(project_path)
            
            # Получить текущую ветку
            branch = subprocess.check_output(['git', 'branch', '--show-current'], text=True).strip()
            
            # Получить статус
            status = subprocess.check_output(['git', 'status', '--short'], text=True)
            
            # Получить последние коммиты
            commits = subprocess.check_output(['git', 'log', '--oneline', '-n', '5'], text=True)
            
            text = f"""🌿 Git статус проекта
━━━━━━━━━━━━━━━━━━━━

📌 Ветка: {branch}

"""
            
            if status.strip():
                text += f"📝 Изменения:\n{status}\n"
            else:
                text += "✅ Нет изменений\n\n"
            
            text += f"📜 Последние коммиты:\n{commits}"
            
            return {"content": [{"type": "text", "text": text}]}
        except Exception as e:
            return {"content": [{"type": "text", "text": f"❌ Ошибка: {str(e)}"}], "isError": True}
    
    elif name == "git_search":
        try:
            import subprocess
            import os
            
            query = args.get("query", "")
            if not query:
                return {"content": [{"type": "text", "text": "❌ Укажите поисковый запрос"}], "isError": True}
            
            project_path = "/Users/igorurev/FlutterProjects/ChatBot"
            os.chdir(project_path)
            
            # Поиск через git grep
            try:
                results = subprocess.check_output(['git', 'grep', '-n', query], text=True)
            except subprocess.CalledProcessError:
                results = ""
            
            text = f"""🔍 Результаты поиска: "{query}"
━━━━━━━━━━━━━━━━━━━━

"""
            
            if results.strip():
                # Ограничиваем вывод первыми 20 строками
                all_lines = results.strip().split('\n')
                lines = all_lines[:20]
                text += '\n'.join(lines)
                if len(all_lines) > 20:
                    remaining = len(all_lines) - 20
                    text += f"\n\n... и еще {remaining} результатов"
            else:
                text += "Ничего не найдено"
            
            return {"content": [{"type": "text", "text": text}]}
        except Exception as e:
            return {"content": [{"type": "text", "text": f"❌ Ошибка поиска: {str(e)}"}], "isError": True}
    
    # ============================================
    # GITHUB INTEGRATION
    # ============================================
    
    elif name == "github_list_kotlin_files":
        try:
            from github import Github
            import os
            
            repo_url = args.get("repo_url", "")
            branch = args.get("branch", "main")
            
            if not repo_url:
                return {"content": [{"type": "text", "text": "❌ Укажите URL репозитория"}], "isError": True}
            
            # Парсим repo_url (https://github.com/user/repo)
            parts = repo_url.rstrip('/').split('/')
            if len(parts) < 2:
                return {"content": [{"type": "text", "text": "❌ Некорректный URL репозитория"}], "isError": True}
            
            repo_name = f"{parts[-2]}/{parts[-1]}"
            
            # Подключаемся к GitHub (без токена для публичных репозиториев)
            g = Github()
            repo = g.get_repo(repo_name)
            
            # Получаем список всех .kt файлов (сканируем от корня)
            kt_files = []
            
            def scan_directory(path=""):
                try:
                    contents = repo.get_contents(path, ref=branch)
                    if not isinstance(contents, list):
                        contents = [contents]
                    
                    for content in contents:
                        if content.type == "dir":
                            # Пропускаем служебные директории
                            if content.name not in [".git", ".github", "build", "gradle", ".gradle", "node_modules"]:
                                scan_directory(content.path)
                        elif content.name.endswith(".kt"):
                            kt_files.append(content.path)
                except Exception as e:
                    # Игнорируем ошибки доступа к отдельным директориям
                    pass
            
            # Начинаем сканирование с корня
            scan_directory("")
            
            text = f"""📦 Репозиторий: {repo_name}
🌿 Ветка: {branch}
📂 Найдено Kotlin файлов: {len(kt_files)}

"""
            if kt_files:
                text += "📄 Список файлов:\n"
                for i, file_path in enumerate(kt_files[:50], 1):
                    text += f"{i}. {file_path}\n"
                if len(kt_files) > 50:
                    text += f"\n... и ещё {len(kt_files) - 50} файлов"
            else:
                text += "❌ Kotlin файлы не найдены во всём репозитории\n\n"
                text += "💡 Возможные причины:\n"
                text += "• Репозиторий приватный (нужен токен GitHub)\n"
                text += "• Неверная ветка (проверьте настройки)\n"
                text += "• В репозитории нет .kt файлов"
            
            return {"content": [{"type": "text", "text": text}], "files": kt_files}
        except Exception as e:
            return {"content": [{"type": "text", "text": f"❌ Ошибка доступа к GitHub: {str(e)}\n\nПроверьте:\n• URL репозитория корректен\n• Репозиторий публичный\n• Ветка существует"}], "isError": True}
    
    elif name == "github_get_file_content":
        try:
            from github import Github
            
            repo_url = args.get("repo_url", "")
            file_path = args.get("file_path", "")
            branch = args.get("branch", "main")
            
            if not repo_url or not file_path:
                return {"content": [{"type": "text", "text": "❌ Укажите repo_url и file_path"}], "isError": True}
            
            # Парсим repo_url
            parts = repo_url.rstrip('/').split('/')
            repo_name = f"{parts[-2]}/{parts[-1]}"
            
            # Подключаемся к GitHub
            g = Github()
            repo = g.get_repo(repo_name)
            
            # Получаем содержимое файла
            file_content = repo.get_contents(file_path, ref=branch)
            content = file_content.decoded_content.decode('utf-8')
            
            lines_count = len(content.split('\n'))
            size_kb = len(content) / 1024
            
            text = f"""📄 Файл: {file_path}
📦 Репозиторий: {repo_name}
🌿 Ветка: {branch}
📊 Размер: {size_kb:.1f} KB
📝 Строк: {lines_count}

{'='*50}

{content[:5000]}

{'='*50}

{'✂️ Содержимое обрезано (показаны первые 5000 символов)' if len(content) > 5000 else '✅ Полное содержимое'}
"""
            
            return {"content": [{"type": "text", "text": text}], "file_content": content}
        except Exception as e:
            return {"content": [{"type": "text", "text": f"❌ Ошибка чтения файла: {str(e)}"}], "isError": True}
    
    elif name == "local_list_kotlin_files":
        try:
            import os
            
            project_path = args.get("project_path", "/Users/igorurev/FlutterProjects/ChatBot")
            
            if not os.path.exists(project_path):
                return {"content": [{"type": "text", "text": f"❌ Директория не найдена: {project_path}"}], "isError": True}
            
            kt_files = []
            
            # Рекурсивно сканируем директорию
            for root, dirs, files in os.walk(project_path):
                # Пропускаем служебные директории
                dirs[:] = [d for d in dirs if d not in ['.git', '.github', 'build', 'gradle', '.gradle', 'node_modules', '.idea']]
                
                for file in files:
                    if file.endswith('.kt'):
                        full_path = os.path.join(root, file)
                        # Делаем путь относительным от project_path
                        relative_path = os.path.relpath(full_path, project_path)
                        kt_files.append(relative_path)
            
            text = f"""📁 Локальный проект: {project_path}
📂 Найдено Kotlin файлов: {len(kt_files)}

"""
            if kt_files:
                text += "📄 Список файлов:\n"
                for i, file_path in enumerate(kt_files[:50], 1):
                    text += f"{i}. {file_path}\n"
                if len(kt_files) > 50:
                    text += f"\n... и ещё {len(kt_files) - 50} файлов"
            else:
                text += "❌ Kotlin файлы не найдены в проекте"
            
            return {"content": [{"type": "text", "text": text}], "files": kt_files}
        except Exception as e:
            return {"content": [{"type": "text", "text": f"❌ Ошибка сканирования локальной директории: {str(e)}"}], "isError": True}
    
    elif name == "local_get_file_content":
        try:
            import os
            
            file_path = args.get("file_path", "")
            
            if not file_path:
                return {"content": [{"type": "text", "text": "❌ Укажите file_path"}], "isError": True}
            
            if not os.path.exists(file_path):
                return {"content": [{"type": "text", "text": f"❌ Файл не найден: {file_path}"}], "isError": True}
            
            with open(file_path, 'r', encoding='utf-8') as f:
                content = f.read()
            
            lines_count = len(content.split('\n'))
            size_kb = len(content) / 1024
            file_name = os.path.basename(file_path)
            
            text = f"""📄 Файл: {file_name}
📁 Путь: {file_path}
📊 Размер: {size_kb:.1f} KB
📝 Строк: {lines_count}

{'='*50}

{content[:5000]}

{'='*50}

{'✂️ Содержимое обрезано (показаны первые 5000 символов)' if len(content) > 5000 else '✅ Полное содержимое'}
"""
            
            return {"content": [{"type": "text", "text": text}], "file_content": content}
        except Exception as e:
            return {"content": [{"type": "text", "text": f"❌ Ошибка чтения файла: {str(e)}"}], "isError": True}
    
    # ============================================
    # SUPPORT COMMANDS
    # ============================================
    
    elif name == "support_answer":
        question = args.get("question", "")
        user_id = args.get("user_id", "user_001")
        user_name = args.get("user_name", "Пользователь")
        device_model = args.get("device_model", "Unknown Device")
        android_version = args.get("android_version", "Unknown")
        
        if not question:
            return {"content": [{"type": "text", "text": "❌ Укажите вопрос"}], "isError": True}
        
        # Получаем открытые тикеты пользователя (если есть в системе)
        open_tickets = get_user_tickets(user_id, "open")
        
        # Ищем ответ в FAQ
        faq_answer = search_faq(question)
        
        # Формируем ответ с динамическими данными
        text = f"🛟 Служба поддержки\n━━━━━━━━━━━━━━━━━━━━\n\n"
        text += f"👤 Пользователь: {user_name}\n"
        text += f"📱 Устройство: {device_model} (Android {android_version})\n"
        text += f"🆔 ID: {user_id}\n\n"
        
        if open_tickets:
            text += f"📋 У вас есть {len(open_tickets)} открытых тикетов:\n"
            for ticket in open_tickets[:2]:
                text += f"  • {ticket['id']}: {ticket['subject']}\n"
            text += "\n"
        
        text += f"💬 Вопрос: {question}\n\n"
        
        if faq_answer:
            text += f"📚 Ответ из FAQ:\n\n{faq_answer}\n\n"
            text += "✅ Надеюсь, это помогло! Если проблема сохраняется, используйте /ticket для создания тикета."
        else:
            text += "❓ К сожалению, точного ответа в FAQ нет.\n\n"
            text += "Попробуйте:\n"
            text += "• Уточнить вопрос более конкретно\n"
            text += "• Использовать /ticket для создания тикета поддержки\n"
            text += "• Проверить раздел Troubleshooting в документации"
        
        return {"content": [{"type": "text", "text": text}]}
    
    elif name == "support_user_info":
        user_id = args.get("user_id", "")
        if not user_id:
            return {"content": [{"type": "text", "text": "❌ Укажите user_id"}], "isError": True}
        
        user = get_user_info(user_id)
        if not user:
            return {"content": [{"type": "text", "text": f"❌ Пользователь {user_id} не найден"}], "isError": True}
        
        text = f"""👤 Информация о пользователе
━━━━━━━━━━━━━━━━━━━━

ID: {user['id']}
Имя: {user['name']}
Email: {user['email']}
Подписка: {user['subscription']}
Регистрация: {user['registration_date']}
Последний вход: {user['last_login']}
Устройство: {user['device']}
Android: {user['android_version']}
Версия приложения: {user['app_version']}"""
        
        return {"content": [{"type": "text", "text": text}]}
    
    elif name == "support_tickets":
        user_id = args.get("user_id", "")
        status_filter = args.get("status")
        
        if not user_id:
            return {"content": [{"type": "text", "text": "❌ Укажите user_id"}], "isError": True}
        
        tickets = get_user_tickets(user_id, status_filter)
        
        if not tickets:
            status_msg = f" со статусом '{status_filter}'" if status_filter else ""
            return {"content": [{"type": "text", "text": f"📋 У пользователя нет тикетов{status_msg}"}]}
        
        text = f"📋 Тикеты пользователя ({len(tickets)}):\n━━━━━━━━━━━━━━━━━━━━\n\n"
        
        for ticket in tickets:
            status_icon = {"open": "🟢", "in_progress": "🟡", "closed": "⚫"}.get(ticket['status'], "⚪")
            priority_icon = {"high": "🔴", "medium": "🟡", "low": "🟢"}.get(ticket['priority'], "⚪")
            
            text += f"{status_icon} {ticket['id']}: {ticket['subject']}\n"
            text += f"   Категория: {ticket['category']}\n"
            text += f"   Приоритет: {priority_icon} {ticket['priority']}\n"
            text += f"   Создан: {format_date_russian(ticket['created_at'])}\n\n"
        
        return {"content": [{"type": "text", "text": text}]}
    
    elif name == "support_ticket_details":
        ticket_id = args.get("ticket_id", "")
        if not ticket_id:
            return {"content": [{"type": "text", "text": "❌ Укажите ticket_id"}], "isError": True}
        
        ticket = get_ticket_by_id(ticket_id)
        if not ticket:
            return {"content": [{"type": "text", "text": f"❌ Тикет {ticket_id} не найден"}], "isError": True}
        
        status_icon = {"open": "🟢", "in_progress": "🟡", "closed": "⚫"}.get(ticket['status'], "⚪")
        
        text = f"""🎫 Детали тикета {ticket['id']}
━━━━━━━━━━━━━━━━━━━━

Тема: {ticket['subject']}
Статус: {status_icon} {ticket['status']}
Приоритет: {ticket['priority']}
Категория: {ticket['category']}
Создан: {format_date_russian(ticket['created_at'])}
Обновлен: {format_date_russian(ticket['updated_at'])}
Назначен: {ticket['assigned_to']}

Описание:
{ticket['description']}
"""
        
        if ticket.get('messages'):
            text += f"\n💬 Сообщения ({len(ticket['messages'])}):\n"
            for msg in ticket['messages']:
                sender = "👤" if msg['from'] == 'user' else "🛟"
                text += f"\n{sender} {msg['from']} ({format_date_russian(msg['timestamp'])}):\n{msg['text']}\n"
        
        if ticket.get('resolution'):
            text += f"\n✅ Решение:\n{ticket['resolution']}"
        
        return {"content": [{"type": "text", "text": text}]}
    
    elif name == "support_create_ticket":
        user_id = args.get("user_id", "")
        subject = args.get("subject", "")
        description = args.get("description", "")
        category = args.get("category", "other")
        priority = args.get("priority", "medium")
        
        if not user_id or not subject or not description:
            return {"content": [{"type": "text", "text": "❌ Укажите user_id, subject и description"}], "isError": True}
        
        # Создаем тикет без проверки существования пользователя
        # Это позволяет работать с динамическими user_id
        new_ticket = create_support_ticket(user_id, subject, description, category, priority)
        
        text = f"""✅ Тикет успешно создан!
━━━━━━━━━━━━━━━━━━━━

🎫 ID: {new_ticket['id']}
Тема: {new_ticket['subject']}
Категория: {new_ticket['category']}
Приоритет: {new_ticket['priority']}
Статус: {new_ticket['status']}

Ваш тикет принят в обработку.
Ожидаемое время ответа: 24 часа."""
        
        return {"content": [{"type": "text", "text": text}]}
    
    elif name == "support_stats":
        stats = get_support_stats()
        
        text = f"""📊 Статистика поддержки
━━━━━━━━━━━━━━━━━━━━

Всего тикетов: {stats['total']}
🟢 Открыто: {stats['open']}
🟡 В работе: {stats['in_progress']}
⚫ Закрыто: {stats['closed']}

📂 По категориям:"""
        
        for cat, count in stats['categories'].items():
            text += f"\n  • {cat}: {count}"
        
        return {"content": [{"type": "text", "text": text}]}
    
    return {"content": [{"type": "text", "text": f"❌ Неизвестный инструмент: {name}"}], "isError": True}

# ============================================
# HTTP SERVER
# ============================================

class MCPHandler(BaseHTTPRequestHandler):
    def do_POST(self):
        # Обработка /set_interval
        if self.path == "/set_interval":
            content_length = int(self.headers.get('Content-Length', 0))
            body = self.rfile.read(content_length).decode('utf-8')
            
            try:
                data = json.loads(body)
                interval_minutes = data.get("interval_minutes", 30)
                
                # Используем глобальную переменную sync_scheduler
                global sync_scheduler
                if sync_scheduler and sync_scheduler.set_interval(interval_minutes):
                    response = {"status": "success", "interval_minutes": interval_minutes}
                    self.send_response(200)
                else:
                    response = {"status": "error", "message": "Invalid interval"}
                    self.send_response(400)
                
                self.send_header('Content-Type', 'application/json')
                self.end_headers()
                self.wfile.write(json.dumps(response).encode('utf-8'))
                return
            except Exception as e:
                self.send_error(500, str(e))
                return
        
        # Обработка /set_todoist_token
        if self.path == "/set_todoist_token":
            content_length = int(self.headers.get('Content-Length', 0))
            body = self.rfile.read(content_length).decode('utf-8')
            
            try:
                data = json.loads(body)
                token = data.get("token", "")
                
                # Обновляем глобальную переменную TODOIST_API_TOKEN
                global TODOIST_API_TOKEN
                TODOIST_API_TOKEN = token
                
                print(f"✅ Todoist токен обновлён: {token[:10]}...")
                
                response = {"status": "success"}
                self.send_response(200)
                
                self.send_header('Content-Type', 'application/json')
                self.end_headers()
                self.wfile.write(json.dumps(response).encode('utf-8'))
                return
            except Exception as e:
                self.send_error(500, str(e))
                return
        
        # Обработка /mcp
        if self.path != "/mcp":
            self.send_error(404)
            return
        
        content_length = int(self.headers.get('Content-Length', 0))
        body = self.rfile.read(content_length).decode('utf-8')
        
        try:
            request = json.loads(body)
        except json.JSONDecodeError:
            self.send_error(400, "Invalid JSON")
            return
        
        method = request.get("method", "")
        params = request.get("params")
        req_id = request.get("id")
        
        print(f"📨 {method}", params if params else "")
        
        result = None
        error = None
        
        if method == "initialize":
            result = {
                "protocolVersion": "2024-11-05",
                "capabilities": {
                    "tools": {"listChanged": False},
                    "resources": {"subscribe": False, "listChanged": False},
                    "prompts": {"listChanged": False}
                },
                "serverInfo": {
                    "name": "MCP Reminder Agent Server",
                    "version": "2.0.0"
                }
            }
        
        elif method == "notifications/initialized":
            result = {}
        
        elif method == "tools/list":
            result = {"tools": TOOLS}
        
        elif method == "tools/call":
            name = params.get("name", "") if params else ""
            args = params.get("arguments") if params else None
            result = handle_tool_call(name, args)
        
        elif method == "resources/list":
            result = {"resources": []}
        
        elif method == "prompts/list":
            result = {"prompts": []}
        
        else:
            error = {"code": -32601, "message": f"Method not found: {method}"}
        
        response = {"jsonrpc": "2.0", "id": req_id}
        if error:
            response["error"] = error
            print(f"❌ Error: {error['message']}")
        else:
            response["result"] = result
            print(f"✅ OK")
        
        response_body = json.dumps(response).encode('utf-8')
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Content-Length', len(response_body))
        self.end_headers()
        self.wfile.write(response_body)
    
    def do_OPTIONS(self):
        self.send_response(200)
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Access-Control-Allow-Methods', 'POST, OPTIONS')
        self.send_header('Access-Control-Allow-Headers', 'Content-Type')
        self.end_headers()
    
    def log_message(self, format, *args):
        pass

def get_local_ip():
    """Получить локальный IP"""
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except:
        return "localhost"

# ============================================
# MAIN
# ============================================

# Глобальная переменная для планировщика синхронизации
sync_scheduler = None

if __name__ == "__main__":
    PORT = 3000
    IP = get_local_ip()
    
    # Инициализация
    init_database()
    
    # Запуск планировщика ежедневной сводки (18:00 каждый день)
    daily_scheduler = DailyScheduler(hour=18, minute=0)
    daily_scheduler.start()
    
    # Запуск планировщика периодической синхронизации (каждые 30 минут)
    sync_scheduler = PeriodicSyncScheduler(interval_minutes=30)
    sync_scheduler.start()
    
    print()
    print("🚀 MCP Reminder Agent Server запущен!")
    print()
    print("📱 Для подключения с Android используйте:")
    print(f"   http://{IP}:{PORT}/mcp")
    print()
    print("🔧 Доступные инструменты:")
    for t in TOOLS:
        print(f"   - {t['name']}: {t['description']}")
    print()
    print("Нажмите Ctrl+C для остановки")
    print()
    
    server = HTTPServer(("0.0.0.0", PORT), MCPHandler)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n👋 Остановка сервера...")
        daily_scheduler.stop()
        sync_scheduler.stop()
        server.shutdown()
        print("✅ Сервер остановлен")

