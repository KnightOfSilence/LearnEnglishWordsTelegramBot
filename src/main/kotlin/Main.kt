import java.io.File

const val LEARNING_THRESHOLD = 3
const val FIRST_FOUR_WORDS_TO_TAKE = 4

val wordsFile = File("words.txt")
val dictionary = mutableListOf<Word>()

data class Word(
    val english: String,
    val russian: String,
    var correctAnswersCount: Int = 0,
)

fun loadDictionary(): MutableList<Word> {
    val dictionaryFile = File("words.txt")
    val lines = dictionaryFile.readLines()

    for (line in lines) {
        val parts = line.split("|")
        val correctAnswers = parts.getOrNull(2)?.toIntOrNull() ?: 0
        val word =
            Word(english = parts[0], russian = parts[1], correctAnswersCount = correctAnswers)
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
                learnWords(dictionary)
            }

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
            println("${correctAnswer.english}: ")

            val shuffledOption = questionWords.shuffled()
            shuffledOption.forEachIndexed { index, word -> println("${index + 1} - ${word.russian}") }
            println("----------")
            println("0 - Меню")
            println("Ваш ответ(введите номер): ")
            val userAnswerInput = readlnOrNull()?.toIntOrNull()
            when (userAnswerInput) {
                0 -> {
                    "Выход в главное меню"
                    showStartScreen()
                }

                in 1..questionWords.size -> {
                    val selectedWord = shuffledOption[userAnswerInput!! - 1]
                    if (selectedWord == correctAnswer) {
                        println("Правильно!")
                        correctAnswer.correctAnswersCount++
                        saveDictionary(dictionary)

                    } else {
                        println(
                            "Неправильно! ${correctAnswer.english} " +
                                    "– это ${correctAnswer.russian}"
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

fun saveDictionary(dictionary: MutableList<Word>) {

    wordsFile.bufferedWriter().use { out ->
        dictionary.forEach { word ->
            out.write("${word.english}|${word.russian}|${word.correctAnswersCount}\n")
        }
    }
}

fun main() {
    loadDictionary()
    calculateStatistics(dictionary)
    println("Содержимое словаря:")
    dictionary.forEach { println(it) }

    val learnedList = learnWords(dictionary)
    println(learnedList)
}