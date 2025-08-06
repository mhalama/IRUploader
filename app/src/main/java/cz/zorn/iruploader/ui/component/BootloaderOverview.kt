package cz.zorn.iruploader.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cz.zorn.iruploader.R
import dev.jeziellago.compose.markdowntext.MarkdownText

@Composable
fun BootloaderOverview(shareBootloader: () -> Unit) {
    val markdownContent = """
## Nahrání bootloaderu

Než bude možné mikrokontrolér programovat, je nejprve nutné do něj jednorázově nahrát bootloader. Ten slouží jako speciální program, který umožňuje přijímat další programy prostřednictvím infračerveného signálu.

Bez tohoto kroku mikrokontrolér nebude schopen komunikovat s touto aplikací a přijímat program. Nahrání bootloaderu je tedy nezbytným prvním krokem při uvádění zařízení do provozu.

Podrobnější informace o tom, jak bootloader funguje, naleznete v clanku: [Jak funguje bootloader](https://zorn.cz/bootloader/overview).

Bootloader si můžete stáhnout přímo z této aplikace pomocí tlačítka níže.          
    """.trimIndent()

    Column(modifier = Modifier.fillMaxSize().padding(8.dp),) {
        MarkdownText(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            markdown = markdownContent,
        )

        Button({ shareBootloader() }) {
            Text(stringResource(R.string.get_bootloader))
        }
    }
}
