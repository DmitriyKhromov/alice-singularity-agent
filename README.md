# alice-singularity-agent

LangChain4j + GigaChat агент: CLI на естественном языке → задачи в SingularityApp.

## Стек

- Java 21, Spring Boot 3.4
- LangChain4j через `chat.giga:langchain4j-gigachat`
- LLM: GigaChat (`GigaChat-2-Max` по умолчанию, scope `GIGACHAT_API_PERS`; тариф Ultra у заказчика, id `GigaChat-3-Ultra` на PERS пока недоступен)
- API: SingularityApp REST v2

## Контракт ответа

Агент всегда отвечает в формате:

```text
Status: success | error
Action: <описание действия>
Data: <результат API или null>
Errors: <текст ошибки или null>
```

Правила для модели: `src/main/resources/prompts/system.md`.

## Tools

| Tool | HTTP | Файл |
|---|---|---|
| `createTask` | `POST /v2/task` | `tools/CreateTaskTool.java` |
| `listTasks` | `GET /v2/task` | `tools/ListTasksTool.java` |
| `updateTask` | `PATCH /v2/task/{id}` | `tools/UpdateTaskTool.java` |

HTTP-клиент: `singularity/SingularityClient.java`.  
Debug tool: `System.out.println("[SingularityTool] ...")` в каждом tool.

## Настройка

1. Java 21, Maven 3.9+.
2. Скопируйте `.env` → `.env` и заполните:

```text
GIGACHAT_AUTH_KEY=...          # кабинет GigaChat
GIGACHAT_SCOPE=GIGACHAT_API_PERS
GIGACHAT_MODEL=GigaChat-2-Max
SINGULARITY_API_TOKEN=...      # Singularity → Доступ к API (права: задачи)
```

3. Ключ GigaChat: [developers.sber.ru](https://developers.sber.ru/) → GigaChat → ключ авторизации.  
4. Токен Singularity: личный кабинет → «Доступ к API» → создать токен с правом на задачи.

`.env` подхватывается при старте (dotenv). Секреты не коммитьте.

## Запуск CLI

```bash
export JAVA_HOME=/path/to/jdk-21
mvn -q -DskipTests package
java -jar target/alice-singularity-agent-0.1.0.jar "создай задачу купить молоко на завтра"
```

Или:

```bash
mvn -q spring-boot:run -Dspring-boot.run.arguments='создай задачу купить молоко'
```

Примеры фраз:

- `создай задачу купить молоко`
- `поставь пробежка на завтра`
- `какие у меня задачи`
- `переименуй задачу T-... в Купить хлеб`
- `сколько будет 2+2` → ожидается `Status: error` без вызова API

## Отчёт ДЗ

См. `REPORT.md` в корне репозитория.

## Этап 2 — навык «Сингуларити Бот»

ТЗ: [`TZ-stage2.md`](TZ-stage2.md).