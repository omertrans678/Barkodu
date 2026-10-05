package tr.texgo.kargobarkod

import androidx.compose.ui.test.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import android.graphics.Canvas
import android.graphics.Bitmap
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MiuixScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun cargoManualDuplicateUndoAndExport() {
        compose.onNodeWithText("Kargo Barkod").assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Kamerayı aç").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText("Tex", useUnmergedTree = true).performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("Kamerayı aç").assertIsEnabled()
        compose.onNodeWithText("Elle ekle").performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.onNode(hasSetTextAction()).performTextInput("7312345678")
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("Ekle", useUnmergedTree = true).performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("7312345678").assertIsDisplayed()
        compose.onNodeWithText("Elle ekle").performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.onNode(hasSetTextAction()).performTextInput("7312345678")
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("Ekle", useUnmergedTree = true).performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("7312345678 (2 adet)").assertIsDisplayed()
        compose.onNodeWithText("Geri al").performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("7312345678").assertIsDisplayed()
        compose.onNodeWithText("Aras", useUnmergedTree = true).performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("Elle ekle").performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.onNode(hasSetTextAction()).performTextInput("ZZ12345678")
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("Ekle", useUnmergedTree = true).performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("ZZ12345678").assertIsDisplayed()
        compose.onNodeWithText("Tex 1", useUnmergedTree = true).performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("7312345678").assertIsDisplayed()
        compose.onNodeWithText("Sil").performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("7312345678").assertDoesNotExist()
        compose.onNodeWithText("Geri al").performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("7312345678").assertIsDisplayed()
        compose.onNodeWithText("TXT kaydet").assertIsEnabled()
        compose.onNodeWithText("Bitir / Excel").assertIsEnabled()
        compose.onNodeWithText("Paylaş").assertIsEnabled()
        compose.onNodeWithText("Temizle").performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("Listeleri temizle?").assertIsDisplayed()
        compose.onNodeWithText("Vazgeç", useUnmergedTree = true).performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("7312345678").assertIsDisplayed()
        screenshot("miuix-portrait.png")
    }
    @Test
    @Config(qualifiers = "w891dp-h411dp-land-night-mdpi")
    fun landscapeDarkKeepsCameraListAndActionsVisible() {
        compose.onNodeWithText("Kargo Barkod").assertIsDisplayed()
        compose.runOnIdle {
            compose.activity.selectCargo(3)
            compose.activity.addManual("7212345678")
        }
        compose.onNodeWithText("7212345678").assertIsDisplayed()
        compose.onNodeWithText("Kargo seçin, kamerayı açın").assertIsDisplayed()
        compose.onNodeWithText("Excel").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("Geri al").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("Kamera").assertIsDisplayed().assertIsEnabled()
        screenshot("miuix-landscape.png")
    }

    @Test fun repeatWarningIsOptionalAndDoesNotIncreaseQuantity() {
        compose.onNodeWithText("Kargo Barkod").assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle {
            compose.activity.selectCargo(2)
            compose.activity.handleDecodedBarcode("7312345678", 0)
            compose.activity.handleDecodedBarcode("7312345678", 800)
            compose.activity.handleDecodedBarcode("7312345678", 900)
        }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithText("Zaten okundu · Yeniden eklenmedi").assertIsDisplayed()
        screenshot("repeat-warning.png")
        compose.onNodeWithText("7312345678").assertIsDisplayed()
        compose.runOnIdle { org.junit.Assert.assertEquals(1, compose.activity.model.items[0].quantity) }
        compose.onNodeWithText("Ayarlar").performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.onNode(isToggleable()).assertIsOn().performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.onNode(isToggleable()).assertIsOff()
        screenshot("repeat-settings.png")
        compose.onNodeWithText("Tamam", useUnmergedTree = true).performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.runOnIdle { compose.activity.handleDecodedBarcode("7312345678", 2000) }
        compose.onNodeWithText("Zaten okundu · Yeniden eklenmedi").assertDoesNotExist()
        compose.onNodeWithText("7312345678").assertIsDisplayed()
        compose.runOnIdle {
            compose.activity.handleDecodedBarcode(null, 3000)
            compose.activity.handleDecodedBarcode(null, 3500)
            compose.activity.handleDecodedBarcode("7312345678", 3501)
            Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeBy(100)
        compose.runOnIdle { org.junit.Assert.assertEquals(2, compose.activity.model.items[0].quantity) }
        screenshot("repeat-second-order.png")
        compose.onNodeWithText("7312345678 (2 adet)").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("7312345678 (2 adet)").assertIsDisplayed()
        compose.onNodeWithText("Ayarlar").performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.onNode(isToggleable()).assertIsOff()
    }

    private fun screenshot(name: String) {
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(image))
            File("C:/MyData/TexGo/KargoAndroid/$name").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

}


