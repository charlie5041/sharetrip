package tw.tripshare.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MahjongTest {
    private val twd = Currency.of("TWD")
    private val members = listOf("a", "b", "c", "d").mapIndexed { i, id -> Member(id, id.uppercase(), i.toLong(), 0) }
    private val game = MahjongGame("g", listOf("a", "b", "c", "d"), baseMinor = 50, perTaiMinor = 20, currency = "TWD", startedAt = 1000, createdBy = "u")

    private fun hand(winner: String, loser: String?, tai: Int, game: String = "g") =
        MahjongHand("h$winner$loser$tai", game, winner, loser, tai, 0, "u")

    // a 胡 off b, 3 台; c 自摸 2 台; d 胡 off a, 5 台.
    private val hands = listOf(hand("a", "b", 3), hand("c", null, 2), hand("d", "a", 5))

    @Test fun discardOnlyTheDiscarderPays() {
        assertEquals(mapOf("b" to -110L, "a" to 110L), Mahjong.deltas(game, hand("a", "b", 3)))
    }

    @Test fun selfDrawnEveryonePays() {
        assertEquals(mapOf("a" to -90L, "b" to -90L, "d" to -90L, "c" to 270L), Mahjong.deltas(game, hand("c", null, 2)))
    }

    @Test fun scoresAddUpAndSumToZero() {
        val s = Mahjong.scores(game, hands)
        assertEquals(listOf("a", "b", "c", "d"), s.keys.toList())
        assertEquals(mapOf("a" to -130L, "b" to -200L, "c" to 270L, "d" to 60L), s)
        assertEquals(0L, s.values.sum())
    }

    @Test fun handsOfOtherGamesDontCount() {
        assertEquals(0L, Mahjong.scores(game, listOf(hand("a", "b", 3, game = "other"))).values.sumOf { kotlin.math.abs(it) })
    }

    @Test fun zeroTaiIsJustTheBase() {
        assertEquals(50L, game.price(0))
    }

    @Test fun expensesMoveBalancesByExactlyTheScores() {
        val es = Mahjong.toExpenses(game, hands, inTotal = false, title = "麻將", createdBy = "u")
        assertEquals(setOf("c", "d"), es.map { it.payerId }.toSet())
        es.forEach {
            assertEquals(ExpenseCategory.mahjong, it.category)
            assertEquals(SplitMode.exact, it.splitMode)
            assertEquals("g", it.mahjongId)
            assertFalse(it.inTotal)
            assertEquals(it.amountMinor, it.splits.values.sum())
            assertEquals(1000L, it.occurredAt)
        }
        assertEquals(270L + 60L, es.sumOf { it.amountMinor })
        assertEquals(Mahjong.scores(game, hands), balances(members, es, emptyList(), twd))
    }

    @Test fun nothingToWriteWhenEven() {
        assertTrue(Mahjong.toExpenses(game, emptyList(), inTotal = true, title = "麻將", createdBy = "u").isEmpty())
    }

    @Test fun roundTripsThroughMaps() {
        assertEquals(game, MahjongGame.fromMap("g", game.toMap()))
        val h = hand("c", null, 2)
        val back = MahjongHand.fromMap(h.id, h.toMap())
        assertEquals(h, back)
        assertNull(back.loserId)
        val e = Mahjong.toExpenses(game, hands, inTotal = false, title = "麻將", createdBy = "u").first()
        val eBack = Expense.fromMap("x", e.toMap())
        assertFalse(eBack.inTotal)
        assertEquals("g", eBack.mahjongId)
        assertEquals(ExpenseCategory.mahjong, eBack.category)
        // An ordinary expense stores neither field and reads back as counted.
        val plain = e.copy(inTotal = true, mahjongId = null).toMap()
        assertFalse("inTotal" in plain || "mahjongId" in plain)
        assertTrue(Expense.fromMap("y", plain).inTotal)
    }
}
