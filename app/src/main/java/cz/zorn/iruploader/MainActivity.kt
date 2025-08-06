package cz.zorn.iruploader

import android.content.Intent
import android.hardware.ConsumerIrManager
import android.net.Uri
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
import cz.zorn.iruploader.ui.theme.IRUploaderTheme
import kotlinx.serialization.Serializable
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

@Serializable
data object ScreenMain : NavKey

@Serializable
data object ScreenMessage : NavKey

@Serializable
data object ScreenBootloader : NavKey

sealed class BottomNavItem(val screen: NavKey, val labelResId: Int, val icon: ImageVector) {
    object Main : BottomNavItem(ScreenMain, R.string.bottom_nav_main, Icons.Filled.Home)
    object Messages : BottomNavItem(ScreenMessage, R.string.bottom_nav_messages, Icons.Filled.Email)
}

val bottomNavItems = listOf(BottomNavItem.Main, BottomNavItem.Messages)

class MainActivity : ComponentActivity() {
    private val model: MainActivityVM by viewModel()

    private lateinit var irManager: ConsumerIrManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (getSystemService(CONSUMER_IR_SERVICE) != null)
            irManager = getSystemService(CONSUMER_IR_SERVICE) as ConsumerIrManager

        enableEdgeToEdge()
        setContent {
            val serverState by model.serverState.collectAsStateWithLifecycle()
            val firmwares by model.firmwares.collectAsStateWithLifecycle()
            val messages by model.messages.collectAsStateWithLifecycle()
            val uploadingState by model.uploadingState.collectAsStateWithLifecycle()

            IRUploaderTheme {
                val backStack = rememberNavBackStack(ScreenMain) // Default screen is now ScreenMain
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
                            entry<ScreenMain> {
                                Column {
                                    Overview(serverState, uploadingState) {
                                        backStack.add(ScreenBootloader)
                                    }
                                    FirmwareList(
                                        firmwares,
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
}
