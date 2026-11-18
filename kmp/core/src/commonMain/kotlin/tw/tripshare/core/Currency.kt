package tw.tripshare.core

/**
 * Currencies the app offers, with how many decimal places their amounts
 * carry. Amounts are stored as whole numbers in these minor units.
 *
 * TWD and JPY are treated as having no decimals: ISO 4217 gives TWD two,
 * but nobody pays in 角 any more, and a 0.5 that cannot be paid only
 * produces a settlement nobody can carry out.
 */
data class Currency(val code: String, val name: String, val decimals: Int) {

    /** Minor units → a display string, e.g. 12345 USD → "123.45". */
    fun format(amountMinor: Long): String {
        if (decimals == 0) return amountMinor.toString()
        val neg = amountMinor < 0
        val s = (if (neg) -amountMinor else amountMinor).toString().padStart(decimals + 1, '0')
        val whole = s.substring(0, s.length - decimals)
        val frac = s.substring(s.length - decimals)
        return "${if (neg) "-" else ""}$whole.$frac"
    }

    /** A typed amount → minor units, or null when it isn't a valid amount here. */
    fun parse(input: String): Long? {
        val t = input.trim().replace(",", "")
        val m = AMOUNT.matchEntire(t) ?: return null
        val frac = m.groupValues[2]
        if (frac.length > decimals) return null
        return (m.groupValues[1] + frac.padEnd(decimals, '0')).toLongOrNull()
    }

    companion object {
        private val AMOUNT = Regex("""(\d+)(?:\.(\d*))?""")

        val all = listOf(
            Currency("TWD", "新台幣", 0),
            Currency("JPY", "日圓", 0),
            Currency("KRW", "韓元", 0),
            Currency("USD", "美元", 2),
            Currency("EUR", "歐元", 2),
            Currency("HKD", "港幣", 2),
            Currency("CNY", "人民幣", 2),
            Currency("THB", "泰銖", 2),
            Currency("SGD", "新加坡幣", 2),
            Currency("VND", "越南盾", 0),
            Currency("GBP", "英鎊", 2),
            Currency("AUD", "澳幣", 2),
            Currency("NZD", "紐幣", 2),
            Currency("CAD", "加幣", 2),
            Currency("CHF", "瑞士法郎", 2),
            Currency("MYR", "馬來西亞令吉", 2),
            Currency("PHP", "菲律賓披索", 2),
            Currency("IDR", "印尼盾", 0),
            Currency("HUF", "匈牙利福林", 0),
            Currency("CZK", "捷克克朗", 2),
        )

        fun of(code: String): Currency =
            all.firstOrNull { it.code == code } ?: throw IllegalArgumentException("不支援的幣別：$code")
    }
}
