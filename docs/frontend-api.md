# API первого спринта для фронтенда

Локальный backend: `http://localhost:8080`. Swagger UI: `/swagger-ui/index.html`.
Контракт: `/v3/api-docs` (JSON) и `/v3/api-docs.yaml` (YAML).
Документация описывает реализованное поведение, а не прежние примеры Miro.

## Последовательность действий

1. Хост отправляет `POST /rooms` и сохраняет `code` и **`participant.token`**.
2. Фронтенд формирует приглашение `/join/{code}` на своём домене.
3. Гость вводит имя и отправляет `POST /rooms/{code}/join`.
   Сохранить его собственный токен нужно из **`currentParticipant.token`**.
4. Каждый пользователь отправляет `GET /rooms/{code}` со своим токеном в
   **`X-Participant-Token`** и отображает настройки и список участников.
   Лобби можно обновлять каждые 3 секунды; WebSocket пока нет.

До создания или присоединения получить лобби нельзя. `CurrentParticipant` не
передаётся в теле: сервер определяет участника по заголовку. `Authorization: Bearer`
сейчас не используется. Токен действует 24 часа с выдачи и не обновляется от GET.
Не показывайте и не отправляйте свой токен другим участникам. Общий список его не содержит.

## Создать комнату

```http
POST /rooms
Content-Type: application/json

{"displayName":"Anna","preferences":{"contentType":"MOVIES","genreIds":[28,35],"maxDurationMinutes":120}}
```

Успех: `201 Created`, заголовок `Location: /rooms/{code}`.

```json
{
  "code": "K7M2QX",
  "status": "LOBBY",
  "preferences": {"contentType": "MOVIES", "genreIds": [28,35], "maxDurationMinutes": 120},
  "participant": {
    "id": 1, "displayName": "Anna", "host": true,
    "token": "3f2b8c1e-5a4d-4e7b-9c2a-1d6f0b7e8a90"
  }
}
```

`displayName` обязателен: не пустой, 3–20 символов. `preferences` можно не передать
или передать `null`: тогда сохраняются `MOVIES`, `genreIds: []`, `maxDurationMinutes: null`.
Если объект настроек передан, `contentType` обязателен: `MOVIES` либо `TV_SERIES`.
Отсутствие, `null` или `[]` для жанров означает любые жанры. Элементы `null` запрещены,
дубли удаляются, порядок не гарантирован. Названия жанров backend не возвращает.
Длительность: **любое целое 30–150 включительно либо null**; отсутствие также означает
без ограничения. Значения 90/120/150 — удобные кнопки интерфейса, не enum API.

## Присоединиться

```http
POST /rooms/K7M2QX/join
Content-Type: application/json

{"displayName":"Egor"}
```

Успех: `201 Created`.

```json
{
  "code": "K7M2QX", "status": "LOBBY",
  "currentParticipant": {
    "id": 2, "displayName": "Egor", "host": false,
    "token": "7c1487a2-c472-4482-b426-e361409028e7"
  }
}
```

Комната должна существовать, быть в `LOBBY` и содержать меньше 5 участников,
включая хоста. Имя должно быть свободно без учёта регистра (`Anna` и `anna` совпадают).
Вход не восстанавливает прошлую сессию: повтор с занятым именем вернёт `NAME_TAKEN`.

## Получить лобби

```http
GET /rooms/K7M2QX
X-Participant-Token: 7c1487a2-c472-4482-b426-e361409028e7
```

Успех: `200 OK`.

```json
{
  "code": "K7M2QX", "status": "LOBBY",
  "preferences": {"contentType": "MOVIES", "genreIds": [28,35], "maxDurationMinutes": 120},
  "participantCount": 2, "maxParticipants": 5,
  "participants": [
    {"id": 1, "displayName": "Anna", "host": true},
    {"id": 2, "displayName": "Egor", "host": false}
  ]
}
```

Участники отсортированы по ID. `participantCount` включает хоста. GET возвращает
и последующие статусы `VOTING`/`FINISHED`; переходы между ними не реализуются этим API.
Коды комнат регистронезависимы. `roomId`, токены и время их выдачи в лобби не передаются.

## Ошибки

Бизнес-ошибки и нарушения Bean Validation:

```json
{"code":"NAME_TAKEN","message":"Name already has taken","fieldErrors":[]}
```

```json
{
  "code": "VALIDATION_ERROR", "message": "Validation failed",
  "fieldErrors": [{"field":"preferences.maxDurationMinutes","message":"must be less than or equal to 150"}]
}
```

Ориентируйтесь на HTTP-статус и `code`, а не на текст `message`.
`fieldErrors` содержит пути полей запроса; для бизнес-ошибок он пуст.

| Операция | HTTP | code | Причина / действие интерфейса |
| --- | --- | --- | --- |
| Создание, вход | 400 | `VALIDATION_ERROR` | Проверить поля формы; показать `fieldErrors` |
| GET | 401 | `INVALID_TOKEN` | Токен отсутствует, пустой, неизвестен или истёк; защищённое лобби недоступно |
| GET | 403 | `WRONG_ROOM` | Токен участника другой комнаты; использовать токен нужной комнаты |
| GET, вход | 404 | `ROOM_NOT_FOUND` | Комната не найдена; показать ошибку кода приглашения |
| Вход | 409 | `ROOM_NOT_JOINABLE` | Статус уже не `LOBBY`; вход закрыт |
| Вход | 409 | `ROOM_FULL` | В комнате уже 5 человек |
| Вход | 409 | `NAME_TAKEN` | Имя занято, предложить другое |
| Создание, вход | 400 | отсутствует | Некорректный JSON, тип значения, неизвестный enum либо отсутствующее тело |
| Создание, вход | 415 | отсутствует | Неверный Content-Type; отправлять JSON |
| Создание | 503 | отсутствует | Не выделен уникальный код за 5 попыток; можно повторить создание |
| Все | 500 | отсутствует | Техническая ошибка; для входа сюда относится исчерпание попыток генерации токена |

Ошибки HTTP/JSON и технические ошибки используют стандартный формат Spring, например:

```json
{"status":400,"error":"Bad Request","path":"/rooms"}
```

Он может дополнительно содержать `timestamp`; наличие `code`/`fieldErrors` не гарантировано.
Проверка GET: сначала токен, затем наличие комнаты, затем принадлежность участника.
Проверка входа: наличие комнаты, статус, заполненность, занятость имени.
Если нарушены несколько условий, возвращается первая найденная ошибка.

Не повторяйте POST автоматически после тайм-аута/сетевой ошибки: операция могла
успешно выполниться, а ответ — потеряться. Повторный join создаёт нового участника
или конфликтует по имени; механизма idempotency key пока нет.

## Swagger и подключение фронтенда

В Swagger нажать **Authorize**, вставить UUID токена без `Bearer`, затем выполнить GET.
Создание и вход работают без авторизации. Примеры токенов в документации вымышленные:
для Try it out используйте токен из своего успешного POST.

Swagger на backend работает с тем же origin. Для frontend на другом порту/домене
нужен dev/reverse proxy либо отдельно согласованная CORS-конфигурация: разрешённые
origin и заголовки (`Content-Type`, `X-Participant-Token`). Текущий backend CORS не настраивает.
