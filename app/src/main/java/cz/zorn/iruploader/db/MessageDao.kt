package cz.zorn.iruploader.db

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

interface MessageDao {
    fun getMessages(): Flow<List<Message>>
    suspend fun deleteMessage(message: Message)
    suspend fun upsertMessage(message: Message)
}

class MessageDaoImpl(private val db: FirmwareDatabase) : MessageDao {
    override fun getMessages() =
        db.messageQueries.getMessages().asFlow().mapToList(Dispatchers.IO)

    override suspend fun deleteMessage(message: Message): Unit = withContext(Dispatchers.IO) {
        db.messageQueries.deleteMessage(message.content).await()
    }

    override suspend fun upsertMessage(message: Message): Unit = withContext(Dispatchers.IO) {
        db.messageQueries.upsertMessage(message.content).await()
    }
}