package cz.zorn.iruploader.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
abstract class MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(message: Message)

    @Query("DELETE FROM message WHERE id = :id")
    abstract suspend fun delete(id: String)

    @Query("select * from message order by ts desc")
    abstract fun messages(): Flow<List<Message>>
}