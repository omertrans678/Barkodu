package com.omerceren.barkodu;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.activity.ComponentActivity;
import androidx.annotation.NonNull;
import androidx.camera.core.*;
import androidx.camera.core.resolutionselector.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.*;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends ComponentActivity {
    private static final int CAMERA_REQUEST=10, SAVE_REQUEST=20;
    private final CargoModel model=new CargoModel();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService cameraExecutor=Executors.newSingleThreadExecutor();
    private final ExecutorService fileExecutor=Executors.newSingleThreadExecutor();
    private LinearLayout root;
    private PreviewView previewView;
    private android.graphics.Bitmap pausedFrame;
    private int frameVersion;
    public android.graphics.Bitmap getPausedFrame(){return pausedFrame;}
    private ProcessCameraProvider cameraProvider;
    private BarcodeScanner scanner;
    private boolean running=false, starting=false;
    private int session=0;
    private final ScanGate scanGate=new ScanGate();
    private boolean repeatWarningEnabled=true, repeatWarningVisible=false;
    private Runnable resetWarning;
    private final RepeatConfirmation confirmation=new RepeatConfirmation();
    private Runnable noticeUndo;
    private String notice="";
    private long noticeId;
    private Runnable resetNotice;
    private int page=0;
    private int themeMode=0;
    private File pendingExport;
    private boolean exporting=false;
    private Runnable resetFeedback;

    private void message(String value){statusMessage=value;render();}

    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);load();repeatWarningEnabled=getPreferences(MODE_PRIVATE).getBoolean("repeatWarningEnabled",true);
        page=getPreferences(MODE_PRIVATE).getInt("page",0);
        themeMode=getPreferences(MODE_PRIVATE).getInt("themeMode",0);
        if(themeMode<0||themeMode>2)themeMode=0;
        if(saved!=null){String pending=saved.getString("pendingExport");if(pending!=null)pendingExport=new File(pending);}
        buildUi();render();
    }
    private MiuixUi ui;
    private String statusMessage="Select a carrier to start.", resolutionMessage="Offline scanning";
    private boolean successFeedback=false;
    private void buildUi(){
        ui=new MiuixUi(this);
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);setContentView(root);
        root.addView(ui.createView(),new LinearLayout.LayoutParams(-1,-1));
        if(model.selected>=0)statusMessage="Carrier ready. You can open the camera.";
    }
    private void render(){if(ui!=null)ui.update();}
    public CargoModel getModel(){return model;}
    public String getStatusMessage(){return statusMessage;}
    public String getResolutionMessage(){return resolutionMessage;}
    public boolean isCameraActive(){return running||starting;}
    public boolean isSuccessFeedback(){return successFeedback;}
    public boolean isRepeatWarningEnabled(){return repeatWarningEnabled;}
    public boolean isRepeatWarningVisible(){return repeatWarningVisible;}
    public RepeatConfirmation.Event getPendingRepeat(){return confirmation.get();}
    public int getPage(){return page;}
    public int getThemeMode(){return themeMode;}
    public void setThemeMode(int value){if(value<0||value>2)return;themeMode=value;getPreferences(MODE_PRIVATE).edit().putInt("themeMode",value).apply();render();}
    public void pauseScanning(){stopCamera();}
    public void navigate(int value){if(value!=0)stopCamera();page=value;getPreferences(MODE_PRIVATE).edit().putInt("page",value).apply();render();}
    public String getNotice(){return notice;}
    public long getNoticeId(){return noticeId;}
    public boolean isNoticeUndoAvailable(){return noticeUndo!=null;}
    public void dismissNotice(){if(resetNotice!=null)handler.removeCallbacks(resetNotice);notice="";noticeUndo=null;render();}
    private void showNotice(String text,Runnable undo){
        dismissNotice();notice=text;noticeUndo=undo;noticeId++;render();
        resetNotice=this::dismissNotice;handler.postDelayed(resetNotice,7000);
    }
    public void undoNotice(long id){if(id!=noticeId||noticeUndo==null)return;Runnable action=noticeUndo;dismissNotice();action.run();save();render();}
    public void closeRepeat(long id){if(confirmation.consume(id)!=null){dismissRepeatWarning();render();}}
    public void confirmRepeat(long id){RepeatConfirmation.Event event=confirmation.consume(id);if(event==null)return;dismissRepeatWarning();addConfirmed(event.code,event.carrier);}
    public void setRepeatWarningEnabled(boolean enabled){
        repeatWarningEnabled=enabled;getPreferences(MODE_PRIVATE).edit().putBoolean("repeatWarningEnabled",enabled).apply();
        if(!enabled)dismissRepeatWarning();render();
    }
    private void dismissRepeatWarning(){if(resetWarning!=null)handler.removeCallbacks(resetWarning);confirmation.dismiss();repeatWarningVisible=false;}
    private boolean requestAdd(String raw){
        String code=CargoModel.normalize(raw);
        if(!CargoModel.valid(code)){message(code.isEmpty()?"Barcode is required.":"Use 3–64 letters or digits.");return false;}
        if(model.selected<0){message("Select a carrier first.");return false;}
        boolean duplicate=false;for(CargoModel.Item item:model.items)if(item.code.equals(code)){duplicate=true;break;}
        if(duplicate&&repeatWarningEnabled){
            if(!confirmation.offer(code,model.selected)){message("Resolve the current repeat warning first.");return false;}
            dismissNotice();repeatWarningVisible=true;render();
            long id=confirmation.get().id;
            int timeout=10000;
            if(Build.VERSION.SDK_INT>=29){android.view.accessibility.AccessibilityManager accessibility=(android.view.accessibility.AccessibilityManager)getSystemService(ACCESSIBILITY_SERVICE);timeout=accessibility.getRecommendedTimeoutMillis(timeout,android.view.accessibility.AccessibilityManager.FLAG_CONTENT_TEXT|android.view.accessibility.AccessibilityManager.FLAG_CONTENT_CONTROLS);}
            resetWarning=()->closeRepeat(id);handler.postDelayed(resetWarning,timeout);return true;
        }
        return addConfirmed(code,model.selected);
    }
    void handleDecodedBarcode(String code,long now){
        ScanGate.Decision decision=scanGate.observe(code,now,repeatWarningEnabled);
        if(decision==ScanGate.Decision.ADD)requestAdd(code);
    }
    public PreviewView createPreview(){
        previewView=new PreviewView(this);previewView.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);previewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);return previewView;
    }
    public void selectCargo(int id){model.selected=id;save();render();message(CargoModel.NAMES[id]+" selected.");}
    public void selectTab(int id){model.active=id;save();render();}
    public void toggleCamera(){if(running||starting){stopCamera();message("Camera stopped.");}else startCamera();}
    public void undoAction(){if(model.undo()){render();save();message("Last action undone.");}}
    public void removeItem(CargoModel.Item item){for(CargoModel.Item stored:model.items)if(stored.id==item.id){int index=model.items.indexOf(stored);model.remove(stored);save();showNotice("Barcode deleted",()->model.restore(stored,index));return;}}
    public void clearAction(){ArrayList<CargoModel.Item> before=new ArrayList<>(model.items);dismissRepeatWarning();model.clear();save();showNotice("All lists cleared",()->{for(int i=0;i<before.size();i++)model.restore(before.get(i),i);});}
    public boolean addManual(String code){return requestAdd(code);}
    public void exportAction(String extension,boolean share){export(extension,share);}
    private boolean addConfirmed(String raw,int carrier){try{CargoModel.Item item=model.addForCarrier(raw,carrier);render();save();message(CargoModel.NAMES[item.cargo]+" · Barcode added");flash();return true;}catch(IllegalArgumentException error){message(error.getMessage());return false;}}
    private void flash(){
        if(resetFeedback!=null)handler.removeCallbacks(resetFeedback);successFeedback=true;render();
        resetFeedback=()->{successFeedback=false;render();};handler.postDelayed(resetFeedback,500);
        android.os.Vibrator vibrator=(android.os.Vibrator)getSystemService(VIBRATOR_SERVICE);if(vibrator!=null&&vibrator.hasVibrator()){if(Build.VERSION.SDK_INT>=26)vibrator.vibrate(VibrationEffect.createOneShot(50,VibrationEffect.DEFAULT_AMPLITUDE));else vibrator.vibrate(50);}
    }
    private void startCamera(){
        if(model.selected<0){message("Select a carrier first.");return;}
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.CAMERA},CAMERA_REQUEST);return;}
        starting=true;frameVersion++;int token=++session;render();message("Starting camera…");ListenableFuture<ProcessCameraProvider> future=ProcessCameraProvider.getInstance(this);
        future.addListener(()->{if(token!=session||!starting)return;try{cameraProvider=future.get();bindCamera(token);}catch(Exception error){stopCamera();message("Could not start the camera. Try again.");}},ContextCompat.getMainExecutor(this));
    }
    private void bindCamera(int token){
        if(scanner==null){BarcodeScannerOptions options=new BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_CODE_128,Barcode.FORMAT_CODE_39,Barcode.FORMAT_CODE_93,Barcode.FORMAT_ITF,Barcode.FORMAT_CODABAR).build();scanner=BarcodeScanning.getClient(options);}
        ResolutionSelector max=new ResolutionSelector.Builder().setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY).build();
        Preview preview=new Preview.Builder().setResolutionSelector(max).build();preview.setSurfaceProvider(previewView.getSurfaceProvider());
        ImageAnalysis analysis=new ImageAnalysis.Builder().setResolutionSelector(max).setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();
        analysis.setAnalyzer(cameraExecutor,image->{
            if(token!=session||!running||image.getImage()==null){image.close();return;}
            InputImage input=InputImage.fromMediaImage(image.getImage(),image.getImageInfo().getRotationDegrees());
            scanner.process(input).addOnSuccessListener(results->{
                if(token!=session||!running)return;String code=null;
                for(Barcode value:results){String candidate=CargoModel.normalize(value.getRawValue());if(CargoModel.valid(candidate)){if(code==null)code=candidate;if(CargoModel.classify(candidate,model.selected)==model.selected){code=candidate;break;}}}
                handleDecodedBarcode(code,SystemClock.elapsedRealtime());
            }).addOnFailureListener(error->{/* The next latest frame is retried. */}).addOnCompleteListener(task->image.close());
        });
        cameraProvider.unbindAll();cameraProvider.bindToLifecycle(this,CameraSelector.DEFAULT_BACK_CAMERA,preview,analysis);running=true;starting=false;scanGate.pause();root.setKeepScreenOn(true);render();
        android.util.Size size=analysis.getResolutionInfo()!=null?analysis.getResolutionInfo().getResolution():null;resolutionMessage=size==null?"Built-in barcode scanning":size.getWidth()+" × "+size.getHeight()+" · ML Kit";message("Camera on · Scan horizontally or vertically.");
    }
    private void stopCamera(){
        android.graphics.Bitmap snapshot=null;
        if(running&&previewView!=null)try{snapshot=previewView.getBitmap();}catch(RuntimeException ignored){}
        if(snapshot!=null){
            final android.graphics.Bitmap captured=snapshot;
            final int captureVersion=++frameVersion;
            cameraExecutor.execute(()->{
                android.graphics.Bitmap blurred=FrameBlur.create(captured);
                handler.post(()->{if(captureVersion==frameVersion&&!running&&!starting&&!isDestroyed()){pausedFrame=blurred;render();}});
            });
        }
        session++;running=false;starting=false;if(cameraProvider!=null)cameraProvider.unbindAll();if(root!=null)root.setKeepScreenOn(false);successFeedback=false;dismissRepeatWarning();scanGate.pause();render();}
    @Override public void onRequestPermissionsResult(int request,@NonNull String[] permissions,@NonNull int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==CAMERA_REQUEST){if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED) {if(page==0)startCamera();}else message("Camera permission denied. You can add barcodes manually.");}}
    @Override public void onConfigurationChanged(@NonNull Configuration configuration){super.onConfigurationChanged(configuration);render();}
    @Override protected void onStop(){stopCamera();super.onStop();}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);if(scanner!=null)scanner.close();cameraExecutor.shutdown();fileExecutor.shutdown();super.onDestroy();}
    @Override protected void onSaveInstanceState(@NonNull Bundle out){if(pendingExport!=null)out.putString("pendingExport",pendingExport.getAbsolutePath());super.onSaveInstanceState(out);}

    private void load(){try{JSONObject saved=new JSONObject(getPreferences(MODE_PRIVATE).getString("state","{}"));model.selected=saved.optInt("selected",-1);if(model.selected<0||model.selected>3)model.selected=-1;model.active=saved.optInt("active",model.selected>=0?model.selected:0);if(model.active<0||model.active>4)model.active=0;JSONArray rows=saved.optJSONArray("items");if(rows!=null)for(int i=0;i<rows.length();i++){JSONObject x=rows.getJSONObject(i);String code=CargoModel.normalize(x.optString("code"));int cargo=x.optInt("cargo",4),quantity=x.optInt("quantity",1);if(CargoModel.valid(code)&&cargo>=0&&cargo<=4&&quantity>0)for(int unit=0;unit<quantity;unit++)model.items.add(new CargoModel.Item(code,cargo,1));}}catch(JSONException ignored){}}
    private void save(){try{JSONObject data=new JSONObject();data.put("selected",model.selected);data.put("active",model.active);JSONArray rows=new JSONArray();for(CargoModel.Item item:model.items){JSONObject x=new JSONObject();x.put("code",item.code);x.put("cargo",item.cargo);x.put("quantity",item.quantity);rows.put(x);}data.put("items",rows);getPreferences(MODE_PRIVATE).edit().putString("state",data.toString()).apply();}catch(JSONException error){message("Could not save. Export your list.");}}
    private String mime(String extension){return extension.equals("xlsx")?"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet":"text/plain";}
    private void export(String extension,boolean share){
        stopCamera();if(model.items.isEmpty()||exporting||pendingExport!=null)return;exporting=true;
        CargoModel snapshot=new CargoModel();for(CargoModel.Item item:model.items)snapshot.items.add(item.copy());
        showNotice("Preparing file…",null);
        fileExecutor.execute(()->{
            String date=new SimpleDateFormat("yyyy-MM-dd-HHmmss-SSS",Locale.ROOT).format(new Date());File directory=new File(getCacheDir(),"exports");directory.mkdirs();File output=new File(directory,"barkodu-"+date+"."+extension);
            try{
                if(extension.equals("xlsx")){try(FileOutputStream stream=new FileOutputStream(output)){XlsxExport.write(snapshot,stream);}}
                else try(FileOutputStream stream=new FileOutputStream(output)){stream.write(("\uFEFF"+snapshot.text()).getBytes(StandardCharsets.UTF_8));}
                runOnUiThread(()->{
                    exporting=false;if(isDestroyed()||isFinishing())return;
                    try{
                        if(share){Uri uri=FileProvider.getUriForFile(this,getPackageName()+".files",output);Intent intent=new Intent(Intent.ACTION_SEND);intent.setType(mime(extension));intent.putExtra(Intent.EXTRA_STREAM,uri);intent.setClipData(ClipData.newRawUri("Carrier barcodes",uri));intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(intent,"Share carrier list"));showNotice("Share file ready",null);}
                        else {pendingExport=output;Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType(mime(extension));intent.putExtra(Intent.EXTRA_TITLE,output.getName());startActivityForResult(intent,SAVE_REQUEST);showNotice("Choose where to save",null);}
                    }catch(Exception error){showNotice("No app can handle this file",null);}
                });
            }catch(Exception error){runOnUiThread(()->{exporting=false;showNotice("Could not create the file. Try again.",null);});}
        });
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(request!=SAVE_REQUEST)return;
        File input=pendingExport;pendingExport=null;
        if(result!=RESULT_OK||data==null||data.getData()==null){showNotice("Save cancelled. Your lists are preserved.",null);return;}
        if(input==null){showNotice("Export file is unavailable. Try again.",null);return;}
        Uri output=data.getData();
        fileExecutor.execute(()->{
            try(InputStream in=new FileInputStream(input);OutputStream out=getContentResolver().openOutputStream(output)){
                if(out==null)throw new IOException();byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
                runOnUiThread(()->showNotice("File saved. Your lists are preserved.",null));
            }catch(IOException|SecurityException error){runOnUiThread(()->showNotice("Could not save the file. Try again.",null));}
        });
    }
}



