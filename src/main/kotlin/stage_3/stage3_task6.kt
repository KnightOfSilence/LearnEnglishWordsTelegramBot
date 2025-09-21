package stage_3

import stage_2.Word

public fun calculateStatistics(dictionary: MutableList<Word>): String {
    val totalCount = dictionary.map{it.original}.count()
    val learnedCount = dictionary.filter { it.correctAnswersCount >= 0 }.count()
    val percent = (learnedCount.toDouble() / totalCount.toDouble()) * 100
    return "Выучено $learnedCount из $totalCount слов | $percent%"
}