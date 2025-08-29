package cz.zorn.iruploader

import android.hardware.usb.UsbDevice
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.zorn.iruploader.ServerState.*
import cz.zorn.iruploader.db.FirmwareDesc
import cz.zorn.iruploader.db.Message
import cz.zorn.iruploader.irotg.IROTG
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class UploadingState {
    data class UPLOADING(val firmware: FirmwareDesc, val progress: Int) : UploadingState()
    object IDLE : UploadingState()
}

enum class IR_TRANSMITTER {
    NOT_AVAILABLE, INTERNAL, EXTERNAL
}

sealed class ServerState {
    data class READY(val ip: String) : ServerState()
    object STOPPED : ServerState()
}

class MainActivityVM(
    private val socketServer: SocketServer,
    private val uploaderRepository: UploaderRepository,
    private val irotg: IROTG,
) : ViewModel() {
    private val _hasIRTransmitter = MutableStateFlow(IR_TRANSMITTER.NOT_AVAILABLE)
    val hasIRTransmitter = _hasIRTransmitter.asStateFlow()

    private val _serverState = MutableStateFlow<ServerState>(STOPPED)
    val serverState = _serverState.asStateFlow()

    val uploadingState = MutableStateFlow<UploadingState>(UploadingState.IDLE)

    val firmwares = uploaderRepository.getFirmwares()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val messages = uploaderRepository.getMessages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun sendFirmware(fw: FirmwareDesc) {
        viewModelScope.launch {
            uploaderRepository.sendFlash(fw).collect { progress ->
                uploadingState.value = UploadingState.UPLOADING(fw, progress.pct)
            }
            uploadingState.value = UploadingState.IDLE
        }
    }

    fun deleteFirmware(fw: FirmwareDesc) {
        viewModelScope.launch { uploaderRepository.deleteFirmware(fw) }
    }

    fun sendMessage(content: String) {
        viewModelScope.launch { uploaderRepository.sendMessage(Message(content)) }
    }

    fun resendMessage(message: Message) {
        viewModelScope.launch { uploaderRepository.sendMessage(message) }
    }

    fun deleteMessage(message: Message) {
        viewModelScope.launch { uploaderRepository.deleteMessage(message) }
    }

    // USB device transmitter is prioritized if available
    fun connectUsbDevice(device: UsbDevice) {
        viewModelScope.launch {
            irotg.identifyDevice(device)
            _hasIRTransmitter.value = IR_TRANSMITTER.EXTERNAL
            registerIRTransmitter(irotg::sendIRDataToExternalDevice)
        }
    }

    fun registerIRTransmitter(transmitter: suspend (freq: Int, pattern: IntArray) -> Unit) {
        if (_hasIRTransmitter.value == IR_TRANSMITTER.NOT_AVAILABLE) {
            _hasIRTransmitter.value = IR_TRANSMITTER.INTERNAL
        }
        uploaderRepository.registerIRTransmitter(transmitter)
    }

    init {
        viewModelScope.launch {
            socketServer.state.collect {
                when (it) {
                    SocketServerState.Stopped -> _serverState.value = STOPPED
                    is SocketServerState.Started -> _serverState.value = READY(it.ip)
                    is SocketServerState.Uploaded -> sendFirmware(it.firmware.asFirmwareDesc())
                }
            }
        }
    }
}