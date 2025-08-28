package cz.zorn.iruploader.irotg

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbManager
import androidx.annotation.Keep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import timber.log.Timber

@Keep
enum class DeviceIdentify {
    UNKNOWN, D552, D571, D226
}

interface IROTG {
    suspend fun identifyDevice(device: UsbDevice)
    suspend fun sendIRDataToExternalDevice(freq: Int, irdata: IntArray)
}

@OptIn(ExperimentalUnsignedTypes::class)
class IROTGImpl(private val context: Context) : IROTG {
    private var kind: DeviceIdentify = DeviceIdentify.UNKNOWN
    private var _device: UsbDevice? = null

    override suspend fun identifyDevice(device: UsbDevice): Unit =
        withContext(Dispatchers.IO) {
            val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
            val connection =
                usbManager.openDevice(device) ?: return@withContext
            val usbInterface = device.getInterface(0)

            if (!connection.claimInterface(usbInterface, true)) {
                Timber.e("Failed to claim USB interface.")
                connection.close()
                return@withContext
            }

            try {
                var endpointIn: UsbEndpoint? = null
                var endpointOut: UsbEndpoint? = null

                for (i in 0 until usbInterface.endpointCount) {
                    val endpoint = usbInterface.getEndpoint(i)
                    if (endpoint.direction == 128) {
                        endpointIn = endpoint
                    }
                    if (endpoint.direction == 0) {
                        endpointOut = endpoint
                    }
                }

                endpointIn ?: return@withContext
                endpointOut ?: return@withContext

                val authPacket = ArrayList<UByte>().apply {
                    add(0xFC.toUByte())
                    add(0xFC.toUByte())
                    add(0xFC.toUByte())
                    add(0xFC.toUByte())
                }

                val bytePacket = authPacket.map { it.toByte() }.toByteArray()
                connection.bulkTransfer(endpointOut, bytePacket, bytePacket.size, 100)

                val buffer = ByteArray(128)
                val bytesRead = connection.bulkTransfer(endpointIn, buffer, buffer.size, 100)
                if (bytesRead == 6) {
                    val byte1 = buffer[4].toUByte()
                    val byte2 = buffer[5].toUByte()
                    kind = when {
                        byte1 == 2.toUByte() && byte2 == 170.toUByte() -> {
                            Timber.e("Identified device as D571")
                            DeviceIdentify.D571
                        }

                        byte1 == 112.toUByte() && byte2 == 1.toUByte() -> {
                            Timber.e("Identified device as D552")
                            DeviceIdentify.D552
                        }

                        else -> {
                            Timber.e("Unknown device type received: $byte1, $byte2")
                            DeviceIdentify.UNKNOWN
                        }
                    }
                    _device = device
                } else {
                    Timber.e("Failed to read response from device")
                }
            } finally {
                connection.releaseInterface(usbInterface)
                connection.close()
            }
        }

    /** Bit-reverse jednoho bajtu (abcdefgh -> hgfedcba). */
    private fun bitReverse(b: UByte): UByte {
        var x = b.toInt() and 0xFF
        var rev = 0
        repeat(8) { i ->
            if (((x shr i) and 1) == 1) {
                rev = rev or (1 shl (7 - i))
            }
        }
        return rev.toUByte()
    }

    override suspend fun sendIRDataToExternalDevice(freq: Int, irdata: IntArray): Unit =
        withContext(Dispatchers.IO) {
            _device?.let { device ->
                val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
                val connection =
                    usbManager.openDevice(device) ?: return@withContext
                val usbInterface = device.getInterface(0)

                if (!connection.claimInterface(usbInterface, true)) {
                    Timber.e("Failed to claim USB interface.")
                    connection.close()
                    return@withContext
                }

                if (irdata.size % 2 != 0) {
                    Timber.e("The code value is odd, which is incorrect.")
                    //sendIRDataToExternalDevice(freq, irdata.copyOfRange(0,irdata.size-1))
                }

                var endpointIn: UsbEndpoint? = null
                var endpointOut: UsbEndpoint? = null

                for (i in 0 until usbInterface.endpointCount) {
                    val endpoint = usbInterface.getEndpoint(i)
                    if (endpoint.direction == 128) {
                        endpointIn = endpoint
                    }
                    if (endpoint.direction == 0) {
                        endpointOut = endpoint
                    }
                }

                endpointIn ?: return@withContext
                endpointOut ?: return@withContext

                when (kind) {
                    DeviceIdentify.D552 -> {
                        sendIRDataToExternalDeviceD552(freq, irdata) { bytes ->
                            connection.bulkTransfer(
                                endpointOut,
                                bytes.toByteArray(),
                                bytes.size,
                                100
                            )
                            val buffer = ByteArray(4*1024)
                            val bytesRead = connection.bulkTransfer(endpointIn, buffer, buffer.size, 100)
                            Timber.d("data: $bytesRead")
                        }
                    }

                    DeviceIdentify.D571 -> {
                        // TODO: Implement D571 device handling
                    }

                    else -> Timber.e("Unknown device, please consult the relevant documentation.")
                }
            }
        }

    private suspend fun sendIRDataToExternalDeviceD552(
        freq: Int,
        irdata: IntArray,
        sendData: (fragment: UByteArray) -> Unit
    ) {
        val wavZip = WavZip()
        val payload: UByteArray = wavZip.compress(irdata)

        val len = payload.size
        val lenHi = ((len shr 8) and 0xFF).toUByte()
        val lenLo = (len and 0xFF).toUByte()

        val freqVal = freq + 524_288 - 1
        val fLo = (freqVal and 0xFF).toUByte()
        val fMi = ((freqVal shr 8) and 0xFF).toUByte()
        val fHi = ((freqVal shr 16) and 0xFF).toUByte()

        val header = ubyteArrayOf(
            0xFFu, 0xFFu, 0xFFu, 0xFFu,
            bitReverse(fMi).inv(),
            bitReverse(fHi).inv(),
            bitReverse(fLo).inv(),
            bitReverse(lenHi).inv(),
            bitReverse(lenLo).inv()
        )

        val stream = UByteArray(header.size + payload.size)
        header.copyInto(stream, 0)
        payload.copyInto(stream, header.size)

        for (chunk in prepareChunks(stream)) {
            sendData(chunk)
            delay(2)
        }

        Timber.i("Odesílání IR dat dokončeno.")
    }

    private fun prepareChunks(stream: UByteArray): List<UByteArray> {
        val chunks: MutableList<UByteArray> = mutableListOf()
        chunks.clear()

        var pos = 0
        while (pos < stream.size) {
            val remaining = stream.size - pos
            if (remaining >= 62) {
                val block = UByteArray(63) // 62 + checksum
                stream.copyInto(block, destinationOffset = 0, startIndex = pos, endIndex = pos + 62)
                val sum = checksum63(block, upto = 62) // jen prvních 62 bajtů
                block[62] = sum
                chunks += block
                pos += 62
            } else {
                // poslední kratší blok – bez checksumu (dle originálu)
                val block = UByteArray(remaining)
                stream.copyInto(
                    block,
                    destinationOffset = 0,
                    startIndex = pos,
                    endIndex = stream.size
                )
                chunks += block
                pos = stream.size
            }
        }
        return chunks
    }

    private fun checksum63(block: UByteArray, upto: Int): UByte {
        var sum = 0
        for (i in 0 until upto) sum += block[i].toInt() and 0xFF
        val merged = ((sum and 0xF0) or ((sum shr 8) and 0x0F)).toUByte()
        return bitReverse(merged).inv()
    }
}
