package stage_3

import stage_2.Word

public fun calculateStatistics(dictionary: MutableList<Word>): String {
    val totalCount = dictionary.size
    val learnedCount = dictionary.filter { it.correctAnswersCount >= 3 }.count()
    if (totalCount == 0) return "Словарь пуст"
    else {
        val percent = (learnedCount.toDouble() / totalCount.toDouble()) * 100
        return "Выучено $learnedCount из $totalCount слов | ${"%.1f".format(percent)}%"
    }
}