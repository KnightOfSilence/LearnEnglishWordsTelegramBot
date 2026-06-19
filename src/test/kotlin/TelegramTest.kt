import java.io.File
import java.net.URLDecoder
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.concurrent.Flow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TelegramTest {
    @Test
    fun `bot token can be read from argument or environment`() {
        assertEquals("argument-token", resolveBotToken(arrayOf("argument-token"), emptyMap()))
        assertEquals(
            "environment-token",
            resolveBotToken(emptyArray(), mapOf("TELEGRAM_BOT_TOKEN" to "environment-token")),
        )
    }

    @Test
    fun `bot token is required`() {
        assertFailsWith<IllegalArgumentException> {
            resolveBotToken(emptyArray(), emptyMap())
        }
    }

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
    fun `telegram api error is parsed and reported`() {
        val response = parseUpdates(
            """
            {
              "ok": false,
              "error_code": 401,
              "description": "Unauthorized"
            }
            """.trimIndent(),
        )

        assertEquals(false, response.ok)
        assertEquals(401, response.errorCode)
        assertEquals("Unauthorized", response.description)

        val error = assertFailsWith<IllegalStateException> {
            ensureTelegramApiResponseOk(response)
        }
        assertEquals(
            "Telegram API вернул ошибку (код 401: Unauthorized). Проверьте токен и что не запущена другая копия бота.",
            error.message,
        )
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
            trainerProvider = { trainer },
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
            trainerProvider = { trainer },
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
            trainerProvider = { trainer },
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
            trainerProvider = { trainer },
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
    fun `answer callback checks answer and sends next question`() {
        val trainer = createTrainer(
            """
            cat|кошка|0
            dog|собака|0
            """.trimIndent(),
        )
        val question = assertNotNull(trainer.getNextQuestion())
        val correctIndex = question.variants.indexOf(question.correctWord)
        val sentMessages = mutableListOf<String>()
        val sentQuestions = mutableListOf<Question>()
        val update = TelegramUpdate(
            updateId = 35,
            callbackQuery = TelegramCallbackQuery(
                id = "callback-35",
                data = "$CALLBACK_DATA_ANSWER_PREFIX$correctIndex",
                message = TelegramMessage(TelegramChat(1001), question.correctWord.original),
            ),
        )

        handleUpdate(
            botToken = "token",
            update = update,
            trainerProvider = { trainer },
            messageSender = { _, _, text, _ ->
                sentMessages += text
                "{}"
            },
            questionSender = { _, _, nextQuestion ->
                sentQuestions += nextQuestion
                "{}"
            },
            callbackAnswerer = { _, _ -> "true" },
        )

        assertEquals(listOf("Правильно!"), sentMessages)
        assertEquals(1, question.correctWord.correctAnswersCount)
        assertEquals(1, sentQuestions.size)
        assertEquals(trainer.currentQuestion, sentQuestions.single())
    }

    @Test
    fun `incorrect answer shows correct translation and continues learning`() {
        val trainer = createTrainer(
            """
            cat|кошка|0
            dog|собака|0
            """.trimIndent(),
        )
        val question = assertNotNull(trainer.getNextQuestion())
        val incorrectIndex = question.variants.indexOfFirst { it != question.correctWord }
        val sentMessages = mutableListOf<String>()
        val sentQuestions = mutableListOf<Question>()

        handleUpdate(
            botToken = "token",
            update = TelegramUpdate(
                updateId = 36,
                callbackQuery = TelegramCallbackQuery(
                    id = "callback-36",
                    data = "$CALLBACK_DATA_ANSWER_PREFIX$incorrectIndex",
                    message = TelegramMessage(TelegramChat(1002), question.correctWord.original),
                ),
            ),
            trainerProvider = { trainer },
            messageSender = { _, _, text, _ ->
                sentMessages += text
                "{}"
            },
            questionSender = { _, _, nextQuestion ->
                sentQuestions += nextQuestion
                "{}"
            },
            callbackAnswerer = { _, _ -> "true" },
        )

        assertEquals(
            listOf("Неправильно! ${question.correctWord.original} - ${question.correctWord.translated}"),
            sentMessages,
        )
        assertEquals(0, question.correctWord.correctAnswersCount)
        assertEquals(1, sentQuestions.size)
    }

    @Test
    fun `answer without active question returns to menu`() {
        val trainer = createTrainer("cat|кошка|0")
        val sentMessages = mutableListOf<Triple<Long, String, InlineKeyboardMarkup?>>()

        handleUpdate(
            botToken = "token",
            update = TelegramUpdate(
                updateId = 37,
                callbackQuery = TelegramCallbackQuery(
                    id = "callback-37",
                    data = "${CALLBACK_DATA_ANSWER_PREFIX}0",
                    message = TelegramMessage(TelegramChat(1003), "old question"),
                ),
            ),
            trainerProvider = { trainer },
            messageSender = { _, chatId, text, keyboard ->
                sentMessages += Triple(chatId, text, keyboard)
                "{}"
            },
            callbackAnswerer = { _, _ -> "true" },
        )

        assertEquals(1, sentMessages.size)
        assertEquals(1003L, sentMessages.single().first)
        assertEquals("Сначала выберите «Учить слова».", sentMessages.single().second)
        assertEquals(mainMenuKeyboard, sentMessages.single().third)
    }

    @Test
    fun `reset progress callback clears only current chat trainer`() {
        val trainer = createTrainer("cat|кошка|3")
        val sentMessages = mutableListOf<Triple<Long, String, InlineKeyboardMarkup?>>()

        handleUpdate(
            botToken = "token",
            update = TelegramUpdate(
                updateId = 38,
                callbackQuery = TelegramCallbackQuery(
                    id = "callback-38",
                    data = CALLBACK_RESET_PROGRESS,
                    message = TelegramMessage(TelegramChat(1004), "Выберите действие:"),
                ),
            ),
            trainerProvider = { trainer },
            messageSender = { _, chatId, text, keyboard ->
                sentMessages += Triple(chatId, text, keyboard)
                "{}"
            },
            callbackAnswerer = { _, _ -> "true" },
        )

        assertEquals(0, trainer.dictionary.single().correctAnswersCount)
        assertEquals(1, sentMessages.size)
        assertEquals(1004L, sentMessages.single().first)
        assertEquals("Прогресс сброшен.", sentMessages.single().second)
        assertEquals(mainMenuKeyboard, sentMessages.single().third)
    }

    @Test
    fun `chat trainers have independent persistent progress`() {
        val root = kotlin.io.path.createTempDirectory("telegram-progress-").toFile()
        val source = File(root, "words.txt").apply { writeText("cat|кошка|2\n") }
        val progressDirectory = File(root, "users")
        val firstTrainer = createTrainerForChat(111, source, progressDirectory)
        val secondTrainer = createTrainerForChat(222, source, progressDirectory)

        repeat(3) {
            val question = assertNotNull(firstTrainer.getNextQuestion())
            assertTrue(firstTrainer.checkAnswer(question.variants.indexOf(question.correctWord) + 1))
        }

        assertEquals(Statistics(1, 1, 100), firstTrainer.getStatistics())
        assertEquals(Statistics(0, 1, 0), secondTrainer.getStatistics())
        assertEquals(
            Statistics(1, 1, 100),
            createTrainerForChat(111, source, progressDirectory).getStatistics(),
        )
        assertEquals("cat|кошка|2", source.readText().trim())
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
            """{"inline_keyboard":[[{"text":"Учить слова","callback_data":"learn_words"}],[{"text":"Статистика","callback_data":"statistics"}],[{"text":"Сбросить прогресс","callback_data":"reset_progress"}]]}""",
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
