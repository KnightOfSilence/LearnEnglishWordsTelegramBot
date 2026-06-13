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
