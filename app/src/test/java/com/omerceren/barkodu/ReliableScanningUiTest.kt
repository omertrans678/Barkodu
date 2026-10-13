package com.omerceren.barkodu

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Vibrator
import android.provider.Settings
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],qualifiers="w411dp-h891dp-port-mdpi")
class ReliableScanningUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun settle() {androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeBy(500);compose.waitForIdle()}
    private fun nav(name:String) {compose.onNodeWithContentDescription(name).performClick();settle()}
    private fun absence(a:MainActivity,start:Long) {repeat(7){a.handleCameraFrame(null,start+it*250,true)}}

    @Test fun scanDelayDoesNotDiscardNextCandidateOrDelayAbsenceEvidence() {
        compose.runOnIdle {
            val a=compose.activity;a.selectCargo(2);a.setScanDelayMs(500)
            listOf(0L,60L,120L).forEach {a.handleCameraFrame("731111",it)}
            assertEquals(1,a.model.items.size)
            listOf(180L,240L,300L,600L).forEach {a.handleCameraFrame("732222",it)}
            assertEquals(1,a.model.items.size)
            a.handleCameraFrame("732222",620);assertEquals(2,a.model.items.size)
            a.setScanDelayMs(2000);absence(a,1000)
            listOf(2620L,2680L,2740L).forEach {a.handleCameraFrame("732222",it)}
            assertNotNull(a.pendingRepeat);assertEquals(2,a.model.items.size)
        }
    }

    @Test fun successHapticOnlyAfterCommittedAdditionAndWarningsCanReappear() {
        compose.runOnIdle {
            val a=compose.activity;a.selectCargo(0)
            val vibrator=a.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            val shadow=Shadows.shadowOf(vibrator);shadow.setHasVibrator(true)
            a.handleCameraFrame("4161215728",0);a.handleCameraFrame("4161215728",60)
            assertTrue(a.model.items.isEmpty());assertFalse(shadow.isVibrating)
            a.handleCameraFrame("4161215728",120);assertEquals(1,a.model.items.size);assertTrue(shadow.isVibrating)
            vibrator.cancel()
            for(t in 180..1800 step 60)a.handleCameraFrame("4161215728",t.toLong())
            assertNull(a.pendingRepeat);assertFalse(shadow.isVibrating)
            // Moving/unstable empty camera frames do not release the presentation lock.
            repeat(12){a.handleCameraFrame(null,2000+it*250L,false)}
            a.handleCameraFrame("4161215728",5200);a.handleCameraFrame("4161215728",5260);a.handleCameraFrame("4161215728",5320)
            assertNull(a.pendingRepeat);assertFalse(shadow.isVibrating)
            absence(a,6000)
            a.handleCameraFrame("4161215728",7800);a.handleCameraFrame("4161215728",7860);a.handleCameraFrame("4161215728",7920)
            val first=a.pendingRepeat!!.id;assertEquals(2,a.scanFeedback);assertFalse(shadow.isVibrating)
            a.closeRepeat(first);assertNull(a.pendingRepeat)
            a.handleCameraFrame("4161215728",8000);assertNull(a.pendingRepeat)
            absence(a,9000)
            a.handleCameraFrame("4161215728",10800);a.handleCameraFrame("4161215728",10860);a.handleCameraFrame("4161215728",10920)
            val second=a.pendingRepeat!!.id;assertTrue(second>first);assertFalse(shadow.isVibrating)
            a.confirmRepeat(second);assertEquals(2,a.model.items.size);assertTrue(shadow.isVibrating)
            vibrator.cancel();a.confirmRepeat(second);assertEquals(2,a.model.items.size);assertFalse(shadow.isVibrating)
        }
    }
    @Test fun partialLengthCannotBeSavedAndOtherCarrierLengthsStillWork() {
        compose.runOnIdle {
            val a=compose.activity;a.selectCargo(0)
            repeat(4){a.handleCameraFrame("41612157278",it*100L)}
            assertTrue(a.model.items.isEmpty());assertNull(a.pendingRepeat)
            repeat(3){a.handleCameraFrame("4161215728",500+it*100L)};assertEquals("4161215728",a.model.items.single().code)
            repeat(3){a.handleCameraFrame("41612157278",1000+it*100L)}
            repeat(3){a.handleCameraFrame("4161215728",1500+it*100L)}
            assertNull("Rejected partial variants must not re-arm duplicates",a.pendingRepeat)
            assertFalse(a.addManual("41712157278"))
            a.selectCargo(2);assertTrue(a.addManual("731234"));assertEquals(2,a.model.items.size)
            a.selectCargo(3);assertTrue(a.addManual("721234567890"));assertEquals(3,a.model.items.size)
        }
    }
    @Test fun cargoDropdownSelectionAndClearDoNotDeleteRecords() {
        compose.onNodeWithText("Select carrier").performClick();settle()
        compose.onAllNodesWithText("ARAS").onLast().assertIsDisplayed()
        compose.onAllNodesWithText("DHL").onLast().performClick();settle()
        compose.runOnIdle {assertEquals(0,compose.activity.model.selected);compose.activity.addManual("4161215728")};settle()
        compose.onNodeWithTag("scan-carrier").performClick();settle()
        compose.onAllNodesWithText("Clear selection").onLast().performClick();settle()
        compose.runOnIdle {assertEquals(-1,compose.activity.model.selected);assertEquals(1,compose.activity.model.items.size);assertFalse(compose.activity.isCameraActive)}
        compose.activityRule.scenario.recreate();settle()
        compose.runOnIdle {assertEquals(-1,compose.activity.model.selected);assertEquals(1,compose.activity.model.items.size)}
    }
    @Test fun intermittentActualDecoderFramesReachCommittedAddition() {
        compose.runOnIdle {
            val a=compose.activity;a.selectCargo(0)
            val image=com.google.zxing.MultiFormatWriter().encode("4161215728",com.google.zxing.BarcodeFormat.CODE_128,800,240)
            val pixels=ByteArray(image.width*image.height){i->if(image[i%image.width,i/image.width])0 else 255.toByte()}
            val decoder=CameraBarcodeDecoder()
            repeat(5){i->a.handleCameraFrame(if(i%2==0)decoder.decode(pixels,800,240) else null,i*100L,false)}
            assertEquals("4161215728",a.model.items.single().code)
            repeat(5){i->a.handleCameraFrame(if(i%2==0)decoder.decode(pixels,800,240) else null,500+i*100L,false)}
            assertEquals(1,a.model.items.size);assertNull(a.pendingRepeat)
        }
    }
    @Test fun longListScrollsToEveryEntryAndTabsHaveReferenceInsets() {
        compose.runOnIdle {
            val a=compose.activity;a.model.selected=2
            repeat(1000){a.model.addForCarrier("73${100000+it}",2)}
            a.selectTab(2)
        };settle()
        val list=compose.activity.model.visible()
        compose.onNodeWithTag("scan-list").performScrollToIndex(999);settle()
        compose.onNodeWithTag("barcode-${list.last().id}").assertIsDisplayed()
        compose.onNodeWithTag("scan-list").performTouchInput{swipeDown()};settle()
        compose.onNodeWithTag("barcode-${list.last().id}").assertDoesNotExist()
        val box=compose.onNodeWithTag("carrier-tabs-card").fetchSemanticsNode().boundsInRoot
        val tabs=compose.onNodeWithTag("carrier-tabs-inset").fetchSemanticsNode().boundsInRoot
        assertTrue(tabs.left-box.left>=20f);assertTrue(box.right-tabs.right>=20f)
    }
    @Test fun delayAndWhitelistPersistThroughUiAndRecreation() {
        nav("Settings")
        compose.onNodeWithText("Scan delay").performScrollTo().performClick();settle()
        compose.onNode(hasSetTextAction()).performTextReplacement("500")
        compose.onNodeWithText("Confirm",useUnmergedTree=true).performClick();settle()
        compose.onNodeWithText("Carrier whitelist").performScrollTo().performClick();settle()
        compose.onNode(hasSetTextAction()).performTextReplacement("416, 417, HTS")
        compose.onAllNodesWithText("Save",useUnmergedTree=true).onLast().performClick();settle()
        compose.activityRule.scenario.recreate();settle()
        compose.runOnIdle {
            val a=compose.activity;assertEquals(500,a.scanDelayMs);assertTrue(a.rules.matches("4171215728",0));assertFalse(a.rules.matches("4171215728",1))
            a.selectCargo(0);assertTrue(a.addManual("4171215728"));assertNull(a.pendingUnknown);assertEquals(1,a.model.items.size)
        }
    }
    @Test fun longPressMultiSelectionDeleteUndoAndSafeExit() {
        compose.runOnIdle {val a=compose.activity;a.selectCargo(2);repeat(3){a.addManual("73${111111+it}")}};settle()
        val ids=compose.activity.model.items.map {it.id}
        compose.onNodeWithTag("barcode-${ids[0]}").performTouchInput {longClick()};settle()
        compose.onNodeWithTag("selection-${ids[0]}",useUnmergedTree=true).assertIsOn()
        compose.onNodeWithTag("barcode-${ids[1]}").performClick();settle()
        compose.onNodeWithContentDescription("Delete selected").performClick();settle()
        compose.runOnIdle {assertEquals(listOf(ids[2]),compose.activity.model.items.map {it.id});assertFalse(compose.activity.selectionMode)}
        compose.onNodeWithText("Undo").performClick();settle()
        compose.runOnIdle {assertEquals(ids,compose.activity.model.items.map {it.id})}
        compose.activityRule.scenario.recreate();settle()
        compose.runOnIdle {assertEquals(ids,compose.activity.model.items.map {it.id});compose.activity.beginSelection(ids[0])};settle()
        compose.runOnIdle {compose.activity.onBackPressedDispatcher.onBackPressed();assertFalse(compose.activity.selectionMode);assertFalse(compose.activity.isFinishing)}
    }
    @Test fun independentScrollPositionsAndContextBoundsSurviveNavigation() {
        compose.runOnIdle {val a=compose.activity
            a.selectCargo(2);repeat(60){a.addManual("73${100000+it}")}
            a.selectCargo(3);repeat(60){a.addManual("72${100000+it}")}
        };settle()
        compose.onNodeWithTag("scan-list").performScrollToIndex(20);settle()
        val aras=compose.activity.model.items.filter {it.cargo==3}[20]
        compose.onNodeWithTag("barcode-${aras.id}").assertIsDisplayed()
        compose.onNode(hasText("TEX") and hasAnyAncestor(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.Role,androidx.compose.ui.semantics.Role.Tab)),useUnmergedTree=true).performClick();settle()
        compose.onNodeWithTag("scan-list").performScrollToIndex(10);settle()
        compose.onNode(hasText("ARAS") and hasAnyAncestor(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.Role,androidx.compose.ui.semantics.Role.Tab)),useUnmergedTree=true).performClick();settle()
        compose.onNodeWithTag("barcode-${aras.id}").assertIsDisplayed()
        val context=compose.onNodeWithTag("carrier-pages").fetchSemanticsNode().boundsInRoot
        val box=compose.onNodeWithTag("carrier-tabs-card").fetchSemanticsNode().boundsInRoot
        assertTrue(context.width<=box.width);assertTrue(context.left>=box.left&&context.right<=box.right)
        nav("Save");nav("Settings");nav("Scan")
        compose.onNodeWithTag("barcode-${aras.id}").assertIsDisplayed()
        compose.activityRule.scenario.recreate();settle()
        compose.onNodeWithTag("barcode-${aras.id}").assertIsDisplayed()
    }
    @Test fun noNetworkPermissionTelemetrySdkOrAutomaticBackupAndNoTopGap() {
        compose.runOnIdle {
            val a=compose.activity;val info=a.packageManager.getPackageInfo(a.packageName,PackageManager.GET_PERMISSIONS)
            assertFalse(info.requestedPermissions.orEmpty().contains("android.permission.INTERNET"));assertFalse(info.requestedPermissions.orEmpty().contains("android.permission.ACCESS_NETWORK_STATE"))
            assertEquals(0,a.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
            assertThrows(ClassNotFoundException::class.java){Class.forName("com.google.mlkit.vision.barcode.BarcodeScanning")}
            assertThrows(ClassNotFoundException::class.java){Class.forName("com.google.android.datatransport.runtime.TransportRuntime")}
        }
        val title=compose.onNode(hasText("Scan") and hasAnyAncestor(hasTestTag("pages")),useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        assertTrue("Blank toolbar must not consume the top",title.top<24f)
    }
    @Test fun aboutHonorsDisabledSystemAnimations() {
        compose.runOnIdle {Settings.Global.putFloat(compose.activity.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,0f)}
        compose.activityRule.scenario.recreate();settle();nav("Settings")
        compose.onNodeWithText("About").performScrollTo().performClick();settle()
        assertEquals(0f,compose.onNodeWithTag("about-screen").fetchSemanticsNode().boundsInRoot.left,0.01f)
        compose.onNodeWithContentDescription("Back").performClick();settle()
        compose.onNodeWithTag("about-screen").assertDoesNotExist()
    }
}
