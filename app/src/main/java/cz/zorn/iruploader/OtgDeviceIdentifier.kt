package cz.zorn.iruploader

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class OtgDeviceIdentifier(private val context: Context) {

    private val usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var usbDevice: UsbDevice? = null
    private var connection: UsbDeviceConnection? = null
    private var endpointIn: UsbEndpoint? = null
    private var endpointOut: UsbEndpoint? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val _deviceState = MutableStateFlow<Pair<DeviceType, String>?>(null)
    val deviceState: StateFlow<Pair<DeviceType, String>?> = _deviceState.asStateFlow()

    private val usbGrantReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == GRANT_USB_ACTION && intent.getBooleanExtra("permission", false)) {
                usbDevice?.let { scope.launch { connectDevice(it) } } ?: Log.w(
                    "OTG",
                    "Zařízení nenalezeno"
                )
            } else {
                Log.w("OTG", "Oprávnění k USB zamítnuto")
            }
        }
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device = intent.getParcelableExtra<UsbDevice>("device")
            when (intent.action) {
                "android.hardware.usb.action.USB_DEVICE_ATTACHED" -> device?.let {
                    scope.launch {
                        startIdentification(
                            it
                        )
                    }
                }

                "android.hardware.usb.action.USB_DEVICE_DETACHED" -> stop()
            }
        }
    }

    enum class DeviceType {
        UNKNOWN, D552, D571
    }

    suspend fun identifyDevice(): Pair<DeviceType, String> =
        suspendCancellableCoroutine { continuation ->
            context.registerReceiver(
                usbGrantReceiver,
                IntentFilter(GRANT_USB_ACTION),
                if (Build.VERSION.SDK_INT >= 33) 2 else 0
            )
            context.registerReceiver(usbReceiver, IntentFilter().apply {
                addAction("android.hardware.usb.action.USB_DEVICE_ATTACHED")
                addAction("android.hardware.usb.action.USB_DEVICE_DETACHED")
            }, if (Build.VERSION.SDK_INT >= 33) 2 else 0)

            usbManager.deviceList.values.firstOrNull()?.let { device ->
                scope.launch { startIdentification(device) }
            }
                ?: continuation.resumeWithException(IllegalStateException("Žádné USB zařízení nenalezeno"))

            scope.launch {
                deviceState.collect { state ->
                    state?.let { continuation.resume(it) }
                }
            }
        }

    fun stop() {
        scope.cancel()
        connection?.releaseInterface(usbDevice?.getInterface(0))
        connection?.close()
        context.unregisterReceiver(usbGrantReceiver)
        context.unregisterReceiver(usbReceiver)
        usbDevice = null
        connection = null
        _deviceState.value = null
    }

    private suspend fun startIdentification(device: UsbDevice) {
        usbDevice = device
        if (usbManager.hasPermission(device)) {
            connectDevice(device)
        } else {
            usbManager.requestPermission(
                device, PendingIntent.getBroadcast(
                    context, 0, Intent(GRANT_USB_ACTION),
                    if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_IMMUTABLE else 0
                )
            )
        }
    }

    private suspend fun connectDevice(device: UsbDevice) {
        val usbInterface = device.getInterface(0)
        val endpoints = (0 until usbInterface.endpointCount).map { usbInterface.getEndpoint(it) }
        endpointIn = endpoints.find { it.direction == 128 }
        endpointOut = endpoints.find { it.direction == 0 }

        if (endpointIn == null || endpointOut == null) {
            Log.e("OTG", "Nepodařilo se najít endpointy")
            return
        }

        connection = usbManager.openDevice(device)?.apply {
            if (!claimInterface(usbInterface, true)) {
                Log.e("OTG", "Nepodařilo se získat rozhraní")
                return
            }
        } ?: run {
            Log.e("OTG", "Nepodařilo se otevřít zařízení")
            return
        }

        write(arrayListOf(252u, 252u, 252u, 252u))
        val buffer = ByteArray(16384)
        repeat(3) { // Max 3 pokusy
            val readBytes = connection?.bulkTransfer(endpointIn, buffer, buffer.size, 100) ?: 0
            if (readBytes == 6 && buffer.take(4).all { it.toUByte() == 250.toUByte() }) {
                val b4 = buffer[4].toUByte().toInt()
                val b5 = buffer[5].toUByte().toInt()
                val result = when {
                    b4 == 2 && b5 == 170 -> DeviceType.D571 to "2AA"
                    b4 == 112 && b5 == 1 -> DeviceType.D552 to "112"
                    else -> DeviceType.UNKNOWN to "$b4$b5"
                }
                _deviceState.value = result
                write(arrayListOf(250u, 250u, 250u, 250u))
                return
            }
            delay(100)
        }
        _deviceState.value = DeviceType.UNKNOWN to "0"
    }

    private fun write(data: ArrayList<UByte>) {
        connection?.bulkTransfer(
            endpointOut,
            data.map { it.toByte() }.toByteArray(),
            data.size,
            100
        )
    }

    companion object {
        private const val GRANT_USB_ACTION = "com.esmart.ir.otg.GRANT_USB"
    }
}