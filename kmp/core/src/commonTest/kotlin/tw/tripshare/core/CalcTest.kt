package tw.tripshare.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CalcTest {
    private val jpy = Currency("JPY", "日圓", 0)
    private val usd = Currency("USD", "美元", 2)

    @Test fun timesBeforePlus() {
        assertEquals(2560.0, Calc.evaluate("1,280×2"))
        assertEquals(7.0, Calc.evaluate("1+2*3"))
        assertEquals(4.0, Calc.evaluate("10−3×2"))
        assertEquals(1.5, Calc.evaluate("3÷2"))
    }

    @Test fun rejectsUnfinishedOrBroken() {
        assertNull(Calc.evaluate(""))
        assertNull(Calc.evaluate("12+"))
        assertNull(Calc.evaluate("5÷0"))
        assertNull(Calc.evaluate("1..2"))
        assertNull(Calc.evaluate("abc"))
    }

    @Test fun roundsToTheCurrency() {
        assertEquals(1667L, Calc.minor("5000÷3", jpy))
        assertEquals(333L, Calc.minor("10÷3", usd))
        assertEquals(1050L, Calc.minor("10.5", usd))
        assertNull(Calc.minor("3−5", jpy))
        assertNull(Calc.minor("0", jpy))
    }

    @Test fun keypadKeepsTheExpressionValid() {
        var e = ""
        for (k in listOf("+", "1", "2", "8", "0", "×", "+", "2")) e = Calc.press(e, k, 0)
        assertEquals("1280+2", e)
        assertEquals("1280+", Calc.press(e, "⌫", 0))
        assertEquals("1280+2", Calc.press(e, ".", 0))
        assertEquals("0.", Calc.press("", ".", 2))
        assertEquals("1.25", Calc.press("1.25", "9", 2))
        assertEquals("7", Calc.press("0", "7", 2))
        assertEquals("", Calc.press("12+3", "C", 2))
    }

    @Test fun knowsWhenThereIsSomethingToShow() {
        assertEquals(false, Calc.hasOperator("1280"))
        assertEquals(true, Calc.hasOperator("1280×2"))
    }
}
