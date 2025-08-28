package cz.zorn.iruploader.db

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

interface FirmwareDao {
    fun getFirmwares(): Flow<List<Firmware>>
    suspend fun deleteFirmware(fw: Firmware)
    suspend fun insertFirmware(firmware: Firmware)
}

class FirmwareDaoImpl(private val db: FirmwareDatabase) : FirmwareDao {
    override fun getFirmwares() =
        db.firmwareQueries.getFirmwares().asFlow().mapToList(Dispatchers.IO)

    override suspend fun deleteFirmware(fw: Firmware): Unit = withContext(Dispatchers.IO) {
        db.firmwareQueries.deleteFirmware(fw.id).await()
    }

    override suspend fun insertFirmware(firmware: Firmware): Unit = withContext(Dispatchers.IO) {
        db.firmwareQueries.upsertFirmware(
            name = firmware.name,
            author = firmware.author,
            version = firmware.version,
            hex = firmware.hex
        ).await()
    }
}