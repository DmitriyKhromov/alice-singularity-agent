# ТЗ: CLI-агент SingularityApp на LangChain4j + GigaChat

## 0. Решения заказчика (зафиксировано 2026-11-22)

| Вопрос | Решение |
|---|---|
| Объём MVP | **Только CLI + `REPORT.md`** (навык Алисы — этап 2, не блокирует сдачу) |
| Дедлайн курса / срочный HTTPS | Нет |
| GigaChat | Тариф **Ultra**, ключ есть, scope **`GIGACHAT_API_PERS`** |
| SingularityApp | Токен создан, права на **задачи**; Pro |
| Задача без даты | Разрешено → «Входящие» / без `start` |
| Операции MVP | **create / list / update** (отдельные tools) |
| `get_task` | Не обязателен в MVP |
| Алиса (этап 2) | Timeweb, приватный навык «Задачи», только для владельца |
| Стек курса | Java + LangChain4j — **ок** преподавателю |
| Язык REPORT | Русский |
| Репозиторий | `github.com/DmitriyKhromov/alice-singularity-agent` |
| Каркас | **Spring Boot** |
| Tools | **Отдельные** на каждую операцию |

---

## 1. Цель MVP

Собрать **AI-агента на LangChain4j** с LLM **GigaChat Ultra**, который:

1. принимает запрос на естественном языке из CLI;
2. через **отдельные tools** вызывает API SingularityApp (`create` / `list` / `update`);
3. возвращает ответ в **фиксированном контракте**;
4. сопровождается **`REPORT.md`** для сдачи домашнего задания курса.

**Для курса:** natural-language SDK над API SingularityApp.  
**Для пользователя (этап 2):** тот же агент позже подключается к навыку Алисы «Задачи» на Timeweb.

---

## 2. Стек (зафиксирован)

| Компонент | Выбор |
|---|---|
| Язык | Java 21 |
| Каркас | Spring Boot |
| Агент / tools | LangChain4j |
| LLM | **GigaChat Ultra** (`GIGACHAT_API_PERS`) |
| Task API | SingularityApp REST API v2 |
| Вход MVP | CLI |
| Вход этап 2 | Яндекс Диалоги → HTTPS webhook на Timeweb |
| Сборка | Maven (`pom.xml`) |
| Репозиторий | https://github.com/DmitriyKhromov/alice-singularity-agent |

**Не использовать** как основной LLM: Alice AI LLM / YandexGPT, DeepSeek, Ollama.

---

## 3. Обязательные условия домашнего задания

Работа (MVP) считается выполненной, только если выполнено всё ниже.

- [ ] Агент на **LangChain4j** запускается по инструкции из репозитория.
- [ ] Реализованы **отдельные** API-tools: create / list / update; хотя бы одна операция вызывается реально.
- [ ] Внутри tools — явный HTTP-вызов; есть **debug-вывод** результата tool (`log` / `System.out`); в `REPORT.md` указаны **файл и диапазон строк** объявления tool и HTTP-вызова.
- [ ] Агент корректно выбирает нужную операцию; в отчёте есть пример «запрос → ожидаемый метод» + фрагмент лога/trace.
- [ ] Финальный ответ агента всегда в контракте:

```text
Status: success | error
Action: <описание действия>
Data: <результат API>
Errors: <если есть>
```

- [ ] Контракт описан в репозитории (`README.md` и/или `prompts/system.md`); в `REPORT.md` — ссылка на файл:строки.
- [ ] Запуск из CLI, например:

```bash
java -jar target/alice-singularity-agent.jar "создай задачу купить молоко на завтра"
```

- [ ] Минимум **5** тестовых запросов; из них минимум **3** с реальным вызовом tool/API.
- [ ] В корне есть **`REPORT.md`** на русском — главный артефакт сдачи (см. §12).
- [ ] Зависимости зафиксированы в `pom.xml`.
- [ ] Секреты не закоммичены; есть `.env` и описание настройки.

**Навык Алисы не входит в критерии приёмки MVP.**

---

## 4. Архитектура MVP

```text
CLI ──► Spring Boot / AgentCli
              │
              ▼
     LangChain4j Agent (GigaChat Ultra + system prompt)
              │
              ├── createTaskTool  ──► POST   /v2/task
              ├── listTasksTool   ──► GET    /v2/task
              └── updateTaskTool  ──► PATCH  /v2/task/{id}
```

- Бизнес-логика и выбор операции — только в агенте + tools.
- Нельзя для happy-path CLI обходить агента «голым» парсером с прямым вызовом API.
- Парсер дат внутри tool или как помощь LLM допустим.

### Этап 2 (не блокирует MVP)

```text
Алиса «Задачи» → Диалоги → HTTPS Timeweb /alice/webhook → тот же агент
```

- Приватный навык, один владелец = один `SINGULARITY_API_TOKEN`.
- Webhook только форматирует контракт агента в короткий `response.text`.

---

## 5. Вне скоупа MVP

- Навык Алисы, деплой на Timeweb, публичный HTTPS.
- `get_task` (можно добавить позже).
- Привычки, проекты, канбан, чек-листы.
- Публикация навыка в каталог Алисы.
- Мультипользовательность.
- Замена встроенного AI Singularity / Telegram-бота.

---

## 6. API SingularityApp

- Base URL: `https://api.singularity-app.com`
- Auth: `Authorization: Bearer <SINGULARITY_API_TOKEN>`
- Документация: https://singularity-app.ru/wiki/api/
- Swagger: https://api.singularity-app.com/v2/api
- Токен: уже создан заказчиком, права на **задачи**.

### 6.1. Операции MVP (отдельные tools)

| Tool | HTTP | Назначение |
|---|---|---|
| `createTask` | `POST /v2/task` | создать задачу |
| `listTasks` | `GET /v2/task` | список задач (фильтры по возможности API) |
| `updateTask` | `PATCH /v2/task/{id}` | обновить title / start / статус и т.п. |

### 6.2. Создание задачи

```http
POST /v2/task
Authorization: Bearer <token>
Content-Type: application/json

{
  "title": "Купить молоко",
  "start": "2026-11-23",
  "priority": 1
}
```

- Без даты/`start` — допустимо (задача во «Входящие»).
- Точные поля и формат даты сверить со Swagger.
- Timezone: `Europe/Moscow`.

### 6.3. Debug tool

Обязателен лог результата каждого tool, например:

```text
[SingularityTool] tool=createTask status=200 body={...}
```

В `REPORT.md` указать файл и строки этих логов.

---

## 7. Агент LangChain4j + GigaChat Ultra

### 7.1. System prompt

Хранить полным текстом в `prompts/system.md` и продублировать в `REPORT.md`.

Содержание:

- роль: оператор API SingularityApp / постановщик задач;
- границы: только create / list / update; не выдумывать id; не оффтоп;
- правила вызова каждого tool;
- timezone: `Europe/Moscow`;
- обязательный формат финального ответа Status / Action / Data / Errors;
- при нехватке данных — `Status: error`.

### 7.2. GigaChat

- Тариф: **Ultra**
- Scope: **`GIGACHAT_API_PERS`**
- Ключ у заказчика есть → env `GIGACHAT_AUTH_KEY`
- Модель: зафиксировать доступную Ultra-модель в README/конфиге (сверить с кабинетом; ориентир `GigaChat-2-Max` или актуальный аналог Ultra)

```text
GIGACHAT_AUTH_KEY=
GIGACHAT_SCOPE=GIGACHAT_API_PERS
GIGACHAT_OAUTH_URL=https://ngw.devices.sberbank.ru:9443/api/v2/oauth
GIGACHAT_BASE_URL=https://api.giga.chat/v1
GIGACHAT_MODEL=GigaChat-2-Max
```

URL/имя модели сверить с документацией на момент реализации.

### 7.3. Поведение

- Создание / список / обновление → соответствующий tool.
- Оффтоп → `Status: error`, без вызова Singularity (минимум 1 тест).
- Финальный ответ всегда в контракте.

---

## 8. Интерфейсы

### 8.1. CLI (обязателен)

```bash
java -jar target/alice-singularity-agent.jar "создай задачу купить молоко на завтра"
```

или `mvn spring-boot:run` / `exec:java` — как удобнее, описать в README.

Вывод stdout — контракт ответа. В логах — вызовы tools.

Spring Boot: приложение может быть web-приложением; для MVP достаточно CLI-команды / `ApplicationRunner` / отдельного main-профиля. Эндпоинты Алисы на этапе 2.

### 8.2. Алиса (этап 2, не в приёмке MVP)

- `POST /alice/webhook`, `GET /health`
- Хостинг: **Timeweb**
- Навык: приватный, имя/запуск **«Задачи»**
- Тот же агент, короткий `response.text` из контракта

---

## 9. Конфигурация

`.env` (не в git) + `.env`:

```text
# GigaChat
GIGACHAT_AUTH_KEY=
GIGACHAT_SCOPE=GIGACHAT_API_PERS
GIGACHAT_OAUTH_URL=https://ngw.devices.sberbank.ru:9443/api/v2/oauth
GIGACHAT_BASE_URL=https://api.giga.chat/v1
GIGACHAT_MODEL=GigaChat-2-Max

# SingularityApp
SINGULARITY_API_TOKEN=
SINGULARITY_API_BASE_URL=https://api.singularity-app.com

# App
APP_TIMEZONE=Europe/Moscow
SERVER_PORT=8080
LOG_LEVEL=INFO
```

`.gitignore` должен исключать `.env`, `target/`, `.idea/`.

---

## 10. Контракт ответа

Описать в `README.md` (раздел «Контракт ответа») и `prompts/system.md`.

Успех:

```text
Status: success
Action: create task
Data: {"id":"T-...","title":"Купить молоко","start":"2026-11-23"}
Errors: null
```

Ошибка:

```text
Status: error
Action: create task
Data: null
Errors: не указано название задачи
```

---

## 11. Тест-план (≥5; ≥3 с tool)

| # | Запрос | Ожидание |
|---|---|---|
| 1 | «создай задачу купить молоко» | `createTask`, без даты |
| 2 | «поставь пробежка на завтра» | `createTask` с датой |
| 3 | «какие у меня задачи» / «что на сегодня» | `listTasks` |
| 4 | «перенеси … на послезавтра» или «переименуй …» | `updateTask` |
| 5 | оффтоп | `Status: error`, без API |

В `REPORT.md`: запрос, ответ, был ли tool, какой.

---

## 12. REPORT.md (критично, на русском)

Главный артефакт сдачи. История работы, не summary:

1. LLM: GigaChat Ultra, настройка, ключ (без секрета в тексте).
2. API: SingularityApp, операции create/list/update.
3. Как запустить CLI.
4. **Все промпты полным текстом**.
5. Неудачные попытки и как чинили.
6. Пять тестовых запросов и результаты.
7. Tools: файл:строки + debug-лог.
8. Где описан контракт ответа.
9. Выводы.
10. (Опционально) план этапа 2 — Алиса на Timeweb.

Без полных промптов и разбора неудач отчёт неполный.

---

## 13. Структура репозитория

```text
alice-singularity-agent/
  TZ.md
  REPORT.md
  README.md
  .env.example
  .gitignore
  pom.xml
  prompts/system.md
  src/main/java/.../
    Application.java
    cli/AgentCli.java
    agent/AgentFactory.java
    agent/ResponseContract.java
    tools/CreateTaskTool.java
    tools/ListTasksTool.java
    tools/UpdateTaskTool.java
    singularity/SingularityClient.java
    config/...
  src/test/java/.../
```

Пакет — на усмотрение (`dev.alice.singularity` и т.п.).  
Классы Алисы (`alice/*`) — только на этапе 2.

---

## 14. Порядок реализации MVP

1. Spring Boot skeleton + загрузка env.
2. `SingularityClient`: create / list / update + ручная проверка токеном.
3. Три отдельных LangChain4j tool + debug-логи.
4. AgentFactory + `prompts/system.md` + GigaChat Ultra.
5. CLI: фраза → агент → контракт в stdout.
6. ≥5 тестов; `REPORT.md` (промпты, фейлы, логи).
7. README + `.env` + добить `.gitignore`.
8. Чеклист §15.

Этап 2 (после сдачи ДЗ): см. **`TZ-stage2.md`** — webhook Алисы на Timeweb рядом с dolya.

---

## 15. Критерии приёмки MVP

- [ ] Агент LangChain4j + GigaChat Ultra запускается по README.
- [ ] Три отдельных tool (create/list/update) реально ходят в Singularity; указаны файл:строки + debug.
- [ ] Агент выбирает нужный tool; есть пример + лог.
- [ ] Ответ в контракте; контракт описан в репо.
- [ ] ≥5 запросов в REPORT.md (русский).
- [ ] REPORT.md с полными промптами и неудачами.
- [ ] Секреты не в git.

Не требуется для MVP: навык Алисы, деплой Timeweb, `get_task`.

---

## 16. Риски

- Поля API v2 — сверять со Swagger.
- Без `GIGACHAT_AUTH_KEY` агент не сдаётся.
- Качество дат — заложить примеры в system prompt.
- Не логировать секреты.
- `.idea/` не коммитить.

---

## 17. Definition of Done (MVP)

1. CLI-фраза создаёт / списком показывает / обновляет задачи в SingularityApp через LangChain4j + GigaChat Ultra + отдельные tools.  
2. Ответы в контракте; `REPORT.md` готов к сдаче курса.  
3. Репозиторий: https://github.com/DmitriyKhromov/alice-singularity-agent  

**Этап 2 DoD (позже):** голос «Алиса, запусти Задачи» → задача в Singularity через того же агента на Timeweb.
