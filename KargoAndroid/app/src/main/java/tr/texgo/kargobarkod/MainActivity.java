package tr.texgo.kargobarkod;

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
    private ProcessCameraProvider cameraProvider;
    private BarcodeScanner scanner;
    private boolean running=false, starting=false;
    private int session=0;
    private final ScanGate scanGate=new ScanGate();
    private boolean repeatWarningEnabled=true, repeatWarningVisible=false;
    private Runnable resetWarning;
    private File pendingExport;
    private Runnable resetFeedback;

    private void message(String value){statusMessage=value;render();}

    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);load();repeatWarningEnabled=getPreferences(MODE_PRIVATE).getBoolean("repeatWarningEnabled",true);
        if(saved!=null){String pending=saved.getString("pendingExport");if(pending!=null)pendingExport=new File(pending);}
        buildUi();render();
    }
    private MiuixUi ui;
    private String statusMessage="Select a carrier below to start.", resolutionMessage="Offline scanning";
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
    public void setRepeatWarningEnabled(boolean enabled){
        repeatWarningEnabled=enabled;getPreferences(MODE_PRIVATE).edit().putBoolean("repeatWarningEnabled",enabled).apply();
        if(!enabled)dismissRepeatWarning();render();
    }
    private void dismissRepeatWarning(){if(resetWarning!=null)handler.removeCallbacks(resetWarning);repeatWarningVisible=false;}
    void handleDecodedBarcode(String code,long now){
        ScanGate.Decision decision=scanGate.observe(code,now,repeatWarningEnabled);
        if(decision==ScanGate.Decision.ADD){dismissRepeatWarning();add(code);}
        else if(decision==ScanGate.Decision.WARN){
            repeatWarningVisible=true;render();
            resetWarning=()->{repeatWarningVisible=false;render();};handler.postDelayed(resetWarning,1600);
        }
    }
    public PreviewView createPreview(){
        previewView=new PreviewView(this);previewView.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);previewView.setScaleType(PreviewView.ScaleType.FIT_CENTER);return previewView;
    }
    public void selectCargo(int id){model.selected=id;model.active=id;save();render();message(CargoModel.NAMES[id]+" selected. Your list is preserved.");}
    public void selectTab(int id){model.active=id;render();}
    public void toggleCamera(){if(running||starting){stopCamera();message("Camera stopped.");}else startCamera();}
    public void undoAction(){if(model.undo()){render();save();message("Last action undone.");}}
    public void removeItem(CargoModel.Item item){for(CargoModel.Item stored:model.items)if(stored.cargo==item.cargo&&stored.code.equals(item.code)){model.remove(stored);render();save();message("Deleted. Undo is available.");return;}}
    public void clearAction(){model.clear();render();save();message("Cleared. Use Undo to restore.");}
    public boolean addManual(String code){return add(code);}
    public void exportAction(String extension,boolean share){export(extension,share);}
    private boolean add(String raw){try{CargoModel.Item item=model.add(raw);render();save();message(CargoModel.NAMES[item.cargo]+" · "+item.code+(item.quantity>1?" scanned again":" added"));flash();return true;}catch(IllegalArgumentException error){message(error.getMessage());return false;}}
    private void flash(){
        if(resetFeedback!=null)handler.removeCallbacks(resetFeedback);successFeedback=true;render();
        resetFeedback=()->{successFeedback=false;render();};handler.postDelayed(resetFeedback,500);
        android.os.Vibrator vibrator=(android.os.Vibrator)getSystemService(VIBRATOR_SERVICE);if(vibrator!=null&&vibrator.hasVibrator()){if(Build.VERSION.SDK_INT>=26)vibrator.vibrate(VibrationEffect.createOneShot(50,VibrationEffect.DEFAULT_AMPLITUDE));else vibrator.vibrate(50);}
    }
    private void startCamera(){
        if(model.selected<0){message("Select a carrier first.");return;}
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.CAMERA},CAMERA_REQUEST);return;}
        starting=true;int token=++session;render();message("Starting camera…");ListenableFuture<ProcessCameraProvider> future=ProcessCameraProvider.getInstance(this);
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
    private void stopCamera(){session++;running=false;starting=false;if(cameraProvider!=null)cameraProvider.unbindAll();if(root!=null)root.setKeepScreenOn(false);successFeedback=false;dismissRepeatWarning();scanGate.pause();render();}
    @Override public void onRequestPermissionsResult(int request,@NonNull String[] permissions,@NonNull int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==CAMERA_REQUEST){if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)startCamera();else message("Camera permission denied. You can add barcodes manually.");}}
    @Override public void onConfigurationChanged(@NonNull Configuration configuration){super.onConfigurationChanged(configuration);boolean resume=running;stopCamera();buildUi();render();if(resume)startCamera();}
    @Override protected void onStop(){stopCamera();super.onStop();}
    @Override protected void onDestroy(){if(resetFeedback!=null)handler.removeCallbacks(resetFeedback);if(resetWarning!=null)handler.removeCallbacks(resetWarning);if(scanner!=null)scanner.close();cameraExecutor.shutdown();fileExecutor.shutdown();super.onDestroy();}
    @Override protected void onSaveInstanceState(@NonNull Bundle out){if(pendingExport!=null)out.putString("pendingExport",pendingExport.getAbsolutePath());super.onSaveInstanceState(out);}

    private void load(){try{JSONObject saved=new JSONObject(getPreferences(MODE_PRIVATE).getString("state","{}"));model.selected=saved.optInt("selected",-1);if(model.selected<0||model.selected>3)model.selected=-1;model.active=model.selected>=0?model.selected:0;JSONArray rows=saved.optJSONArray("items");if(rows!=null)for(int i=0;i<rows.length();i++){JSONObject x=rows.getJSONObject(i);String code=CargoModel.normalize(x.optString("code"));int cargo=x.optInt("cargo",4),quantity=x.optInt("quantity",1);if(CargoModel.valid(code)&&cargo>=0&&cargo<=4&&quantity>0)model.items.add(new CargoModel.Item(code,cargo,quantity));}}catch(JSONException ignored){}}
    private void save(){try{JSONObject data=new JSONObject();data.put("selected",model.selected);JSONArray rows=new JSONArray();for(CargoModel.Item item:model.items){JSONObject x=new JSONObject();x.put("code",item.code);x.put("cargo",item.cargo);x.put("quantity",item.quantity);rows.put(x);}data.put("items",rows);getPreferences(MODE_PRIVATE).edit().putString("state",data.toString()).apply();}catch(JSONException error){message("Could not save. Export your list.");}}
    private String mime(String extension){return extension.equals("xlsx")?"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet":"text/plain";}
    private void export(String extension,boolean share){
        stopCamera();if(model.items.isEmpty())return;String date=new SimpleDateFormat("yyyy-MM-dd-HHmmss",Locale.ROOT).format(new Date());File directory=new File(getCacheDir(),"exports");directory.mkdirs();File output=new File(directory,"barkodu-"+date+"."+extension);
        try{if(extension.equals("xlsx"))XlsxExport.write(model,new FileOutputStream(output));else try(FileOutputStream stream=new FileOutputStream(output)){stream.write(("\uFEFF"+model.text()).getBytes(StandardCharsets.UTF_8));}
            if(share){Uri uri=FileProvider.getUriForFile(this,getPackageName()+".files",output);Intent intent=new Intent(Intent.ACTION_SEND);intent.setType(mime(extension));intent.putExtra(Intent.EXTRA_STREAM,uri);intent.setClipData(ClipData.newRawUri("Carrier barcodes",uri));intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(intent,"Share carrier list"));}
            else {pendingExport=output;Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType(mime(extension));intent.putExtra(Intent.EXTRA_TITLE,output.getName());startActivityForResult(intent,SAVE_REQUEST);}
        }catch(Exception error){message("Could not create the file. Try again.");}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==SAVE_REQUEST&&result==RESULT_OK&&data!=null&&data.getData()!=null&&pendingExport!=null){File input=pendingExport;Uri output=data.getData();fileExecutor.execute(()->{try(InputStream in=new FileInputStream(input);OutputStream out=getContentResolver().openOutputStream(output)){if(out==null)throw new IOException();byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);runOnUiThread(()->message("File saved. Your lists are preserved."));}catch(IOException error){runOnUiThread(()->message("Could not save the file. Try again."));}});}}
}

