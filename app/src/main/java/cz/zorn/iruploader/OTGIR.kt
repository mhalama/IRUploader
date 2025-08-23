/*

import android.content.Context
import android.hardware.ConsumerIrManager
import android.util.Log
import kotlin.jvm.internal.Intrinsics

class DeviceDetector(private val context: Context) {

    // Instance MyIRCommunicator pro kontrolu vestavěného IR
    private val irCommunicator = MyIRCommunicator.instance

    // Kontrola dostupných zařízení
    fun detectAvailableDevices(usbHostManager: UsbHostManager? = null): DeviceDetectionResult {
        val result = DeviceDetectionResult()

        // Kontrola vestavěného IR vysílače
        result.hasInnerIr = irCommunicator.hasInnerIr(context)
        if (result.hasInnerIr) {
            Log.i("DeviceDetector", "Vestavěný IR vysílač nalezen.")
        } else {
            Log.i("DeviceDetector", "Vestavěný IR vysílač nenalezen.")
        }

        // Kontrola externího zařízení přes USB OTG
        if (usbHostManager != null) {
            val deviceIdentify = usbHostManager.getDeviceIdentify()
            result.usbDevice = when (deviceIdentify) {
                UsbHostManager.DeviceIdentify.d571 -> {
                    Log.i("DeviceDetector", "Nalezeno USB zařízení: d571")
                    "d571"
                }
                UsbHostManager.DeviceIdentify.d552 -> {
                    Log.i("DeviceDetector", "Nalezeno USB zařízení: d552")
                    "d552"
                }
                UsbHostManager.DeviceIdentify.d552_old -> {
                    Log.i("DeviceDetector", "Nalezeno USB zařízení: d552_old")
                    "d552_old"
                }
                else -> {
                    Log.w("DeviceDetector", "Neznámé USB zařízení: ${deviceIdentify.name()}")
                    null
                }
            }

            // Získání sériového čísla zařízení, pokud je připojeno
            if (result.usbDevice != null) {
                irCommunicator.getDeviceSN(usbHostManager)
                Log.i("DeviceDetector", "Požadavek na sériové číslo odeslán pro zařízení: ${result.usbDevice}")
            }
        } else {
            Log.i("DeviceDetector", "Žádný UsbHostManager není poskytnut, externí zařízení nejsou kontrolována.")
        }

        return result
    }
}

// Datová třída pro výsledek detekce
data class DeviceDetectionResult(
    var hasInnerIr: Boolean = false,
    var usbDevice: String? = null
)

// Extension funkce pro inicializaci a detekci
fun Context.detectIrDevices(usbHostManager: UsbHostManager? = null): DeviceDetectionResult {
    Intrinsics.checkNotNullParameter(this, "context")
    val detector = DeviceDetector(this)
    // Inicializace MyIRCommunicator s kontextem
    MyIRCommunicator.instance.initApp(this)
    return detector.detectAvailableDevices(usbHostManager)
}

// Příklad použití
fun exampleUsage(context: Context, usbHostManager: UsbHostManager?) {
    val result = context.detectIrDevices(usbHostManager)

    println("Detekce zařízení:")
    println("Vestavěný IR: ${if (result.hasInnerIr) "Ano" else "Ne"}")
    println("Externí USB zařízení: ${result.usbDevice ?: "Žádné"}")
}

 */