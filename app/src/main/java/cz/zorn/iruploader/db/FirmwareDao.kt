package cz.zorn.iruploader.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
abstract class FirmwareDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(firmware: Firmware)

    @Query("DELETE FROM firmware WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Query("select id, name, author, version, ts from firmware order by ts desc")
    abstract fun firmwareDescs(): Flow<List<FirmwareDesc>>

    @Query("select hex from firmware where id=:id")
    abstract fun hex(id: Long): String?
}