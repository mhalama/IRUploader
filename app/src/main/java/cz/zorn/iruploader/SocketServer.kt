package cz.zorn.iruploader

import cz.zorn.iruploader.db.Firmware
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket

sealed class SocketServerState {
    data class Started(val ip: String) : SocketServerState()
    data class Uploaded(val firmware: Firmware) : SocketServerState()
    object Stopped : SocketServerState()
}

interface SocketServer {
    fun start()
    fun stop()
    val state: StateFlow<SocketServerState>
}

class SocketServerImpl(
    val port: Int,
    private val scope: CoroutineScope,
    private val repository: UploaderRepository
) : SocketServer {
    private lateinit var serverSocket: ServerSocket

    private var running = false

    private val _state = MutableStateFlow<SocketServerState>(SocketServerState.Stopped)
    override val state: StateFlow<SocketServerState> = _state

    override fun start() {
        scope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(port)
                Timber.d("Server běží na portu $port")

                running = true
                _state.update { SocketServerState.Started(getLocalIpAddress()) }

                while (running && !serverSocket.isClosed) {
                    val clientSocket: Socket = serverSocket.accept()

                    val fw = repository.saveFirmwareFromInputStream(clientSocket.getInputStream())
                    _state.update { SocketServerState.Uploaded(fw) }

                    clientSocket.close()
                }
                running = false
                _state.update { SocketServerState.Stopped }
            } catch (e: Exception) {
                Timber.d("Chyba: ${e.message}")
            }
        }
    }

    override fun stop() {
        try {
            serverSocket.close()
        } catch (e: Exception) {
            Timber.w("Chyba při zavírání serveru: ${e.message}")
        }
        running = false
        _state.update { SocketServerState.Stopped }
        Timber.d("Server zastaven")
    }

    private fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (intf in interfaces) {
                val addresses = intf.inetAddresses
                for (addr in addresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress ?: continue
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e("Chyba při získávání IP adresy: ${e.message}")
        }
        return "?"
    }
}