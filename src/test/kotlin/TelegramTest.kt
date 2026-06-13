import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.ByteBuffer
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.Flow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
    fun `callback query is parsed with source chat and callback data`() {
        val response = parseUpdates(
            """
            {
              "ok": true,
              "result": [
                {
                  "update_id": 30,
                  "callback_query": {
                    "id": "callback-123",
                    "data": "learn_words",
                    "message": {
                      "chat": {"id": 777},
                      "text": "Выберите действие:"
                    }
                  }
                }
              ]
            }
            """.trimIndent(),
        )

        val callback = response.result.single().callbackQuery
        assertEquals("callback-123", callback?.id)
        assertEquals(CALLBACK_LEARN_WORDS, callback?.data)
        assertEquals(777L, callback?.message?.chat?.id)
    }

    @Test
    fun `callback is acknowledged and response is sent to source chat`() {
        val sentMessages = mutableListOf<Triple<Long, String, InlineKeyboardMarkup?>>()
        val answeredCallbacks = mutableListOf<String>()
        val update = TelegramUpdate(
            updateId = 31,
            callbackQuery = TelegramCallbackQuery(
                id = "callback-31",
                data = CALLBACK_STATISTICS,
                message = TelegramMessage(TelegramChat(888), "Выберите действие:"),
            ),
        )
        val trainer = createTrainer(
            """
            cat|кошка|3
            dog|собака|0
            house|дом|3
            """.trimIndent(),
        )

        handleUpdate(
            botToken = "token",
            update = update,
            trainer = trainer,
            messageSender = { _, chatId, text, keyboard ->
                sentMessages += Triple(chatId, text, keyboard)
                "{}"
            },
            callbackAnswerer = { _, callbackId ->
                answeredCallbacks += callbackId
                "true"
            },
        )

        assertEquals(listOf("callback-31"), answeredCallbacks)
        assertEquals(1, sentMessages.size)
        assertEquals(888L, sentMessages.single().first)
        assertEquals("Выучено 2 из 3 слов | 66%", sentMessages.single().second)
        assertEquals(mainMenuKeyboard, sentMessages.single().third)
    }

    @Test
    fun `text message receives menu keyboard in its own chat`() {
        val sentMessages = mutableListOf<Triple<Long, String, InlineKeyboardMarkup?>>()
        val update = TelegramUpdate(
            updateId = 32,
            message = TelegramMessage(TelegramChat(-999), "/start"),
        )
        val trainer = createTrainer("cat|кошка|0")

        handleUpdate(
            botToken = "token",
            update = update,
            trainer = trainer,
            messageSender = { _, chatId, text, keyboard ->
                sentMessages += Triple(chatId, text, keyboard)
                "{}"
            },
        )

        assertEquals(1, sentMessages.size)
        assertEquals(-999L, sentMessages.single().first)
        assertEquals("Выберите действие:", sentMessages.single().second)
        assertEquals(mainMenuKeyboard, sentMessages.single().third)
    }

    @Test
    fun `learn words callback sends next question to source chat`() {
        val trainer = createTrainer(
            """
            cat|кошка|0
            dog|собака|0
            house|дом|0
            """.trimIndent(),
        )
        val sentQuestions = mutableListOf<Pair<Long, Question>>()
        val update = TelegramUpdate(
            updateId = 33,
            callbackQuery = TelegramCallbackQuery(
                id = "callback-33",
                data = CALLBACK_LEARN_WORDS,
                message = TelegramMessage(TelegramChat(999), "Выберите действие:"),
            ),
        )

        handleUpdate(
            botToken = "token",
            update = update,
            trainer = trainer,
            questionSender = { _, chatId, question ->
                sentQuestions += chatId to question
                "{}"
            },
            callbackAnswerer = { _, _ -> "true" },
        )

        assertEquals(1, sentQuestions.size)
        assertEquals(999L, sentQuestions.single().first)
        assertEquals(trainer.currentQuestion, sentQuestions.single().second)
    }

    @Test
    fun `learn words callback reports when all words are learned`() {
        val trainer = createTrainer("cat|кошка|3")
        val sentMessages = mutableListOf<Triple<Long, String, InlineKeyboardMarkup?>>()
        val update = TelegramUpdate(
            updateId = 34,
            callbackQuery = TelegramCallbackQuery(
                id = "callback-34",
                data = CALLBACK_LEARN_WORDS,
                message = TelegramMessage(TelegramChat(1000), "Выберите действие:"),
            ),
        )

        handleUpdate(
            botToken = "token",
            update = update,
            trainer = trainer,
            messageSender = { _, chatId, text, keyboard ->
                sentMessages += Triple(chatId, text, keyboard)
                "{}"
            },
            callbackAnswerer = { _, _ -> "true" },
        )

        assertEquals(1, sentMessages.size)
        assertEquals(1000L, sentMessages.single().first)
        assertEquals("Вы выучили все слова в базе", sentMessages.single().second)
        assertEquals(mainMenuKeyboard, sentMessages.single().third)
    }

    @Test
    fun `question keyboard contains translated answers and indexed callback data`() {
        val correctWord = Word("cat", "кошка")
        val question = Question(
            correctWord = correctWord,
            variants = listOf(
                Word("dog", "собака"),
                correctWord,
                Word("house", "дом"),
            ),
        )

        val keyboard = createQuestionKeyboard(question)

        assertEquals(
            listOf(
                InlineKeyboardButton("собака", "answer_0"),
                InlineKeyboardButton("кошка", "answer_1"),
                InlineKeyboardButton("дом", "answer_2"),
            ),
            keyboard.inlineKeyboard.flatten(),
        )
    }

    @Test
    fun `send question request uses english word and answer keyboard`() {
        val correctWord = Word("cat", "кошка")
        val question = Question(
            correctWord = correctWord,
            variants = listOf(correctWord, Word("dog", "собака")),
        )
        val request = createSendMessageRequest(
            botToken = "token",
            chatId = 123,
            text = question.correctWord.original,
            replyMarkup = createQuestionKeyboard(question),
        )
        val parameters = parseFormBody(readBody(request))

        assertEquals("cat", parameters["text"])
        assertEquals(
            """{"inline_keyboard":[[{"text":"кошка","callback_data":"answer_0"}],[{"text":"собака","callback_data":"answer_1"}]]}""",
            parameters["reply_markup"],
        )
    }

    @Test
    fun `empty dictionary statistics are formatted for telegram`() {
        assertEquals(
            "Словарь пуст.",
            formatStatistics(Statistics(learnedWords = 0, totalWords = 0, learnedPercent = 0)),
        )
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

    @Test
    fun `send message request serializes inline keyboard as reply markup`() {
        val request = createSendMessageRequest("token", 123, "Меню", mainMenuKeyboard)
        val parameters = parseFormBody(readBody(request))

        assertEquals("123", parameters["chat_id"])
        assertEquals("Меню", parameters["text"])
        assertEquals(
            """{"inline_keyboard":[[{"text":"Учить слова","callback_data":"learn_words"}],[{"text":"Статистика","callback_data":"statistics"}]]}""",
            parameters["reply_markup"],
        )
    }

    @Test
    fun `callback data must fit telegram byte limit`() {
        InlineKeyboardButton("Кнопка", "a".repeat(TELEGRAM_CALLBACK_DATA_MAX_BYTES))

        assertFailsWith<IllegalArgumentException> {
            InlineKeyboardButton("Кнопка", "я".repeat(TELEGRAM_CALLBACK_DATA_MAX_BYTES))
        }
        assertFailsWith<IllegalArgumentException> {
            InlineKeyboardButton("Кнопка", "")
        }
    }

    @Test
    fun `answer callback query request uses callback id`() {
        val request = createAnswerCallbackQueryRequest("token", "callback & 1")

        assertEquals("POST", request.method())
        assertEquals(
            "https://api.telegram.org/bottoken/answerCallbackQuery",
            request.uri().toString(),
        )
        assertEquals("callback_query_id=callback+%26+1", readBody(request))
    }

    @Test
    fun `inline keyboard requires at least one button`() {
        assertFailsWith<IllegalArgumentException> {
            InlineKeyboardMarkup(emptyList())
        }
        assertFailsWith<IllegalArgumentException> {
            InlineKeyboardMarkup(listOf(emptyList()))
        }
        assertTrue(mainMenuKeyboard.inlineKeyboard.flatten().isNotEmpty())
    }

    private fun parseFormBody(body: String): Map<String, String> =
        body.split("&").associate { parameter ->
            val (name, value) = parameter.split("=", limit = 2)
            name to URLDecoder.decode(value, StandardCharsets.UTF_8)
        }

    private fun createTrainer(content: String): LearnWordsTrainer {
        val file = kotlin.io.path.createTempFile(prefix = "telegram-dictionary-", suffix = ".txt")
            .toFile()
            .apply { writeText(content) }
        return LearnWordsTrainer(file)
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
