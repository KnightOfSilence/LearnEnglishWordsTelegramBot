import java.io.File

const val LEARNING_THRESHOLD = 3
const val FIRST_FOUR_WORDS_TO_TAKE = 4

val wordsFile = File("words.txt")
var dictionary = mutableListOf<Word>()

data class Word(
    val original: String,
    val translated: String,
    var correctAnswersCount: Int = 0,
)

fun loadDictionary(): List<Word> {
    val dictionaryFile = File("words.txt")
    val lines = dictionaryFile.readLines()

    for (line in lines) {
        val parts = line.split("|")
        val correctAnswers = parts.getOrNull(2)?.toIntOrNull() ?: 0
        val word =
            Word(original = parts[0], translated = parts[1], correctAnswersCount = correctAnswers)
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
            1 -> {
                "Учить слова"
                println(learnWords(dictionary))
            }

            2 -> {
                "Статистика"
                println(calculateStatistics(dictionary))
            }

            0 -> {
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

fun learnWords(dictionary: MutableList<Word>) {
    while (true) {
        val notLearnedList = dictionary.filter { it.correctAnswersCount < LEARNING_THRESHOLD }

        if (notLearnedList.isEmpty()) {
            println("Все слова в словаре выучены!")
            break
        } else {
            val questionWords = notLearnedList.shuffled().take(FIRST_FOUR_WORDS_TO_TAKE)
            val correctAnswer = questionWords.random()

            println()
            println("${correctAnswer.original}: ")

            val shuffledOption = questionWords.shuffled()
            shuffledOption.forEachIndexed { index, word -> println("${index + 1} - ${word.translated}") }
            println("----------")
            println("0 - Меню")
            println("Ваш ответ(введите номер): ")
            val userAnswerInput = readlnOrNull()?.toIntOrNull()
            when (userAnswerInput) {
                0 -> {
                    "Выход в главное меню"
                    break
                }

                in 1..questionWords.size -> {
                    val selectedWord = shuffledOption[userAnswerInput!! - 1]
                    if (selectedWord == correctAnswer) {
                        println("Правильно!")
                        correctAnswer.correctAnswersCount++
                        saveDictionary(dictionary)

                    } else {
                        println(
                            "Неправильно! ${correctAnswer.original} " +
                                    "– это ${correctAnswer.translated}"
                        )
                    }
                }

                else -> {
                    println("Некорректный ввод. Введите число от 0 до ${questionWords.size}.")
                }
            }
        }
    }
}

fun saveDictionary(dictionary: List<Word>) {

    wordsFile.bufferedWriter().use { out ->
        dictionary.forEach { word ->
            out.write("${word.original}|${word.translated}|${word.correctAnswersCount}\n")
        }
    }
}

fun main() {
    loadDictionary()
    showStartScreen()
}