package cz.zorn.iruploader.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
@Entity(tableName = "message")
data class Message(
    @PrimaryKey()
    var id: String = "",

    var ts: String = Clock.System.now().toString(),
)