package stage_2

import java.io.File

val wordsFile = File("words.txt")
val dictionary = mutableListOf<Word>()

data class Word(
    val original: String,
    val translate: String,
    val correctAnswersCount: Int = 0,
)

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
}