import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.util.Properties

const val YANDEX_DICTIONARY_BASE_URL = "https://dictionary.yandex.net/api/v1/dicservice.json"
const val YANDEX_DICTIONARY_API_KEY_ENV = "YANDEX_DICTIONARY_API_KEY"
const val YANDEX_DICTIONARY_LANG_ENV = "YANDEX_DICTIONARY_LANG"
const val YANDEX_DICTIONARY_WORDS_ENV = "YANDEX_DICTIONARY_WORDS"
const val YANDEX_DICTIONARY_DEFAULT_LANG = "en-ru"
const val LOCAL_PROPERTIES_FILE_NAME = "local.properties"

val defaultYandexDictionaryWords = listOf(
    "cat",
    "dog",
    "house",
    "book",
    "water",
    "sun",
    "work",
    "family",
    "school",
    "friend",
)

@Serializable
data class YandexLookupResponse(
    val def: List<YandexDefinition> = emptyList(),
)

@Serializable
data class YandexDefinition(
    val tr: List<YandexTranslation> = emptyList(),
)

@Serializable
data class YandexTranslation(
    val text: String,
)

@Serializable
data class YandexErrorResponse(
    val code: Int? = null,
    val message: String? = null,
)

class YandexDictionaryClient(
    private val apiKey: String,
    private val lang: String = YANDEX_DICTIONARY_DEFAULT_LANG,
    private val baseUrl: String = YANDEX_DICTIONARY_BASE_URL,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val responseLoader: (HttpRequest) -> String = ::loadResponseBody,
) {
    init {
        require(apiKey.isNotBlank()) { "API-ключ Yandex Dictionary не должен быть пустым." }
        require(lang.isNotBlank()) { "Направление перевода не должно быть пустым." }
    }

    fun loadWords(words: List<String>): List<Word> =
        words.mapNotNull { lookup(it) }

    fun lookup(word: String): Word? {
        val normalizedWord = word.trim()
        if (normalizedWord.isBlank()) {
            return null
        }

        val responseBody = responseLoader(createLookupRequest(normalizedWord))
        val error = json.decodeFromString<YandexErrorResponse>(responseBody)
        if (error.code != null && error.code != 200) {
            error("Yandex Dictionary API вернул ошибку (${error.code}: ${error.message.orEmpty()}).")
        }

        val response = json.decodeFromString<YandexLookupResponse>(responseBody)
        val translated = response.def
            .asSequence()
            .flatMap { it.tr.asSequence() }
            .map { it.text.trim() }
            .firstOrNull { it.isNotBlank() }

        return translated?.let { Word(normalizedWord, it) }
    }

    fun createLookupRequest(word: String): HttpRequest =
        HttpRequest.newBuilder()
            .uri(
                URI.create(
                    "$baseUrl/lookup?key=${urlEncode(apiKey)}&lang=${urlEncode(lang)}&text=${urlEncode(word)}",
                ),
            )
            .build()
}

fun loadInitialDictionary(
    sourceDictionary: File,
    environment: Map<String, String> = System.getenv(),
    localPropertiesFile: File = File(LOCAL_PROPERTIES_FILE_NAME),
    yandexDictionaryLoader: (String, String, List<String>) -> List<Word> = { apiKey, lang, words ->
        YandexDictionaryClient(apiKey = apiKey, lang = lang).loadWords(words)
    },
): List<Word> {
    val localProperties = loadLocalProperties(localPropertiesFile)
    val apiKey = findConfigValue(YANDEX_DICTIONARY_API_KEY_ENV, environment, localProperties)
    if (apiKey != null) {
        val lang = findConfigValue(YANDEX_DICTIONARY_LANG_ENV, environment, localProperties)
            ?: YANDEX_DICTIONARY_DEFAULT_LANG
        val words = parseYandexDictionaryWords(
            findConfigValue(YANDEX_DICTIONARY_WORDS_ENV, environment, localProperties),
        )
            .ifEmpty { defaultYandexDictionaryWords }

        return yandexDictionaryLoader(apiKey, lang, words)
    }

    return loadDictionaryFromFile(sourceDictionary)
}

fun loadLocalProperties(localPropertiesFile: File): Map<String, String> {
    if (!localPropertiesFile.exists()) {
        return emptyMap()
    }

    val properties = Properties()
    localPropertiesFile.inputStream().use(properties::load)
    return properties.entries.associate { (key, value) -> key.toString() to value.toString() }
}

fun loadDictionaryFromFile(sourceDictionary: File): List<Word> {
    if (!sourceDictionary.exists()) {
        return emptyList()
    }

    return sourceDictionary.readLines().mapNotNull { line ->
        val parts = line.split("|").map(String::trim)
        val original = parts.getOrNull(0).orEmpty()
        val translated = parts.getOrNull(1).orEmpty()
        if (original.isBlank() || translated.isBlank()) {
            null
        } else {
            Word(original, translated)
        }
    }
}

fun parseYandexDictionaryWords(rawWords: String?): List<String> =
    rawWords
        ?.split(",", ";", "\n")
        ?.map(String::trim)
        ?.filter(String::isNotBlank)
        ?.distinct()
        .orEmpty()

private fun findConfigValue(
    key: String,
    environment: Map<String, String>,
    localProperties: Map<String, String>,
): String? =
    environment[key]?.takeIf { it.isNotBlank() }
        ?: localProperties[key]?.takeIf { it.isNotBlank() }

private fun loadResponseBody(request: HttpRequest): String {
    val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
    return response.body()
}

private fun urlEncode(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8)
