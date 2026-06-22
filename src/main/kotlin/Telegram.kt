import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets

const val TELEGRAM_BASE_URL = "https://api.telegram.org"
const val TELEGRAM_MESSAGE_MAX_LENGTH = 4096
const val TELEGRAM_CALLBACK_DATA_MAX_BYTES = 64
const val TELEGRAM_DISABLE_NOTIFICATION = true
const val CALLBACK_SELECT_LANGUAGE = "select_language"
const val CALLBACK_MAIN_MENU = "main_menu"
const val CALLBACK_STATISTICS = "statistics"
const val CALLBACK_RESET_PROGRESS = "reset_progress"
const val CALLBACK_ENGLISH_BEGINNER = "english_beginner"
const val CALLBACK_ENGLISH_ADVANCED = "english_advanced"
const val CALLBACK_LEGACY_ENGLISH = "language_english"
const val CALLBACK_DATA_ANSWER_PREFIX = "answer_"
const val CORRECT_ANSWER_EMOJI = "😊"
const val INCORRECT_ANSWER_EMOJI = "😢"
const val COMMAND_MENU = "menu"
const val MAIN_MENU_TEXT = "Главный экран:"
const val LANGUAGE_MENU_TEXT = "Выберите язык:"

private val telegramHttpClient: HttpClient = HttpClient.newHttpClient()
private val telegramJson = Json { ignoreUnknownKeys = true }

@Serializable
data class TelegramUpdatesResponse(
    val ok: Boolean,
    val result: List<TelegramUpdate> = emptyList(),
    @SerialName("error_code")
    val errorCode: Int? = null,
    val description: String? = null,
)

@Serializable
data class TelegramApiResponse(
    val ok: Boolean,
    @SerialName("error_code")
    val errorCode: Int? = null,
    val description: String? = null,
)

@Serializable
data class TelegramUpdate(
    @SerialName("update_id")
    val updateId: Long,
    val message: TelegramMessage? = null,
    @SerialName("callback_query")
    val callbackQuery: TelegramCallbackQuery? = null,
)

@Serializable
data class TelegramMessage(
    val chat: TelegramChat,
    val text: String? = null,
)

@Serializable
data class TelegramChat(
    val id: Long,
)

@Serializable
data class TelegramCallbackQuery(
    val id: String,
    val message: TelegramMessage? = null,
    val data: String? = null,
)

@Serializable
data class TelegramBotCommand(
    val command: String,
    val description: String,
) {
    init {
        require(command.matches(Regex("[a-z0-9_]{1,32}"))) {
            "Команда Telegram должна содержать от 1 до 32 символов: a-z, 0-9 или _."
        }
        require(description.isNotBlank()) { "Описание команды не должно быть пустым." }
    }
}

@Serializable
data class InlineKeyboardButton(
    val text: String,
    @SerialName("callback_data")
    val callbackData: String,
) {
    init {
        require(text.isNotBlank()) { "Текст кнопки не должен быть пустым." }
        require(callbackData.toByteArray(StandardCharsets.UTF_8).size in 1..TELEGRAM_CALLBACK_DATA_MAX_BYTES) {
            "Длина callback_data должна быть от 1 до $TELEGRAM_CALLBACK_DATA_MAX_BYTES байт."
        }
    }
}

@Serializable
data class InlineKeyboardMarkup(
    @SerialName("inline_keyboard")
    val inlineKeyboard: List<List<InlineKeyboardButton>>,
) {
    init {
        require(inlineKeyboard.isNotEmpty() && inlineKeyboard.all { it.isNotEmpty() }) {
            "Клавиатура должна содержать хотя бы одну кнопку."
        }
    }
}

val mainMenuKeyboard = InlineKeyboardMarkup(
    inlineKeyboard = listOf(
        listOf(InlineKeyboardButton("Выбрать язык", CALLBACK_SELECT_LANGUAGE)),
        listOf(InlineKeyboardButton("Статистика", CALLBACK_STATISTICS)),
        listOf(InlineKeyboardButton("Сбросить прогресс", CALLBACK_RESET_PROGRESS)),
    ),
)

val languageMenuKeyboard = InlineKeyboardMarkup(
    inlineKeyboard = listOf(
        listOf(InlineKeyboardButton("Английский начальный", CALLBACK_ENGLISH_BEGINNER)),
        listOf(InlineKeyboardButton("Английский продвинутый", CALLBACK_ENGLISH_ADVANCED)),
        listOf(InlineKeyboardButton("Главный экран", CALLBACK_MAIN_MENU)),
    ),
)

val learningModeNamesByCallback = mapOf(
    CALLBACK_ENGLISH_BEGINNER to "Английский начальный",
    CALLBACK_ENGLISH_ADVANCED to "Английский продвинутый",
    CALLBACK_LEGACY_ENGLISH to "Английский начальный",
)

val botMenuCommands = listOf(
    TelegramBotCommand(COMMAND_MENU, "Показать меню"),
)

data class TrainerKey(
    val chatId: Long,
    val learningModeCallback: String,
)

fun normalizeLearningModeCallback(learningModeCallback: String): String =
    if (learningModeCallback == CALLBACK_LEGACY_ENGLISH) {
        CALLBACK_ENGLISH_BEGINNER
    } else {
        learningModeCallback
    }

fun isTelegramCommand(text: String, command: String): Boolean {
    val normalizedText = text.trim().substringBefore(" ")
    return normalizedText == "/$command" || normalizedText.startsWith("/$command@")
}

fun main(args: Array<String>) {
    val botToken = resolveBotToken(args)
    val selectedLearningModes = mutableMapOf<Long, String>()
    val trainers = mutableMapOf<TrainerKey, LearnWordsTrainer>()
    var updateId = 0L

    setBotCommands(botToken)
    println("Бот запущен. Отправьте сообщение или /menu вашему боту в Telegram.")
    println("Меню команд Telegram обновлено: /menu.")
    println("Для остановки нажмите Ctrl+C.")

    while (true) {
        Thread.sleep(3000)
        val updates = parseUpdates(getUpdates(botToken, updateId))
        ensureTelegramApiResponseOk(updates)

        if (updates.result.isNotEmpty()) {
            println("Получено обновлений: ${updates.result.size}")
        }
        updates.result.sortedBy { it.updateId }.forEach { update ->
            handleUpdate(
                botToken = botToken,
                update = update,
                trainerProvider = { chatId ->
                    val learningMode = normalizeLearningModeCallback(
                        selectedLearningModes[chatId] ?: CALLBACK_ENGLISH_BEGINNER,
                    )
                    trainers.getOrPut(TrainerKey(chatId, learningMode)) {
                        createTrainerForChat(chatId, learningMode)
                    }
                },
                learningModeSelector = { chatId, learningMode ->
                    selectedLearningModes[chatId] = normalizeLearningModeCallback(learningMode)
                },
            )
        }

        updateId = updates.result.maxOfOrNull { it.updateId + 1 } ?: updateId
    }
}

fun createTrainerForChat(
    chatId: Long,
    learningModeCallback: String = CALLBACK_ENGLISH_BEGINNER,
    progressDirectory: File = File("user-progress"),
    environment: Map<String, String> = System.getenv(),
    localPropertiesFile: File = File(LOCAL_PROPERTIES_FILE_NAME),
    initialDictionaryLoader: (String) -> List<Word> = { selectedLearningMode ->
        loadInitialDictionaryForLearningMode(selectedLearningMode, environment, localPropertiesFile)
    },
): LearnWordsTrainer {
    val normalizedLearningModeCallback = normalizeLearningModeCallback(learningModeCallback)
    val progressFile = createProgressFileForChat(chatId, normalizedLearningModeCallback, progressDirectory)
    if (!progressFile.exists()) {
        progressFile.parentFile?.mkdirs()
        val initialDictionary = initialDictionaryLoader(normalizedLearningModeCallback)
        progressFile.writeText(
            initialDictionary.joinToString(
                separator = "\n",
                postfix = if (initialDictionary.isEmpty()) "" else "\n",
            ) { "${it.original}|${it.translated}|${it.correctAnswersCount}" },
        )
    }
    return LearnWordsTrainer(progressFile)
}

fun loadInitialDictionaryForLearningMode(
    learningModeCallback: String,
    environment: Map<String, String> = System.getenv(),
    localPropertiesFile: File = File(LOCAL_PROPERTIES_FILE_NAME),
): List<Word> =
    if (normalizeLearningModeCallback(learningModeCallback) == CALLBACK_ENGLISH_ADVANCED) {
        loadAdvancedEnglishDictionary(environment, localPropertiesFile)
    } else {
        loadInitialDictionary(environment, localPropertiesFile)
    }

fun createProgressFileForChat(
    chatId: Long,
    learningModeCallback: String = CALLBACK_ENGLISH_BEGINNER,
    progressDirectory: File = File("user-progress"),
): File {
    val normalizedLearningModeCallback = normalizeLearningModeCallback(learningModeCallback)
    val fileName = if (normalizedLearningModeCallback == CALLBACK_ENGLISH_ADVANCED) {
        "$chatId-advanced.txt"
    } else {
        "$chatId.txt"
    }
    return File(progressDirectory, fileName)
}

fun resolveBotToken(
    args: Array<String>,
    environment: Map<String, String> = System.getenv(),
): String {
    val botToken = args.firstOrNull()?.takeIf { it.isNotBlank() }
        ?: environment["TELEGRAM_BOT_TOKEN"]?.takeIf { it.isNotBlank() }

    return requireNotNull(botToken) {
        "Передайте токен первым аргументом или задайте переменную TELEGRAM_BOT_TOKEN."
    }
}

fun parseUpdates(responseBody: String): TelegramUpdatesResponse =
    telegramJson.decodeFromString(responseBody)

fun ensureTelegramApiResponseOk(response: TelegramUpdatesResponse) {
    ensureTelegramApiResponseOk(response.ok, response.errorCode, response.description)
}

fun ensureTelegramApiResponseOk(response: TelegramApiResponse) {
    ensureTelegramApiResponseOk(response.ok, response.errorCode, response.description)
}

private fun ensureTelegramApiResponseOk(
    ok: Boolean,
    errorCode: Int?,
    description: String?,
) {
    check(ok) {
        val details = listOfNotNull(
            errorCode?.let { "код $it" },
            description,
        ).joinToString(": ")
        if (details.isBlank()) {
            "Telegram API вернул ошибку. Проверьте токен и что не запущена другая копия бота."
        } else {
            "Telegram API вернул ошибку ($details). Проверьте токен и что не запущена другая копия бота."
        }
    }
}

fun setBotCommands(botToken: String): String {
    val request = createSetBotCommandsRequest(botToken)
    val response = telegramHttpClient.send(request, HttpResponse.BodyHandlers.ofString())
    ensureTelegramApiResponseOk(telegramJson.decodeFromString<TelegramApiResponse>(response.body()))
    return response.body()
}

fun createSetBotCommandsRequest(botToken: String): HttpRequest {
    require(botToken.isNotBlank()) { "Токен Telegram-бота не должен быть пустым." }

    return HttpRequest.newBuilder()
        .uri(URI.create("$TELEGRAM_BASE_URL/bot$botToken/setMyCommands"))
        .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
        .POST(HttpRequest.BodyPublishers.ofString("commands=${urlEncode(telegramJson.encodeToString(botMenuCommands))}"))
        .build()
}

fun getUpdates(botToken: String, updateId: Long): String {
    val urlGetUpdates = "$TELEGRAM_BASE_URL/bot$botToken/getUpdates?offset=$updateId"
    val requestUpdates = HttpRequest.newBuilder().uri(URI.create(urlGetUpdates)).build()
    val responseUpdates = telegramHttpClient.send(requestUpdates, HttpResponse.BodyHandlers.ofString())
    return responseUpdates.body()
}

fun handleUpdate(
    botToken: String,
    update: TelegramUpdate,
    trainerProvider: (Long) -> LearnWordsTrainer,
    learningModeSelector: (Long, String) -> Unit = { _, _ -> },
    messageSender: (String, Long, String, InlineKeyboardMarkup?) -> String = ::sendMessage,
    questionSender: (String, Long, Question) -> String = ::sendQuestion,
    callbackAnswerer: (String, String) -> String = ::answerCallbackQuery,
) {
    val chatId = update.message?.chat?.id ?: update.callbackQuery?.message?.chat?.id ?: return

    val messageText = update.message?.text
    if (messageText != null && !isTelegramCommand(messageText, "start")) {
        messageSender(botToken, chatId, MAIN_MENU_TEXT, mainMenuKeyboard)
    }

    update.callbackQuery?.let { callback ->
        callback.data?.let { println("Получен callback: $it") }
        callbackAnswerer(botToken, callback.id)
        when {
            callback.data == CALLBACK_SELECT_LANGUAGE ->
                messageSender(botToken, chatId, LANGUAGE_MENU_TEXT, languageMenuKeyboard)

            callback.data == CALLBACK_MAIN_MENU ->
                messageSender(botToken, chatId, MAIN_MENU_TEXT, mainMenuKeyboard)

            callback.data == CALLBACK_STATISTICS -> {
                val responseText = formatStatistics(trainerProvider(chatId).getStatistics())
                messageSender(botToken, chatId, responseText, mainMenuKeyboard)
            }

            callback.data == CALLBACK_RESET_PROGRESS -> {
                val trainer = trainerProvider(chatId)
                trainer.resetProgress()
                messageSender(botToken, chatId, "Прогресс сброшен.", mainMenuKeyboard)
            }

            learningModeNamesByCallback[callback.data] != null -> {
                val callbackData = callback.data.orEmpty()
                learningModeSelector(chatId, normalizeLearningModeCallback(callbackData))
                val learningModeName = learningModeNamesByCallback.getValue(callbackData)
                val trainer = trainerProvider(chatId)
                val statistics = trainer.getStatistics()
                val restartCompletedSection =
                    statistics.totalWords > 0 && statistics.learnedWords == statistics.totalWords
                if (restartCompletedSection) {
                    trainer.resetProgress()
                }
                messageSender(
                    botToken,
                    chatId,
                    if (restartCompletedSection) {
                        "Раздел «$learningModeName» уже пройден. Начинаем заново."
                    } else {
                        "Выбран раздел: $learningModeName"
                    },
                    null,
                )
                sendNextQuestion(botToken, chatId, trainer, messageSender, questionSender)
            }

            callback.data?.startsWith(CALLBACK_DATA_ANSWER_PREFIX) == true ->
                handleAnswer(
                    botToken = botToken,
                    chatId = chatId,
                    callbackData = callback.data,
                    trainer = trainerProvider(chatId),
                    messageSender = messageSender,
                    questionSender = questionSender,
                )

            else ->
                messageSender(botToken, chatId, "Неизвестная команда.", mainMenuKeyboard)
        }
    }
}

private fun handleAnswer(
    botToken: String,
    chatId: Long,
    callbackData: String,
    trainer: LearnWordsTrainer,
    messageSender: (String, Long, String, InlineKeyboardMarkup?) -> String,
    questionSender: (String, Long, Question) -> String,
) {
    val answerIndex = callbackData.substringAfter(CALLBACK_DATA_ANSWER_PREFIX).toIntOrNull()
    val question = trainer.currentQuestion
    if (answerIndex == null || question == null) {
        messageSender(
            botToken,
            chatId,
            "Сначала выберите язык.",
            mainMenuKeyboard,
        )
        return
    }

    val isCorrect = trainer.checkAnswer(answerIndex + 1)
    val resultText = if (isCorrect) {
        "Правильно! $CORRECT_ANSWER_EMOJI"
    } else {
        "Неправильно! $INCORRECT_ANSWER_EMOJI"
    }
    messageSender(botToken, chatId, resultText, null)
    sendNextQuestion(botToken, chatId, trainer, messageSender, questionSender)
}

private fun sendNextQuestion(
    botToken: String,
    chatId: Long,
    trainer: LearnWordsTrainer,
    messageSender: (String, Long, String, InlineKeyboardMarkup?) -> String,
    questionSender: (String, Long, Question) -> String,
) {
    val question = trainer.getNextQuestion()
    if (question == null) {
        messageSender(botToken, chatId, "Вы выучили все слова в базе", mainMenuKeyboard)
    } else {
        questionSender(botToken, chatId, question)
    }
}

fun sendQuestion(botToken: String, chatId: Long, question: Question): String =
    sendMessage(
        botToken = botToken,
        chatId = chatId,
        text = question.correctWord.original,
        replyMarkup = createQuestionKeyboard(question),
    )

fun createQuestionKeyboard(question: Question): InlineKeyboardMarkup =
    InlineKeyboardMarkup(
        inlineKeyboard = question.variants.mapIndexed { index, word ->
            listOf(
                InlineKeyboardButton(
                    text = word.translated,
                    callbackData = "$CALLBACK_DATA_ANSWER_PREFIX$index",
                ),
            )
        },
    )

fun formatStatistics(statistics: Statistics): String =
    if (statistics.totalWords == 0) {
        "Словарь пуст."
    } else {
        "Выучено ${statistics.learnedWords} из ${statistics.totalWords} слов | ${statistics.learnedPercent}%"
    }

fun sendMessage(
    botToken: String,
    chatId: Long,
    text: String,
    replyMarkup: InlineKeyboardMarkup? = null,
): String {
    val request = createSendMessageRequest(botToken, chatId, text, replyMarkup)
    val response = telegramHttpClient.send(request, HttpResponse.BodyHandlers.ofString())
    return response.body()
}

fun createSendMessageRequest(
    botToken: String,
    chatId: Long,
    text: String,
    replyMarkup: InlineKeyboardMarkup? = null,
    disableNotification: Boolean = TELEGRAM_DISABLE_NOTIFICATION,
): HttpRequest {
    require(botToken.isNotBlank()) { "Токен Telegram-бота не должен быть пустым." }
    val textLength = text.codePointCount(0, text.length)
    require(textLength in 1..TELEGRAM_MESSAGE_MAX_LENGTH) {
        "Длина сообщения должна быть от 1 до $TELEGRAM_MESSAGE_MAX_LENGTH символов."
    }

    val parameters = mutableListOf(
        "chat_id=$chatId",
        "text=${urlEncode(text)}",
        "disable_notification=$disableNotification",
    )
    if (replyMarkup != null) {
        parameters += "reply_markup=${urlEncode(telegramJson.encodeToString(replyMarkup))}"
    }

    return HttpRequest.newBuilder()
        .uri(URI.create("$TELEGRAM_BASE_URL/bot$botToken/sendMessage"))
        .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
        .POST(HttpRequest.BodyPublishers.ofString(parameters.joinToString("&")))
        .build()
}

fun answerCallbackQuery(botToken: String, callbackQueryId: String): String {
    val request = createAnswerCallbackQueryRequest(botToken, callbackQueryId)
    val response = telegramHttpClient.send(request, HttpResponse.BodyHandlers.ofString())
    return response.body()
}

fun createAnswerCallbackQueryRequest(botToken: String, callbackQueryId: String): HttpRequest {
    require(botToken.isNotBlank()) { "Токен Telegram-бота не должен быть пустым." }
    require(callbackQueryId.isNotBlank()) { "Идентификатор callback-запроса не должен быть пустым." }

    return HttpRequest.newBuilder()
        .uri(URI.create("$TELEGRAM_BASE_URL/bot$botToken/answerCallbackQuery"))
        .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
        .POST(HttpRequest.BodyPublishers.ofString("callback_query_id=${urlEncode(callbackQueryId)}"))
        .build()
}

private fun urlEncode(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8)
