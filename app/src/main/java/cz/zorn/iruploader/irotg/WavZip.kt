package cz.zorn.iruploader.irotg

import android.R.attr.data
import timber.log.Timber
import kotlin.math.roundToInt

/**
 * Třída pro jednoduchou kompresi dat, pravděpodobně pro WAV soubory,
 * založenou na nahrazení dvou nejčastějších párů celých čísel.
 */
class WavZip {

    // Properties pro uložení dvou nejčastějších párů.
    // Inicializovány jako prázdné pole, naplní se během komprese.
    var mostFrequentPair1: IntArray = intArrayOf(0, 0)
        private set // Setter je privátní, aby se hodnota dala měnit jen uvnitř třídy.
    var mostFrequentPair2: IntArray = intArrayOf(0, 0)
        private set

    // Data class pro přehlednější uchování páru a jeho frekvence.
    private data class PairFrequency(val pair: Pair<Int, Int>, val count: Int) {
        // Vypočtená vlastnost pro součet (pro účely řazení).
        val unsignedSum = pair.first.toUInt() + pair.second.toUInt()
    }

    /**
     * Zkomprimuje pole celých čísel.
     *
     * @param data Vstupní data jako pole celých čísel (očekává se sudý počet).
     * @return Zkomprimovaná data jako UByteArray.
     */
    @OptIn(ExperimentalUnsignedTypes::class)
    fun compress(datax: IntArray): UByteArray {
        val data = if (datax.size % 2 != 0) datax.plus(0) else datax

        // 1. Najdi frekvenci všech párů
        val frequencies = data
            .asSequence()
            .chunked(2) // Rozdělí pole na seznamy po dvou prvcích
            .map { it.also { Timber.d("$it") }  }
            .map { it[0] to it[1] } // Převede každý [a, b] na Pair(a, b)
            .groupingBy { it } // Seskupí stejné páry
            .eachCount() // Spočítá výskyty v každé skupině
            .map { (pair, count) -> PairFrequency(pair, count) } // Převede na naši data class

        // 2. Najdi dva nejčastější páry
        val topTwo = frequencies.sortedByDescending { it.count }.take(2)

        val dataToEncode = if (topTwo.size < 2) {
            // Pokud nemáme alespoň dva různé páry, komprese se neprovede,
            // kódujeme původní data.
            data.toList()
        } else {
            // Seřadíme je podle součtu, aby byl výsledek deterministický.
            val (p1, p2) = topTwo.sortedBy { it.unsignedSum }

            this.mostFrequentPair1 = intArrayOf(p1.pair.first, p1.pair.second)
            this.mostFrequentPair2 = intArrayOf(p2.pair.first, p2.pair.second)

            // 3. Nahraď nejčastější páry ve vstupních datech
            data.asSequence()
                .chunked(2)
                .flatMap { chunk ->
                    when (chunk[0] to chunk[1]) {
                        p1.pair -> listOf(0)
                        p2.pair -> listOf(1)
                        else -> chunk // Ponecháme původní pár
                    }
                }
                .toList()
        }

        // 4. Zakóduj hlavičku (nejčastější páry)
        val header = mutableListOf<UByte>().apply {
            addAll(encodeInt(mostFrequentPair2[0]))
            addAll(encodeInt(mostFrequentPair2[1]))
            addAll(encodeInt(mostFrequentPair1[0]))
            addAll(encodeInt(mostFrequentPair1[1]))
        }

        // 5. Zakóduj tělo zprávy
        val body = dataToEncode.flatMap { encodeInt(it) }

        // 6. Sestav finální výsledek: hlavička + oddělovač + tělo
        return (header + listOf(0xFFu, 0xFFu, 0xFFu) + body).toUByteArray()
    }

    /**
     * Kóduje jedno celé číslo do pole UByte pomocí varianty variabilní délky.
     */
    private fun encodeInt(value: Int): List<UByte> {
        return when {
            // Velká čísla se kódují po 7 bitech, s nejvyšším bitem jako indikátorem pokračování.
            value > 2032 -> {
                val result = mutableListOf<UByte>()
                var num = value
                do {
                    var byteVal = (num and 0x7F).toUByte()
                    num = num shr 7
                    if (num != 0) {
                        byteVal = byteVal or 0x80u
                    }
                    // Speciální případ: hodnota 255 (0xFF) je nahrazena 254 (0xFE)
                    result.add(if (byteVal == 0xFFu.toUByte()) 0xFEu.toUByte() else byteVal)
                } while (num != 0)
                result
            }
            // 0 a 1 jsou speciální případy (nejčastější hodnoty po nahrazení).
            value == 0 || value == 1 -> listOf(value.toUByte())
            // Ostatní malá čísla se "zmenší" a zaokrouhlí.
            else -> listOf(((value / 16.0) + 0.5).roundToInt().toUByte())
        }
    }
}