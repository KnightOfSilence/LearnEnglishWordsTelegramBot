import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

const val TELEGRAM_BASE_URL = "https://api.telegram.org"

fun main(args: Array<String>) {

    val botToken = args[0]
    val urlGetMe = "$TELEGRAM_BASE_URL/bot$botToken/getMe"
    val urlGetUpdates = "$TELEGRAM_BASE_URL/bot$botToken/getUpdates"

    val client: HttpClient = HttpClient.newBuilder().build()
    val request: HttpRequest = HttpRequest.newBuilder().uri(URI.create(urlGetMe)).build()
    val response: HttpResponse<String> = client.send(request, HttpResponse.BodyHandlers.ofString())
    println(response.body())

    val requestUpdates = HttpRequest.newBuilder().uri(URI.create(urlGetUpdates)).build()
    val responseUpdates = client.send(requestUpdates, HttpResponse.BodyHandlers.ofString())
    println(responseUpdates.body())
}