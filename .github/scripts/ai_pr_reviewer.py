#!/usr/bin/env python3
"""
AI PR Reviewer - автоматический ревью Pull Request
Использует RAG для контекста и Claude для анализа
"""

import os
import sys
import json
from pathlib import Path
from typing import List, Dict, Any
import anthropic
from github import Github
from sentence_transformers import SentenceTransformer
import chromadb
from chromadb.utils import embedding_functions

# Конфигурация
ANTHROPIC_API_KEY = os.getenv("ANTHROPIC_API_KEY")
GITHUB_TOKEN = os.getenv("GITHUB_TOKEN")
PR_NUMBER = int(os.getenv("PR_NUMBER", "0"))
REPO_NAME = os.getenv("GITHUB_REPOSITORY", "")

# Модели
EMBEDDING_MODEL = "all-MiniLM-L6-v2"
CLAUDE_MODEL = "claude-3-haiku-20240307"  # Claude 3 Haiku (быстрая, доступна на вашем тарифе)


class CodeRAG:
    """RAG система для индексации и поиска по коду"""
    
    def __init__(self, index_path="rag_index"):
        self.index_path = index_path
        self.client = chromadb.PersistentClient(path=index_path)
        self.embedding_fn = embedding_functions.SentenceTransformerEmbeddingFunction(
            model_name=EMBEDDING_MODEL
        )
        
        # Создаем или получаем коллекцию
        try:
            self.collection = self.client.get_collection(
                name="codebase",
                embedding_function=self.embedding_fn
            )
            print(f"📚 Загружена существующая коллекция: {self.collection.count()} документов")
        except:
            self.collection = self.client.create_collection(
                name="codebase",
                embedding_function=self.embedding_fn,
                metadata={"description": "Codebase knowledge base"}
            )
            print("📚 Создана новая коллекция")
    
    def index_codebase(self, repo_path="."):
        """Индексировать весь код проекта"""
        print("🔍 Индексация кодовой базы...")
        
        docs = []
        metadatas = []
        ids = []
        
        # Расширения файлов для индексации
        code_extensions = {'.kt', '.java', '.py', '.md', '.yml', '.yaml', '.kts', '.toml', '.gradle'}
        
        # Игнорируемые папки
        ignore_dirs = {'build', '.gradle', '.git', '.github', 'node_modules', 'rag_index'}
        
        count = 0
        for path in Path(repo_path).rglob('*'):
            # Пропускаем директории и игнорируемые папки
            if path.is_dir():
                continue
            
            if any(ignored in path.parts for ignored in ignore_dirs):
                continue
            
            if path.suffix not in code_extensions:
                continue
            
            try:
                content = path.read_text(encoding='utf-8', errors='ignore')
                
                # Разбиваем большие файлы на чанки
                if len(content) > 2000:
                    chunks = self._split_into_chunks(content, max_size=2000)
                    for i, chunk in enumerate(chunks):
                        docs.append(chunk)
                        metadatas.append({
                            'file_path': str(path),
                            'file_type': path.suffix,
                            'chunk_index': i
                        })
                        ids.append(f"{path}_{i}")
                        count += 1
                else:
                    docs.append(content)
                    metadatas.append({
                        'file_path': str(path),
                        'file_type': path.suffix,
                        'chunk_index': 0
                    })
                    ids.append(str(path))
                    count += 1
                
                if count % 10 == 0:
                    print(f"  Проиндексировано файлов: {count}")
                    
            except Exception as e:
                print(f"  ⚠️ Ошибка чтения {path}: {e}")
                continue
        
        # Добавляем все документы в ChromaDB
        if docs:
            # Батчами по 100 документов
            batch_size = 100
            for i in range(0, len(docs), batch_size):
                batch_docs = docs[i:i+batch_size]
                batch_metas = metadatas[i:i+batch_size]
                batch_ids = ids[i:i+batch_size]
                
                self.collection.add(
                    documents=batch_docs,
                    metadatas=batch_metas,
                    ids=batch_ids
                )
            
            print(f"✅ Проиндексировано {count} документов")
        else:
            print("⚠️ Не найдено документов для индексации")
    
    def _split_into_chunks(self, text: str, max_size: int = 2000) -> List[str]:
        """Разбить текст на чанки"""
        lines = text.split('\n')
        chunks = []
        current_chunk = []
        current_size = 0
        
        for line in lines:
            line_size = len(line) + 1  # +1 для \n
            if current_size + line_size > max_size and current_chunk:
                chunks.append('\n'.join(current_chunk))
                current_chunk = [line]
                current_size = line_size
            else:
                current_chunk.append(line)
                current_size += line_size
        
        if current_chunk:
            chunks.append('\n'.join(current_chunk))
        
        return chunks
    
    def search_similar_code(self, query: str, n_results: int = 5) -> List[Dict[str, Any]]:
        """Найти похожий код"""
        if self.collection.count() == 0:
            return []
        
        results = self.collection.query(
            query_texts=[query],
            n_results=min(n_results, self.collection.count())
        )
        
        similar_docs = []
        if results['documents'] and results['documents'][0]:
            for i, doc in enumerate(results['documents'][0]):
                similar_docs.append({
                    'content': doc,
                    'file_path': results['metadatas'][0][i]['file_path'],
                    'distance': results['distances'][0][i] if 'distances' in results else 0
                })
        
        return similar_docs


class PRAnalyzer:
    """Анализатор Pull Request"""
    
    def __init__(self):
        self.github = Github(GITHUB_TOKEN)
        self.claude = anthropic.Anthropic(api_key=ANTHROPIC_API_KEY)
        self.rag = CodeRAG()
    
    def get_pr_info(self) -> Dict[str, Any]:
        """Получить информацию о PR"""
        print(f"📋 Получение информации о PR #{PR_NUMBER}...")
        
        repo = self.github.get_repo(REPO_NAME)
        pr = repo.get_pull(PR_NUMBER)
        
        # Получаем diff
        diff_content = ""
        try:
            with open("pr_diff.txt", "r") as f:
                diff_content = f.read()
        except:
            print("⚠️ Не найден файл pr_diff.txt")
        
        # Получаем список измененных файлов
        files = pr.get_files()
        changed_files = []
        
        for file in files:
            changed_files.append({
                'filename': file.filename,
                'status': file.status,
                'additions': file.additions,
                'deletions': file.deletions,
                'changes': file.changes,
                'patch': file.patch if hasattr(file, 'patch') else None
            })
        
        return {
            'number': PR_NUMBER,
            'title': pr.title,
            'description': pr.body or "",
            'author': pr.user.login,
            'diff': diff_content,
            'files': changed_files,
            'base_ref': pr.base.ref,
            'head_ref': pr.head.ref
        }
    
    def analyze_pr_with_rag(self, pr_info: Dict[str, Any]) -> str:
        """Анализировать PR с использованием RAG и Claude"""
        print("🤖 Анализ PR с помощью Claude + RAG...")
        
        # Индексируем кодовую базу
        self.rag.index_codebase()
        
        # Поиск похожего кода для контекста
        rag_context = []
        for file in pr_info['files']:
            if file['patch']:
                similar = self.rag.search_similar_code(file['patch'], n_results=3)
                rag_context.extend(similar)
        
        # Формируем контекст из RAG
        rag_context_text = "\n\n".join([
            f"Файл: {item['file_path']}\n```\n{item['content'][:500]}\n```"
            for item in rag_context[:5]
        ])
        
        # Формируем промпт для Claude
        prompt = self._build_review_prompt(pr_info, rag_context_text)
        
        # Вызываем Claude
        try:
            message = self.claude.messages.create(
                model=CLAUDE_MODEL,
                max_tokens=4000,
                temperature=0.3,
                messages=[{
                    "role": "user",
                    "content": prompt
                }]
            )
            
            review = message.content[0].text
            return review
            
        except Exception as e:
            print(f"❌ Ошибка вызова Claude: {e}")
            return f"❌ Не удалось сгенерировать ревью: {str(e)}"
    
    def _build_review_prompt(self, pr_info: Dict[str, Any], rag_context: str) -> str:
        """Построить промпт для Claude"""
        
        # Список измененных файлов
        files_summary = "\n".join([
            f"- {f['filename']} ({f['status']}, +{f['additions']}/-{f['deletions']})"
            for f in pr_info['files']
        ])
        
        # Patches
        patches_text = "\n\n".join([
            f"Файл: {f['filename']}\n```diff\n{f['patch'][:1000]}\n```"
            for f in pr_info['files'] if f['patch']
        ])
        
        prompt = f"""Ты - опытный код-ревьюер для Android проекта на Kotlin. Твоя задача - проанализировать Pull Request и дать конструктивное ревью.

📋 **Информация о PR:**
- Номер: #{pr_info['number']}
- Название: {pr_info['title']}
- Автор: {pr_info['author']}
- Описание: {pr_info['description'][:500]}

📂 **Измененные файлы:**
{files_summary}

📝 **Изменения (diff):**
{patches_text[:3000]}

📚 **Контекст из существующего кода (RAG):**
{rag_context[:2000]}

---

**Задание:**
Проведи детальное code review этого PR. В своем ревью обязательно включи:

## ✅ Положительные моменты
- Что сделано хорошо
- Какие best practices применены

## ⚠️ Замечания и потенциальные проблемы
- Укажи файл и строку (если возможно)
- Объясни проблему
- Предложи решение

## 💡 Рекомендации
- Как улучшить код
- Что добавить (тесты, документация и т.д.)

## 📊 Общая оценка
- Можно ли мержить PR?
- Требуются ли изменения?

**Важно:**
- Будь конкретным и конструктивным
- Указывай файлы и строки кода
- Предлагай примеры улучшений
- Используй emoji для визуальной структуры
- Пиши на русском языке

Формат ответа - GitHub Flavored Markdown."""

        return prompt
    
    def post_review_comment(self, review_text: str):
        """Опубликовать ревью в PR"""
        print("📤 Публикация ревью в PR...")
        
        try:
            repo = self.github.get_repo(REPO_NAME)
            pr = repo.get_pull(PR_NUMBER)
            
            # Добавляем заголовок
            full_review = f"""## 🤖 AI Code Review

{review_text}

---
*Автоматическое ревью сгенерировано Claude 3.5 Sonnet с использованием RAG*
*Проверено {self.rag.collection.count()} документов из кодовой базы*
"""
            
            # Постим комментарий
            pr.create_issue_comment(full_review)
            print("✅ Ревью успешно опубликовано!")
            
            # Сохраняем в файл для артефактов
            with open("review_output.md", "w") as f:
                f.write(full_review)
            
        except Exception as e:
            print(f"❌ Ошибка публикации ревью: {e}")
            # Сохраняем ревью в файл даже при ошибке
            with open("review_output.md", "w") as f:
                f.write(review_text)


def main():
    """Основная функция"""
    print("=" * 60)
    print("🤖 AI PR Reviewer")
    print("=" * 60)
    
    # Проверка переменных окружения
    if not ANTHROPIC_API_KEY:
        print("❌ ANTHROPIC_API_KEY не установлен")
        sys.exit(1)
    
    if not GITHUB_TOKEN:
        print("❌ GITHUB_TOKEN не установлен")
        sys.exit(1)
    
    if PR_NUMBER == 0:
        print("❌ PR_NUMBER не установлен")
        sys.exit(1)
    
    if not REPO_NAME:
        print("❌ GITHUB_REPOSITORY не установлен")
        sys.exit(1)
    
    try:
        # Создаем анализатор
        analyzer = PRAnalyzer()
        
        # Получаем информацию о PR
        pr_info = analyzer.get_pr_info()
        print(f"✅ PR #{pr_info['number']}: {pr_info['title']}")
        print(f"📝 Измененных файлов: {len(pr_info['files'])}")
        
        # Анализируем с RAG
        review = analyzer.analyze_pr_with_rag(pr_info)
        
        # Публикуем ревью
        analyzer.post_review_comment(review)
        
        print("=" * 60)
        print("✅ Ревью завершено успешно!")
        print("=" * 60)
        
    except Exception as e:
        print(f"❌ Критическая ошибка: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)


if __name__ == "__main__":
    main()
