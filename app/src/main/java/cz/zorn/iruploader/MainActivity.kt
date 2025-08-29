package cz.zorn.iruploader

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.ConsumerIrManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import cz.zorn.iruploader.ui.component.BootloaderOverview
import cz.zorn.iruploader.ui.component.FirmwareList
import cz.zorn.iruploader.ui.component.MessageList
import cz.zorn.iruploader.ui.component.MessageSender
import cz.zorn.iruploader.ui.component.Overview
import cz.zorn.iruploader.ui.component.OverviewSimple
import cz.zorn.iruploader.ui.theme.IRUploaderTheme
import kotlinx.serialization.Serializable
import org.koin.androidx.viewmodel.ext.android.viewModel
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

@Serializable
data object ScreenOverview : NavKey

@Serializable
data object ScreenMain : NavKey

@Serializable
data object ScreenMessage : NavKey

@Serializable
data object ScreenBootloader : NavKey

sealed class BottomNavItem(val screen: NavKey, val labelResId: Int, val icon: ImageVector) {
    object Overview : BottomNavItem(ScreenOverview, R.string.bottom_nav_overview, Icons.Filled.Home)
    object Firmware : BottomNavItem(ScreenMain, R.string.bottom_nav_main, Icons.Filled.Build)
    object Messages : BottomNavItem(ScreenMessage, R.string.bottom_nav_messages, Icons.Filled.Email)
}

val bottomNavItems = listOf(BottomNavItem.Overview, BottomNavItem.Firmware, BottomNavItem.Messages)

class MainActivity : ComponentActivity() {
    private val model: MainActivityVM by viewModel()

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (ACTION_USB_PERMISSION == intent?.action) {
                synchronized(this) {
                    val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        device?.apply {
                            connectUsbDevice(device)
                        }
                    } else {
                        Timber.d("permission denied for device $device")
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Default IR transmitter if available
        getSystemService(CONSUMER_IR_SERVICE)?.let {
            val irManager = it as ConsumerIrManager
            if (irManager.hasIrEmitter()) {
                model.registerIRTransmitter { freq, pattern ->
                    val pat = pattern.joinToString(" ") { it.toString() }
                    Timber.d("IR (${pattern.size}): $pat")
                    irManager.transmit(freq, pattern)
                }
            }
        }

        handleUsbConnectedDevice()

        enumerateUsbDevices()

        enableEdgeToEdge()
        setContent {
            val serverState by model.serverState.collectAsStateWithLifecycle()
            val hasIrTransmitter by model.hasIRTransmitter.collectAsStateWithLifecycle()
            val firmwares by model.firmwares.collectAsStateWithLifecycle()
            val messages by model.messages.collectAsStateWithLifecycle()
            val uploadingState by model.uploadingState.collectAsStateWithLifecycle()

            IRUploaderTheme {
                val backStack = rememberNavBackStack(ScreenOverview) // Default screen is now ScreenMain
                val currentScreenKey = backStack.lastOrNull()

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        NavigationBar {
                            bottomNavItems.forEach { item ->
                                NavigationBarItem(
                                    icon = {
                                        Icon(
                                            item.icon,
                                            contentDescription = stringResource(item.labelResId)
                                        )
                                    },
                                    label = { Text(stringResource(item.labelResId)) },
                                    selected = item.screen == currentScreenKey,
                                    onClick = {
                                        if (item.screen != currentScreenKey) {
                                            backStack.replaceAll { item.screen }
                                        }
                                    }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    NavDisplay(
                        backStack = backStack,
                        onBack = { backStack.removeLastOrNull() },
                        entryProvider = entryProvider {
                            entry<ScreenOverview> {
                                Column {
                                    Overview(serverState, hasIrTransmitter, uploadingState) {
                                        backStack.add(ScreenBootloader)
                                    }
                                }
                            }
                            entry<ScreenMain> {
                                Column {
                                    OverviewSimple(serverState, hasIrTransmitter, uploadingState) {
                                        backStack.add(ScreenBootloader)
                                    }
                                    FirmwareList(
                                        firmwares,
                                        hasIrTransmitter,
                                        onSendFirmware = { model.sendFirmware(it) },
                                        onDeleteFirmware = { model.deleteFirmware(it) })
                                }
                            }
                            entry<ScreenMessage> {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    MessageSender { model.sendMessage(it) }
                                    MessageList(
                                        messages,
                                        onResend = { model.resendMessage(it) },
                                        onDelete = { model.deleteMessage(it) }
                                    )
                                }
                            }
                            entry<ScreenBootloader> {
                                BootloaderOverview {
                                    shareAssetFile("bootloader.hex")
                                }
                            }
                        },
                        transitionSpec = {
                            slideInHorizontally(initialOffsetX = { it }) togetherWith
                                    slideOutHorizontally(targetOffsetX = { -it })
                        },
                        popTransitionSpec = {
                            slideInHorizontally(initialOffsetX = { -it }) togetherWith
                                    slideOutHorizontally(targetOffsetX = { it })
                        },
                        predictivePopTransitionSpec = {
                            slideInHorizontally(initialOffsetX = { -it }) togetherWith
                                    slideOutHorizontally(targetOffsetX = { it })
                        },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        enumerateUsbDevices()
    }

    fun shareAssetFile(assetFileName: String) {
        val assetManager = this.assets
        val cacheDir = this.cacheDir
        val tempFile = File(cacheDir, assetFileName)

        try {
            val inputStream: InputStream = assetManager.open(assetFileName)
            val outputStream: OutputStream = FileOutputStream(tempFile)
            copyFile(inputStream, outputStream)
            inputStream.close()
            outputStream.flush()
            outputStream.close()

            val uri: Uri = FileProvider.getUriForFile(
                this,
                "${this.packageName}.provider",
                tempFile
            )

            val shareIntent: Intent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_STREAM, uri)
                type = application.contentResolver.getType(uri) ?: "*/*" // Výchozí MIME typ
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_file)))
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    @Throws(IOException::class)
    private fun copyFile(inputStream: InputStream, outputStream: OutputStream) {
        val buffer = ByteArray(1024)
        var read: Int
        while (inputStream.read(buffer).also { read = it } != -1) {
            outputStream.write(buffer, 0, read)
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun connectUsbDevice(device: UsbDevice) {
        val usbManager = getSystemService(USB_SERVICE) as? UsbManager ?: return
        if (!usbManager.hasPermission(device)) {
            Timber.d("Asking for permission to connect device $device ...")
            val permissionIntent = PendingIntent.getBroadcast(
                this,
                0,
                Intent(ACTION_USB_PERMISSION),
                PendingIntent.FLAG_IMMUTABLE
            )
            val filter = IntentFilter(ACTION_USB_PERMISSION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(usbReceiver, filter, RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(usbReceiver, filter)
            }
            usbManager.requestPermission(device, permissionIntent)
        } else {
            Timber.d("Connecting device $device ...")
            model.connectUsbDevice(device)
        }
    }

    private fun handleUsbConnectedDevice() {
        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE) as? UsbDevice
        }
        device?.let { connectUsbDevice(it) }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun enumerateUsbDevices() {
        val usbManager = getSystemService(USB_SERVICE) as? UsbManager ?: return
        usbManager.deviceList.values.forEach { device ->
            Timber.d("Mam zarizeni: ${device.deviceName} : ${device.deviceClass} : ${device.deviceId} : ${device.productId} : ${device.vendorId}")

            if (usbManager.hasPermission(device)) {
                connectUsbDevice(device)
            } else {
                val permissionIntent = PendingIntent.getBroadcast(
                    this,
                    0,
                    Intent(ACTION_USB_PERMISSION),
                    PendingIntent.FLAG_IMMUTABLE
                )
                val filter = IntentFilter(ACTION_USB_PERMISSION)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(usbReceiver, filter, RECEIVER_NOT_EXPORTED)
                } else {
                    registerReceiver(usbReceiver, filter)
                }
                usbManager.requestPermission(device, permissionIntent)
            }
        }
    }

    companion object {
        private const val ACTION_USB_PERMISSION = "cz.zorn.iruploader.USB_PERMISSION"
    }
}
