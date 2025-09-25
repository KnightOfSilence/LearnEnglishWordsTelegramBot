import stage_2.Word
import stage_3.loadDictionary
import java.io.File

val wordsFile = File("words.txt")
val dictionary = mutableListOf<Word>()

data class Word(
    val original: String,
    val translate: String,
    val correctAnswersCount: Int = 0,
)

fun loadDictionary(): MutableList<stage_2.Word> {
    val dictionaryFile = File("words.txt")


    val dictionary: MutableList<stage_2.Word> = mutableListOf()
    val lines = dictionaryFile.readLines()

    for (line in lines) {
        val parts = line.split("|")
        val correctAnswers = parts.getOrNull(2)?.toIntOrNull() ?: 0
        val word =
            Word(original = parts[0], translate = parts[1], correctAnswersCount = correctAnswers)
        stage_2.dictionary.add(word)
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
            2 -> "Статистика"
            3 -> {
                "Выход"
                break
            }

            else -> "Введите число 1, 2 или 0"
        }
    }
}

public fun calculateStatistics(dictionary: MutableList<Word>): String {
    val totalCount = dictionary.size
    val learnedCount = dictionary.filter { it.correctAnswersCount >= 3 }.count()
    if (totalCount == 0) return "Словарь пуст"
    else {
        val percent = (learnedCount.toDouble() / totalCount.toDouble()) * 100
        return "Выучено $learnedCount из $totalCount слов | ${"%.1f".format(percent)}%"
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