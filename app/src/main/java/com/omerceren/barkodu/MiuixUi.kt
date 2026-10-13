package com.omerceren.barkodu

import android.content.Intent
import android.net.Uri
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import kotlinx.coroutines.CancellationException
import androidx.core.view.WindowCompat
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
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
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme as materialDarkColors
import androidx.compose.material3.lightColorScheme as materialLightColors
import androidx.compose.material3.Checkbox as SelectionCheckbox
import top.yukonga.miuix.kmp.extra.SuperBottomSheet
import android.provider.Settings
import androidx.compose.ui.draw.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.extra.SuperArrow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.*
import top.yukonga.miuix.kmp.extra.SuperDialog
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
    @Composable private fun str(id:Int,vararg args:Any)=stringResource(id,*args)
    private var revision by mutableIntStateOf(0)
    fun update() { revision++ }
    fun createView(): View = ComposeView(activity).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            val tick=revision
            val dark=when(activity.themeMode){1->true;2->false;else->isSystemInDarkTheme()}
            val colors=if(dark) darkColorScheme(surface=Color.Black,background=Color.Black)
                else lightColorScheme()
            MaterialTheme(colorScheme=if(dark)materialDarkColors(primary=colors.primary,surface=colors.surface,background=colors.background,onSurface=colors.onSurface) else materialLightColors(primary=colors.primary,surface=colors.surface,background=colors.background,onSurface=colors.onSurface)) { MiuixTheme(colors=colors) { App() } }
        }
    }
    @Composable private fun Action(label:String, modifier:Modifier=Modifier, enabled:Boolean=true, onClick:()->Unit) {
        Button(onClick=onClick,modifier=modifier,enabled=enabled,minWidth=0.dp,minHeight=44.dp,
            colors=if(label in listOf(str(R.string.add),str(R.string.clear),str(R.string.confirm))) ButtonDefaults.buttonColorsPrimary() else ButtonDefaults.buttonColors(),
            insideMargin=PaddingValues(horizontal=12.dp,vertical=10.dp)) { Text(label,fontSize=14.sp) }
    }
    @Composable private fun App() {
        val tick=revision
        val model=activity.model
        val page=activity.page
        val pager=rememberPagerState(initialPage=page,pageCount={3})
        val scope=rememberCoroutineScope()
        val navigation=remember(pager,scope) {DemoPagerNavigation(pager,scope)}
        LaunchedEffect(page){if(page!=navigation.selectedPage)navigation.animateToPage(page)}
        LaunchedEffect(pager){snapshotFlow {pager.currentPage}.collect {navigation.syncPage()}}
        LaunchedEffect(pager){snapshotFlow {pager.settledPage}.drop(1).collect {if(activity.page!=it)activity.navigate(it)}}
        LaunchedEffect(pager){snapshotFlow {pager.isScrollInProgress}.collect {if(it&&activity.isCameraActive)activity.pauseScanning()}}
        var about by rememberSaveable { mutableStateOf(false) }
        var license by rememberSaveable { mutableStateOf("") }
        var carrierSheet by rememberSaveable {mutableStateOf(false)}
        var themeDialog by rememberSaveable {mutableStateOf(false)}
        var delayDialog by rememberSaveable {mutableStateOf(false)}
        var delayInput by rememberSaveable {mutableStateOf("")}
        var whitelistDialog by rememberSaveable {mutableStateOf(false)}
        var whitelistCarrier by rememberSaveable {mutableIntStateOf(0)}
        var prefixInput by rememberSaveable {mutableStateOf("")}
        var settingsError by rememberSaveable {mutableStateOf("")}
        var motionEnabled by remember {mutableStateOf(Settings.Global.getFloat(activity.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)>0f)}
        DisposableEffect(Unit) {
            val observer=object:ContentObserver(Handler(Looper.getMainLooper())) {override fun onChange(selfChange:Boolean){motionEnabled=Settings.Global.getFloat(activity.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)>0f}}
            activity.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),false,observer)
            onDispose {activity.contentResolver.unregisterContentObserver(observer)}
        }
        BackHandler(activity.selectionMode&&!about) {activity.exitSelection()}
        var clear by rememberSaveable { mutableStateOf(false) }
        var manual by rememberSaveable { mutableStateOf(false) }
        var input by rememberSaveable { mutableStateOf("") }
        var error by rememberSaveable { mutableStateOf("") }
        val focus=remember { FocusRequester() }
        val keyboard=LocalSoftwareKeyboardController.current
        // Hoisted outside page transitions: tab navigation never resets list position.
        val lists=List(4) { rememberLazyListState() }
        val settingsScroll=rememberScrollState()
        val saveScroll=rememberScrollState()
        val aboutScroll=rememberScrollState()
        val behavior=MiuixScrollBehavior()
        val scrollDistance by remember(about,page,model.active) { derivedStateOf { if(about) aboutScroll.value else if(page==0)
            lists[model.active].firstVisibleItemIndex*80+lists[model.active].firstVisibleItemScrollOffset
            else if(page==1) saveScroll.value else settingsScroll.value } }
        val barProgress by animateFloatAsState((scrollDistance/80f).coerceIn(0f,1f),Motion.tween(),label="Progressive title")
        val barColor=lerp(MiuixTheme.colorScheme.surface,Panel,barProgress)
        BackHandler(about&&license.isNotEmpty()) {license=""}
        val pending=activity.pendingRepeat
        val repeatId=pending?.id ?: -1L
        val noticeId=activity.noticeId
        val snackbarData=remember(repeatId,noticeId,activity.notice) {
            object:SnackbarData {
                override val visuals=SnackbarVisuals(
                    if(pending!=null) activity.getString(R.string.already_scanned,pending.code) else activity.notice,
                    if(pending!=null) activity.getString(R.string.add_again) else if(activity.isNoticeUndoAvailable) activity.getString(R.string.undo) else null,
                    false,SnackbarDuration.Indefinite)
                override suspend fun dismiss(){if(pending!=null) activity.closeRepeat(repeatId) else activity.dismissNotice()}
                override suspend fun performAction(){if(pending!=null) activity.confirmRepeat(repeatId) else activity.undoNotice(noticeId)}
            }
        }
        val aboutDark=when(activity.themeMode){1->true;2->false;else->isSystemInDarkTheme()}
        val glow=if(about&&motionEnabled) {
            val transition=rememberInfiniteTransition(label="About background")
            val shift by transition.animateFloat(initialValue=0.15f,targetValue=0.85f,animationSpec=infiniteRepeatable(tween(18000,easing=LinearEasing),RepeatMode.Reverse),label="Background glow")
            shift
        } else 0.5f
        val aboutTransition=remember {Animatable(0f)}
        val aboutProgress=aboutTransition.value
        LaunchedEffect(about,motionEnabled) {val target=if(about) 1f else 0f; if(motionEnabled)aboutTransition.animateTo(target,Motion.spring()) else aboutTransition.snapTo(target)}
        PredictiveBackHandler(about&&license.isEmpty()) {events->
            try {
                events.collect {aboutTransition.snapTo(1f-it.progress)}
                about=false
            } catch(cancelled:CancellationException) {if(motionEnabled)aboutTransition.animateTo(1f,Motion.spring()) else aboutTransition.snapTo(1f)}
        }
        val aboutBrush=Brush.verticalGradient(if(aboutDark) listOf(Color(0xFF101B49),Color(0xFF102963),Color(0xFF281D48)) else listOf(Color(0xFFE8E6FF),Color(0xFFFFE2EF),Color(0xFFD7ECF6)))
        SideEffect {
            val first=if(about) (if(aboutDark) 0xFF101B49 else 0xFFE8E6FF).toInt() else (if(aboutDark) android.graphics.Color.BLACK else 0xFFF7F7F7.toInt())
            val last=if(about) (if(aboutDark) 0xFF281D48 else 0xFFF5E8F2).toInt() else first
            activity.window.setBackgroundDrawable(if(about) android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,intArrayOf(first,last)) else android.graphics.drawable.ColorDrawable(first))
            activity.window.statusBarColor=first;activity.window.navigationBarColor=last
            WindowCompat.getInsetsController(activity.window,activity.window.decorView).apply {
                isAppearanceLightStatusBars=!aboutDark;isAppearanceLightNavigationBars=!aboutDark
            }
        }
        Box(Modifier.fillMaxSize()) {
        // MiuixDefault: covered page moves a quarter width and dims; the top page slides in.
        Scaffold(modifier=Modifier.nestedScroll(behavior.nestedScrollConnection).then(if(about) Modifier.clearAndSetSemantics {} else Modifier).graphicsLayer {
                translationX=-size.width*0.25f*aboutProgress
                alpha=1f-0.1f*aboutProgress
            },
            containerColor=MiuixTheme.colorScheme.surface,
            topBar={},
            bottomBar={NavigationBar(color=MiuixTheme.colorScheme.surface) {
                listOf(ScanIcon,SaveIcon,SettingsIcon).forEachIndexed { index,icon ->
                    val selected=navigation.selectedPage==index
                    val focus=(1f-abs(pager.currentPage+pager.currentPageOffsetFraction-index)).coerceIn(0f,1f)
                    val lift by animateFloatAsState(if(selected) 1f else 0f,Motion.spring(),label="Navigation icon $index")
                    NavigationBarItem(selected=selected,onClick={activity.pauseScanning();navigation.animateToPage(index)},
                        icon=if(selected) listOf(MiuixIcons.Heavy.Scan,MiuixIcons.Heavy.FileDownloads,MiuixIcons.Heavy.Settings)[index] else icon,
                        label=listOf(str(R.string.scan),str(R.string.save),str(R.string.settings))[index],modifier=Modifier.testTag("navigation-$index").semantics {this.selected=selected}.graphicsLayer {
                            scaleX=0.96f+0.08f*focus;scaleY=scaleX;translationY=-2.dp.toPx()*lift
                        })
                }
            }},
            snackbarHost={
                AnimatedVisibility(pending!=null||activity.notice.isNotEmpty(),enter=fadeIn(Motion.tween())+slideInVertically(Motion.spring()){it/2},exit=fadeOut(Motion.tween())) {
                    var noticeDrag by remember {mutableFloatStateOf(0f)}
                    LaunchedEffect(repeatId,noticeId){if(pending!=null||activity.notice.isNotEmpty())noticeDrag=0f}
                    val noticeOffset by animateFloatAsState(noticeDrag,Motion.spring(),label="Notice swipe")
                    Box(Modifier.fillMaxWidth().padding(horizontal=8.dp).testTag("notice").offset {IntOffset(noticeOffset.roundToInt(),0)}
                        .pointerInput(repeatId,noticeId) {
                            awaitEachGesture {
                                val down=awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial)
                                var distance=0f
                                do {
                                    val event=awaitPointerEvent(PointerEventPass.Initial)
                                    val change=event.changes.firstOrNull {it.id==down.id} ?: break
                                    distance+=change.position.x-change.previousPosition.x
                                    if(abs(distance) > viewConfiguration.touchSlop) {change.consume();noticeDrag=distance}
                                } while(change.pressed)
                                if(abs(distance) > 100.dp.toPx()) {
                                    noticeDrag=if(distance>0) size.width.toFloat() else -size.width.toFloat()
                                    if(pending!=null) activity.closeRepeat(repeatId) else activity.dismissNotice()
                                } else noticeDrag=0f
                            }
                        }) { Snackbar(snackbarData,Modifier.fillMaxWidth()) }

                }
            }
        ) { insets ->
            Box(Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets).background(MiuixTheme.colorScheme.surface)) {
                val pageContent: @Composable (Int) -> Unit = { destination ->
                    val renderTick=revision
                    when(destination) {
                        0 -> Column(Modifier.fillMaxSize()) { Text(str(R.string.scan),Modifier.padding(horizontal=24.dp,vertical=8.dp),fontSize=30.sp); Scan(lists,onAdd={activity.pauseScanning();error="";manual=true},onCarrier={activity.pauseScanning();carrierSheet=true}) }
                        1 -> Column(Modifier.fillMaxSize().verticalScroll(saveScroll).padding(horizontal=16.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                            Text(str(R.string.save),fontSize=30.sp)
                            Text(str(R.string.barcodes_ready,model.items.size),Modifier.padding(horizontal=16.dp),fontSize=14.sp,color=Muted)
                            if(model.items.isEmpty()) Empty(str(R.string.empty_save),str(R.string.empty_save_hint))
                            Card(Modifier.fillMaxWidth(),insideMargin=PaddingValues(0.dp)) {
                                BasicComponent(title=str(R.string.excel),summary=str(R.string.excel_summary),enabled=model.items.isNotEmpty(),onClick={activity.exportAction("xlsx",false)})
                                BasicComponent(title=str(R.string.txt),summary=str(R.string.text_export_summary),enabled=model.items.isNotEmpty(),onClick={activity.exportAction("txt",false)})
                                BasicComponent(title=str(R.string.share_as_txt),summary=str(R.string.share_summary),enabled=model.items.isNotEmpty(),onClick={activity.exportAction("txt",true)})
                            }
                            Card(Modifier.fillMaxWidth(),insideMargin=PaddingValues(0.dp)) {
                                BasicComponent(title=str(R.string.clear_all),summary=str(R.string.clear_all_summary),enabled=model.items.isNotEmpty(),onClick={clear=true})
                            }
                        }
                        2 -> Column(Modifier.fillMaxSize().verticalScroll(settingsScroll).padding(horizontal=16.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                            Text(str(R.string.settings),Modifier.padding(horizontal=16.dp),fontSize=30.sp)
                            Text(versionLabel(),Modifier.padding(horizontal=16.dp),fontSize=14.sp,color=Muted)
                            Card(Modifier.fillMaxWidth(),insideMargin=PaddingValues(0.dp)) {
                                BasicComponent(title=str(R.string.theme),summary=str(R.string.theme_summary),onClick={themeDialog=true},endActions={Text(listOf(str(R.string.system),str(R.string.black),str(R.string.white))[activity.themeMode],fontSize=14.sp,color=Muted)})
                                SettingSwitch(str(R.string.repeat_scan_warning),str(R.string.repeat_summary),activity.isRepeatWarningEnabled,activity::setRepeatWarningEnabled)
                                SettingSwitch(str(R.string.unrecognized_barcode_sound),str(R.string.unknown_sound_summary),activity.isUnknownSoundEnabled,activity::setUnknownSoundEnabled)
                                SettingSwitch(str(R.string.confirm_unrecognized_barcodes),str(R.string.unknown_confirmation_summary),activity.isUnknownConfirmationEnabled,activity::setUnknownConfirmationEnabled)
                                BasicComponent(title=str(R.string.scan_delay),summary=str(R.string.scan_delay_summary,activity.scanDelayMs),onClick={settingsError="";delayInput=activity.scanDelayMs.toString();delayDialog=true})
                                BasicComponent(title=str(R.string.whitelist),summary=str(R.string.whitelist_summary),onClick={settingsError="";whitelistCarrier=activity.model.selected.coerceAtLeast(0);prefixInput=activity.rules.prefixes(whitelistCarrier).joinToString(", ");whitelistDialog=true})
                            }
                            Card(Modifier.fillMaxWidth(),insideMargin=PaddingValues(0.dp)) {
                                BasicComponent(title=str(R.string.about),endActions={Text(str(R.string.barkodu),fontSize=14.sp,color=Muted)},onClick={activity.pauseScanning();about=true})
                            }
                        }
                    }
                }
                HorizontalPager(state=pager,modifier=Modifier.fillMaxSize().testTag("pages"),
                    flingBehavior=PagerDefaults.flingBehavior(state=pager,snapAnimationSpec=Motion.spring()),
                    userScrollEnabled=!manual&&!clear&&!carrierSheet&&!themeDialog&&!delayDialog&&!whitelistDialog&&pending==null&&activity.pendingUnknown==null) {destination->pageContent(destination)}
            }
            SuperBottomSheet(show=carrierSheet,title=str(R.string.choose_carrier),onDismissRequest={carrierSheet=false}) {
                Column(Modifier.fillMaxWidth().testTag("carrier-sheet")) {
                    (CargoModel.NAMES.indices.toList()+(-1)).forEach {id->
                        BasicComponent(title=if(id<0)str(R.string.clear_selection) else CargoModel.NAMES[id],
                            onClick={activity.selectCargo(id);carrierSheet=false},
                            endActions={Checkbox(checked=model.selected==id,onCheckedChange={activity.selectCargo(id);carrierSheet=false})})
                    }
                }
            }
            SuperDialog(show=themeDialog,title=str(R.string.theme),onDismissRequest={themeDialog=false}) {
                listOf(str(R.string.system),str(R.string.black),str(R.string.white)).forEachIndexed {index,name->
                    BasicComponent(title=name,onClick={activity.setThemeMode(index);themeDialog=false},endActions={Checkbox(checked=activity.themeMode==index,onCheckedChange={activity.setThemeMode(index);themeDialog=false})})
                }
            }
            SuperDialog(show=delayDialog,title=str(R.string.scan_delay),onDismissRequest={delayDialog=false}) {
                TextField(value=delayInput,onValueChange={delayInput=it;settingsError=""},label=str(R.string.delay_ms),singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth())
                if(settingsError.isNotEmpty())Text(settingsError,color=Danger)
                Row(Modifier.padding(top=16.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Action(str(R.string.cancel),Modifier.weight(1f)){delayDialog=false}
                    Action(str(R.string.confirm),Modifier.weight(1f)) {
                        val value=delayInput.toIntOrNull()
                        if(value==null||value !in 0..2000)settingsError=activity.getString(R.string.delay_invalid)
                        else {activity.setScanDelayMs(value);delayDialog=false}
                    }
                }
            }
            SuperDialog(show=whitelistDialog,title=str(R.string.whitelist),onDismissRequest={whitelistDialog=false}) {
                TabRow(tabs=CargoModel.NAMES.toList(),selectedTabIndex=whitelistCarrier,onTabSelected={whitelistCarrier=it;prefixInput=activity.rules.prefixes(it).joinToString(", ");settingsError=""})
                Text(str(R.string.prefix_help),Modifier.padding(vertical=12.dp),fontSize=13.sp,color=Muted)
                TextField(value=prefixInput,onValueChange={prefixInput=it;settingsError=""},label=str(R.string.prefixes),modifier=Modifier.fillMaxWidth())
                if(settingsError.isNotEmpty())Text(settingsError,color=Danger)
                Row(Modifier.padding(top=16.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Action(str(R.string.cancel),Modifier.weight(1f)){whitelistDialog=false}
                    Action(str(R.string.save),Modifier.weight(1f)) {
                        if(activity.updateWhitelist(whitelistCarrier,prefixInput.split(Regex("[,;\\s]+"))))whitelistDialog=false
                        else settingsError=activity.getString(R.string.prefix_invalid)
                    }
                }
            }
            val submit: () -> Unit = {
                error=when {input.isBlank()->activity.getString(R.string.barcode_required); !CargoModel.valid(input)->activity.getString(R.string.barcode_invalid); else->""}
                if(error.isEmpty()) {
                    if(activity.addManual(input)){input="";manual=false;keyboard?.hide()} else {error=activity.statusMessage;focus.requestFocus()}
                } else focus.requestFocus()
            }
            SuperDialog(show=manual,title=str(R.string.add_manually),onDismissRequest={manual=false;keyboard?.hide()}) {
                Column(Modifier.fillMaxWidth().imePadding(),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                    Text(str(R.string.manual_hint),fontSize=13.sp,color=Muted)
                    val errorColor by animateColorAsState(if(error.isEmpty()) Accent else Danger,Motion.tween(),label="Field error")
                    TextField(value=input,onValueChange={input=it.take(64);error=""},label=str(R.string.barcode),singleLine=true,
                        modifier=Modifier.fillMaxWidth().focusRequester(focus).semantics {if(error.isNotEmpty()) error(error)},borderColor=errorColor,
                        backgroundColor=if(error.isEmpty()) MiuixTheme.colorScheme.secondaryContainer else MiuixTheme.colorScheme.errorContainer,
                        keyboardOptions=KeyboardOptions(capitalization=KeyboardCapitalization.None,keyboardType=KeyboardType.Ascii,imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={submit()}))
                    AnimatedVisibility(error.isNotEmpty(),enter=fadeIn(Motion.tween())+expandVertically(Motion.spring()),exit=fadeOut(Motion.tween())) {Text(error,color=Danger,fontSize=13.sp)}
                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        Action(str(R.string.cancel),Modifier.weight(1f)){manual=false;keyboard?.hide()}
                        Action(str(R.string.add),Modifier.weight(1f),onClick=submit)
                    }
                }
                LaunchedEffect(manual) {if(manual){focus.requestFocus();keyboard?.show()}}
            }
            SuperDialog(show=activity.pendingUnknown!=null,title=str(R.string.unrecognized_barcode),summary=str(R.string.unknown_barcode_question),onDismissRequest={activity.resolveUnknown(false)}) {
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Action(str(R.string.cancel),Modifier.weight(1f)){activity.resolveUnknown(false)}
                    Action(str(R.string.add),Modifier.weight(1f)){activity.resolveUnknown(true)}
                }
            }
            SuperDialog(show=clear,title=str(R.string.clear_all_lists),summary=str(R.string.clear_all_question),onDismissRequest={clear=false}) {
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Action(str(R.string.cancel),Modifier.weight(1f)){clear=false}
                    Action(str(R.string.clear),Modifier.weight(1f)){activity.clearAction();clear=false}
                }
            }
        }
        if(aboutProgress>0f||about) {
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha=0.5f*aboutProgress)))
            Scaffold(modifier=Modifier.fillMaxSize().graphicsLayer {
                    translationX=((1f-aboutProgress)*size.width).roundToInt().toFloat()
                    shape=RoundedCornerShape(topStart=28.dp,bottomStart=28.dp)
                    clip=aboutProgress<0.999f
                }.background(aboutBrush).drawBehind {
                    drawRect(Brush.radialGradient(listOf(if(aboutDark) Color(0x443B60E2) else Color(0x99D8D2FF),Color.Transparent),center=Offset(size.width*glow,size.height*0.2f),radius=size.height*0.7f))
                    drawRect(Brush.radialGradient(listOf(if(aboutDark) Color(0x44382D73) else Color(0xBBFFD0E3),Color.Transparent),center=Offset(size.width*(1-glow),size.height*0.5f),radius=size.height*0.6f))
                },containerColor=Color.Transparent,
                topBar={SmallTopAppBar(title=if(license.isEmpty()) "" else str(R.string.license),color=Color.Transparent,
                    navigationIcon={IconButton(onClick={if(license.isNotEmpty())license="" else about=false},modifier=Modifier.padding(horizontal=12.dp)){Icon(MiuixIcons.Regular.Back,activity.getString(R.string.back),Modifier.size(26.dp))}})}
            ) {aboutInsets->
                Box(Modifier.fillMaxSize().padding(aboutInsets).testTag("about-screen")) {
                    if(license.isNotEmpty()) Column(Modifier.fillMaxSize().verticalScroll(aboutScroll).padding(20.dp)){Text(license,fontSize=13.sp,color=Muted)}
                    else About(aboutDark,aboutScroll,onLicense={license=activity.assets.open(it).bufferedReader().use {reader->reader.readText()}})
                }
            }
        }
        }
    }
    private fun versionLabel() = activity.getString(R.string.version_label,BuildConfig.VERSION_NAME,BuildConfig.VERSION_CODE)
    @Composable private fun SettingSwitch(title:String,summary:String?,checked:Boolean,onChange:(Boolean)->Unit) {
        BasicComponent(title=title,summary=summary,onClick={onChange(!checked)},endActions={Switch(checked=checked,onCheckedChange=onChange)})
    }
    @Composable private fun Scan(lists:List<LazyListState>,onAdd:()->Unit,onCarrier:()->Unit) {
        val tick=revision
        val model=activity.model
        val windowSize=LocalWindowInfo.current.containerSize
        val compact=windowSize.width>windowSize.height
        val pager=rememberPagerState(initialPage=model.active,pageCount={CargoModel.NAMES.size})
        val scope=rememberCoroutineScope()
        val navigation=remember(pager,scope){DemoPagerNavigation(pager,scope)}
        LaunchedEffect(model.active){if(model.active!=navigation.selectedPage)navigation.animateToPage(model.active)}
        LaunchedEffect(pager){snapshotFlow{pager.currentPage}.collect {navigation.syncPage()}}
        LaunchedEffect(pager){snapshotFlow{pager.settledPage}.drop(1).collect {if(model.active!=it)activity.selectTab(it)}}
        Column(Modifier.fillMaxSize().padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            if(compact) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                Camera(Modifier.height(80.dp).aspectRatio(16f/9f).testTag("camera-preview"))
                Box(Modifier.weight(1f)){CarrierPicker(onCarrier)}
            } else {
                Camera(Modifier.fillMaxWidth().aspectRatio(16f/9f).testTag("camera-preview"))
                CarrierPicker(onCarrier)
            }
            Card(Modifier.fillMaxWidth().weight(1f).padding(bottom=8.dp).testTag("carrier-tabs-card"),insideMargin=PaddingValues(if(compact)8.dp else 12.dp)) {
                TabRow(tabs=CargoModel.NAMES.toList(),selectedTabIndex=navigation.selectedPage,onTabSelected=navigation::animateToPage)
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text(str(R.string.list_totals,model.items.size,model.count(0),model.count(1),model.count(2),model.count(3)),Modifier.weight(1f),fontSize=11.sp,color=Muted)
                    IconButton(onClick=onAdd,modifier=Modifier.size(40.dp).testTag("add-barcode")){Icon(MiuixIcons.Regular.Add,str(R.string.add_manually),Modifier.size(24.dp),tint=Accent)}
                }
                if(activity.selectionMode) Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text(str(R.string.selected_count,activity.selectedItemIds.size),Modifier.weight(1f),fontSize=13.sp)
                    IconButton(onClick=activity::deleteSelection,enabled=activity.selectedItemIds.isNotEmpty()){Icon(DeleteIcon,str(R.string.delete_selected))}
                    IconButton(onClick=activity::exitSelection){Icon(MiuixIcons.Regular.Back,str(R.string.cancel_selection))}
                }
                HorizontalPager(state=pager,modifier=Modifier.fillMaxWidth().weight(1f).testTag("carrier-pages"),
                    flingBehavior=PagerDefaults.flingBehavior(state=pager,snapAnimationSpec=Motion.spring()),userScrollEnabled=!activity.selectionMode) {carrier->
                    val rows=model.items.filter {it.cargo==carrier}
                    LazyColumn(state=lists[carrier],modifier=Modifier.fillMaxSize().then(if(carrier!=pager.currentPage)Modifier.clearAndSetSemantics{} else Modifier).testTag(if(carrier==model.active)"scan-list" else "scan-list-$carrier"),contentPadding=PaddingValues(vertical=6.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        if(rows.isEmpty())item(key="empty"){Empty(str(R.string.no_barcodes),str(R.string.add_hint))}
                        items(rows,key={it.id}) {item->BarcodeCard(item,Modifier.animateItem(fadeInSpec=Motion.tween(),placementSpec=Motion.spring(),fadeOutSpec=Motion.tween()))}
                    }
                }
            }
            Text(activity.statusMessage,Modifier.padding(bottom=4.dp),fontSize=12.sp,color=if(activity.isSuccessFeedback)Accent else Muted,maxLines=2,overflow=TextOverflow.Ellipsis)
        }
    }
    @Composable private fun Camera(modifier:Modifier) {
        val tick=revision
        val active=activity.isCameraActive
        val ready=activity.isPreviewReady
        val overlay by animateFloatAsState(if(ready) 0f else 1f,Motion.tween(),label="Camera pause")
        val feedback=activity.scanFeedback
        var lastFeedback by remember {mutableIntStateOf(1)}
        if(feedback!=0)lastFeedback=feedback
        val flash by animateFloatAsState(if(feedback!=0) 1f else 0f,Motion.tween(),label="Scan success")
        val frame=activity.pausedFrame
        DisposableEffect(Unit) {onDispose {activity.pauseScanning()}}
        Box(modifier.clip(Shape).background(Panel).clickable(enabled=activity.model.selected>=0,onClickLabel=if(active) activity.getString(R.string.stop_scanning) else activity.getString(R.string.start_scanning)) {activity.toggleCamera()}
            .semantics {contentDescription=if(active) activity.getString(R.string.camera_on_description) else activity.getString(R.string.camera_off_description);stateDescription=if(active) activity.getString(R.string.on) else activity.getString(R.string.off)}) {
            AndroidView(factory={activity.createPreview()},modifier=Modifier.fillMaxSize())
            if(overlay>0f) {
                Box(Modifier.fillMaxSize().graphicsLayer {alpha=overlay}.background(Panel)) {
                    if(frame!=null) Image(frame.asImageBitmap(),null,Modifier.fillMaxSize().testTag("paused-frame"),contentScale=ContentScale.Crop)
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=if(frame==null) 0.3f else 0.25f)))
                }
                Column(Modifier.align(Alignment.Center).graphicsLayer {alpha=overlay},horizontalAlignment=Alignment.CenterHorizontally) {
                    Icon(ScanIcon,null,tint=Color.White,modifier=Modifier.size(30.dp))
                    Spacer(Modifier.height(10.dp));Text(if(activity.model.selected<0) activity.getString(R.string.select_carrier_first) else if(active&&!ready) activity.getString(R.string.camera_starting) else activity.getString(R.string.tap_to_scan),color=Color.White,fontSize=16.sp)
                }
            }
            if(ready) Row(Modifier.align(Alignment.BottomCenter).padding(10.dp).background(Color(0x66000000),RoundedCornerShape(14.dp)).padding(horizontal=12.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically) {
                Icon(MiuixIcons.Regular.Pause,null,Modifier.size(16.dp),tint=Color.White)
                Spacer(Modifier.width(8.dp));Text(str(R.string.tap_to_stop),color=Color.White,fontSize=12.sp)
            }
            if(flash>0f) {
                val signal=if(lastFeedback==2) Color(0xFFD50000) else Color(0xFF00C853)
                Box(Modifier.fillMaxSize().testTag("scan-feedback").semantics {stateDescription=if(lastFeedback==2) activity.getString(R.string.unrecognized) else activity.getString(R.string.recognized)}
                    .border(12.dp,signal.copy(alpha=flash*0.12f),Shape).border(8.dp,signal.copy(alpha=flash*0.24f),Shape).border(4.dp,signal.copy(alpha=flash),Shape))
            }
        }
    }
    @Composable private fun CarrierPicker(onClick:()->Unit) {
        val tick=revision
        Card(Modifier.fillMaxWidth().testTag("scan-carrier"),insideMargin=PaddingValues(0.dp)) {
            BasicComponent(title=if(activity.model.selected<0)str(R.string.select_carrier) else CargoModel.NAMES[activity.model.selected],summary=str(R.string.carrier_to_scan),onClick=onClick)
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
                val heroAlpha by remember(scroll) { derivedStateOf { (1f-scroll.value/600f).coerceIn(0f,1f) } }
                Box(Modifier.size(92.dp).graphicsLayer {alpha=heroAlpha}.background(Color.White,RoundedCornerShape(26.dp)),contentAlignment=Alignment.Center) {
                    Icon(MiuixIcons.Regular.Scan,activity.getString(R.string.app_icon_description),Modifier.size(58.dp),tint=Color(0xFF246ADD))
                }
                Text(str(R.string.barkodu),Modifier.padding(top=16.dp).graphicsLayer {alpha=heroAlpha},fontSize=35.sp,fontWeight=FontWeight.Medium,color=titleColor)
                Text(versionLabel(),Modifier.padding(top=6.dp),fontSize=14.sp,color=Muted)
                Text(str(R.string.copyright),Modifier.padding(top=8.dp),fontSize=12.sp,color=Muted)
                Spacer(Modifier.height(viewport*0.20f))
                Card(Modifier.fillMaxWidth(),colors=CardDefaults.defaultColors(color=cardColor),insideMargin=PaddingValues(0.dp)) {
                    LinkRow(str(R.string.view_source),activity.getString(R.string.github)) {activity.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/omertrans678/Barkodu")))}
                }
                Spacer(Modifier.height(16.dp))
                Card(Modifier.fillMaxWidth(),colors=CardDefaults.defaultColors(color=cardColor),insideMargin=PaddingValues(0.dp)) {
                    LinkRow(str(R.string.license),activity.getString(R.string.apache_license)) {onLicense("project-license.txt")}
                    LinkRow(str(R.string.third_party_licenses),"") {onLicense("third-party-licenses.txt")}
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
        Box(modifier.fillMaxWidth().clip(Shape).background(if(move < -0.5f) Color(0xFFCC0000) else Color.Transparent)) {
            Row(Modifier.matchParentSize().padding(horizontal=20.dp),horizontalArrangement=Arrangement.End,verticalAlignment=Alignment.CenterVertically) {
                Icon(DeleteIcon,activity.getString(R.string.delete),tint=Color.White,modifier=Modifier.size(22.dp))
            }
            Card(Modifier.fillMaxWidth().testTag("barcode-${item.id}").offset{IntOffset(move.roundToInt(),0)}.combinedClickable(onClick={if(activity.selectionMode)activity.toggleSelection(item.id)},onLongClick={activity.beginSelection(item.id)}).semantics {
                customActions=listOf(CustomAccessibilityAction(activity.getString(R.string.delete_barcode,item.code)){activity.removeItem(item);true})
            }.pointerInput(item.id,activity.selectionMode) {
                if(!activity.selectionMode)detectHorizontalDragGestures(onDragCancel={drag=0f},onDragEnd={
                    if(drag<=-threshold) activity.removeItem(item) else drag=0f
                }) {change,amount->change.consume();drag=(drag+amount).coerceIn(-threshold*2,0f)}
            },colors=CardDefaults.defaultColors(color=MiuixTheme.colorScheme.secondaryContainer),insideMargin=PaddingValues(16.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    if(activity.selectionMode)SelectionCheckbox(checked=item.id in activity.selectedItemIds,onCheckedChange={activity.toggleSelection(item.id)},modifier=Modifier.testTag("selection-${item.id}"),colors=androidx.compose.material3.CheckboxDefaults.colors(checkedColor=Accent))
                    Text(item.code,Modifier.weight(1f),fontSize=16.sp,fontWeight=FontWeight.Medium)
                }
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
