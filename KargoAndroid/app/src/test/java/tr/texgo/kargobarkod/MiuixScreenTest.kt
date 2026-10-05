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
        compose.onNodeWithText("Barkodu").assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Open camera").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText("Tex", useUnmergedTree = true).performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("Open camera").assertIsEnabled()
        compose.onNodeWithText("Add manually").performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.onNode(hasSetTextAction()).performTextInput("7312345678")
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("Add", useUnmergedTree = true).performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("7312345678").assertIsDisplayed()
        compose.onNodeWithText("Add manually").performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.onNode(hasSetTextAction()).performTextInput("7312345678")
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("Add", useUnmergedTree = true).performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("7312345678 (2 pcs)").assertIsDisplayed()
        compose.onNodeWithText("Undo").performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("7312345678").assertIsDisplayed()
        compose.onNodeWithText("Aras", useUnmergedTree = true).performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("Add manually").performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.onNode(hasSetTextAction()).performTextInput("ZZ12345678")
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("Add", useUnmergedTree = true).performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("ZZ12345678").assertIsDisplayed()
        compose.onNodeWithText("Tex 1", useUnmergedTree = true).performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("7312345678").assertIsDisplayed()
        compose.onNodeWithText("Delete").performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("7312345678").assertDoesNotExist()
        compose.onNodeWithText("Undo").performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("7312345678").assertIsDisplayed()
        compose.onNodeWithText("Save TXT").assertIsEnabled()
        compose.onNodeWithText("Finish / Excel").assertIsEnabled()
        compose.onNodeWithText("Share").assertIsEnabled()
        compose.onNodeWithText("Clear").performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("Clear all lists?").assertIsDisplayed()
        compose.onNodeWithText("Cancel", useUnmergedTree = true).performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("7312345678").assertIsDisplayed()
        screenshot("miuix-portrait.png")
    }
    @Test
    @Config(qualifiers = "w891dp-h411dp-land-night-mdpi")
    fun landscapeDarkKeepsCameraListAndActionsVisible() {
        compose.onNodeWithText("Barkodu").assertIsDisplayed()
        compose.runOnIdle {
            compose.activity.selectCargo(3)
            compose.activity.addManual("7212345678")
        }
        compose.onNodeWithText("7212345678").assertIsDisplayed()
        compose.onNodeWithText("Select a carrier, then open the camera").assertIsDisplayed()
        compose.onNodeWithText("Excel").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("Undo").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("Camera").assertIsDisplayed().assertIsEnabled()
        screenshot("miuix-landscape.png")
    }

    @Test fun repeatWarningIsOptionalAndDoesNotIncreaseQuantity() {
        compose.onNodeWithText("Barkodu").assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle {
            compose.activity.selectCargo(2)
            compose.activity.handleDecodedBarcode("7312345678", 0)
            compose.activity.handleDecodedBarcode("7312345678", 800)
            compose.activity.handleDecodedBarcode("7312345678", 900)
        }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithText("Already scanned · Not added again").assertIsDisplayed()
        screenshot("repeat-warning.png")
        compose.onNodeWithText("7312345678").assertIsDisplayed()
        compose.runOnIdle { org.junit.Assert.assertEquals(1, compose.activity.model.items[0].quantity) }
        compose.onNodeWithText("Settings").performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.onNode(isToggleable()).assertIsOn().performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.onNode(isToggleable()).assertIsOff()
        screenshot("repeat-settings.png")
        compose.onNodeWithText("OK", useUnmergedTree = true).performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.runOnIdle { compose.activity.handleDecodedBarcode("7312345678", 2000) }
        compose.onNodeWithText("Already scanned · Not added again").assertDoesNotExist()
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
        compose.onNodeWithText("7312345678 (2 pcs)").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("7312345678 (2 pcs)").assertIsDisplayed()
        compose.onNodeWithText("Settings").performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.onNode(isToggleable()).assertIsOff()
    }

    private fun screenshot(name: String) {
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(image))
            File("../$name").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

}


