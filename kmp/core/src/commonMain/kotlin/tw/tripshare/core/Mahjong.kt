package tw.tripshare.core

/**
 * A mahjong game played during the trip (台灣 16 張): who sits at the
 * table, the stake (底) and the price of each 台. Amounts are in the trip's
 * base currency's minor units, so the result can go straight into 結算.
 * Nobody's 台 are worked out here: players count them, 莊 and 連莊 included.
 */
data class MahjongGame(
    val id: String,
    /**
     * Everyone in the game, in the order they were picked. More than four
     * may take turns; who sits at the table is recorded per hand.
     */
    val players: List<String>,
    val baseMinor: Long,
    val perTaiMinor: Long,
    val currency: String,
    val startedAt: Long,
    val createdBy: String,
) {
    fun toMap(): Doc = mapOf(
        "players" to players,
        "baseMinor" to baseMinor,
        "perTaiMinor" to perTaiMinor,
        "currency" to currency,
        "startedAt" to startedAt,
        "createdBy" to createdBy,
    )

    /** What one 胡 with [tai] 台 costs one person who pays. */
    fun price(tai: Int): Long = add(baseMinor, mul(perTaiMinor, tai.toLong()))

    companion object {
        fun fromMap(id: String, m: Doc) = MahjongGame(
            id = id,
            players = m.strings("players"),
            baseMinor = m.long("baseMinor"),
            perTaiMinor = m.long("perTaiMinor"),
            currency = m.str("currency"),
            startedAt = m.long("startedAt"),
            createdBy = m.str("createdBy"),
        )
    }
}

/**
 * One hand (一筆): [winnerId] 胡, off [loserId]'s discard, or 自摸 when that
 * is null, paid by [payers]. [tai] leaves out the dealer's: whoever pays the
 * dealer, or is paid by them, adds 1 + 2 × [streak] 台 on top.
 */
data class MahjongHand(
    val id: String,
    val gameId: String,
    val winnerId: String,
    val loserId: String?,
    val tai: Int,
    val at: Long,
    val createdBy: String,
    /** Who paid a 自摸. Empty for hands from before it was asked. */
    val payers: List<String> = emptyList(),
    /** 莊家, or null when nobody marked one (hands from 0.6.x count 台 with the dealer's already in). */
    val dealerId: String? = null,
    /** 連莊: how many hands in a row the dealer has kept the deal. */
    val streak: Int = 0,
    /** 0.6.1 only: who sat at the table; read for old 自摸, never written. */
    val table: List<String> = emptyList(),
) {
    val selfDrawn: Boolean get() = loserId == null

    /** Who pays: the discarder, or for 自摸 the marked payers (old hands: everyone else seated). */
    fun payersIn(game: MahjongGame): List<String> = loserId?.let { listOf(it) }
        ?: payers.ifEmpty { table.ifEmpty { game.players }.filter { it != winnerId } }

    /** 台 between the winner and [payer]: the dealer's extra when either of them deals. */
    fun taiFor(payer: String): Int =
        tai + if (dealerId != null && (dealerId == winnerId || dealerId == payer)) 1 + 2 * streak else 0

    fun toMap(): Doc = mapOf(
        "gameId" to gameId,
        "winnerId" to winnerId,
        "loserId" to loserId,
        "tai" to tai.toLong(),
        "at" to at,
        "createdBy" to createdBy,
        "payers" to payers,
        "dealerId" to dealerId,
        "streak" to streak.toLong(),
    )

    companion object {
        fun fromMap(id: String, m: Doc) = MahjongHand(
            id = id,
            gameId = m.str("gameId"),
            winnerId = m.str("winnerId"),
            loserId = m.strOrNull("loserId"),
            tai = m.long("tai").toInt(),
            at = m.long("at"),
            createdBy = m.str("createdBy"),
            payers = m.strings("payers"),
            dealerId = m.strOrNull("dealerId"),
            streak = m.longOr("streak", 0).toInt(),
            table = m.strings("table"),
        )
    }
}

object Mahjong {
    /**
     * What [hand] moves, per player: the winner gains, payers lose.
     * 放槍: the one who discarded pays. 自摸: each marked payer pays. Each
     * pays 底 + 台 × 每台, with the dealer's 台 where the dealer is involved.
     */
    fun deltas(game: MahjongGame, hand: MahjongHand): Map<String, Long> {
        val out = LinkedHashMap<String, Long>()
        var won = 0L
        hand.payersIn(game).filter { it != hand.winnerId }.forEach { p ->
            val price = game.price(hand.taiFor(p))
            out[p] = -price
            won = add(won, price)
        }
        out[hand.winnerId] = won
        return out
    }

    /** Each player's running total, in seat order; everyone starts at 0. Sums to 0. */
    fun scores(game: MahjongGame, hands: List<MahjongHand>): Map<String, Long> {
        val out = LinkedHashMap<String, Long>()
        game.players.forEach { out[it] = 0 }
        hands.filter { it.gameId == game.id }.forEach { h ->
            deltas(game, h).forEach { (id, v) -> out[id] = add(out[id] ?: 0, v) }
        }
        return out
    }

    /**
     * The game's result as expenses, ready for 結算: one per winner, paid by
     * the winner and split (exact amounts) among the losers who owe them, so
     * the balances move by exactly the scores. [inTotal] false keeps them out
     * of 總花費. Empty when nobody won or lost anything.
     */
    fun toExpenses(
        game: MahjongGame,
        hands: List<MahjongHand>,
        inTotal: Boolean,
        title: String,
        createdBy: String,
    ): List<Expense> {
        val transfers = suggestTransfers(scores(game, hands))
        return transfers.groupBy { it.toId }.map { (winner, owed) ->
            val splits = LinkedHashMap<String, Long>()
            owed.forEach { splits[it.fromId] = add(splits[it.fromId] ?: 0, it.amountMinor) }
            Expense(
                id = "",
                title = title,
                category = ExpenseCategory.mahjong,
                amountMinor = splits.values.sum(),
                currency = game.currency,
                rateToBase = "1",
                payerId = winner,
                splitMode = SplitMode.exact,
                splits = splits,
                occurredAt = game.startedAt,
                createdBy = createdBy,
                updatedAt = 0,
                inTotal = inTotal,
                mahjongId = game.id,
            )
        }
    }
}
