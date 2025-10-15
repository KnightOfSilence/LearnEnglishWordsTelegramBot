import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

const val TELEGRAM_BASE_URL = "https://api.telegram.org"

fun main(args: Array<String>) {

    val botToken = args[0]
    var updateId = 0
    val updateIdRegex = """"update_id":(\d+)""".toRegex()
    val messageTextRegex = """"text":"([^"]*)"""".toRegex()

    while (true) {
        Thread.sleep(3000)
        val updates: String = getUpdates(botToken, updateId)
        println(updates)

        val lastMatchResult = updateIdRegex.findAll(updates).lastOrNull()

        if (lastMatchResult != null) {
            val updateIdString = lastMatchResult.groupValues[1]
            updateId = updateIdString.toInt() + 1
            println("Последний updateId: ${updateId - 1}")
        }

        val lastMessageMatch = messageTextRegex.findAll(updates).lastOrNull()
        if (lastMessageMatch != null) {
            val messageText = lastMessageMatch.groupValues[1]
            println("Последнее сообщение: $messageText")
        }
    }
}

fun getUpdates(botToken: String, updateId: Int): String {
    val urlGetUpdates = "$TELEGRAM_BASE_URL/bot$botToken/getUpdates?offset=$updateId"
    val client: HttpClient = HttpClient.newBuilder().build()
    val requestUpdates = HttpRequest.newBuilder().uri(URI.create(urlGetUpdates)).build()
    val responseUpdates = client.send(requestUpdates, HttpResponse.BodyHandlers.ofString())
    return responseUpdates.body()
}
