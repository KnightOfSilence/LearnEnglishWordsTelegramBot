import java.io.File

const val LEARNING_THRESHOLD = 3

val wordsFile = File("words.txt")
val dictionary = mutableListOf<Word>()

data class Word(
    val original: String,
    val translate: String,
    val correctAnswersCount: Int = 0,
)

fun loadDictionary(): MutableList<Word> {
    val dictionaryFile = File("words.txt")
    val lines = dictionaryFile.readLines()

    for (line in lines) {
        val parts = line.split("|")
        val correctAnswers = parts.getOrNull(2)?.toIntOrNull() ?: 0
        val word =
            Word(original = parts[0], translate = parts[1], correctAnswersCount = correctAnswers)
        dictionary.add(word)
    }
    return dictionary
}

fun showStartScreen() {
    while (true) {
        println("Меню:")
        println("1 – Учить слова")
        println("2 – Статистика")
        println("0 – Выход")

        print("Ваш выбор: ")
        val userChoice = readln().toIntOrNull()
        when (userChoice) {
            1 -> "Учить слова"
            2 -> {
                "Статистика"
                calculateStatistics(dictionary)
            }

            3 -> {
                "Выход"
                break
            }

            else -> "Введите число 1, 2 или 0"
        }
    }
}

fun calculateStatistics(dictionary: MutableList<Word>): String {
    val totalCount = dictionary.size
    val learnedCount = dictionary.filter { it.correctAnswersCount >= LEARNING_THRESHOLD }.count()
    if (totalCount == 0) return "Словарь пуст"
    else {
        val percent = (learnedCount.toDouble() / totalCount.toDouble()) * 100
        return "Выучено $learnedCount из $totalCount слов | ${"%.1f".format(percent)}%"
    }
}

fun learnedList(dictionary: MutableList<Word>) {
    while (true) {
        val notLearnedList = dictionary.filter { it.correctAnswersCount < LEARNING_THRESHOLD }

        if (notLearnedList.isEmpty()) {
            println("Все слова в словаре выучены!")
            break
        } else {
            val questionWords = notLearnedList.shuffled().take(4)
            val correctAnswer = questionWords.random()

            println()
            println("${correctAnswer.original}:")

            val shuffledOption = questionWords.shuffled()
            shuffledOption.forEachIndexed { index, word -> println("${index + 1} - ${word.translate}") }
            println("Ваш ответ(введите номер): ")
            val userAnswerInput = readlnOrNull()?.toIntOrNull()
        }
    }
}

fun main() {

    val lines: List<String> = wordsFile.readLines()

    for (line in lines) {
        val parts = line.split("|")
        val correctAnswers = parts.getOrNull(2)?.toIntOrNull() ?: 0
        val word =
            Word(original = parts[0], translate = parts[1], correctAnswersCount = correctAnswers)
        dictionary.add(word)
    }
    println("Содержимое словаря:")
    dictionary.forEach { println(it) }

    val dictionary = loadDictionary()
}