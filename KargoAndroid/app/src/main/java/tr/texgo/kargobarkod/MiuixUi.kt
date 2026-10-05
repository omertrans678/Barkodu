package tr.texgo.kargobarkod

import android.view.View
import androidx.core.view.WindowCompat
import android.content.res.Configuration
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.*
import top.yukonga.miuix.kmp.extra.SuperDialog

class MiuixUi(private val activity: MainActivity) {
    private var revision by mutableIntStateOf(0)
    fun update() { revision++ }
    fun createView(): View = ComposeView(activity).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            val dark = isSystemInDarkTheme()
            SideEffect {
                WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            MiuixTheme(colors = if (dark) darkColorScheme() else lightColorScheme()) {
                Scaffold { insets -> Screen(Modifier.padding(insets).consumeWindowInsets(insets)) }
            }
        }
    }
    @Composable private fun Action(label: String, modifier: Modifier = Modifier, enabled: Boolean = true, primary: Boolean = false, onClick: () -> Unit) {
        Button(onClick = onClick, modifier = modifier, enabled = enabled,
            minWidth = 0.dp, minHeight = 44.dp, insideMargin = PaddingValues(horizontal = 8.dp, vertical = 9.dp),
            colors = if (primary) ButtonDefaults.buttonColorsPrimary() else ButtonDefaults.buttonColors()) {
            Text(label, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    @Composable private fun Screen(modifier: Modifier) {
        val tick = revision
        val model = activity.model
        // Snapshot quantities so Compose observes repeat counts changed by the scanner.
        val rows = model.visible().map { it.copy() }
        val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        val scroll = rememberLazyListState()
        var settingsDialog by remember { mutableStateOf(false) }
        var clearDialog by remember { mutableStateOf(false) }
        var manualDialog by remember { mutableStateOf(false) }
        var input by remember { mutableStateOf("") }
        var error by remember { mutableStateOf("") }
        val newest = rows.firstOrNull()?.let { "${model.active}:${it.code}:${it.quantity}" }
        LaunchedEffect(newest) { if (rows.isNotEmpty()) scroll.scrollToItem(0) }
        val colors = MiuixTheme.colorScheme
        Column(modifier.fillMaxSize().background(colors.background).padding(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Kargo Barkod", Modifier.weight(1f), fontSize = if (landscape) 20.sp else 26.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
                Text("1.2 · Miuix", fontSize = 11.sp, color = colors.onSurfaceVariantSummary)
            }
            Text(activity.statusMessage, fontSize = 12.sp, color = if (activity.isSuccessFeedback) Color(0xFF169B55) else colors.onSurfaceVariantSummary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (landscape) {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Camera(Modifier.weight(0.4f).fillMaxHeight(), activity.isCameraActive, activity.isSuccessFeedback, activity.resolutionMessage, activity.isRepeatWarningVisible)
                    ListPanel(Modifier.weight(0.6f).fillMaxHeight(), rows, scroll)
                }
            } else {
                val height = (LocalConfiguration.current.screenHeightDp * 0.22f).coerceIn(95f, 210f)
                Camera(Modifier.fillMaxWidth().height(height.dp), activity.isCameraActive, activity.isSuccessFeedback, activity.resolutionMessage, activity.isRepeatWarningVisible)
                ListPanel(Modifier.weight(1f).fillMaxWidth(), rows, scroll)
            }
            Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Okutulacak kargo", Modifier.weight(1f), fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
                    Text("Ayarlar", Modifier.clickable { settingsDialog = true }.padding(6.dp), color = colors.primary, fontSize = 13.sp)
                    Text("Elle ekle", Modifier.clickable { error = ""; manualDialog = true }.padding(6.dp), color = colors.primary, fontSize = 13.sp)
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (id in 0..3) Action(CargoModel.NAMES[id], primary = model.selected == id) { activity.selectCargo(id) }
                }
                Spacer(Modifier.height(7.dp))
                if (landscape) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Action(if(activity.isCameraActive) "Kapat" else "Kamera", Modifier.weight(1f), model.selected >= 0, true) { activity.toggleCamera() }
                        Action("Geri al", Modifier.weight(1f), model.canUndo()) { activity.undoAction() }
                        Action("Excel", Modifier.weight(1f), model.items.isNotEmpty(), true) { activity.exportAction("xlsx", false) }
                        Action("TXT", Modifier.weight(1f), model.items.isNotEmpty()) { activity.exportAction("txt", false) }
                        Action("Paylaş", Modifier.weight(1f), model.items.isNotEmpty()) { activity.exportAction("txt", true) }
                        Action("Temizle", Modifier.weight(1f), model.items.isNotEmpty()) { clearDialog = true }
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Action(if(activity.isCameraActive) "Kamerayı kapat" else "Kamerayı aç", Modifier.weight(1.4f), model.selected >= 0, true) { activity.toggleCamera() }
                        Action("Geri al", Modifier.weight(1f), model.canUndo()) { activity.undoAction() }
                        Action("Paylaş", Modifier.weight(1f), model.items.isNotEmpty()) { activity.exportAction("txt", true) }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Action("Bitir / Excel", Modifier.weight(1.4f), model.items.isNotEmpty(), true) { activity.exportAction("xlsx", false) }
                        Action("TXT kaydet", Modifier.weight(1f), model.items.isNotEmpty()) { activity.exportAction("txt", false) }
                        Action("Temizle", Modifier.weight(1f), model.items.isNotEmpty()) { clearDialog = true }
                    }
                }
            }
        }
        SuperDialog(show = settingsDialog, title = "Okuma ayarları", onDismissRequest = { settingsDialog = false }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Tekrar okuma uyarısı", Modifier.weight(1f), fontSize = 15.sp)
                    Switch(checked = activity.isRepeatWarningEnabled, onCheckedChange = { activity.setRepeatWarningEnabled(it) })
                }
                Text("Aynı barkod kamerada kaldığında yeniden eklenmez. Uyarı açıksa bir kez bildirim gösterilir. Uyarıyı kapatmak tekrar ekleme engelini kapatmaz.", fontSize = 13.sp)
                Text("İkinci siparişi saymak için barkodu görüntüden çıkarıp tekrar gösterin. Farklı barkodlar beklemeden eklenir.", fontSize = 13.sp)
                Action("Tamam", Modifier.fillMaxWidth(), primary = true) { settingsDialog = false }
            }
        }
        SuperDialog(show = clearDialog, title = "Listeleri temizle?", summary = "Tüm kargolardaki barkodlar silinir. Geri al ile geri yükleyebilirsiniz.", onDismissRequest = { clearDialog = false }) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Action("Vazgeç", Modifier.weight(1f)) { clearDialog = false }
                Action("Temizle", Modifier.weight(1f), primary = true) { activity.clearAction(); clearDialog = false }
            }
        }
        SuperDialog(show = manualDialog, title = "Elle ekle", onDismissRequest = { manualDialog = false }) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TextField(value = input, onValueChange = { input = it.take(64) }, label = "Barkod", singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters))
                Text("DHL/MNG: 416, HTS · HepsiJet: 416, H0, HTS\nTex: 73 · Aras: 72, P0, A0, FL0\nDiğerleri Bilinmeyen listesine eklenir.", fontSize = 12.sp)
                if (error.isNotEmpty()) Text(error, color = Color(0xFFCE3434), fontSize = 12.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Action("Kapat", Modifier.weight(1f)) { manualDialog = false }
                    Action("Ekle", Modifier.weight(1f), primary = true) {
                        if (activity.addManual(input)) { input = ""; manualDialog = false } else error = activity.statusMessage
                    }
                }
            }
        }
    }
    @Composable private fun Camera(modifier: Modifier, active: Boolean, feedback: Boolean, resolution: String, warning: Boolean) {
        Box(modifier.clip(RoundedCornerShape(22.dp)).background(Color(0xFF181C23))) {
            AndroidView(factory = { activity.createPreview() }, modifier = Modifier.fillMaxSize())
            if (!active) Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("▥", color = Color.White, fontSize = 30.sp)
                Text("Kargo seçin, kamerayı açın", color = Color(0xFFBEC6D4), fontSize = 13.sp)
            }
            Text(resolution, Modifier.align(Alignment.BottomStart).background(Color(0x99000000)).padding(8.dp), color = Color.White, fontSize = 10.sp)
            if (warning) Text("Zaten okundu · Yeniden eklenmedi", Modifier.align(Alignment.TopCenter).padding(8.dp).background(Color(0xFF6B4500), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 8.dp), color = Color(0xFFFFD783), fontSize = 12.sp, maxLines = 2)
            if (feedback) {
                Box(Modifier.fillMaxSize().border(3.dp, Color(0xFF20C35A), RoundedCornerShape(22.dp)))
                Text("✓", Modifier.align(Alignment.TopEnd).padding(12.dp).background(Color(0xFF169B55), RoundedCornerShape(12.dp)).padding(7.dp), color = Color.White, fontSize = 24.sp)
            }
        }
    }
    @Composable private fun ListPanel(modifier: Modifier, rows: List<CargoModel.Item>, scroll: androidx.compose.foundation.lazy.LazyListState) {
        val model = activity.model
        val colors = MiuixTheme.colorScheme
        Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                for (id in CargoModel.NAMES.indices) {
                    Button(onClick = { activity.selectTab(id) }, minWidth = 0.dp, minHeight = 34.dp, insideMargin = PaddingValues(horizontal = 10.dp, vertical = 7.dp), colors = if(model.active == id) ButtonDefaults.buttonColorsPrimary() else ButtonDefaults.buttonColors()) {
                        Text("${CargoModel.NAMES[id]} ${model.count(id)}", fontSize = 11.sp)
                    }
                }
            }
            Text("${CargoModel.NAMES[model.active]} · ${rows.size} barkod · Toplam ${model.items.size}", fontSize = 11.sp, color = colors.onSurfaceVariantSummary)
            Card(Modifier.weight(1f).fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
                if (rows.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Bu kargoda henüz barkod yok", color = colors.onSurfaceVariantSummary, fontSize = 13.sp)
                } else LazyColumn(Modifier.fillMaxSize(), state = scroll) {
                    items(rows, key = { "${it.cargo}:${it.code}" }) { item ->
                        Row(Modifier.fillMaxWidth().background(if(item.quantity > 1) Color(0x22FF3636) else Color.Transparent).padding(horizontal = 12.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(item.code + if(item.quantity > 1) " (${item.quantity} adet)" else "", Modifier.weight(1f), color = if(item.quantity > 1) Color(0xFFCE3434) else colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Action("Sil") { activity.removeItem(item) }
                        }
                    }
                }
            }
        }
    }
}
