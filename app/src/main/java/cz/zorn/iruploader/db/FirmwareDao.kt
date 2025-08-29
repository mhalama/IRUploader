package cz.zorn.iruploader.db

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

interface FirmwareDao {
    fun getFirmwares(): Flow<List<FirmwareDesc>>
    suspend fun loadFirmware(id: Long): String?
    suspend fun deleteFirmware(id: Long)
    suspend fun insertFirmware(firmware: Firmware)
}

class FirmwareDaoImpl(private val db: FirmwareDatabase) : FirmwareDao {
    override fun getFirmwares() =
        db.firmwareQueries.firmwareDesc().asFlow().mapToList(Dispatchers.IO)

    override suspend fun loadFirmware(id: Long): String? {
        return db.firmwareQueries.firmwareHex(id).asFlow().mapToOneOrNull(Dispatchers.IO).firstOrNull()
    }

    override suspend fun deleteFirmware(id: Long): Unit = withContext(Dispatchers.IO) {
        db.firmwareQueries.deleteFirmware(id).await()
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

fun Firmware.asFirmwareDesc(): FirmwareDesc = FirmwareDesc(
    id = id,
    name = name,
    author = author,
    version = version,
    ts = ts
)