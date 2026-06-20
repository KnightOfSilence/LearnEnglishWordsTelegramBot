# LearnEnglishWordsTelegramBot

Это JVM-приложение Telegram-бота, а не Android-приложение. Оно запускается на компьютере
или сервере и не устанавливается в Android Emulator.

## Словарь

Слова размещаются в файле `words.txt` в формате:

```text
английское слово|перевод|0
```

При первом запуске для нового чата бот создаёт отдельный файл прогресса в папке
`user-progress/`.

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

После запуска откройте меню команд Telegram или отправьте боту `/start` / `/menu`.
Если сообщений нет, бот просто ждёт новые апдейты.

Для остановки бота нажмите `Ctrl+C`.

## Сборка для публикации

Для публикации на сервере соберите FAT-jar с зависимостями:

```bash
./gradlew clean shadowJar
```

Готовый файл появится здесь:

```text
build/libs/WordsTelegramBot-1.0-SNAPSHOT-all.jar
```

Запускайте именно этот `all.jar`. Обычный jar без зависимостей приведёт к ошибкам вида
`NoClassDefFoundError: kotlinx/serialization/json/JsonBuilder`.

## Публикация на VPS

1. Создайте VPS с Ubuntu и подключитесь по SSH:

```bash
ssh root@100.100.100.100
```

2. Обновите пакеты и установите JDK:

```bash
apt update
apt upgrade
apt install default-jdk
java --version
```

3. Скопируйте jar и словарь на сервер:

```bash
scp build/libs/WordsTelegramBot-1.0-SNAPSHOT-all.jar root@100.100.100.100:/root/bot.jar
scp words.txt root@100.100.100.100:/root/words.txt
```

4. Запустите бота на сервере в фоне:

```bash
nohup java -jar bot.jar <ТОКЕН ТЕЛЕГРАМ> &
```

Логи запуска можно смотреть командой:

```bash
tail -f nohup.out
```

После запуска можно отключиться от SSH, бот продолжит работать на VPS.

Если на сервер уже был скопирован старый `bot.jar`, пересоберите `all.jar` и загрузите его
повторно командой `scp`.

Если бот запустился, но не отвечает:

1. Убедитесь, что вы отправили сообщение, `/start` или `/menu` именно этому боту.
2. Проверьте `nohup.out`: при неверном токене или второй запущенной копии бот выведет ошибку Telegram API.
3. Убедитесь, что на сервере лежит файл `words.txt` рядом с `bot.jar`.

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
