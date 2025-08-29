package cz.zorn.iruploader.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        Firmware::class,
        Message::class,
    ],
    version = 1,
)

abstract class IRUploaderDatabase : RoomDatabase() {
    abstract val firmwareDao: FirmwareDao
    abstract val messageDao: MessageDao
}
