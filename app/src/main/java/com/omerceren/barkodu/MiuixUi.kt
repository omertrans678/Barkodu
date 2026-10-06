package com.omerceren.barkodu

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.core.view.WindowCompat
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.pager.*
import kotlinx.coroutines.flow.drop
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperSwitch
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.*
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.extra.SuperSpinner
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import kotlin.math.abs
import kotlin.math.roundToInt

private val Panel: Color @Composable get() = MiuixTheme.colorScheme.surfaceContainer
private val Muted: Color @Composable get() = MiuixTheme.colorScheme.onSurfaceVariantSummary
private val Accent: Color @Composable get() = MiuixTheme.colorScheme.primary
private val Danger: Color @Composable get() = MiuixTheme.colorScheme.error
private val Shape = RoundedCornerShape(24.dp)
private object Motion {
    const val Fast = 180
    const val Normal = 260
    val Ease = FastOutSlowInEasing
    fun <T> tween() = tween<T>(Normal, easing = Ease)
    fun <T> spring() = spring<T>(dampingRatio = 0.9f, stiffness = 550f)
}
private val ScanIcon = MiuixIcons.Regular.Scan
private val SaveIcon = MiuixIcons.Regular.FileDownloads
private val SettingsIcon = MiuixIcons.Regular.Settings
private val DeleteIcon = MiuixIcons.Regular.Delete

class MiuixUi(private val activity: MainActivity) {
    private var revision by mutableIntStateOf(0)
    fun update() { revision++ }
    fun createView(): View = ComposeView(activity).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            val tick=revision
            val dark=when(activity.themeMode){1->true;2->false;else->isSystemInDarkTheme()}
            val colors=if(dark) darkColorScheme(surface=Color.Black,background=Color.Black,primary=Color(0xFF7CAEFF),
                surfaceContainer=Color(0xFF202020),secondaryContainer=Color(0xFF303030),onSecondaryContainer=Color.White,error=Color(0xFFFF8A80))
                else lightColorScheme(surface=Color(0xFFF7F7F7),background=Color(0xFFF7F7F7),primary=Color(0xFF2868CC),
                    surfaceContainer=Color.White,secondaryContainer=Color(0xFFF0F0F0),onSecondaryContainer=Color(0xFF242424))
            MiuixTheme(colors=colors) { App() }
        }
    }
    @Composable private fun Action(label:String, modifier:Modifier=Modifier, enabled:Boolean=true, onClick:()->Unit) {
        Button(onClick=onClick,modifier=modifier,enabled=enabled,minWidth=0.dp,minHeight=44.dp,
            colors=if(label=="Add") ButtonDefaults.buttonColorsPrimary() else ButtonDefaults.buttonColors(),
            insideMargin=PaddingValues(horizontal=12.dp,vertical=10.dp)) { Text(label,fontSize=14.sp) }
    }
    @Composable private fun App() {
        val tick=revision
        val model=activity.model
        val page=activity.page
        val pager=rememberPagerState(initialPage=page,pageCount={3})
        var requestedPage by remember {mutableStateOf<Int?>(null)}
        LaunchedEffect(page){
            if(pager.currentPage!=page || pager.currentPageOffsetFraction!=0f) pager.animateScrollToPage(page,animationSpec=Motion.tween())
            if(requestedPage==page)requestedPage=null
        }
        LaunchedEffect(pager){snapshotFlow {pager.settledPage}.drop(1).collect {if(requestedPage==null&&activity.page!=it)activity.navigate(it)}}
        LaunchedEffect(pager){snapshotFlow {pager.isScrollInProgress}.collect {if(it&&activity.isCameraActive)activity.pauseScanning()}}
        var about by rememberSaveable { mutableStateOf(false) }
        var license by rememberSaveable { mutableStateOf("") }
        var clear by rememberSaveable { mutableStateOf(false) }
        var manual by rememberSaveable { mutableStateOf(false) }
        var input by rememberSaveable { mutableStateOf("") }
        var error by rememberSaveable { mutableStateOf("") }
        val focus=remember { FocusRequester() }
        val keyboard=LocalSoftwareKeyboardController.current
        // Hoisted outside page transitions: tab navigation never resets list position.
        val lists=List(5) { rememberLazyListState() }
        val settingsScroll=rememberScrollState()
        val saveScroll=rememberScrollState()
        val aboutScroll=rememberScrollState()
        val behavior=MiuixScrollBehavior()
        val title=if(about) "About" else listOf("Scan","Save","Settings")[page]
        val scrollDistance = if(about) aboutScroll.value else if(page==0)
            lists[model.active].firstVisibleItemIndex*80+lists[model.active].firstVisibleItemScrollOffset
            else if(page==1) saveScroll.value else settingsScroll.value
        val barProgress by animateFloatAsState((scrollDistance/80f).coerceIn(0f,1f),Motion.tween(),label="Progressive title")
        val barColor=lerp(MiuixTheme.colorScheme.surface,Panel,barProgress)
        BackHandler(about) { if(license.isNotEmpty()) license="" else about=false }
        val pending=activity.pendingRepeat
        val repeatId=pending?.id ?: -1L
        val noticeId=activity.noticeId
        val snackbarData=remember(repeatId,noticeId,activity.notice) {
            object:SnackbarData {
                override val visuals=SnackbarVisuals(
                    if(pending!=null) "Already scanned · ${pending.code}" else activity.notice,
                    if(pending!=null) "Yine ekle" else if(activity.isNoticeUndoAvailable) "Undo" else null,
                    false,SnackbarDuration.Indefinite)
                override suspend fun dismiss(){if(pending!=null) activity.closeRepeat(repeatId) else activity.dismissNotice()}
                override suspend fun performAction(){if(pending!=null) activity.confirmRepeat(repeatId) else activity.undoNotice(noticeId)}
            }
        }
        val aboutDark=when(activity.themeMode){1->true;2->false;else->isSystemInDarkTheme()}
        val glow=if(about) {
            val transition=rememberInfiniteTransition(label="About background")
            val shift by transition.animateFloat(initialValue=0.15f,targetValue=0.85f,animationSpec=infiniteRepeatable(tween(18000,easing=LinearEasing),RepeatMode.Reverse),label="Background glow")
            shift
        } else 0.5f
        val aboutBrush=Brush.verticalGradient(if(aboutDark) listOf(Color(0xFF101B49),Color(0xFF102963),Color(0xFF281D48)) else listOf(Color(0xFFE8E6FF),Color(0xFFFFE2EF),Color(0xFFF5E8F2)))
        SideEffect {
            val first=if(about) (if(aboutDark) 0xFF101B49 else 0xFFE8E6FF).toInt() else (if(aboutDark) android.graphics.Color.BLACK else 0xFFF7F7F7.toInt())
            val last=if(about) (if(aboutDark) 0xFF281D48 else 0xFFF5E8F2).toInt() else first
            activity.window.setBackgroundDrawable(if(about) android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,intArrayOf(first,last)) else android.graphics.drawable.ColorDrawable(first))
            activity.window.statusBarColor=first;activity.window.navigationBarColor=last
            WindowCompat.getInsetsController(activity.window,activity.window.decorView).apply {
                isAppearanceLightStatusBars=!aboutDark;isAppearanceLightNavigationBars=!aboutDark
            }
        }
        Scaffold(modifier=Modifier.nestedScroll(behavior.nestedScrollConnection).then(if(about) Modifier.background(aboutBrush).drawBehind {
                drawRect(Brush.radialGradient(listOf(if(aboutDark) Color(0x443B60E2) else Color(0x55D8D7FF),Color.Transparent),center=Offset(size.width*glow,size.height*0.3f),radius=size.height*0.55f))
                drawRect(Brush.radialGradient(listOf(if(aboutDark) Color(0x44382D73) else Color(0x55FFBAD7),Color.Transparent),center=Offset(size.width*(1-glow),size.height*0.7f),radius=size.height*0.5f))
            } else Modifier),
            containerColor=if(about) Color.Transparent else MiuixTheme.colorScheme.surface,
            topBar={
                if(about) SmallTopAppBar(title=if(license.isEmpty()) "" else "License",color=Color.Transparent,titleColor=MiuixTheme.colorScheme.onSurface,
                    navigationIcon={IconButton(onClick={if(license.isNotEmpty()) license="" else about=false},modifier=Modifier.padding(horizontal=12.dp)) {Icon(MiuixIcons.Regular.Back,"Back",Modifier.size(26.dp))}})
                else if(page==0) SmallTopAppBar(title=title,color=barColor,titleColor=MiuixTheme.colorScheme.onSurface,scrollBehavior=behavior,
                    actions={IconButton(onClick={activity.pauseScanning();error="";manual=true},modifier=Modifier.padding(end=12.dp)) {Icon(MiuixIcons.Regular.Add,"Add manually",Modifier.size(26.dp),tint=Accent)}})
                else TopAppBar(title=title,color=barColor,scrollBehavior=behavior)
            },
            bottomBar={if(!about) NavigationBar(color=MiuixTheme.colorScheme.surface) {
                listOf(ScanIcon,SaveIcon,SettingsIcon).forEachIndexed { index,icon -> NavigationBarItem(selected=page==index,
                    onClick={if(index!=activity.page){requestedPage=index;activity.navigate(index)}},icon=if(page==index) listOf(MiuixIcons.Heavy.Scan,MiuixIcons.Heavy.FileDownloads,MiuixIcons.Heavy.Settings)[index] else icon,label=listOf("Scan","Save","Settings")[index])
                }
            }},
            snackbarHost={
                AnimatedVisibility(pending!=null||activity.notice.isNotEmpty(),enter=fadeIn(Motion.tween())+slideInVertically(Motion.spring()){it/2},exit=fadeOut(Motion.tween())) {
                    Row(Modifier.fillMaxWidth().padding(horizontal=8.dp).background(MiuixTheme.colorScheme.secondaryContainer,RoundedCornerShape(16.dp)).semantics {liveRegion=LiveRegionMode.Polite},verticalAlignment=Alignment.CenterVertically) {
                        Snackbar(snackbarData,Modifier.weight(1f),colors=SnackbarDefaults.snackbarColors(containerColor=MiuixTheme.colorScheme.secondaryContainer,actionContentColor=Accent))
                        Text("Close",Modifier.clickable {if(pending!=null) activity.closeRepeat(repeatId) else activity.dismissNotice()}.padding(horizontal=12.dp,vertical=18.dp),fontSize=12.sp,color=Muted)
                    }
                }
            }
        ) { insets ->
            Box(Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets).background(if(about) Color.Transparent else MiuixTheme.colorScheme.surface)) {
                val pageContent: @Composable (Int) -> Unit = { destination ->
                    val renderTick=revision
                    when(destination) {
                        0 -> Scan(lists[model.active])
                        1 -> Column(Modifier.fillMaxSize().verticalScroll(saveScroll).padding(horizontal=16.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                            Text("${model.items.size} barcodes ready",Modifier.padding(horizontal=16.dp),fontSize=14.sp,color=Muted)
                            if(model.items.isEmpty()) Empty("Your saved list is empty","Scan a barcode or add one manually.")
                            Card(Modifier.fillMaxWidth(),insideMargin=PaddingValues(0.dp)) {
                                SuperArrow(title="Excel",summary="One barcode per row",enabled=model.items.isNotEmpty(),onClick={activity.exportAction("xlsx",false)})
                                SuperArrow(title="TXT",summary="Plain text carrier list",enabled=model.items.isNotEmpty(),onClick={activity.exportAction("txt",false)})
                                SuperArrow(title="Share",summary="Send your list to another app",enabled=model.items.isNotEmpty(),onClick={activity.exportAction("txt",true)})
                            }
                            Card(Modifier.fillMaxWidth(),insideMargin=PaddingValues(0.dp)) {
                                SuperSpinner(items=listOf(SpinnerEntry(title="Clear all…",summary="Remove all entries")),selectedIndex=0,title="More actions",showValue=false,enabled=model.items.isNotEmpty(),onSelectedIndexChange={clear=true})
                            }
                        }
                        2 -> Column(Modifier.fillMaxSize().verticalScroll(settingsScroll).padding(horizontal=16.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                            Text(versionLabel(),Modifier.padding(horizontal=16.dp),fontSize=14.sp,color=Muted)
                            Card(Modifier.fillMaxWidth(),insideMargin=PaddingValues(0.dp)) {
                                SuperSpinner(items=listOf("System","Black","White").map {SpinnerEntry(title=it)},selectedIndex=activity.themeMode,
                                    title="Theme",onSelectedIndexChange=activity::setThemeMode)
                                SuperSwitch(checked=activity.isRepeatWarningEnabled,onCheckedChange=activity::setRepeatWarningEnabled,title="Repeat scan warning",
                                    summary="Confirm before adding a barcode already in your list.")
                            }
                            Card(Modifier.fillMaxWidth(),insideMargin=PaddingValues(0.dp)) {
                                SuperArrow(title="About",endActions={Text("Barkodu",fontSize=14.sp,color=Muted)},onClick={activity.pauseScanning();about=true})
                            }
                        }
                        3 -> if(license.isNotEmpty()) Column(Modifier.fillMaxSize().verticalScroll(aboutScroll).padding(20.dp)) {
                            Text(license,fontSize=13.sp,color=Muted)
                        } else About(aboutDark,aboutScroll,onLicense={license=activity.assets.open(it).bufferedReader().use {reader->reader.readText()}})
                    }
                }
                AnimatedContent(targetState=about,transitionSpec={fadeIn(Motion.tween()) togetherWith fadeOut(tween(Motion.Fast))},label="About") { showAbout ->
                    if(showAbout) pageContent(3) else HorizontalPager(state=pager,modifier=Modifier.fillMaxSize().testTag("pages"),
                        userScrollEnabled=!manual&&!clear&&pending==null) {destination->pageContent(destination)}
                }
            }
            val submit: () -> Unit = {
                error=when {input.isBlank()->"Barcode is required."; !CargoModel.valid(input)->"Use 3–64 letters or digits."; else->""}
                if(error.isEmpty()) {
                    if(activity.addManual(input)){input="";manual=false;keyboard?.hide()} else {error=activity.statusMessage;focus.requestFocus()}
                } else focus.requestFocus()
            }
            SuperDialog(show=manual,title="Add manually",backgroundColor=Panel,onDismissRequest={manual=false;keyboard?.hide()}) {
                Column(Modifier.fillMaxWidth().imePadding(),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                    Text("Enter the barcode exactly as printed. Leading zeros are preserved.",fontSize=13.sp,color=Muted)
                    val errorColor by animateColorAsState(if(error.isEmpty()) Accent else Danger,Motion.tween(),label="Field error")
                    TextField(value=input,onValueChange={input=it.take(64);error=""},label="Barcode",singleLine=true,
                        modifier=Modifier.fillMaxWidth().focusRequester(focus).semantics {if(error.isNotEmpty()) error(error)},borderColor=errorColor,
                        backgroundColor=if(error.isEmpty()) MiuixTheme.colorScheme.secondaryContainer else MiuixTheme.colorScheme.errorContainer,
                        keyboardOptions=KeyboardOptions(capitalization=KeyboardCapitalization.Characters,keyboardType=KeyboardType.Ascii,imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={submit()}))
                    AnimatedVisibility(error.isNotEmpty(),enter=fadeIn(Motion.tween())+expandVertically(Motion.spring()),exit=fadeOut(Motion.tween())) {Text(error,color=Danger,fontSize=13.sp)}
                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        Action("Cancel",Modifier.weight(1f)){manual=false;keyboard?.hide()}
                        Action("Add",Modifier.weight(1f),onClick=submit)
                    }
                }
                LaunchedEffect(manual) {if(manual){focus.requestFocus();keyboard?.show()}}
            }
            SuperDialog(show=clear,title="Clear all lists?",summary="Remove all barcodes. Undo will restore them.",onDismissRequest={clear=false}) {
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Action("Cancel",Modifier.weight(1f)){clear=false}
                    Action("Clear",Modifier.weight(1f)){activity.clearAction();clear=false}
                }
            }
        }
    }
    private fun versionLabel() = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
    @Composable private fun Scan(scroll:LazyListState) {
        val tick=revision
        val model=activity.model
        val rows=model.visible().map {it.copy()}
        val landscape=LocalConfiguration.current.screenWidthDp>LocalConfiguration.current.screenHeightDp
        LazyColumn(Modifier.fillMaxSize().testTag("scan-list"),state=scroll,contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            if(landscape) item(key="wide-header") {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                    Camera(Modifier.weight(0.36f).aspectRatio(16f/9f).testTag("camera-preview"))
                    Column(Modifier.weight(0.64f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        CarrierPicker();CarrierTabs()
                        Text("${rows.size} entries · ${model.items.size} total",Modifier.padding(horizontal=12.dp),fontSize=12.sp,color=Muted)
                    }
                }
            } else {
                item(key="camera") {Camera(Modifier.fillMaxWidth().aspectRatio(16f/9f).testTag("camera-preview"))}
                item(key="carrier") {CarrierPicker()}
                item(key="tabs") {CarrierTabs()}
                item(key="count") {Text("${rows.size} entries · ${model.items.size} total",Modifier.padding(horizontal=12.dp),fontSize=12.sp,color=Muted)}
            }
            if(rows.isEmpty()) item(key="empty") {Empty("No barcodes yet","Scan or use + to add a barcode.")}
            items(rows,key={it.id}) {item->BarcodeCard(item,Modifier.animateItem(fadeInSpec=Motion.tween(),placementSpec=Motion.spring(),fadeOutSpec=Motion.tween()))}
            item(key="status") {Text(activity.statusMessage,Modifier.padding(8.dp),fontSize=12.sp,color=if(activity.isSuccessFeedback) Accent else Muted)}
        }
    }
    @Composable private fun Camera(modifier:Modifier) {
        val tick=revision
        val active=activity.isCameraActive
        val overlay by animateFloatAsState(if(active) 0f else 1f,Motion.tween(),label="Camera pause")
        val flash by animateFloatAsState(if(activity.isSuccessFeedback) 1f else 0f,Motion.tween(),label="Scan success")
        val frame=activity.pausedFrame
        DisposableEffect(Unit) {onDispose {activity.pauseScanning()}}
        Box(modifier.clip(Shape).background(Panel).clickable(enabled=activity.model.selected>=0,onClickLabel=if(active) "Stop scanning" else "Start scanning") {activity.toggleCamera()}
            .semantics {contentDescription=if(active) "Camera on. Tap to stop scanning" else "Camera off. Tap to scan";stateDescription=if(active) "On" else "Off"}) {
            AndroidView(factory={activity.createPreview()},modifier=Modifier.fillMaxSize())
            if(overlay>0f) {
                Box(Modifier.fillMaxSize().graphicsLayer {alpha=overlay}.background(Panel)) {
                    if(frame!=null) Image(frame.asImageBitmap(),null,Modifier.fillMaxSize().testTag("paused-frame"),contentScale=ContentScale.Crop)
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=if(frame==null) 0.3f else 0.25f)))
                }
                Column(Modifier.align(Alignment.Center).graphicsLayer {alpha=overlay},horizontalAlignment=Alignment.CenterHorizontally) {
                    Icon(ScanIcon,null,tint=Color.White,modifier=Modifier.size(30.dp))
                    Spacer(Modifier.height(10.dp));Text(if(activity.model.selected<0) "Select a carrier first" else "Tap to scan",color=Color.White,fontSize=16.sp)
                }
            }
            if(active) Row(Modifier.align(Alignment.BottomCenter).padding(10.dp).background(Color(0x66000000),RoundedCornerShape(14.dp)).padding(horizontal=12.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically) {
                Icon(MiuixIcons.Regular.Pause,null,Modifier.size(16.dp),tint=Color.White)
                Spacer(Modifier.width(8.dp));Text("Tap to stop",color=Color.White,fontSize=12.sp)
            }
            if(flash>0f) Box(Modifier.fillMaxSize().border(2.dp,Accent.copy(alpha=flash),Shape))
        }
    }
    @Composable private fun CarrierPicker() {
        val tick=revision
        val model=activity.model
        Card(Modifier.fillMaxWidth().testTag("scan-carrier"),insideMargin=PaddingValues(0.dp)) {
            SuperSpinner(items=(0..3).map {id->SpinnerEntry(title=CargoModel.NAMES[id])},selectedIndex=model.selected.coerceAtLeast(0),
                title=if(model.selected<0) "Select carrier" else CargoModel.NAMES[model.selected],summary="Carrier to scan",showValue=false,onSelectedIndexChange=activity::selectCargo)
        }
    }
    @Composable private fun CarrierTabs() {
        val tick=revision
        val model=activity.model
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel).padding(4.dp).selectableGroup(),horizontalArrangement=Arrangement.spacedBy(3.dp)) {
            CargoModel.NAMES.forEachIndexed { index, name ->
                val selected=model.active==index
                val color by animateColorAsState(if(selected) MiuixTheme.colorScheme.secondaryContainer else Panel,Motion.tween(),label="Carrier tab")
                Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(color)
                    .selectable(selected=selected,role=Role.Tab,onClick={activity.selectTab(index)})
                    .semantics {contentDescription="$name, ${model.count(index)} barcodes"}.padding(vertical=10.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                    Text(name,fontSize=12.sp,fontWeight=if(selected) FontWeight.SemiBold else FontWeight.Normal,maxLines=1)
                    Text("${model.count(index)}",fontSize=11.sp,color=Muted,maxLines=1)
                }
            }
        }
    }
    // Adapted layout from compose-miuix-ui/miuix Example AboutPage (Apache-2.0).
    @Composable private fun About(dark:Boolean,scroll:androidx.compose.foundation.ScrollState,onLicense:(String)->Unit) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val viewport=maxHeight
            val titleColor=if(dark) Color(0xFF99B4FF) else Color(0xFF792B8F)
            val cardColor=if(dark) Color(0x66394E87) else Color.White.copy(alpha=0.7f)
            Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal=16.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                Spacer(Modifier.height(viewport*0.13f))
                val heroAlpha=(1f-scroll.value/600f).coerceIn(0f,1f)
                Box(Modifier.size(92.dp).graphicsLayer {alpha=heroAlpha}.background(Color.White,RoundedCornerShape(26.dp)),contentAlignment=Alignment.Center) {
                    Icon(MiuixIcons.Regular.Scan,"Barkodu app icon",Modifier.size(58.dp),tint=Color(0xFF246ADD))
                }
                Text("Barkodu",Modifier.padding(top=16.dp).graphicsLayer {alpha=heroAlpha},fontSize=35.sp,fontWeight=FontWeight.Medium,color=titleColor)
                Text(versionLabel(),Modifier.padding(top=6.dp),fontSize=14.sp,color=Muted)
                Text("Ömer tarafından yapılmıştır.",Modifier.padding(top=8.dp),fontSize=12.sp,color=Muted)
                Spacer(Modifier.height(viewport*0.20f))
                Card(Modifier.fillMaxWidth(),colors=CardDefaults.defaultColors(color=cardColor),insideMargin=PaddingValues(0.dp)) {
                    LinkRow("View Source","GitHub") {activity.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/omertrans678/Barkodu")))}
                }
                Spacer(Modifier.height(16.dp))
                Card(Modifier.fillMaxWidth(),colors=CardDefaults.defaultColors(color=cardColor),insideMargin=PaddingValues(0.dp)) {
                    LinkRow("License","Apache-2.0") {onLicense("project-license.txt")}
                    LinkRow("Third-party Licenses","") {onLicense("third-party-licenses.txt")}
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
    @Composable private fun BarcodeCard(item:CargoModel.Item,modifier:Modifier) {
        val density=LocalDensity.current
        val threshold=with(density){100.dp.toPx()}
        var drag by remember(item.id) {mutableFloatStateOf(0f)}


        val move by animateFloatAsState(drag,Motion.spring(),label="Swipe")
        Box(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MiuixTheme.colorScheme.errorContainer)) {
            Row(Modifier.matchParentSize().padding(horizontal=20.dp),horizontalArrangement=Arrangement.End,verticalAlignment=Alignment.CenterVertically) {
                Icon(DeleteIcon,"Delete",tint=Danger,modifier=Modifier.size(22.dp))
            }
            Card(Modifier.fillMaxWidth().testTag("barcode-${item.id}").offset{IntOffset(move.roundToInt(),0)}.semantics {
                customActions=listOf(CustomAccessibilityAction("Delete barcode ${item.code}"){activity.removeItem(item);true})
            }.pointerInput(item.id) {
                detectHorizontalDragGestures(onDragCancel={drag=0f},onDragEnd={
                    if(drag<=-threshold) activity.removeItem(item) else drag=0f
                }) {change,amount->change.consume();drag=(drag+amount).coerceIn(-threshold*2,0f)}
            },colors=CardDefaults.defaultColors(color=MiuixTheme.colorScheme.secondaryContainer),insideMargin=PaddingValues(16.dp)) {
                Text(item.code,fontSize=16.sp,fontWeight=FontWeight.Medium,maxLines=1,overflow=TextOverflow.Ellipsis)
            }
        }
    }
    @Composable private fun Empty(title:String,summary:String) {
        Column(Modifier.fillMaxWidth().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Icon(ScanIcon,null,modifier=Modifier.size(28.dp),tint=Muted);Text(title,fontSize=16.sp);Text(summary,fontSize=12.sp,color=Muted)
        }
    }
    @Composable private fun LinkRow(title:String,value:String,onClick:()->Unit) {
        SuperArrow(title=title,endActions={Text(value,fontSize=14.sp,color=Muted)},onClick=onClick)
    }
}
