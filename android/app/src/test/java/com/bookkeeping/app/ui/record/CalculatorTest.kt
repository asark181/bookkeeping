package com.bookkeeping.app.ui.record

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculatorTest {

    private fun type(vararg keys: String) = keys.fold("") { acc, k -> Calculator.input(acc, k) }

    @Test
    fun evaluatesAdditionAndSubtraction() {
        assertEquals(1270L, Calculator.evaluate("12.5+0.3-0.1"))
        assertEquals(2500L, Calculator.evaluate("25"))
        assertEquals(-500L, Calculator.evaluate("5-10"))
        assertNull(Calculator.evaluate(""))
    }

    @Test
    fun ignoresTrailingOperator() {
        assertEquals(1200L, Calculator.evaluate("12+"))
        assertTrue(Calculator.hasPendingOperator("12+"))
        assertFalse(Calculator.hasPendingOperator("12.5"))
    }

    @Test
    fun limitsDecimalsAndLeadingZero() {
        assertEquals("1.23", type("1", ".", "2", "3", "4"))
        assertEquals("5", type("0", "5"))
        assertEquals("0.", type("."))
        assertEquals("1+0.", type("1", "+", "."))
    }

    @Test
    fun replacesConsecutiveOperators() {
        assertEquals("3-", type("3", "+", "-"))
        assertEquals("", type("+"))
        assertEquals("2+", type("2", ".", "+"))
    }

    @Test
    fun formatsCentsForInput() {
        assertEquals("12", centsToInput(1200))
        assertEquals("12.5", centsToInput(1250))
        assertEquals("12.05", centsToInput(1205))
    }
}
