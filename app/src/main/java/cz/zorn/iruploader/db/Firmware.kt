package cz.zorn.iruploader.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
@Entity(tableName = "firmware")
data class Firmware(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0,

    var name: String? = null,
    var author: String? = null,
    var version: String? = null,
    var ts: String = Clock.System.now().toString(),
    var hex: String = ""
) {
    fun asFirmwareDesc() = FirmwareDesc(id, name, author, version, ts)
}