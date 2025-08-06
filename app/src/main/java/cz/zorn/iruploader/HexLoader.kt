package cz.zorn.iruploader

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class FirmwareMetaData(
    val name: String,
    val version: String,
    val author: String,
    val timestamp: String = currentTimeStamp()
) {
    companion object {
        private fun currentTimeStamp(): String {
            val currentDate = Date()
            val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            return formatter.format(currentDate)
        }
    }
}

interface HexLoader {
    fun loadFlash(hexFile: InputStream, flashSize: Int): ByteBuffer
    fun flashPages(flash: ByteBuffer, pageSize: Int): Map<Int, ByteArray>
    fun getFirmwareMetaData(flash: ByteBuffer): FirmwareMetaData
}

class HexLoaderImpl : HexLoader {
    override fun loadFlash(hexFile: InputStream, flashSize: Int): ByteBuffer {
        val data = ByteArray(flashSize) { 0xFF.toByte() }
        val flash = ByteBuffer.wrap(data)

        hexFile.bufferedReader().readLines().forEach { line ->
            if (line.isNotEmpty() && line.startsWith(":")) {
                val length = Integer.parseInt(line.substring(1, 3), 16)
                val startAddress = Integer.parseInt(line.substring(3, 7), 16)
                val recordType = Integer.parseInt(line.substring(7, 9), 16)
                if (recordType == 0) {
                    for (i in 0 until length) {
                        val byteValue =
                            Integer.parseInt(line.substring(9 + i * 2, 11 + i * 2), 16)
                        flash.put(startAddress + i, byteValue.toByte())
                    }
                }
            }
        }

        return flash
    }

    override fun flashPages(flash: ByteBuffer, pageSize: Int): Map<Int, ByteArray> {
        flash.position(0)

        val flashPages = HashMap<Int, ByteArray>()
        while (flash.hasRemaining()) {
            val flashPage = ByteArray(pageSize)
            val address = flash.position()
            flash.get(flashPage)
            if (flashPage.any { it.toUByte().toInt() != 0xFF }) // stranka neni prazdna
                flashPages.put(address, flashPage)
        }

        return flashPages
    }

    override fun getFirmwareMetaData(flash: ByteBuffer): FirmwareMetaData {
        val flashData = flash.duplicate()
        flashData.position(0)

        val flashContentString = StandardCharsets.US_ASCII.decode(flashData).toString()

        val regex = """FW:\s*(.*?)\s*;\s*(.*?)\s*;\s*(.*?)\u0000""".toRegex()
        val matchResult = regex.find(flashContentString)

        if (matchResult != null) {
            val (name, version, author) = matchResult.destructured
            return FirmwareMetaData(name.trim(), version.trim(), author.trim())
        } else {
            return FirmwareMetaData("Program", "1.0", "Neznamy")
        }
    }
}