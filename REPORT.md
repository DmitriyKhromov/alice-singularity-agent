# REPORT — alice-singularity-agent (этап 1 / MVP)

История работы над домашним заданием: минимальный агент-обёртка над API SingularityApp на LangChain4j + GigaChat.

## 1. LLM и настройка

- **LLM:** GigaChat (тариф Ultra у заказчика)
- **Модель:** `GigaChat-2-Max` — на `GIGACHAT_API_PERS` id `GigaChat-3-Ultra` **недоступен** (`No such model`)
- **Scope:** `GIGACHAT_API_PERS`
- **Интеграция:** `chat.giga:langchain4j-gigachat:0.1.24` → `GigaChatChatModel` + `AiServices` + tools
- **Ключ:** `GIGACHAT_AUTH_KEY` в `.env` (не в git)

Как настроить:

1. Ключ авторизации GigaChat в кабинете Sber.
2. `.env.example` → `.env`, заполнить `GIGACHAT_AUTH_KEY`, `SINGULARITY_API_TOKEN`.
3. Модель по умолчанию: `GigaChat-2-Max` (список `/models` для ключа).

## 2. API

- **Сервис:** SingularityApp REST API v2  
- **Base:** `https://api.singularity-app.com`  
- **Auth:** `Authorization: Bearer <SINGULARITY_API_TOKEN>`  
- **Документация:** https://singularity-app.ru/wiki/api/  

| Tool | HTTP |
|---|---|
| `createTask` | `POST /v2/task` |
| `listTasks` | `GET /v2/task` |
| `updateTask` | `PATCH /v2/task/{id}` |

## 3. Запуск

```bash
cp .env.example .env   # заполнить ключи
export JAVA_HOME=.../jdk-21
mvn -q -DskipTests package
java -jar target/alice-singularity-agent-0.1.0.jar "создай задачу купить молоко на завтра"
```

## 4. Системный промпт (полный текст)

Файл: `src/main/resources/prompts/system.md` (копия: `prompts/system.md`)

```text
Ты — оператор API SingularityApp и natural-language SDK для управления задачами.

Timezone: Europe/Moscow. Сегодняшняя дата и относительные даты («сегодня», «завтра», «послезавтра») считай в этой зоне.

## Границы ответственности
- Разрешены только операции с задачами через tools: createTask, listTasks, updateTask.
- Не отвечай на оффтоп (математика, рецепты, погода, болтовня). Если запрос не про задачи SingularityApp — не вызывай tools и верни Status: error.
- Не выдумывай id задач. Для updateTask бери id только из результата listTasks/createTask или из явного сообщения пользователя.
- Если не хватает данных (например, нет названия для создания) — Status: error, без вызова tool (или после уточнения через Errors).

## Правила tools
- createTask: создай задачу одним вызовом. title обязателен. Если пользователь сказал «завтра/сегодня/послезавтра» — передай start именно этим словом (или YYYY-MM-DD); клиент нормализует timezone. Если даты нет — не передавай start. Не создавай задачу повторно при ошибке: исправь параметры и при необходимости один раз вызови updateTask. priority: 0/1/2, по умолчанию 1.
- listTasks: список задач. Для «на сегодня» передай startDateFrom и startDateTo = сегодняшняя дата YYYY-MM-DD. maxCount по умолчанию 10 (не больше 20).
- updateTask: измени существующую задачу по id (формат вроде T-...). Можно менять title, start (YYYY-MM-DD), note, priority; для архива — journalDate; для корзины — deleteDate.

## Формат финального ответа (строго)
Всегда отвечай только этим блоком, без markdown и без текста вокруг.
В Data используй обычный JSON с символами " (кавычки ASCII), без плейсхолдеров вроде <|superquote|>.
Data держи компактным: для create/update достаточно id, title, start; для list — count и tasks[{id,title,start}].

Status: success | error
Action: <краткое описание действия>
Data: <компактный JSON результата API или null>
Errors: <текст ошибки или null>

Примеры:
Status: success
Action: create task
Data: {"id":"T-...","title":"Купить молоко","start":"2026-11-23"}
Errors: null

Status: error
Action: create task
Data: null
Errors: не указано название задачи
```

### Пользовательский шаблон (CLI)

```text
java -jar target/alice-singularity-agent-0.1.0.jar "<фраза пользователя>"
```

Фраза = `@UserMessage` без обёртки.

## 5. Контракт ответа (где описан)

- `README.md` — раздел «Контракт ответа»
- `src/main/resources/prompts/system.md` — обязательный формат для модели

## 6. Tool: объявление, HTTP, debug

| Что | Где |
|---|---|
| HTTP create/list/update + нормализация дат | `singularity/SingularityClient.java` |
| Tool createTask | `tools/CreateTaskTool.java` |
| Tool listTasks | `tools/ListTasksTool.java` |
| Tool updateTask | `tools/UpdateTaskTool.java` |
| Debug print | `System.out.println("[SingularityTool] tool=... status=... body=...")` в каждом tool |

Пример трассировки (тест 1):

```text
запрос → createTask → POST /v2/task → 201
[SingularityTool] tool=createTask status=ok body={"id":"T-a9daca4a-...","title":"купить молоко","start":null,"priority":1}
```

## 7. Неудачные попытки и как чинили

1. **`GigaChat-3-Ultra` → 404 No such model**  
   Запросили `/models` по ключу — Ultra-id нет. Переключили default на `GigaChat-2-Max`.

2. **Дата `YYYY-MM-DD` / datetime без timezone → HTTP 400**  
   Singularity требует ISO-8601 **с** timezone. Добавили `normalizeDateTime()` (дата, datetime без зоны, «завтра/сегодня»).

3. **Зацикливание tool на «пробежка на завтра»**  
   Модель создавала без start, update падал, повторяла до 100 раундов. Починили нормализацию дат + `maxSequentialToolsInvocations(8)` + уточнение промпта.

4. **`listTasks` таймаут GigaChat**  
   Полный JSON задач огромный. Tool стал возвращать компактный `{count, tasks[{id,title,start}]}`.

5. **`<|superquote|>` вместо кавычек в Data**  
   Пост-обработка в `AgentCliRunner.sanitizeContract` + усиление промпта.

6. **Смешение Spring banner/логов с ответом**  
   `banner-mode: off`; контракт печатается в stdout после tool debug.

## 8. Пять тестовых запросов (факт)

### 1. «создай задачу купить молоко» → `createTask`

```text
[SingularityTool] tool=createTask status=ok body={"id":"T-a9daca4a-94ca-4b07-bdeb-67c270136d84","title":"купить молоко","start":null,"priority":1}
Status: success
Action: create task
Data: {"id":"T-a9daca4a-94ca-4b07-bdeb-67c270136d84","title":"купить молоко"}
Errors: null
```

### 2. «поставь пробежка на завтра» → `createTask` + `updateTask`

```text
[SingularityTool] tool=createTask status=ok body={"id":"T-9cd04d1a-f583-4481-967c-dbeec05ec33a","title":"пробежка","start":null,"priority":1}
[SingularityTool] tool=updateTask status=ok body={"id":"T-9cd04d1a-f583-4481-967c-dbeec05ec33a","title":"пробежка","start":"2026-11-23T00:00:00+03:00","priority":1}
Status: success
Action: set run for tomorrow
Data: {
    "id": "T-9cd04d1a-f583-4481-967c-dbeec05ec33a",
    "title": "пробежка",
    "start": "2026-11-23T00:00:00+03:00"
}
Errors: null
```

### 3. «какие у меня задачи» → `listTasks`

```text
[SingularityTool] tool=listTasks status=ok body={"count":8,"tasks":[...]}
Status: success
Action: get tasks
Data: {"count":8,"tasks":[{"id":"T-009d5e03-...","title":"Д.Р. Максим 13 марта",...}, ...]}
Errors: null
```

### 4. «переименуй задачу T-a9daca4a-… в Купить хлеб» → `updateTask`

```text
[SingularityTool] tool=updateTask status=ok body={"id":"T-a9daca4a-94ca-4b07-bdeb-67c270136d84","title":"Купить хлеб","start":null,"priority":1}
Status: success
Action: rename task
Data: {"id":"T-a9daca4a-94ca-4b07-bdeb-67c270136d84","title":"Купить хлеб"}
Errors: null
```

### 5. «сколько будет 2+2» → без API

```text
Status: error
Action: none
Data: null
Errors: данный запрос вне границ моей компетенции
```

## 9. Выводы

- Сработало: LangChain4j-агент + три отдельных tool с реальным HTTP; CLI; контракт Status/Action/Data/Errors; 5/5 проверочных сценариев.
- Болевые точки: имена моделей GigaChat, формат дат Singularity, размер ответа list, артефакты кавычек модели.
- Этап 2 (Алиса на Timeweb) не делался — по ТЗ вне MVP.

## 10. Структура кода

```text
src/main/java/dev/alice/singularity/
  AliceSingularityApplication.java
  agent/AgentConfig.java, TaskAgent.java
  cli/AgentCliRunner.java
  config/AppProperties.java, JacksonConfig.java
  singularity/SingularityClient.java, SingularityException.java
  tools/CreateTaskTool.java, ListTasksTool.java, UpdateTaskTool.java, TaskJson.java
src/main/resources/prompts/system.md
```
