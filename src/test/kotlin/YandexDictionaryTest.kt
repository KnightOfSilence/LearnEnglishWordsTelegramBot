import java.io.File
import java.net.http.HttpRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class YandexDictionaryTest {
    @Test
    fun `parse yandex dictionary words supports separators and removes duplicates`() {
        assertEquals(
            listOf("cat", "dog", "house", "sun"),
            parseYandexDictionaryWords(" cat, dog;house\ncat\n sun "),
        )
    }

    @Test
    fun `parse yandex dictionary words returns empty list for blank input`() {
        assertTrue(parseYandexDictionaryWords(null).isEmpty())
        assertTrue(parseYandexDictionaryWords(" , ; \n ").isEmpty())
    }

    @Test
    fun `yandex lookup request encodes query parameters and returns first translation`() {
        var request: HttpRequest? = null
        val client = YandexDictionaryClient(
            apiKey = "key 1",
            responseLoader = {
                request = it
                """{"def":[{"tr":[{"text":"кошка"},{"text":"кот"}]}]}"""
            },
        )

        assertEquals(Word("cat", "кошка"), client.lookup("cat"))
        assertEquals(
            "https://dictionary.yandex.net/api/v1/dicservice.json/lookup?key=key+1&lang=en-ru&text=cat",
            request?.uri().toString(),
        )
    }

    @Test
    fun `yandex lookup accepts successful response with code 200`() {
        val client = YandexDictionaryClient(
            apiKey = "key",
            responseLoader = {
                """{"head":{},"def":[{"text":"cat","tr":[{"text":"кошка"}]}],"nmt_code":1,"code":200}"""
            },
        )

        assertEquals(Word("cat", "кошка"), client.lookup("cat"))
    }

    @Test
    fun `yandex lookup returns null when translation is missing`() {
        val client = YandexDictionaryClient(
            apiKey = "key",
            responseLoader = { """{"def":[]}""" },
        )

        assertNull(client.lookup("cat"))
        assertNull(client.lookup(" "))
    }

    @Test
    fun `yandex lookup reports api errors`() {
        val client = YandexDictionaryClient(
            apiKey = "key",
            responseLoader = { """{"code":401,"message":"Invalid key"}""" },
        )

        val error = assertFailsWith<IllegalStateException> {
            client.lookup("cat")
        }
        assertEquals("Yandex Dictionary API вернул ошибку (401: Invalid key).", error.message)
    }

    @Test
    fun `initial dictionary prefers yandex when api key is configured`() {
        val initialDictionary = loadInitialDictionary(
            environment = mapOf(
                YANDEX_DICTIONARY_API_KEY_ENV to "api-key",
                YANDEX_DICTIONARY_LANG_ENV to "en-ru",
                YANDEX_DICTIONARY_WORDS_ENV to "cat,dog",
            ),
            yandexDictionaryLoader = { apiKey, lang, words ->
                assertEquals("api-key", apiKey)
                assertEquals("en-ru", lang)
                assertEquals(listOf("cat", "dog"), words)
                listOf(Word("cat", "кошка"), Word("dog", "собака"))
            },
        )

        assertEquals(listOf(Word("cat", "кошка"), Word("dog", "собака")), initialDictionary)
    }

    @Test
    fun `initial dictionary uses local properties api key when environment key is absent`() {
        val localProperties = createPropertiesFile(
            """
            $YANDEX_DICTIONARY_API_KEY_ENV=local-api-key
            $YANDEX_DICTIONARY_WORDS_ENV=cat
            """.trimIndent(),
        )

        val initialDictionary = loadInitialDictionary(
            environment = emptyMap(),
            localPropertiesFile = localProperties,
            yandexDictionaryLoader = { apiKey, lang, words ->
                assertEquals("local-api-key", apiKey)
                assertEquals(YANDEX_DICTIONARY_DEFAULT_LANG, lang)
                assertEquals(listOf("cat"), words)
                listOf(Word("cat", "кошка"))
            },
        )

        assertEquals(listOf(Word("cat", "кошка")), initialDictionary)
    }

    @Test
    fun `advanced dictionary loads phrases from yandex dictionary`() {
        val advancedDictionary = loadAdvancedEnglishDictionary(
            environment = mapOf(
                YANDEX_DICTIONARY_API_KEY_ENV to "api-key",
                YANDEX_DICTIONARY_PHRASES_ENV to "make a decision, pay attention",
            ),
            yandexDictionaryLoader = { apiKey, lang, phrases ->
                assertEquals("api-key", apiKey)
                assertEquals(YANDEX_DICTIONARY_DEFAULT_LANG, lang)
                assertEquals(listOf("make a decision", "pay attention"), phrases)
                listOf(
                    Word("make a decision", "принять решение"),
                    Word("pay attention", "обратить внимание"),
                )
            },
        )

        assertEquals(
            listOf(
                Word("make a decision", "принять решение"),
                Word("pay attention", "обратить внимание"),
            ),
            advancedDictionary,
        )
    }

    @Test
    fun `advanced dictionary can use custom phrase direction`() {
        val advancedDictionary = loadAdvancedEnglishDictionary(
            environment = mapOf(
                YANDEX_DICTIONARY_API_KEY_ENV to "api-key",
                YANDEX_DICTIONARY_PHRASES_ENV to "make a decision",
                YANDEX_DICTIONARY_PHRASES_LANG_ENV to "en-ru-custom",
            ),
            yandexDictionaryLoader = { _, lang, phrases ->
                assertEquals("en-ru-custom", lang)
                assertEquals(listOf("make a decision"), phrases)
                listOf(Word("make a decision", "принять решение"))
            },
        )

        assertEquals(listOf(Word("make a decision", "принять решение")), advancedDictionary)
    }

    @Test
    fun `initial dictionary requires yandex api key`() {
        val error = assertFailsWith<IllegalStateException> {
            loadInitialDictionary(
                environment = emptyMap(),
                localPropertiesFile = createPropertiesFile(""),
            )
        }

        assertEquals(
            "Для загрузки словаря задайте $YANDEX_DICTIONARY_API_KEY_ENV " +
                "в переменных окружения или в $LOCAL_PROPERTIES_FILE_NAME.",
            error.message,
        )
    }

    @Test
    fun `initial dictionary requires configured yandex words`() {
        val error = assertFailsWith<IllegalStateException> {
            loadInitialDictionary(
                environment = mapOf(YANDEX_DICTIONARY_API_KEY_ENV to "api-key"),
                localPropertiesFile = createPropertiesFile(""),
            )
        }

        assertEquals(
            "Для загрузки словаря задайте $YANDEX_DICTIONARY_WORDS_ENV " +
                "в переменных окружения или в $LOCAL_PROPERTIES_FILE_NAME.",
            error.message,
        )
    }

    @Test
    fun `advanced dictionary requires configured phrases`() {
        val error = assertFailsWith<IllegalStateException> {
            loadAdvancedEnglishDictionary(
                environment = mapOf(YANDEX_DICTIONARY_API_KEY_ENV to "api-key"),
                localPropertiesFile = createPropertiesFile(""),
            )
        }

        assertEquals(
            "Для загрузки продвинутого словаря задайте $YANDEX_DICTIONARY_PHRASES_ENV " +
                "в переменных окружения или в $LOCAL_PROPERTIES_FILE_NAME.",
            error.message,
        )
    }

    private fun createPropertiesFile(content: String): File =
        kotlin.io.path.createTempFile(prefix = "local-", suffix = ".properties")
            .toFile()
            .apply { writeText(content) }
}
