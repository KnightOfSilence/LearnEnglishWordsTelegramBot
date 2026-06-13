import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets

const val TELEGRAM_BASE_URL = "https://api.telegram.org"
const val TELEGRAM_MESSAGE_MAX_LENGTH = 4096

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
            val message = update.message
            if (message?.text != null) {
                sendMessage(botToken, message.chat.id, message.text)
            }
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

fun sendMessage(botToken: String, chatId: Long, text: String): String {
    val request = createSendMessageRequest(botToken, chatId, text)
    val response = telegramHttpClient.send(request, HttpResponse.BodyHandlers.ofString())
    return response.body()
}

fun createSendMessageRequest(botToken: String, chatId: Long, text: String): HttpRequest {
    require(botToken.isNotBlank()) { "Токен Telegram-бота не должен быть пустым." }
    val textLength = text.codePointCount(0, text.length)
    require(textLength in 1..TELEGRAM_MESSAGE_MAX_LENGTH) {
        "Длина сообщения должна быть от 1 до $TELEGRAM_MESSAGE_MAX_LENGTH символов."
    }

    val requestBody = "chat_id=$chatId&text=${urlEncode(text)}"
    return HttpRequest.newBuilder()
        .uri(URI.create("$TELEGRAM_BASE_URL/bot$botToken/sendMessage"))
        .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
        .build()
}

private fun urlEncode(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8)
