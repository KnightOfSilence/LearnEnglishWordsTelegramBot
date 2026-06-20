# LearnEnglishWordsTelegramBot

Это JVM-приложение Telegram-бота, а не Android-приложение. Оно запускается на компьютере
или сервере и не устанавливается в Android Emulator.

## Запуск из Android Studio

1. Откройте окно **Gradle**.
2. Выберите `Tasks` -> `application` -> `run`.
3. В конфигурации запуска добавьте переменную окружения:
   `TELEGRAM_BOT_TOKEN=<токен бота>`.
4. Запустите задачу `run`.

Также бот можно запустить из терминала:

```bash
TELEGRAM_BOT_TOKEN="<токен бота>" ./gradlew run
```

Для остановки бота нажмите `Ctrl+C`.

## Онлайн-словарь Yandex Dictionary

Для новых пользователей бот может брать слова из Yandex Dictionary вместо локального
`words.txt`. API-ключ нельзя хранить в открытом коде, поэтому задайте его через
переменную окружения:

```bash
TELEGRAM_BOT_TOKEN="<токен бота>" \
YANDEX_DICTIONARY_API_KEY="<ключ Yandex Dictionary>" \
YANDEX_DICTIONARY_WORDS="cat,dog,house,book" \
./gradlew run
```

Для локального запуска можно создать файл `local.properties` в корне проекта:

```properties
YANDEX_DICTIONARY_API_KEY=<ключ Yandex Dictionary>
YANDEX_DICTIONARY_WORDS=cat,dog,house,book
```

Файл `local.properties` добавлен в `.gitignore`, поэтому он не должен попадать в
публичный репозиторий.

Дополнительные настройки:

- `YANDEX_DICTIONARY_API_KEY` - ключ Yandex Dictionary.
- `YANDEX_DICTIONARY_WORDS` - список английских слов через запятую, точку с запятой
  или перенос строки. Если не указать, используется встроенный стартовый список.
- `YANDEX_DICTIONARY_LANG` - направление перевода. По умолчанию используется `en-ru`.

Если не задавать `YANDEX_DICTIONARY_API_KEY` ни в окружении, ни в `local.properties`,
бот продолжит использовать локальный `words.txt`. Прогресс пользователей по-прежнему
хранится отдельно в `user-progress/<chat_id>.txt`, поэтому разные чаты не
перезаписывают результаты друг друга.

## Тихие уведомления

Бот отправляет все сообщения через Telegram API с параметром
`disable_notification=true`. Поэтому уведомления от бота приходят без звука на
устройствах пользователей, если Telegram-клиент поддерживает тихие сообщения.
