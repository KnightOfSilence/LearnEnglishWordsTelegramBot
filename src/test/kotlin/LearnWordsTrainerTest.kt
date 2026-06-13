import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LearnWordsTrainerTest {
    @Test
    fun `statistics count learned words`() {
        val trainer = createTrainer(
            """
            cat|кошка|3
            dog|собака|1
            house|дом|4
            """.trimIndent(),
        )

        assertEquals(Statistics(learnedWords = 2, totalWords = 3, learnedPercent = 66), trainer.getStatistics())
    }

    @Test
    fun `question always uses an unlearned word as the correct answer`() {
        val trainer = createTrainer(
            """
            cat|кошка|3
            dog|собака|0
            house|дом|3
            """.trimIndent(),
        )

        repeat(10) {
            val question = assertNotNull(trainer.getNextQuestion())
            assertEquals("dog", question.correctWord.original)
            assertTrue(question.correctWord in question.variants)
            assertEquals(3, question.variants.size)
        }
    }

    @Test
    fun `correct answer updates and saves progress`() {
        val file = createDictionaryFile("cat|кошка|0")
        val trainer = LearnWordsTrainer(file, random = Random(1))
        val question = assertNotNull(trainer.getNextQuestion())
        val correctOption = question.variants.indexOf(question.correctWord) + 1

        assertTrue(trainer.checkAnswer(correctOption))
        assertEquals("cat|кошка|1", file.readText().trim())
        assertNull(trainer.currentQuestion)
    }

    @Test
    fun `incorrect and out of range answers do not update progress`() {
        val file = createDictionaryFile(
            """
            cat|кошка|0
            dog|собака|0
            """.trimIndent(),
        )
        val trainer = LearnWordsTrainer(file, random = Random(1))
        val question = assertNotNull(trainer.getNextQuestion())
        val incorrectOption = question.variants.indexOfFirst { it != question.correctWord } + 1

        assertFalse(trainer.checkAnswer(incorrectOption))
        assertEquals(0, question.correctWord.correctAnswersCount)
        assertFalse(trainer.checkAnswer(99))
    }

    @Test
    fun `dictionary loader skips malformed rows and normalizes progress`() {
        val trainer = createTrainer(
            """
            cat | кошка | -2
            invalid
            |пусто|3

            dog|собака|not-a-number
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                Word("cat", "кошка", 0),
                Word("dog", "собака", 0),
            ),
            trainer.dictionary,
        )
    }

    @Test
    fun `returns no question when every word is learned`() {
        val trainer = createTrainer("cat|кошка|3")

        assertNull(trainer.getNextQuestion())
        assertNull(trainer.currentQuestion)
    }

    @Test
    fun `reset progress clears answers and current question`() {
        val file = createDictionaryFile(
            """
            cat|кошка|3
            dog|собака|2
            """.trimIndent(),
        )
        val trainer = LearnWordsTrainer(file, random = Random(1))
        trainer.getNextQuestion()

        trainer.resetProgress()

        assertEquals(listOf(0, 0), trainer.dictionary.map { it.correctAnswersCount })
        assertNull(trainer.currentQuestion)
        assertEquals(
            """
            cat|кошка|0
            dog|собака|0
            """.trimIndent(),
            file.readText().trim(),
        )
    }

    private fun createTrainer(content: String): LearnWordsTrainer =
        LearnWordsTrainer(createDictionaryFile(content), random = Random(1))

    private fun createDictionaryFile(content: String): File =
        kotlin.io.path.createTempFile(prefix = "dictionary-", suffix = ".txt")
            .toFile()
            .apply { writeText(content) }
}
