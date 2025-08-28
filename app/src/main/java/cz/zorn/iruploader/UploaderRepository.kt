package cz.zorn.iruploader

import cz.zorn.iruploader.db.Firmware
import cz.zorn.iruploader.db.FirmwareDao
import cz.zorn.iruploader.db.Message
import cz.zorn.iruploader.db.MessageDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets

interface UploaderRepository {
    fun getFirmwares(): Flow<List<Firmware>>
    fun getMessages(): Flow<List<Message>>
    suspend fun saveFirmwareFromInputStream(inputStream: InputStream): Firmware
    fun sendFlash(firmware: Firmware): Flow<FlashUploadProgress>
    suspend fun deleteFirmware(fw: Firmware)
    suspend fun deleteMessage(message: Message)
    suspend fun sendMessage(message: Message)
    fun registerIRTransmitter(transmitter: suspend (freq: Int, pattern: IntArray) -> Unit)
}

class UploaderRepositoryImpl(
    private val firmwareDao: FirmwareDao,
    private val messageDao: MessageDao,
    private val loader: HexLoader,
    private val messageSender: IRMessageSender,
    private val firmwareSender: IRFirmwareSender,
) : UploaderRepository {
    private lateinit var irTransmitter: suspend (Int, IntArray) -> Unit

    override fun getFirmwares() = firmwareDao.getFirmwares()
    override fun getMessages() = messageDao.getMessages()

    override fun sendFlash(firmware: Firmware): Flow<FlashUploadProgress> = flow {
        val firstWaitPct = 50
        emit(FlashUploadProgress(0))

        messageSender.sendMessage(MSG_START_BOOTLOADER, irTransmitter)

        for (i in 1..firstWaitPct) {
            delay(DELAY_AFTER_BOOTLOADER_START / firstWaitPct)
            emit(FlashUploadProgress(i))
        }

        val byteArray = firmware.hex.toByteArray(StandardCharsets.US_ASCII)
        val inputStream: InputStream = ByteArrayInputStream(byteArray)
        val flash = loader.loadFlash(inputStream, 512 * 64)
        val hex = loader.flashPages(flash, 64)

        firmwareSender.sendFlash(hex, irTransmitter).collect {
            val progress = it.pct / 100.0 * (100 - firstWaitPct) + firstWaitPct
            emit(FlashUploadProgress(progress.toInt()))
        }
    }

    override suspend fun deleteFirmware(fw: Firmware) = firmwareDao.deleteFirmware(fw)
    override suspend fun deleteMessage(message: Message) = messageDao.deleteMessage(message)
    override suspend fun sendMessage(message: Message): Unit = withContext(Dispatchers.IO) {
        messageDao.upsertMessage(message)
        messageSender.sendMessage(message.content, irTransmitter)
    }

    override fun registerIRTransmitter(transmitter: suspend (Int, IntArray) -> Unit) {
        irTransmitter = transmitter
    }

    override suspend fun saveFirmwareFromInputStream(inputStream: InputStream): Firmware =
        withContext(Dispatchers.IO) {
            val byteArrayOutputStream = ByteArrayOutputStream()
            inputStream.use { it.copyTo(byteArrayOutputStream) }
            val dataBytes = byteArrayOutputStream.toByteArray()

            val flash = loader.loadFlash(ByteArrayInputStream(dataBytes), FLASH_SIZE)
            val metaData = loader.getFirmwareMetaData(flash)
            val firmware = Firmware(
                id = 0,
                name = metaData.name,
                author = metaData.author,
                version = metaData.version,
                hex = dataBytes.toString(StandardCharsets.US_ASCII),
                ts = null
            )
            firmwareDao.insertFirmware(firmware)
            firmware
        }

    companion object {
        const val FLASH_SIZE = 512 * 64
        const val DELAY_AFTER_BOOTLOADER_START = 6000.toLong()
        const val MSG_START_BOOTLOADER = "BOOT"
    }
}