import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.ByteBuffer
import java.util.concurrent.Flow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class TelegramTest {
    @Test
    fun `updates preserve chat id for every message`() {
        val response = parseUpdates(
            """
            {
              "ok": true,
              "result": [
                {
                  "update_id": 10,
                  "message": {
                    "message_id": 1,
                    "chat": {"id": 111, "type": "private"},
                    "text": "first"
                  }
                },
                {
                  "update_id": 11,
                  "message": {
                    "message_id": 2,
                    "chat": {"id": -222, "type": "group"},
                    "text": "second"
                  }
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(listOf(111L, -222L), response.result.map { it.message?.chat?.id })
        assertEquals(listOf("first", "second"), response.result.map { it.message?.text })
    }

    @Test
    fun `non message updates and non text messages can be skipped`() {
        val response = parseUpdates(
            """
            {
              "ok": true,
              "result": [
                {"update_id": 20, "callback_query": {"id": "callback"}},
                {
                  "update_id": 21,
                  "message": {
                    "chat": {"id": 333},
                    "photo": [{"file_id": "photo"}]
                  }
                }
              ]
            }
            """.trimIndent(),
        )

        assertNull(response.result[0].message)
        assertNull(response.result[1].message?.text)
    }

    @Test
    fun `send message request uses post and encodes text`() {
        val request = createSendMessageRequest("token", -123, "Привет & hello")

        assertEquals("POST", request.method())
        assertEquals(
            "https://api.telegram.org/bottoken/sendMessage",
            request.uri().toString(),
        )
        assertEquals(
            "chat_id=-123&text=%D0%9F%D1%80%D0%B8%D0%B2%D0%B5%D1%82+%26+hello",
            readBody(request),
        )
    }

    @Test
    fun `empty and oversized messages are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            createSendMessageRequest("token", 1, "")
        }
        assertFailsWith<IllegalArgumentException> {
            createSendMessageRequest("token", 1, "a".repeat(TELEGRAM_MESSAGE_MAX_LENGTH + 1))
        }
    }

    @Test
    fun `message with maximum unicode length is accepted`() {
        val text = "😀".repeat(TELEGRAM_MESSAGE_MAX_LENGTH)

        createSendMessageRequest("token", 1, text)
    }

    private fun readBody(request: HttpRequest): String {
        val bodyPublisher = request.bodyPublisher().orElseThrow()
        val chunks = mutableListOf<ByteArray>()
        bodyPublisher.subscribe(
            object : Flow.Subscriber<ByteBuffer> {
                override fun onSubscribe(subscription: Flow.Subscription) {
                    subscription.request(Long.MAX_VALUE)
                }

                override fun onNext(item: ByteBuffer) {
                    val bytes = ByteArray(item.remaining())
                    item.get(bytes)
                    chunks += bytes
                }

                override fun onError(throwable: Throwable) {
                    throw throwable
                }

                override fun onComplete() = Unit
            },
        )
        return chunks.fold(ByteArray(0)) { result, bytes -> result + bytes }.decodeToString()
    }
}
