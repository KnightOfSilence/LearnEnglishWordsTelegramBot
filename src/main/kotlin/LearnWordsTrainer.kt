import java.io.File

data class Word(
    val original: String,
    val translated: String,
    var correctAnswersCount: Int = 0,
)

data class Question(
    val correctWord: Word,
    val variants: List<Word>, // Перемешанные варианты для вывода
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
) {
    var currentQuestion: Question? = null // Текущий вопрос, на который отвечает пользователь
    val dictionary = loadDictionary()

    fun getStatistics(): Statistics {
        val learned = dictionary.filter { it.correctAnswersCount >= learnedWordsThreshold }.size
        val total = dictionary.size
        val percent = if (total > 0) (learned * 100 / total) else 0
        return Statistics(learned, total, percent)
    }

    fun getNextQuestion(): Question? {
        val notLearnedWords = dictionary.filter { it.correctAnswersCount < learnedWordsThreshold }
        if (notLearnedWords.isEmpty()) {
            return null
        }

        val questionWords = notLearnedWords.shuffled().take(minOf(notLearnedWords.size, numberOfOptions))
        val correctWord = questionWords.random()

        currentQuestion = Question(
            correctWord = correctWord,
            variants = questionWords.shuffled()
        )
        return currentQuestion
    }

    fun checkAnswer(userAnswer: Int): Boolean {

        val currentQuestion = this.currentQuestion ?: return false
        val correctAnswerIndex = currentQuestion.variants.indexOf(currentQuestion.correctWord)
        if (userAnswer - 1 == correctAnswerIndex) {
            currentQuestion.correctWord.correctAnswersCount++
            saveDictionary()
            return true
        }
        return false
    }

    private fun loadDictionary(): MutableList<Word> {
        if (!wordsFile.exists()) {
            println("Файл со словарем не найден. Создан новый.")
            wordsFile.createNewFile()
            return mutableListOf()
        }
        val dictionary = mutableListOf<Word>()
        wordsFile.readLines().forEach {
            val parts = it.split("|")
            if (parts.size >= 2) {
                val correctAnswers = parts.getOrNull(2)?.toIntOrNull() ?: 0
                dictionary.add(Word(parts[0], parts[1], correctAnswers))
            }
        }
        return dictionary
    }

    private fun saveDictionary() {
        wordsFile.bufferedWriter().use { writer ->
            dictionary.forEach {
                writer.write("${it.original}|${it.translated}|${it.correctAnswersCount}\n")
            }
        }
    }
}
