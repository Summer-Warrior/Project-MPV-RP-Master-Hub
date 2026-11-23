package com.example.data.util

import java.math.BigInteger

/**
 * Arbitrary-precision numeric engine backed by java.math.BigInteger so 21+ digit
 * card passcodes (up to and far beyond 999999999999999999999) and cosmic RP Life Points
 * never overflow or lose precision.
 */
object BigIntMath {

    fun parseOrZero(raw: String): BigInteger {
        val cleaned = raw.trim().replace(",", "").replace("_", "")
        if (cleaned.isEmpty() || cleaned == "-") return BigInteger.ZERO
        return try {
            BigInteger(cleaned)
        } catch (_: NumberFormatException) {
            BigInteger.ZERO
        }
    }

    fun isValidBigInt(raw: String): Boolean {
        val cleaned = raw.trim().replace(",", "").replace("_", "")
        if (cleaned.isEmpty()) return false
        return try {
            BigInteger(cleaned)
            true
        } catch (_: NumberFormatException) {
            false
        }
    }

    fun add(current: String, delta: String): String {
        val result = parseOrZero(current).add(parseOrZero(delta))
        return result.toString()
    }

    fun subtract(current: String, delta: String, clampZero: Boolean = false): String {
        val result = parseOrZero(current).subtract(parseOrZero(delta))
        return if (clampZero && result < BigInteger.ZERO) "0" else result.toString()
    }

    fun multiply(current: String, factor: String): String {
        val result = parseOrZero(current).multiply(parseOrZero(factor))
        return result.toString()
    }

    fun divide(current: String, divisor: String): String {
        val div = parseOrZero(divisor)
        if (div == BigInteger.ZERO) return current
        return parseOrZero(current).divide(div).toString()
    }

    fun compareNumericCode(a: String, b: String): Int {
        val aIsNum = a.isNotBlank() && a.all { it.isDigit() }
        val bIsNum = b.isNotBlank() && b.all { it.isDigit() }
        return if (aIsNum && bIsNum) {
            BigInteger(a).compareTo(BigInteger(b))
        } else {
            a.compareTo(b, ignoreCase = true)
        }
    }

    fun formatWithCommas(raw: String): String {
        val bi = parseOrZero(raw)
        val str = bi.abs().toString()
        val grouped = str.reversed().chunked(3).joinToString(",").reversed()
        return if (bi < BigInteger.ZERO) "-$grouped" else grouped
    }

    fun describeCodeType(codeString: String): String {
        val trimmed = codeString.trim()
        return when {
            trimmed.matches(Regex("^\\d{1,8}$")) -> "${trimmed.length}-Digit Passcode"
            trimmed.matches(Regex("^\\d{9,}$")) -> "${trimmed.length}-Digit BigInt ID"
            trimmed.matches(Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")) -> "UUID v4"
            else -> "Custom String ID"
        }
    }
}
