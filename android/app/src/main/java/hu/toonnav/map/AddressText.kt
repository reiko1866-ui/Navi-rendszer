package hu.toonnav.map

import java.text.Normalizer
import java.util.Locale

object AddressText {
    fun fold(text: String): String {
        val decomposed = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        val stripped = buildString(decomposed.length) {
            for (character in decomposed) {
                if (Character.getType(character) != Character.NON_SPACING_MARK.toInt()) append(character)
            }
        }
        return stripped
            .map { if (it.isLetterOrDigit()) it else ' ' }
            .joinToString("")
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .joinToString(" ")
    }

    fun houseKey(value: String): String {
        return value.trim().lowercase(Locale.ROOT).trimEnd('.').split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")
    }

    fun candidates(raw: String): List<AddressParts> {
        val cleaned = raw.trim()
        if (cleaned.length < 2) return emptyList()
        val found = linkedSetOf<AddressParts>()
        if (cleaned.contains(",")) {
            val pieces = cleaned.split(",").map { stripPostcode(it).trim() }.filter { it.isNotEmpty() }
            if (pieces.size >= 2) {
                addAddress(found, pieces[0], pieces.drop(1).joinToString(" "))
                addAddress(found, pieces.last(), pieces.dropLast(1).joinToString(" "))
            }
        }
        val tokens = tokenize(stripPostcode(cleaned.replace(",", " ")))
        val split = splitHouse(tokens) ?: return found.toList()
        for (size in split.body.size - 1 downTo 1) {
            found.add(
                AddressParts(
                    split.body.take(size).joinToString(" "),
                    split.body.drop(size).joinToString(" "),
                    split.house
                )
            )
        }
        return found.toList()
    }

    private fun addAddress(found: MutableSet<AddressParts>, settlement: String, address: String) {
        val split = splitHouse(tokenize(address)) ?: return
        if (settlement.isBlank() || split.body.isEmpty()) return
        found.add(AddressParts(settlement, split.body.joinToString(" "), split.house))
    }

    private fun stripPostcode(text: String): String {
        return text.split(Regex("\\s+")).filter { !it.matches(Regex("\\d{4}")) }.joinToString(" ")
    }

    private fun tokenize(text: String): List<String> {
        return text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    }

    private fun splitHouse(tokens: List<String>): Split? {
        if (tokens.size < 2) return null
        val last = tokens.last()
        val previous = tokens[tokens.lastIndex - 1]
        val house = when {
            last.any { it.isDigit() } -> last
            previous.any { it.isDigit() } && !last.any { it.isDigit() } -> "$previous $last"
            else -> return null
        }
        val bodySize = tokens.size - if (house.contains(" ")) 2 else 1
        if (bodySize < 1) return null
        return Split(tokens.take(bodySize), house)
    }

    data class AddressParts(val settlement: String, val street: String, val house: String)

    private data class Split(val body: List<String>, val house: String)
}
