import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets

const val TELEGRAM_BASE_URL = "https://api.telegram.org"
const val TELEGRAM_MESSAGE_MAX_LENGTH = 4096
const val TELEGRAM_CALLBACK_DATA_MAX_BYTES = 64
const val CALLBACK_LEARN_WORDS = "learn_words"
const val CALLBACK_STATISTICS = "statistics"

private val telegramHttpClient: HttpClient = HttpClient.newHttpClient()
private val telegramJson = Json { ignoreUnknownKeys = true }

@Serializable
data class TelegramUpdatesResponse(
    val ok: Boolean,
    val result: List<TelegramUpdate> = emptyList(),
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
        listOf(InlineKeyboardButton("Учить слова", CALLBACK_LEARN_WORDS)),
        listOf(InlineKeyboardButton("Статистика", CALLBACK_STATISTICS)),
    ),
)

fun main(args: Array<String>) {
    require(args.isNotEmpty() && args[0].isNotBlank()) {
        "Передайте токен Telegram-бота первым аргументом."
    }

    val botToken = args[0]
    var updateId = 0L
    while (true) {
        Thread.sleep(3000)
        val updates = parseUpdates(getUpdates(botToken, updateId))

        updates.result.forEach { update ->
            handleUpdate(botToken, update)
        }

        updateId = updates.result.maxOfOrNull { it.updateId + 1 } ?: updateId
    }
}

fun parseUpdates(responseBody: String): TelegramUpdatesResponse =
    telegramJson.decodeFromString(responseBody)

fun getUpdates(botToken: String, updateId: Long): String {
    val urlGetUpdates = "$TELEGRAM_BASE_URL/bot$botToken/getUpdates?offset=$updateId"
    val requestUpdates = HttpRequest.newBuilder().uri(URI.create(urlGetUpdates)).build()
    val responseUpdates = telegramHttpClient.send(requestUpdates, HttpResponse.BodyHandlers.ofString())
    return responseUpdates.body()
}

fun handleUpdate(
    botToken: String,
    update: TelegramUpdate,
    messageSender: (String, Long, String, InlineKeyboardMarkup?) -> String = ::sendMessage,
    callbackAnswerer: (String, String) -> String = ::answerCallbackQuery,
) {
    if (update.message?.text != null) {
        messageSender(botToken, update.message.chat.id, "Выберите действие:", mainMenuKeyboard)
    }

    update.callbackQuery?.let { callback ->
        callbackAnswerer(botToken, callback.id)
        val chatId = callback.message?.chat?.id ?: return@let
        val responseText = when (callback.data) {
            CALLBACK_LEARN_WORDS -> "Начинаем учить слова."
            CALLBACK_STATISTICS -> "Показываю статистику."
            else -> "Неизвестная команда."
        }
        messageSender(botToken, chatId, responseText, mainMenuKeyboard)
    }
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
): HttpRequest {
    require(botToken.isNotBlank()) { "Токен Telegram-бота не должен быть пустым." }
    val textLength = text.codePointCount(0, text.length)
    require(textLength in 1..TELEGRAM_MESSAGE_MAX_LENGTH) {
        "Длина сообщения должна быть от 1 до $TELEGRAM_MESSAGE_MAX_LENGTH символов."
    }

    val parameters = mutableListOf(
        "chat_id=$chatId",
        "text=${urlEncode(text)}",
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
