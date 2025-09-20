package stage_3

import stage_2.Word
import java.io.File

fun loadDictionary(): MutableList<Word> {
    val dictionaryFile = File("words.txt")


    val dictionary: MutableList<Word> = mutableListOf()
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

fun main() {

   val dictionary = loadDictionary()
}