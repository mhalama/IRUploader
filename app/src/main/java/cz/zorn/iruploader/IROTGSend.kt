package cz.zorn.iruploader
/*
import android.content.Context
import android.util.Log
import java.util.Collections
import java.util.PriorityQueue
import java.util.Timer
import java.util.TimerTask

class IrDataSender(
    private val context: Context,
    private val mUsbHostManager: UsbHostManager
) {

    private var sendTimer: Timer = Timer()
    private var dataChunks: ArrayList<ArrayList<UByte>> = ArrayList()
    private var chunkIndex: Int = 0

    // Pomocná funkce pro výpočet kontrolního součtu (upravená pro čitelnost)
    private fun calculateChecksumByte(b: UByte): UByte {
        var result: Byte = 0
        for (i in 0 until 8) {
            if ((((b.toUInt() and 255u) shr i) and 1u) == 1u) {
                result = (result.toInt() or (1 shl (7 - i))).toByte()
            }
        }
        return result.toUByte()
    }

    // Pomocná funkce pro výpočet kontrolního součtu balíčku (upravená pro čitelnost)
    private fun calculateChunkChecksum(arrayList: ArrayList<UByte>): UByte? {
        if (arrayList.size != 62) {
            return null
        }
        val sum = arrayList.sumOf { it.toUInt() and 255u }

        // Všimněte si, že se používá UByte.inv() pro bitovou negaci, která nahrazuje operátor '~'
        return calculateChecksumByte(((sum and 240u) or ((sum shr 8) and 15u)).toUByte()).inv()
    }

    /**
     * Odesílá IR data na externí USB OTG zařízení.
     *
     * @param irdata Pole celých čísel představujících IR vzor.
     * @param frequency Frekvence IR signálu v Hz.
     */
    fun sendIRData(irdata: Array<Int>, frequency: Int = 38000) {
        if (irdata.isEmpty() || irdata.size % 2 != 0) {
            Log.e("IrDataSender", "IR data must be a non-empty array with an even number of elements.")
            return
        }

        if (chunkIndex != 0) {
            Log.i("IrDataSender", "A transmission is already in progress. Ignoring new request.")
            return
        }

        val deviceIdentify = mUsbHostManager.deviceIdentify
        val rawDataAsUBytes = b().a(irdata)

        val headerData = ArrayList<UByte>()

        // Přidání hlavičky (4 byty -1)
        headerData.add((-1).toUByte())
        headerData.add((-1).toUByte())
        headerData.add((-1).toUByte())
        headerData.add((-1).toUByte())

        if (deviceIdentify == UsbHostManager.DeviceIdentify.d552 || deviceIdentify == UsbHostManager.DeviceIdentify.d552_old) {
            // Logika pro D552
            val dataList = ArrayList<Byte>()
            for (ubyte in rawDataAsUBytes) {
                dataList.add(ubyte.data)
            }

            val size = dataList.size

            val sizeHighByte = (size shr 8) and 255
            val sizeLowByte = size and 255

            val freqEncoded = frequency + 524287
            val freqHighByte = (freqEncoded shr 16) and 255
            val freqMidByte = (freqEncoded shr 8) and 255
            val freqLowByte = freqEncoded and 255

            headerData.add((freqMidByte.toByte()).toUByte().inv())
            headerData.add((freqHighByte.toByte()).toUByte().inv())
            headerData.add((freqLowByte.toByte()).toUByte().inv())
            headerData.add((sizeHighByte.toByte()).toUByte().inv())
            headerData.add((sizeLowByte.toByte()).toUByte().inv())

            for (byte in dataList) {
                headerData.add(byte.toUByte())
            }
        } else if (deviceIdentify == UsbHostManager.DeviceIdentify.d571) {
            // Logika pro D571
            val result = ArrayList<Byte>()
            val charFrequencyMap = IntArray(256)
            for (ubyte in rawDataAsUBytes) {
                val charValue = ubyte.data.toInt() and 255
                charFrequencyMap[charValue]++
            }

            val priorityQueue = PriorityQueue<e>()
            for (i in 0 until 256) {
                if (charFrequencyMap[i] > 0) {
                    priorityQueue.offer(b.b(charFrequencyMap[i], i.toChar()))
                }
            }

            while (priorityQueue.size > 1) {
                priorityQueue.offer(c(priorityQueue.poll(), priorityQueue.poll()))
            }
            val huffmanTreeRoot = priorityQueue.poll()

            f402a = ArrayList()
            b.a.a(huffmanTreeRoot, StringBuffer())
            Collections.sort(f402a, b.f())

            val dictionarySize = f402a.size
            result.add(((dictionarySize shr 8) and 255).toByte())
            result.add((dictionarySize and 255).toByte())

            for (huffmanCode in f402a) {
                f403b[huffmanCode.f407b] = huffmanCode.c
                result.add(huffmanCode.f407b.toByte())
                result.add(((huffmanCode.f406a shr 8) and 255).toByte())
                result.add((huffmanCode.f406a and 255).toByte())
            }

            val encodedStringBuilder = StringBuilder()
            for (ubyte in rawDataAsUBytes) {
                val charValue = ubyte.data.toInt().toChar()
                encodedStringBuilder.append(f403b[charValue])
            }

            val padding = (8 - (encodedStringBuilder.length % 8)) % 8
            for (i in 0 until padding) {
                encodedStringBuilder.append('0')
            }
            result.add(padding.toByte())

            for (i in 0 until encodedStringBuilder.length / 8) {
                val byteValue = Integer.parseInt(encodedStringBuilder.substring(i * 8, i * 8 + 8), 2)
                result.add(byteValue.toByte())
            }

            val totalSize = result.size
            val sizeHighByte = (totalSize shr 8) and 255
            val sizeLowByte = totalSize and 255

            val freqEncoded = frequency + 524287
            val freqHighByte = (freqEncoded shr 16) and 255
            val freqMidByte = (freqEncoded shr 8) and 255
            val freqLowByte = freqEncoded and 255

            headerData.add(freqMidByte.toUByte().inv())
            headerData.add(freqHighByte.toUByte().inv())
            headerData.add(freqLowByte.toUByte().inv())
            headerData.add(sizeHighByte.toUByte().inv())
            headerData.add(sizeLowByte.toUByte().inv())

            for (byte in result) {
                headerData.add(byte.toUByte())
            }
        } else {
            Log.e("IrDataSender", "Unknown device type. Cannot send IR data.")
            return
        }

        // Stejná logika balíčkování a odesílání pro oba typy zařízení
        val chunkSize = 62
        val totalChunks = if (headerData.size % chunkSize == 0) headerData.size / chunkSize else headerData.size / chunkSize + 1
        dataChunks = ArrayList()

        for (i in 0 until totalChunks) {
            val start = i * chunkSize
            val end = minOf(start + chunkSize, headerData.size)
            val chunk = ArrayList(headerData.subList(start, end))

            while (chunk.size < chunkSize) {
                chunk.add(0.toUByte())
            }

            val checksum = calculateChunkChecksum(chunk)
            if (checksum != null) {
                chunk.add(checksum)
            }

            dataChunks.add(chunk)
        }

        sendTimer.cancel()
        sendTimer = Timer()
        sendTimer.schedule(object : TimerTask() {
            override fun run() {
                if (chunkIndex < dataChunks.size) {
                    mUsbHostManager.write(dataChunks[chunkIndex])
                    chunkIndex++
                } else {
                    cancel()
                    chunkIndex = 0
                }
            }
        }, 0, 2)
    }
}*/