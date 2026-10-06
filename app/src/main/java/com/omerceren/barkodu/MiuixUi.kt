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
import androidx.compose.ui.graphics.vector.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.painterResource
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
import top.yukonga.miuix.kmp.extra.SuperBottomSheet
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
// Small code-native icons; carrier artwork is isolated in CarrierIcon for later replacement.
private fun glyph(name: String, lines: List<List<Float>>): ImageVector = ImageVector.Builder(name,24.dp,24.dp,24f,24f).apply {
    lines.forEach { points -> path(fill = null, stroke = SolidColor(Color.White), strokeLineWidth = 1.7f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(points[0],points[1]); for(i in 2 until points.size step 2) lineTo(points[i],points[i+1])
    } }
}.build()
private val ScanIcon = glyph("Scan",listOf(listOf(3f,8f,3f,3f,8f,3f),listOf(16f,3f,21f,3f,21f,8f),listOf(3f,16f,3f,21f,8f,21f),listOf(16f,21f,21f,21f,21f,16f),listOf(7f,8f,7f,16f),listOf(11f,8f,11f,16f),listOf(16f,8f,16f,16f)))
private val SaveIcon = glyph("Save",listOf(listOf(5f,3f,19f,3f,19f,21f,5f,21f,5f,3f),listOf(9f,8f,15f,8f),listOf(9f,12f,15f,12f),listOf(9f,16f,13f,16f)))
private val SettingsIcon = glyph("Settings",listOf(listOf(4f,6f,20f,6f),listOf(4f,12f,20f,12f),listOf(4f,18f,20f,18f),listOf(8f,3f,8f,9f),listOf(16f,9f,16f,15f),listOf(10f,15f,10f,21f)))
private val BoxIcon = glyph("Carrier",listOf(listOf(4f,7f,12f,3f,20f,7f,20f,17f,12f,21f,4f,17f,4f,7f,12f,11f,20f,7f),listOf(12f,11f,12f,21f)))
private val DeleteIcon = glyph("Delete",listOf(listOf(4f,6f,20f,6f),listOf(8f,6f,8f,3f,16f,3f,16f,6f),listOf(6f,6f,7f,21f,17f,21f,18f,6f),listOf(10f,10f,10f,17f),listOf(14f,10f,14f,17f)))

class MiuixUi(private val activity: MainActivity) {
    private var revision by mutableIntStateOf(0)
    fun update() { revision++ }
    fun createView(): View = ComposeView(activity).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            val tick=revision
            val dark=when(activity.themeMode){1->true;2->false;else->isSystemInDarkTheme()}
            SideEffect {
                val background=if(dark) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                activity.window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(background))
                activity.window.statusBarColor=background
                activity.window.navigationBarColor=background
                WindowCompat.getInsetsController(activity.window,activity.window.decorView).apply {
                    isAppearanceLightStatusBars=!dark; isAppearanceLightNavigationBars=!dark
                }
            }
            val colors=if(dark) darkColorScheme(surface=Color.Black,background=Color.Black,primary=Color(0xFF7CAEFF),
                surfaceContainer=Color(0xFF202020),secondaryContainer=Color(0xFF303030),onSecondaryContainer=Color.White,error=Color(0xFFFF8A80))
                else lightColorScheme(surface=Color.White,background=Color.White,primary=Color(0xFF2868CC),
                    surfaceContainer=Color(0xFFF2F2F2),secondaryContainer=Color(0xFFE6E6E6),onSecondaryContainer=Color(0xFF242424))
            MiuixTheme(colors=colors) { App() }
        }
    }
    @Composable private fun Action(label:String, modifier:Modifier=Modifier, enabled:Boolean=true, onClick:()->Unit) {
        Button(onClick=onClick,modifier=modifier,enabled=enabled,minWidth=0.dp,minHeight=44.dp,
            insideMargin=PaddingValues(horizontal=12.dp,vertical=10.dp)) { Text(label,fontSize=14.sp) }
    }
    @Composable private fun CarrierIcon(id:Int, modifier:Modifier=Modifier) {
        // Replace this slot with the real asset for carrier id 0..3; stable IDs stay intact.
        Icon(BoxIcon,contentDescription=null,modifier=modifier.size(24.dp),tint=Muted)
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
        Scaffold(modifier=Modifier.nestedScroll(behavior.nestedScrollConnection),
            topBar={ SmallTopAppBar(title=title,color=barColor,titleColor=MiuixTheme.colorScheme.onSurface,scrollBehavior=behavior,
                navigationIcon={if(about) Text("‹",Modifier.clickable { if(license.isNotEmpty()) license="" else about=false }.semantics { contentDescription="Back" }.padding(16.dp),fontSize=28.sp)},
                actions={if(page==0&&!about) Text("+",Modifier.clickable {error="";manual=true}.semantics {contentDescription="Add manually"}.padding(16.dp),fontSize=28.sp,color=Accent)} ) },
            bottomBar={if(!about) NavigationBar(color=MiuixTheme.colorScheme.surface) {
                listOf(ScanIcon,SaveIcon,SettingsIcon).forEachIndexed { index,icon -> NavigationBarItem(selected=page==index,
                    onClick={if(index!=activity.page){requestedPage=index;activity.navigate(index)}},icon=icon,label=listOf("Scan","Save","Settings")[index])
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
            Box(Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets).background(MiuixTheme.colorScheme.surface)) {
                val pageContent: @Composable (Int) -> Unit = { destination ->
                    val renderTick=revision
                    when(destination) {
                        0 -> Scan(lists[model.active])
                        1 -> Column(Modifier.fillMaxSize().verticalScroll(saveScroll).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                            Text("${model.items.size} barcodes ready",fontSize=14.sp,color=Muted)
                            if(model.items.isEmpty()) Empty("Your saved list is empty","Scan a barcode or add one manually.")
                            listOf(Triple("Excel","Save each barcode as a separate row","xlsx"),Triple("TXT","Save a plain text carrier list","txt"),Triple("Share","Send your list to another app","txt")).forEach { (name,summary,ext) ->
                                LinkCard(name,summary,SaveIcon,model.items.isNotEmpty()) {activity.exportAction(ext,name=="Share")}
                            }
                            Spacer(Modifier.height(16.dp))
                            Card(Modifier.fillMaxWidth(),insideMargin=PaddingValues(0.dp)) {
                                SuperSpinner(items=listOf(SpinnerEntry(title="Clear all…",summary="Remove all entries")),
                                    selectedIndex=0,title="More actions",showValue=false,enabled=model.items.isNotEmpty(),
                                    onSelectedIndexChange={clear=true})
                            }
                        }
                        2 -> Column(Modifier.fillMaxSize().verticalScroll(settingsScroll).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                            Text("v0.1-beta",fontSize=13.sp,color=Muted)
                            Card(Modifier.fillMaxWidth(),insideMargin=PaddingValues(0.dp)) {
                                SuperSpinner(items=listOf("System","Black","White").map {SpinnerEntry(title=it)},selectedIndex=activity.themeMode,
                                    title="Theme",summary="Choose the app appearance",onSelectedIndexChange=activity::setThemeMode)
                            }
                            Card(Modifier.fillMaxWidth(),insideMargin=PaddingValues(18.dp)) {
                                Row(verticalAlignment=Alignment.CenterVertically) {
                                    Text("Repeat scan warning",Modifier.weight(1f),fontSize=16.sp)
                                    Switch(checked=activity.isRepeatWarningEnabled,onCheckedChange=activity::setRepeatWarningEnabled)
                                }
                                Spacer(Modifier.height(10.dp))
                                Text("Ask before adding a barcode already in your list. When off, a fresh repeat scan adds another card. A barcode held in view is always added only once.",fontSize=13.sp,color=Muted)
                            }
                            LinkCard("About","Barkodu · v0.1-beta",SettingsIcon) {about=true}
                        }
                        3 -> Column(Modifier.fillMaxSize().verticalScroll(aboutScroll).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                            if(license.isNotEmpty()) {
                                Text(license,fontSize=13.sp,color=Muted)
                            } else {
                                Spacer(Modifier.height(24.dp))
                                Image(painterResource(R.drawable.ic_launcher),contentDescription="Barkodu app icon",modifier=Modifier.size(96.dp).clip(RoundedCornerShape(24.dp)).align(Alignment.CenterHorizontally))
                                Text("Barkodu",Modifier.align(Alignment.CenterHorizontally),fontSize=28.sp,fontWeight=FontWeight.Medium)
                                Text("v0.1-beta",Modifier.align(Alignment.CenterHorizontally),fontSize=14.sp,color=Muted)
                                Text("Ömer tarafından yapılmıştır.",Modifier.align(Alignment.CenterHorizontally),fontSize=14.sp,color=Muted)
                                Spacer(Modifier.height(32.dp))
                                Card(Modifier.fillMaxWidth(),insideMargin=PaddingValues(0.dp)) {
                                    LinkRow("GitHub Source Code","GitHub") {activity.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/omertrans678/Barkodu")))}
                                    LinkRow("License","Apache-2.0") {license=activity.assets.open("project-license.txt").bufferedReader().use {it.readText()}}
                                }
                                Card(Modifier.fillMaxWidth(),insideMargin=PaddingValues(0.dp)) {
                                    LinkRow("Third-party Licenses","") {license=activity.assets.open("third-party-licenses.txt").bufferedReader().use {it.readText()}}
                                }
                            }
                        }
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
            SuperBottomSheet(show=manual,title="Add manually",backgroundColor=Panel,onDismissRequest={manual=false;keyboard?.hide()}) {
                Column(Modifier.fillMaxWidth().imePadding(),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                    Text("Enter the barcode exactly as printed. Leading zeros are preserved.",fontSize=13.sp,color=Muted)
                    val errorColor by animateColorAsState(if(error.isEmpty()) Accent else Danger,Motion.tween(),label="Field error")
                    TextField(value=input,onValueChange={input=it.take(64);error=""},label="Barcode",singleLine=true,
                        modifier=Modifier.fillMaxWidth().focusRequester(focus).semantics {if(error.isNotEmpty()) error(error)},borderColor=errorColor,
                        backgroundColor=if(error.isEmpty()) MiuixTheme.colorScheme.secondaryContainer else MiuixTheme.colorScheme.errorContainer,
                        keyboardOptions=KeyboardOptions(capitalization=KeyboardCapitalization.Characters,keyboardType=KeyboardType.Ascii,imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={submit()}))
                    AnimatedVisibility(error.isNotEmpty(),enter=fadeIn(Motion.tween())+expandVertically(Motion.spring()),exit=fadeOut(Motion.tween())) {Text(error,color=Danger,fontSize=13.sp)}
                    Action("Add",Modifier.fillMaxWidth(),onClick=submit)
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
    @Composable private fun Scan(scroll:LazyListState) {
        val tick=revision
        val model=activity.model
        val rows=model.visible().map {it.copy()}
        val landscape=LocalConfiguration.current.screenWidthDp>LocalConfiguration.current.screenHeightDp
        Column(Modifier.fillMaxSize().padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Card(Modifier.fillMaxWidth(),insideMargin=PaddingValues(0.dp)) {
                SuperSpinner(items=(0..3).map {id->SpinnerEntry(title=CargoModel.NAMES[id],icon={CarrierIcon(id,it)})},
                    selectedIndex=model.selected.coerceAtLeast(0),title=if(model.selected<0) "Select carrier" else CargoModel.NAMES[model.selected],
                    summary="Carrier to scan",showValue=false,startAction={CarrierIcon(model.selected)},onSelectedIndexChange=activity::selectCargo)
            }
            if(landscape) Row(Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                Camera(Modifier.weight(0.42f).fillMaxHeight()); ListPanel(Modifier.weight(0.58f),rows,scroll)
            } else {
                Camera(Modifier.fillMaxWidth().heightIn(min=110.dp,max=180.dp).height((LocalConfiguration.current.screenHeightDp*0.2f).dp))
                ListPanel(Modifier.weight(1f),rows,scroll)
            }
            Text(activity.statusMessage,Modifier.padding(bottom=8.dp),fontSize=12.sp,color=if(activity.isSuccessFeedback) Accent else Muted,maxLines=1,overflow=TextOverflow.Ellipsis)
        }
    }
    @Composable private fun Camera(modifier:Modifier) {
        val tick=revision
        val active=activity.isCameraActive
        val overlay by animateFloatAsState(if(active) 0f else 1f,Motion.tween(),label="Camera blur")
        val flash by animateFloatAsState(if(activity.isSuccessFeedback) 1f else 0f,Motion.tween(),label="Scan success")
        val stripeColor=MiuixTheme.colorScheme.outline
        Box(modifier.clip(Shape).background(Panel).clickable(enabled=activity.model.selected>=0,onClickLabel=if(active) "Stop scanning" else "Start scanning") {activity.toggleCamera()}
            .semantics {contentDescription=if(active) "Camera on. Tap to stop scanning" else "Camera off. Tap to scan";stateDescription=if(active) "On" else "Off"}) {
            AndroidView(factory={activity.createPreview()},modifier=Modifier.fillMaxSize())
            if(overlay>0f) {
                Box(Modifier.fillMaxSize().graphicsLayer {alpha=overlay}.background(Panel)) {
                    // No saved frame or running camera: progressive blur is applied to a neutral preview.
                    for(band in 0..7) {
                        val radius=if(Build.VERSION.SDK_INT>=31) (20f*(1-band/7f)*overlay).dp else 0.dp
                        Canvas(Modifier.fillMaxSize().drawWithContent {clipRect(top=size.height*band/8f,bottom=size.height*(band+1)/8f){this@drawWithContent.drawContent()}}.blur(radius)) {
                            for(x in 0..22) {
                                val left=size.width*x/23f
                                drawLine(stripeColor,Offset(left,size.height*0.18f),Offset(left,size.height*0.85f),strokeWidth=if(x%3==0) 7f else 3f)
                            }
                        }
                    }
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha=0.55f),Color.Black.copy(alpha=0.25f)))))
                }
                Column(Modifier.align(Alignment.Center).graphicsLayer {alpha=overlay},horizontalAlignment=Alignment.CenterHorizontally) {
                    Icon(ScanIcon,null,tint=Color.White,modifier=Modifier.size(30.dp))
                    Spacer(Modifier.height(10.dp));Text(if(activity.model.selected<0) "Select a carrier first" else "Tap to scan",color=Color.White,fontSize=16.sp)
                }
            }
            if(active) Text("Tap to stop",Modifier.align(Alignment.BottomCenter).padding(12.dp).background(Color(0xAA000000),RoundedCornerShape(10.dp)).padding(8.dp),color=Color.White,fontSize=12.sp)
            if(flash>0f) Box(Modifier.fillMaxSize().border(2.dp,Accent.copy(alpha=flash),Shape))
        }
    }
    @Composable private fun ListPanel(modifier:Modifier,rows:List<CargoModel.Item>,scroll:LazyListState) {
        val tick=revision
        val model=activity.model
        Column(modifier.fillMaxHeight().clip(Shape).background(Panel).padding(10.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MiuixTheme.colorScheme.surface).padding(4.dp).selectableGroup(),horizontalArrangement=Arrangement.spacedBy(3.dp)) {
                CargoModel.NAMES.forEachIndexed { index, name ->
                    val selected=model.active==index
                    val color by animateColorAsState(if(selected) MiuixTheme.colorScheme.secondaryContainer else MiuixTheme.colorScheme.surface,Motion.tween(),label="Carrier tab")
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(color)
                        .selectable(selected=selected,role=Role.Tab,onClick={activity.selectTab(index)})
                        .semantics { contentDescription="$name, ${model.count(index)} barcodes" }
                        .padding(vertical=10.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                        Text(name,fontSize=12.sp,fontWeight=if(selected) FontWeight.SemiBold else FontWeight.Normal,maxLines=1)
                        Text("${model.count(index)}",fontSize=11.sp,color=Muted,maxLines=1)
                    }
                }
            }
            Text("${rows.size} entries · ${model.items.size} total",Modifier.padding(horizontal=8.dp),fontSize=12.sp,color=Muted)
            Box(Modifier.weight(1f)) {
                androidx.compose.animation.AnimatedVisibility(rows.isEmpty(),enter=fadeIn(Motion.tween()),exit=fadeOut(Motion.tween())) {Empty("No barcodes yet","Scan or use + to add a barcode.")}
                LazyColumn(Modifier.fillMaxSize(),state=scroll,verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    items(rows,key={it.id}) {item-> BarcodeCard(item,Modifier.animateItem(fadeInSpec=Motion.tween(),placementSpec=Motion.spring(),fadeOutSpec=Motion.tween()))}
                }
            }
        }
    }
    @Composable private fun BarcodeCard(item:CargoModel.Item,modifier:Modifier) {
        val density=LocalDensity.current
        val threshold=with(density){100.dp.toPx()}
        var drag by remember(item.id) {mutableFloatStateOf(0f)}


        val move by animateFloatAsState(drag,Motion.spring(),label="Swipe")
        Box(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MiuixTheme.colorScheme.errorContainer)) {
            Row(Modifier.matchParentSize().padding(horizontal=20.dp),horizontalArrangement=if(drag>=0) Arrangement.Start else Arrangement.End,verticalAlignment=Alignment.CenterVertically) {
                Icon(DeleteIcon,"Delete",tint=Danger,modifier=Modifier.size(22.dp))
            }
            Card(Modifier.fillMaxWidth().testTag("barcode-${item.id}").offset{IntOffset(move.roundToInt(),0)}.semantics {
                customActions=listOf(CustomAccessibilityAction("Delete barcode ${item.code}"){activity.removeItem(item);true})
            }.pointerInput(item.id) {
                detectHorizontalDragGestures(onDragCancel={drag=0f},onDragEnd={
                    if(abs(drag)>=threshold) activity.removeItem(item) else drag=0f
                }) {change,amount->change.consume();drag+=amount}
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
    @Composable private fun LinkCard(title:String,summary:String,icon:ImageVector,enabled:Boolean=true,onClick:()->Unit) {
        Card(Modifier.fillMaxWidth().alpha(if(enabled) 1f else 0.45f).semantics {if(!enabled) disabled()},onClick=if(enabled) onClick else null,
            pressFeedbackType=PressFeedbackType.Sink,showIndication=true,insideMargin=PaddingValues(18.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                Icon(icon,null,modifier=Modifier.size(24.dp),tint=Accent)
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {Text(title,fontSize=17.sp);Text(summary,fontSize=12.sp,color=Muted)}
                Text("›",fontSize=24.sp,color=Muted)
            }
        }
    }
    @Composable private fun LinkRow(title:String,value:String,onClick:()->Unit) {
        Row(Modifier.fillMaxWidth().clickable(onClick=onClick).padding(18.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(title,Modifier.weight(1f),fontSize=16.sp);Text(value,fontSize=12.sp,color=Muted);Text(" ›",fontSize=24.sp,color=Muted)
        }
    }
}




