fun main() {
    val trainer = LearnWordsTrainer()

    while (true) {
        println("\nМеню:")
        println("1 – Учить слова")
        println("2 – Статистика")
        println("0 – Выход")
        print("Ваш выбор: ")

        when (readln().toIntOrNull()) {
            1 -> learnWords(trainer)
            2 -> showStatistics(trainer)
            0 -> {
                println("Выход.")
                return
            }
            else -> println("Некорректный ввод. Введите число от 1 до 2 или 0.")
        }
    }
}

fun showStatistics(trainer: LearnWordsTrainer) {
    val stats = trainer.getStatistics()
    if (stats.totalWords == 0) {
        println("Словарь пуст.")
        return
    }
    println("Выучено ${stats.learnedWords} из ${stats.totalWords} слов | ${stats.learnedPercent}%")
    Thread.sleep(2000)
}

fun learnWords(trainer: LearnWordsTrainer) {
    while (true) {
        val question = trainer.getNextQuestion()
        if (question == null) {
            println("Поздравляем! Вы выучили все слова!")
            break
        }

        println("\nСлово: ${question.correctWord.original}")
        println("Варианты ответов:")
        question.variants.forEachIndexed { index, word ->
            println("${index + 1}. ${word.translated}")
        }
        println("0. Выход в меню")
        print("Ваш ответ: ")

        val userAnswer = readln().toIntOrNull()

        when {
            userAnswer == 0 -> break
            userAnswer == null || userAnswer !in 1..question.variants.size -> {
                println("Некорректный ввод. Попробуйте снова.")
                continue
            }
        }

        if (trainer.checkAnswer(userAnswer)) {
            println("Правильно!")
            Thread.sleep(2000)
        } else {
            println("Неправильно! Верный ответ: ${question.correctWord.translated}")
            Thread.sleep(2000)
        }
    }
}