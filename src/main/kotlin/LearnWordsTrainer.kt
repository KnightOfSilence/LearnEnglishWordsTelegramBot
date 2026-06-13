import java.io.File
import kotlin.random.Random

data class Word(
    val original: String,
    val translated: String,
    var correctAnswersCount: Int = 0,
)

data class Question(
    val correctWord: Word,
    val variants: List<Word>,
)

data class Statistics(
    val learnedWords: Int,
    val totalWords: Int,
    val learnedPercent: Int,
)

class LearnWordsTrainer(
    private val wordsFile: File = File("words.txt"),
    private val learnedWordsThreshold: Int = 3,
    private val numberOfOptions: Int = 4,
    private val random: Random = Random.Default,
) {
    var currentQuestion: Question? = null
        private set
    val dictionary: List<Word> = loadDictionary()

    init {
        require(learnedWordsThreshold > 0) { "Порог изучения должен быть больше нуля." }
        require(numberOfOptions > 0) { "Количество вариантов должно быть больше нуля." }
    }

    fun getStatistics(): Statistics {
        val learned = dictionary.count { it.correctAnswersCount >= learnedWordsThreshold }
        val total = dictionary.size
        val percent = if (total > 0) (learned * 100 / total) else 0
        return Statistics(learned, total, percent)
    }

    fun getNextQuestion(): Question? {
        val notLearnedWords = dictionary.filter { it.correctAnswersCount < learnedWordsThreshold }

        if (notLearnedWords.isEmpty()) {
            currentQuestion = null
            return null
        }

        val correctWord = notLearnedWords.random(random)
        val incorrectOptions = dictionary
            .filter { it != correctWord }
            .shuffled(random)
            .take(numberOfOptions - 1)
        val variants = (incorrectOptions + correctWord).shuffled(random)

        currentQuestion = Question(
            correctWord = correctWord,
            variants = variants,
        )
        return currentQuestion
    }

    fun checkAnswer(userAnswer: Int): Boolean {
        val question = currentQuestion ?: return false
        if (userAnswer !in 1..question.variants.size) {
            return false
        }

        val isCorrect = question.variants[userAnswer - 1] == question.correctWord
        if (isCorrect) {
            question.correctWord.correctAnswersCount++
            saveDictionary()
        }
        currentQuestion = null
        return isCorrect
    }

    fun resetProgress() {
        dictionary.forEach { it.correctAnswersCount = 0 }
        currentQuestion = null
        saveDictionary()
    }

    private fun loadDictionary(): List<Word> {
        if (!wordsFile.exists()) {
            println("Файл со словарем не найден. Создан новый.")
            wordsFile.parentFile?.mkdirs()
            wordsFile.createNewFile()
            return emptyList()
        }

        return wordsFile.readLines().mapNotNull { line ->
            val parts = line.split("|").map(String::trim)
            val original = parts.getOrNull(0).orEmpty()
            val translated = parts.getOrNull(1).orEmpty()
            if (original.isBlank() || translated.isBlank()) {
                return@mapNotNull null
            }

            val correctAnswers = parts.getOrNull(2)?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            Word(original, translated, correctAnswers)
        }
    }

    private fun saveDictionary() {
        wordsFile.bufferedWriter().use { writer ->
            dictionary.forEach {
                writer.write("${it.original}|${it.translated}|${it.correctAnswersCount}\n")
            }
        }
    }
}
