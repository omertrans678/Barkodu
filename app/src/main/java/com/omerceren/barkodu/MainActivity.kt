package com.omerceren.barkodu

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.*
import android.util.AtomicFile
import android.view.accessibility.AccessibilityManager
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import androidx.camera.core.*
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.*
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/** Owns screen state; scanning, rules, storage and export are independent Kotlin modules. */
class MainActivity : ComponentActivity() {
    companion object {private const val CAMERA_REQUEST = 10; private const val SAVE_REQUEST = 20}
    val model = CargoModel()
    val rules = CarrierRules()
    private lateinit var preferences: AppPreferences
    private val handler = Handler(Looper.getMainLooper())
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var root: LinearLayout? = null
    private var ui: MiuixUi? = null
    private var previewView: PreviewView? = null
    var pausedFrame: Bitmap? = null
        private set
    private var frameVersion = 0
    private var cameraProvider: ProcessCameraProvider? = null
    private val decoder = CameraBarcodeDecoder()
    private val consensus = DecodeConsensus()
    private val throttle = ScanThrottle()
    private val scanCooldown = ScanThrottle()
    private val scanGate = ScanGate()
    private var lastRejectedCode: String? = null
    @Volatile private var running = false
    private var starting = false
    private var previewReady = false
    @Volatile private var session = 0
    var page = 0
        private set
    var themeMode = 0
        private set
    @Volatile var scanDelayMs = 150
        private set
    var isRepeatWarningEnabled = true
        private set
    var isUnknownSoundEnabled = true
        private set
    var isUnknownConfirmationEnabled = true
        private set
    var pendingUnknown: String? = null
        private set
    private var pendingUnknownCarrier = -1
    private val confirmation = RepeatConfirmation()
    val pendingRepeat get() = confirmation.get()
    val isRepeatWarningVisible get() = pendingRepeat != null
    val isCameraActive get() = running || starting
    val isPreviewReady get() = running && previewReady
    var scanFeedback = 0
        private set
    val isSuccessFeedback get() = scanFeedback == 1
    var statusMessage = ""
        private set
    var resolutionMessage = ""
        private set
    var scannerStatus = ""
        private set
    var notice = ""
        private set
    var noticeId = 0L
        private set
    private var noticeUndo: (() -> Unit)? = null
    val isNoticeUndoAvailable get() = noticeUndo != null
    private var resetNotice: Runnable? = null
    private var resetWarning: Runnable? = null
    private var resetFeedback: Runnable? = null
    private var pendingExport: File? = null
    private var exporting = false
    var selectionMode = false
        private set
    var selectedItemIds: Set<Long> = emptySet()
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = AppPreferences(getPreferences(MODE_PRIVATE))
        preferences.restore(model, rules)
        page = preferences.integer("page", 0).coerceIn(0, 2)
        themeMode = preferences.integer("themeMode", 0).coerceIn(0, 2)
        scanDelayMs = preferences.integer("scanDelayMs", 150).coerceIn(0, 2000)
        isRepeatWarningEnabled = preferences.boolean("repeatWarningEnabled", true)
        isUnknownSoundEnabled = preferences.boolean("unknownSound", true)
        isUnknownConfirmationEnabled = preferences.boolean("unknownConfirmation", true)
        pendingExport = savedInstanceState?.getString("pendingExport")?.let {File(it)}
        runCatching {AtomicFile(File(filesDir, "paused-frame.png")).openRead().use {pausedFrame = BitmapFactory.decodeStream(it)}}
        statusMessage = getString(if(model.selected >= 0) R.string.carrier_ready else R.string.select_carrier_start)
        resolutionMessage = getString(R.string.offline_scanning)
        ui = MiuixUi(this)
        root = LinearLayout(this).also {layout ->
            layout.orientation = LinearLayout.VERTICAL
            setContentView(layout)
            layout.addView(ui!!.createView(), LinearLayout.LayoutParams(-1, -1))
        }
        render()
    }
    private fun render() {ui?.update()}
    private fun message(value: String) {statusMessage = value; render()}
    private fun save() {preferences.save(model)}
    @JvmName("applyThemeMode")
    fun setThemeMode(value: Int) {if(value in 0..2) {themeMode = value; preferences.put("themeMode", value); render()}}
    @JvmName("applyScanDelayMs")
    fun setScanDelayMs(value: Int) {scanDelayMs = value.coerceIn(0, 2000); preferences.put("scanDelayMs", scanDelayMs); render()}
    @JvmName("applyUnknownSoundEnabled")
    fun setUnknownSoundEnabled(value: Boolean) {isUnknownSoundEnabled = value; preferences.put("unknownSound", value); render()}
    @JvmName("applyUnknownConfirmationEnabled")
    fun setUnknownConfirmationEnabled(value: Boolean) {isUnknownConfirmationEnabled = value; preferences.put("unknownConfirmation", value); render()}
    @JvmName("applyRepeatWarningEnabled")
    fun setRepeatWarningEnabled(value: Boolean) {isRepeatWarningEnabled = value; preferences.put("repeatWarningEnabled", value); if(!value) dismissRepeatWarning(); render()}
    fun updateWhitelist(carrier: Int, prefixes: List<String>): Boolean = try {
        rules.setPrefixes(carrier, prefixes); preferences.save(rules); render(); true
    } catch(invalid: IllegalArgumentException) {false}
    fun navigate(value: Int) {if(value !in 0..2) return; if(value != 0) {stopCamera(); exitSelection()}; page = value; preferences.put("page", value); render()}
    fun selectCargo(id: Int) {
        if(id !in -1..3) return
        if(model.selected != id) stopCamera()
        model.selected = id; save()
        message(if(id < 0) getString(R.string.select_carrier_start) else getString(R.string.carrier_selected, CargoModel.NAMES[id]))
    }
    fun selectTab(id: Int) {if(id in CargoModel.NAMES.indices) {model.active = id; save(); render()}}
    fun beginSelection(id: Long) {if(model.items.any {it.id == id}) {stopCamera(); selectionMode = true; selectedItemIds = setOf(id); render()}}
    fun toggleSelection(id: Long) {if(selectionMode && model.items.any {it.id == id}) {selectedItemIds = if(id in selectedItemIds) selectedItemIds-id else selectedItemIds+id; render()}}
    fun exitSelection() {selectionMode = false; selectedItemIds = emptySet(); render()}
    fun deleteSelection() {
        val removed = model.removeMany(selectedItemIds)
        exitSelection()
        if(removed.isNotEmpty()) {save(); showNotice(getString(R.string.entries_deleted, removed.size)) {model.restore(removed)}}
    }
    fun removeItem(item: CargoModel.Item) {
        val removed = model.removeMany(setOf(item.id))
        if(removed.isNotEmpty()) {save(); showNotice(getString(R.string.barcode_deleted)) {model.restore(removed)}}
    }
    fun clearAction() {val removed = model.removeMany(model.items.map {it.id}.toSet()); exitSelection(); save(); showNotice(getString(R.string.all_cleared)) {model.restore(removed)}}
    fun undoAction() {if(model.undo()) {save(); message(getString(R.string.last_undone))}}
    fun dismissNotice() {resetNotice?.let {handler.removeCallbacks(it)}; notice = ""; noticeUndo = null; render()}
    private fun showNotice(text: String, undo: (() -> Unit)? = null) {
        dismissRepeatWarning(); dismissNotice(); notice = text; noticeUndo = undo; noticeId++; render()
        resetNotice = Runnable {dismissNotice()}.also {handler.postDelayed(it, 7000)}
    }
    fun undoNotice(id: Long) {
        if(id != noticeId) return
        val action = noticeUndo ?: return
        dismissNotice(); action(); save(); render()
    }
    private fun dismissRepeatWarning() {resetWarning?.let {handler.removeCallbacks(it)}; confirmation.dismiss()}
    fun closeRepeat(id: Long) {if(confirmation.consume(id) != null) {dismissRepeatWarning(); render()}}
    fun confirmRepeat(id: Long) {val event = confirmation.consume(id) ?: return; dismissRepeatWarning(); addConfirmed(event.code, event.carrier)}
    fun resolveUnknown(add: Boolean) {val code = pendingUnknown; val carrier = pendingUnknownCarrier; pendingUnknown = null; render(); if(add && code != null) requestKnownAdd(code, carrier)}
    fun addManual(code: String) = requestAdd(code)
    private fun requestAdd(raw: String): Boolean {
        val code = CargoModel.normalize(raw)
        if(!CargoModel.valid(code)) {message(getString(if(code.isEmpty()) R.string.barcode_required else R.string.barcode_invalid)); return false}
        val carrier = model.selected
        if(carrier < 0) {message(getString(R.string.select_carrier_first)); return false}
        if(!rules.validLength(code, carrier)) {flashFeedback(2); message(getString(R.string.invalid_length)); return false}
        if(pendingUnknown != null) return false
        // Duplicates are checked before unknown-prefix prompts, and never auto-added.
        if(model.items.any {it.code == code}) return requestKnownAdd(code, carrier)
        if(!rules.matches(code, carrier)) {
            flashFeedback(2)
            if(isUnknownSoundEnabled) warningTone()
            if(isUnknownConfirmationEnabled) {pendingUnknown = code; pendingUnknownCarrier = carrier; render(); return true}
        }
        return requestKnownAdd(code, carrier)
    }
    private fun requestKnownAdd(code: String, carrier: Int): Boolean {
        if(model.items.any {it.code == code}) {
            flashFeedback(2)
            if(!isRepeatWarningEnabled) return true
            if(!confirmation.offer(code, carrier)) return true
            dismissNotice(); render()
            val id = confirmation.get()!!.id
            val accessibility = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
            val timeout = if(Build.VERSION.SDK_INT >= 29) accessibility.getRecommendedTimeoutMillis(10000, AccessibilityManager.FLAG_CONTENT_TEXT or AccessibilityManager.FLAG_CONTENT_CONTROLS) else 10000
            resetWarning = Runnable {closeRepeat(id)}.also {handler.postDelayed(it, timeout.toLong())}
            return true
        }
        return addConfirmed(code, carrier)
    }
    private fun addConfirmed(code: String, carrier: Int): Boolean {
        if(!CargoModel.valid(code) || !rules.validLength(code, carrier)) {message(getString(R.string.invalid_length)); return false}
        model.addForCarrier(code, carrier)
        save(); message(getString(R.string.barcode_added, CargoModel.NAMES[carrier]))
        flashFeedback(if(rules.matches(code, carrier)) 1 else 2)
        vibrateSuccess() // Only committed mutations reach this point, including explicit Add again.
        return true
    }
    private fun vibrateSuccess() {
        val vibrator = getSystemService(VIBRATOR_SERVICE) as? Vibrator ?: return
        if(vibrator.hasVibrator()) {
            if(Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            else @Suppress("DEPRECATION") vibrator.vibrate(50)
        }
    }
    private fun warningTone() {
        runCatching {
            val tone = android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 80)
            tone.startTone(android.media.ToneGenerator.TONE_PROP_NACK, 200)
            scope.launch {try {delay(300)} finally {tone.release()}}
        }
    }
    private fun flashFeedback(result: Int) {
        resetFeedback?.let {handler.removeCallbacks(it)}
        scanFeedback = result; render()
        resetFeedback = Runnable {scanFeedback = 0; render()}.also {handler.postDelayed(it, 850)}
    }
    /** Already-consistent detection event, kept separate from frame consensus for testability. */
    fun handleDecodedBarcode(code: String?, now: Long) {
        // A rejected length must never replace the identity lock of a valid package still in view.
        if(code!=null&&(!CargoModel.valid(code)||model.selected<0||!rules.validLength(code,model.selected))) {
            scanGate.present()
            if(lastRejectedCode!=code){lastRejectedCode=code;requestAdd(code)}
            return
        }
        if(code!=null)lastRejectedCode=null
        if(scanGate.observe(code, now) == ScanGate.Decision.ADD && code != null) requestAdd(code)
    }
    fun handleCameraFrame(code: String?, now: Long, stableFrame: Boolean = true) {
        if(code==null&&!stableFrame){consensus.observe(null,now);scanGate.pause();return}
        if(code == null) {consensus.observe(null,now); scanGate.observe(null, now); return}
        scanGate.present()
        if(!CargoModel.valid(code)) {consensus.reset(); return}
        val stable = consensus.observe(code, now) ?: return
        // Delay controls new events, never absence evidence or the identity lock.
        if(rules.validLength(stable,model.selected) && CargoModel.valid(stable)) {
            if(scanGate.isHeld(stable) || !scanCooldown.shouldProcess(now,scanDelayMs)) return
        }
        handleDecodedBarcode(stable, now)
    }
    fun createPreview(): PreviewView = PreviewView(this).also {view ->
        previewView = view; view.implementationMode = PreviewView.ImplementationMode.COMPATIBLE; view.scaleType = PreviewView.ScaleType.FILL_CENTER
        view.previewStreamState.observe(this) {state ->
            if(previewView === view) {val ready = running && state == PreviewView.StreamState.STREAMING; if(ready != previewReady) {previewReady = ready; render()}}
        }
    }
    fun toggleCamera() {if(isCameraActive) {stopCamera(); message(getString(R.string.camera_stopped))} else startCamera()}
    fun pauseScanning() {stopCamera()}
    private fun startCamera() {
        if(model.selected < 0) {message(getString(R.string.select_carrier_first)); return}
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_REQUEST); return}
        starting = true; previewReady = false; frameVersion++; val token = ++session
        throttle.reset(); consensus.reset(); render(); message(getString(R.string.camera_starting))
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({if(token == session && starting) runCatching {cameraProvider = future.get(); bindCamera(token)}.onFailure {stopCamera(); message(getString(R.string.camera_failed))}}, ContextCompat.getMainExecutor(this))
    }
    private fun bindCamera(token: Int) {
        val view = previewView ?: return
        val provider = cameraProvider ?: return
        // Restore the resolution negotiation used by the known-working v0.5 camera pipeline.
        val resolution = ResolutionSelector.Builder().setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY).build()
        val preview = Preview.Builder().setResolutionSelector(resolution).build().also {it.setSurfaceProvider(view.surfaceProvider)}
        val analysis = ImageAnalysis.Builder().setResolutionSelector(resolution).setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
        var frames=0
        var lastReport=0L
        scannerStatus=getString(R.string.waiting_camera_frames)
        analysis.setAnalyzer(cameraExecutor) {image ->
            try {
                val now=SystemClock.elapsedRealtime()
                if(token == session && running && throttle.shouldProcess(now, 75)) {
                    frames++
                    val value = decoder.decode(image);val stableFrame=decoder.isFrameStable
                    val took=SystemClock.elapsedRealtime()-now
                    val count=frames;val report=now-lastReport>=1000||value!=null
                    if(report)lastReport=now
                    handler.post {if(token == session && running) {
                        if(report)scannerStatus=getString(if(value==null)R.string.analyzing_camera_frames else R.string.camera_decode_detected,count,took)
                        handleCameraFrame(value, now, stableFrame)
                        if(report)render()
                    }}
                }
            } catch(error: Exception) {handler.post {if(token == session && running) {
                consensus.reset();scanGate.pause()
                scannerStatus=getString(R.string.camera_analysis_error,error.javaClass.simpleName);render()
            }}} // Decode failures are not proof of absence; report rather than silently swallowing them.
            finally {image.close()}
        }
        provider.unbindAll(); provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        running = true; starting = false; previewReady = view.previewStreamState.value == PreviewView.StreamState.STREAMING
        scanGate.pause(); root?.keepScreenOn = true; resolutionMessage = getString(R.string.offline_scanning); render()
        message(getString(R.string.camera_on))
    }
    fun persistPausedFrame(frame: Bitmap) {
        val file = AtomicFile(File(filesDir, "paused-frame.png"))
        runCatching {
            val output = file.startWrite()
            try {if(!frame.compress(Bitmap.CompressFormat.PNG, 100, output)) throw IOException(); file.finishWrite(output)}
            catch(error: Exception) {file.failWrite(output)}
        }
    }
    private fun stopCamera() {
        val captured = if(running) runCatching {previewView?.bitmap}.getOrNull() else null
        if(captured != null && !cameraExecutor.isShutdown) {
            val version = ++frameVersion
            cameraExecutor.execute {
                val blurred = FrameBlur.create(captured); persistPausedFrame(blurred)
                handler.post {if(version == frameVersion && !running && !starting && !isDestroyed) {pausedFrame = blurred; render()}}
            }
        }
        session++; running = false; starting = false; previewReady = false
        scannerStatus=""
        cameraProvider?.unbindAll(); root?.keepScreenOn = false; scanFeedback = 0
        dismissRepeatWarning(); scanGate.pause(); consensus.reset(); render()
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if(requestCode == CAMERA_REQUEST) {if(grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {if(page == 0) startCamera()} else message(getString(R.string.camera_denied))}
    }
    override fun onConfigurationChanged(newConfig: Configuration) {super.onConfigurationChanged(newConfig); render()}
    override fun onStop() {stopCamera(); super.onStop()}
    override fun onDestroy() {scope.cancel(); handler.removeCallbacksAndMessages(null); cameraExecutor.shutdown(); super.onDestroy()}
    override fun onSaveInstanceState(outState: Bundle) {pendingExport?.let {outState.putString("pendingExport", it.absolutePath)}; super.onSaveInstanceState(outState)}
    private fun mime(extension: String) = if(extension == "xlsx") "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" else "text/plain"
    fun exportAction(extension: String, share: Boolean) {
        stopCamera(); if(model.items.isEmpty() || exporting || pendingExport != null) return
        exporting = true
        val snapshot = CargoModel().also {copy -> copy.items.addAll(model.items.map {it.copy()})}
        showNotice(getString(R.string.preparing_file))
        scope.launch {
            try {
                val output = withContext(Dispatchers.IO) {
                    val directory = File(cacheDir, "exports").apply {mkdirs()}
                    File(directory, "barkodu-${SimpleDateFormat("yyyy-MM-dd-HHmmss-SSS", Locale.ROOT).format(Date())}.$extension").also {file ->
                        file.outputStream().use {stream -> if(extension == "xlsx") XlsxExport.write(snapshot, stream) else stream.write(("\uFEFF" + snapshot.text()).toByteArray(Charsets.UTF_8))}
                    }
                }
                exporting = false
                if(share) {
                    val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.files", output)
                    val intent = Intent(Intent.ACTION_SEND).setType(mime(extension)).putExtra(Intent.EXTRA_STREAM, uri).apply {clipData=ClipData.newRawUri(getString(R.string.carrier_list), uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)}
                    startActivity(Intent.createChooser(intent, getString(R.string.share_carrier_list)))
                    showNotice(getString(R.string.share_ready))
                } else {
                    pendingExport = output
                    startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mime(extension)).putExtra(Intent.EXTRA_TITLE, output.name), SAVE_REQUEST)
                    showNotice(getString(R.string.choose_save_location))
                }
            } catch(cancelled: CancellationException) {throw cancelled}
            catch(error: Exception) {exporting = false; pendingExport = null; showNotice(getString(R.string.export_failed))}
        }
    }
    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode != SAVE_REQUEST) return
        val input = pendingExport; pendingExport = null
        val uri = data?.data
        if(resultCode != RESULT_OK || uri == null) {showNotice(getString(R.string.save_cancelled)); return}
        if(input == null) {showNotice(getString(R.string.export_unavailable)); return}
        scope.launch {
            try {
                withContext(Dispatchers.IO) {input.inputStream().use {source -> (contentResolver.openOutputStream(uri) ?: throw IOException()).use {destination -> source.copyTo(destination)}}}
                showNotice(getString(R.string.file_saved))
            } catch(cancelled: CancellationException) {throw cancelled}
            catch(error: Exception) {showNotice(getString(R.string.save_failed))}
        }
    }
}
