package com.omerceren.barkodu

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import android.graphics.Canvas
import android.graphics.Bitmap
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],qualifiers="w411dp-h891dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MiuixScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun settle(){androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeBy(500);compose.waitForIdle()}
    private fun nav(name:String){compose.onNodeWithText(name).performClick();settle()}
    private fun manual(code:String){
        compose.onNodeWithContentDescription("Add manually").performClick();settle()
        compose.onNode(hasSetTextAction()).performTextInput(code)
        compose.onNodeWithText("Add",useUnmergedTree=true).performClick();settle()
    }
    @Test fun carrierTabsShowCountsAndKeepScannerSelectionIndependent(){
        compose.runOnIdle {compose.activity.selectCargo(2);compose.activity.addManual("731234")};settle()
        for(name in CargoModel.NAMES) compose.onNodeWithContentDescription("$name, ${if(name=="TEX") 1 else 0} barcodes").assertIsDisplayed()
        compose.onNodeWithContentDescription("ARAS, 0 barcodes").performClick();settle()
        compose.runOnIdle {assertEquals(3,compose.activity.model.active);assertEquals(2,compose.activity.model.selected)}
        compose.onNodeWithText("731234").assertDoesNotExist()
        compose.onNodeWithContentDescription("TEX, 1 barcodes").performClick();settle()
        compose.onNodeWithText("731234").assertIsDisplayed();screenshot("carrier-tabs.png")
    }
    @Test fun swipePagesAndNavigationKeepRecordsAndCameraStopped(){
        compose.runOnIdle {compose.activity.selectCargo(2);compose.activity.addManual("731234")};settle()
        compose.onNodeWithTag("pages").performTouchInput {swipe(androidx.compose.ui.geometry.Offset(width*0.9f,height*0.85f),androidx.compose.ui.geometry.Offset(width*0.1f,height*0.85f),350)};settle()
        compose.runOnIdle {assertEquals(1,compose.activity.page);assertFalse(compose.activity.isCameraActive)}
        compose.onNodeWithText("Excel").assertIsDisplayed()
        compose.onNodeWithTag("pages").performTouchInput {swipe(androidx.compose.ui.geometry.Offset(width*0.9f,height*0.85f),androidx.compose.ui.geometry.Offset(width*0.1f,height*0.85f),350)};settle()
        compose.runOnIdle {assertEquals(2,compose.activity.page)}
        compose.onNodeWithTag("pages").performTouchInput {swipe(androidx.compose.ui.geometry.Offset(width*0.1f,height*0.85f),androidx.compose.ui.geometry.Offset(width*0.9f,height*0.85f),350)};settle()
        compose.runOnIdle {assertEquals(1,compose.activity.page)}
        nav("Scan");compose.onNodeWithText("731234").assertIsDisplayed()
        compose.mainClock.autoAdvance=false
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("Settings").performClick()
        record("page-navigation",12)
        compose.mainClock.autoAdvance=true;settle()
        compose.runOnIdle {assertEquals(2,compose.activity.page)}
    }
    @Test fun themeDropdownPersistsAndChangesAllSurfaces(){
        nav("Settings")
        compose.onNodeWithText("Theme").performClick();settle()
        compose.onNodeWithText("Black",useUnmergedTree=true).performClick();settle()
        compose.runOnIdle {assertEquals(1,compose.activity.themeMode)}
        screenshot("settings-black.png")
        compose.onNodeWithText("Theme").performClick();settle()
        compose.onNodeWithText("White",useUnmergedTree=true).performClick();settle()
        screenshot("settings-white.png")
        compose.activityRule.scenario.recreate();settle()
        compose.runOnIdle {assertEquals(2,compose.activity.themeMode)}
        nav("Scan");screenshot("scan-white.png")
        compose.onNodeWithTag("pages").performTouchInput {swipe(androidx.compose.ui.geometry.Offset(width*0.9f,height*0.85f),androidx.compose.ui.geometry.Offset(width*0.1f,height*0.85f),350)};settle()
        screenshot("save-white.png")
        compose.onNodeWithTag("pages").performTouchInput {swipe(androidx.compose.ui.geometry.Offset(width*0.9f,height*0.85f),androidx.compose.ui.geometry.Offset(width*0.1f,height*0.85f),350)};settle()
        compose.onNodeWithText("About").performClick();settle();screenshot("about-white.png")
    }
    @Test fun spinnerManualValidationRepeatActionAndPages(){
        compose.onNodeWithText("Select carrier").performClick();settle()
        compose.onNode(hasText("TEX") and hasAnyAncestor(hasClickAction()) and !hasAnyAncestor(hasContentDescription("TEX, 0 barcodes")),useUnmergedTree=true).performClick();settle()
        compose.runOnIdle {assertEquals(2,compose.activity.model.selected);assertEquals(0,compose.activity.model.active)}
        compose.onNodeWithContentDescription("Add manually").performClick();settle()
        compose.onNodeWithText("Add",useUnmergedTree=true).performClick();settle()
        compose.onNodeWithText("Barcode is required.").assertIsDisplayed()
        screenshot("manual-error.png")
        compose.onNode(hasSetTextAction()).performTextInput("7312345678")
        screenshot("bottom-sheet.png")
        compose.onNodeWithText("Add",useUnmergedTree=true).performClick();settle()
        compose.onNodeWithText("7312345678").assertIsDisplayed()
        screenshot("scan.png")
        manual("7312345678")
        compose.onAllNodesWithText("7312345678").assertCountEquals(1)
        compose.onNodeWithText("Yine ekle").assertIsDisplayed()
        screenshot("repeat-snackbar.png")
        compose.onNodeWithText("Yine ekle").performClick();settle()
        compose.runOnIdle {val activity=compose.activity;activity.confirmRepeat(1);assertEquals(2,activity.model.items.size)};settle()
        compose.onAllNodesWithText("7312345678").assertCountEquals(2)
        nav("Save");compose.onNodeWithText("Excel").assertIsDisplayed();screenshot("save.png")
        nav("Settings");compose.onNodeWithText("v0.1-beta").assertIsDisplayed();screenshot("settings.png")
        compose.onNodeWithText("About").performClick();settle()
        compose.onNodeWithText("Ömer tarafından yapılmıştır.").assertIsDisplayed();screenshot("about.png")
        compose.onNodeWithText("Third-party Licenses").performClick();settle()
        compose.onNodeWithText("Third-party Licenses",substring=true).assertExists()
    }
    @Test fun scanReentryCloseAndTimeoutDoNotAdd(){
        compose.runOnIdle {
            val a=compose.activity;a.selectCargo(2);a.handleDecodedBarcode("731234",0)
            for(t in 100..2000 step 100)a.handleDecodedBarcode("731234",t.toLong())
            assertNull(a.pendingRepeat);assertEquals(1,a.model.items.size)
            a.handleDecodedBarcode(null,2100);a.handleDecodedBarcode(null,2600);a.handleDecodedBarcode("731234",2601)
            val id=a.pendingRepeat!!.id
            a.handleDecodedBarcode("731234",3500);assertEquals(id,a.pendingRepeat!!.id)
            a.closeRepeat(id);a.handleDecodedBarcode("731234",3600);assertNull(a.pendingRepeat);assertEquals(1,a.model.items.size)
            a.addManual("731234");val newId=a.pendingRepeat!!.id;a.closeRepeat(id);assertEquals(newId,a.pendingRepeat!!.id)
        }
        compose.onNodeWithText("Close").performClick();settle()
        compose.runOnIdle {assertEquals(1,compose.activity.model.items.size);compose.activity.addManual("731234")}
        org.robolectric.shadows.ShadowLooper.idleMainLooper(11,java.util.concurrent.TimeUnit.SECONDS)
        settle()
        compose.runOnIdle {assertNull(compose.activity.pendingRepeat);assertEquals(1,compose.activity.model.items.size)}
    }
    @Test fun legacyMigrationSwipeAndTargetedUndoAcrossNewScan(){
        compose.runOnIdle {
            compose.activity.getPreferences(android.content.Context.MODE_PRIVATE).edit().putString("state","""{"selected":2,"items":[{"code":"7312345678","cargo":2,"quantity":2}]}""").commit()
        }
        compose.activityRule.scenario.recreate();settle()
        compose.onAllNodesWithText("7312345678").assertCountEquals(2)
        val firstId=compose.activity.model.items[0].id
        compose.onNodeWithTag("barcode-$firstId").performTouchInput {swipe(center,center+androidx.compose.ui.geometry.Offset(-35f,0f))};settle()
        compose.onAllNodesWithText("7312345678").assertCountEquals(2)
        compose.onNodeWithTag("barcode-$firstId").performTouchInput {swipeLeft()};settle()
        compose.onAllNodesWithText("7312345678").assertCountEquals(1)
        compose.runOnIdle {compose.activity.addManual("7398765432")}
        compose.onNodeWithText("Undo").performClick();settle()
        compose.onAllNodesWithText("7312345678").assertCountEquals(2)
        compose.onNodeWithText("7398765432").assertIsDisplayed()
        nav("Save");nav("Settings");nav("Scan")
        compose.activityRule.scenario.recreate();settle()
        compose.onAllNodesWithText("7312345678").assertCountEquals(2)
        compose.runOnIdle {assertEquals(2,compose.activity.model.selected)}
    }
    @Test @Config(qualifiers="w891dp-h411dp-land-night-mdpi") fun landscapeKeepsScanUsable(){
        compose.runOnIdle {compose.activity.selectCargo(3);compose.activity.addManual("7212345678")};settle()
        compose.onNodeWithText("Tap to scan").assertIsDisplayed()
        compose.onNodeWithText("7212345678").assertIsDisplayed();screenshot("scan-landscape.png")
        nav("Save");compose.onNodeWithText("Excel").assertIsDisplayed()
    }
    @Test fun disabledWarningAndClearUndo(){
        compose.runOnIdle {val a=compose.activity;a.selectCargo(2);a.setRepeatWarningEnabled(false);a.addManual("731234");a.addManual("731234");assertEquals(2,a.model.items.size)}
        nav("Settings");compose.onNode(isToggleable()).assertIsOff()
        compose.activityRule.scenario.recreate();settle();compose.onNode(isToggleable()).assertIsOff()
        nav("Save");compose.onNodeWithText("More actions").performClick();settle();compose.onNodeWithText("Clear all…").performClick();settle()
        compose.onNodeWithText("Clear",useUnmergedTree=true).performClick();settle()
        compose.onNodeWithText("Your saved list is empty").assertIsDisplayed()
        compose.onNodeWithText("Undo").performClick();settle()
        compose.runOnIdle {assertEquals(2,compose.activity.model.items.size)}
    }
    @Test fun listScrollAndRotationPreserveSelectionAndRecords(){
        compose.runOnIdle {
            val a=compose.activity;a.selectCargo(2)
            repeat(100){a.addManual("73${100000+it}")}
        };settle()
        compose.onNodeWithText("73100099").performTouchInput {swipeUp()};settle()
        val first=compose.onAllNodes(hasText("731000",substring=true)).fetchSemanticsNodes().map {it.config[androidx.compose.ui.semantics.SemanticsProperties.Text].toString()}
        nav("Save");nav("Scan")
        val after=compose.onAllNodes(hasText("731000",substring=true)).fetchSemanticsNodes().map {it.config[androidx.compose.ui.semantics.SemanticsProperties.Text].toString()}
        assertEquals(first,after)
        compose.runOnIdle {
            val config=android.content.res.Configuration(compose.activity.resources.configuration)
            config.orientation=android.content.res.Configuration.ORIENTATION_LANDSCAPE
            compose.activity.onConfigurationChanged(config)
            assertEquals(100,compose.activity.model.items.size);assertEquals(2,compose.activity.model.selected)
        }
    }
    @Test @Config(sdk=[28]) fun oldAndroidAndDisabledMotionKeepActionsAvailable(){
        compose.runOnIdle {
            android.provider.Settings.Global.putFloat(compose.activity.contentResolver,"animator_duration_scale",0f)
            compose.activity.selectCargo(0);compose.activity.addManual("001234")
        };settle()
        compose.onNodeWithText("Tap to scan").assertIsDisplayed()
        compose.onNodeWithText("001234").assertIsDisplayed()
        nav("Save");nav("Settings");nav("Scan")
        compose.onNodeWithText("001234").assertIsDisplayed()
    }
    @Test fun exportUsesSystemPickerAndCopiesDuplicateRows(){
        compose.runOnIdle {val a=compose.activity;a.selectCargo(2);a.setRepeatWarningEnabled(false);a.addManual("001234");a.addManual("001234");a.exportAction("txt",false)}
        var intent:android.content.Intent?=null
        compose.waitUntil(10000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            if(intent==null) intent=org.robolectric.Shadows.shadowOf(compose.activity).nextStartedActivity
            intent!=null
        }
        assertEquals(android.content.Intent.ACTION_CREATE_DOCUMENT,intent!!.action)
        assertEquals("text/plain",intent!!.type)
        val output=File(compose.activity.cacheDir,"export-test.txt")
        compose.runOnIdle {compose.activity.onActivityResult(20,android.app.Activity.RESULT_OK,android.content.Intent().setData(android.net.Uri.fromFile(output)))}
        compose.waitUntil(10000) {org.robolectric.shadows.ShadowLooper.idleMainLooper();compose.activity.notice.startsWith("File saved")}
        assertEquals(2,output.readText().lineSequence().count {it=="001234"})
        assertEquals(2,compose.activity.model.items.size)
    }
    @Test fun recordTransitionsFromRenderedUi(){
        compose.runOnIdle {compose.activity.selectCargo(2);compose.activity.addManual("731234")};settle()
        compose.mainClock.autoAdvance=false
        // Robolectric has no camera hardware. Drive its active preview state explicitly;
        // stopCamera and the Compose transition are real. This is a UI-state capture only.
        compose.runOnIdle {
            val field=MainActivity::class.java.getDeclaredField("running");field.isAccessible=true;field.setBoolean(compose.activity,true)
            compose.activity.navigate(0)
        }
        record("camera-open",12)
        compose.onNodeWithContentDescription("Camera on. Tap to stop scanning").performClick()
        record("camera-close",12)
        compose.onNodeWithContentDescription("Add manually").performClick()
        record("bottom-sheet-open",12)
        compose.onNode(hasSetTextAction()).performTextInput("731234")
        compose.onNodeWithText("Add",useUnmergedTree=true).performClick()
        record("repeat-snackbar",12)
        compose.mainClock.autoAdvance=true;settle();compose.mainClock.autoAdvance=false
        compose.onNodeWithText("Close").performClick();record("repeat-close",12)
        compose.mainClock.autoAdvance=true;settle();compose.mainClock.autoAdvance=false
        compose.runOnIdle {assertNull("Close must consume pending event",compose.activity.pendingRepeat)}
        val id=compose.activity.model.items[0].id
        val card=compose.onNodeWithTag("barcode-$id")
        card.performTouchInput {down(center)}
        for(frame in 0..5){card.performTouchInput {moveBy(androidx.compose.ui.geometry.Offset(-24f,0f),80)};record("swipe",1,frame)}
        card.performTouchInput {up()};record("swipe-delete",12)
        compose.runOnIdle {assertEquals(0,compose.activity.model.items.size);assertTrue("Deletion must offer Undo",compose.activity.isNoticeUndoAvailable)}
        compose.mainClock.autoAdvance=true;settle()
        compose.mainClock.autoAdvance=false
        compose.onNodeWithText("Undo").performClick();record("undo",12)
        compose.mainClock.autoAdvance=true;settle()
        compose.onNodeWithText("731234").assertIsDisplayed()
    }
    private fun record(name:String,count:Int,start:Int=0){
        repeat(count){frame->
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
            compose.mainClock.advanceTimeBy(32)
            screenshot("frames/$name-${(frame+start).toString().padStart(3,'0')}.png")
        }
    }
    private fun screenshot(name:String){compose.runOnIdle {
        val view=compose.activity.window.decorView
        val image=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
        view.draw(Canvas(image));val folder=File("../build/evidence");folder.mkdirs()
        val target=File(folder,name);target.parentFile!!.mkdirs()
        target.outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)}
    }}
}


