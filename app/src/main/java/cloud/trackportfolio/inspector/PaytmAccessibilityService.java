package cloud.trackportfolio.inspector;

import android.accessibilityservice.AccessibilityService;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.os.Handler;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.util.List;

/**
 * User-armed, local, read-only diagnostic of Android-exposed Paytm screens.
 * NEVER reads another app's protected private files or performs UI actions.
 */
public class PaytmAccessibilityService extends AccessibilityService {
    private static final String PAYTM="net.one97.paytm";
    private long lastInspection=0;
    private long lastScreenshot=0;

    @Override protected void onServiceConnected(){
        super.onServiceConnected();
        getSharedPreferences("local",MODE_PRIVATE).edit()
            .putBoolean("service_connected",true).apply();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event){
        if(event==null || event.getPackageName()==null ||
            !PAYTM.contentEquals(event.getPackageName())) return;
        SharedPreferences prefs=getSharedPreferences("local",MODE_PRIVATE);
        long now=System.currentTimeMillis();
        if(!prefs.getBoolean("consent",false) || now>=prefs.getLong("armed_until",0))return;
        prefs.edit().putInt("paytm_events",prefs.getInt("paytm_events",0)+1)
            .putString("last_event","type="+event.getEventType()+
                " class="+event.getClassName()+" time="+now).apply();
        StringBuilder evtext=new StringBuilder("PAYTM ACCESSIBILITY EVENTS\n");
        if(event.getContentDescription()!=null)
            evtext.append("description=").append(event.getContentDescription()).append('\n');
        if(event.getText()!=null){
            for(CharSequence t:event.getText())if(t!=null)
                evtext.append("eventText=").append(t).append('\n');
        }
        if(evtext.length()>32){
            String old=prefs.getString("event_capture","");
            String joined=(old+"\n"+evtext).trim();
            prefs.edit().putString("event_capture",
                joined.substring(Math.max(0,joined.length()-18000))).apply();
        }
        if(now-lastInspection<700)return;
        lastInspection=now;
        try{
            StringBuilder output=new StringBuilder("PAYTM EXPOSED UI HIERARCHIES\n");
            int[] count={0}, windows={0};
            AccessibilityNodeInfo active=getRootInActiveWindow();
            if(active!=null && active.getPackageName()!=null && PAYTM.contentEquals(active.getPackageName())){
                output.append("ACTIVE WINDOW\n");
                inspect(active,output,count,0);
                windows[0]++;
            }else{
                output.append("Active window root unavailable or not Paytm\n");
            }
            List<AccessibilityWindowInfo> all=getWindows();
            output.append("Interactive windows returned: ").append(all==null?0:all.size()).append('\n');
            if(all!=null)for(AccessibilityWindowInfo w:all){
                AccessibilityNodeInfo root=w.getRoot();
                if(root==null || root.getPackageName()==null || !PAYTM.contentEquals(root.getPackageName()))continue;
                windows[0]++;
                output.append("WINDOW id=").append(w.getId()).append(" type=").append(w.getType())
                    .append(" title=").append(w.getTitle()).append('\n');
                inspect(root,output,count,0);
            }
            String capture=output.substring(0,Math.min(output.length(),48000));
            int score=(Parser.hasCode(capture)?10000:0)
                +(Parser.hasRef(capture)?2000:0)
                +Math.min(count[0],500);
            int previous=prefs.getInt("capture_score",-1);
            SharedPreferences.Editor edit=prefs.edit()
                .putInt("node_count",count[0]).putInt("window_count",windows[0]);
            if(score>=previous)edit.putString("capture",capture)
                .putInt("capture_score",score).putLong("capture_at",now);
            edit.apply();
        }catch(Exception ex){
            prefs.edit().putString("capture_error",ex.getClass().getSimpleName()+
                ": "+ex.getMessage()).apply();
        }
        // A small number of consented screen captures; never bypass protected windows.
        if(Build.VERSION.SDK_INT>=30 && now-lastScreenshot>9000 &&
                prefs.getInt("screenshot_attempts",0)<3){
            lastScreenshot=now;
            attemptScreenshot(prefs);
        }else if(Build.VERSION.SDK_INT<30){
            prefs.edit().putString("screenshot_status",
                "Automatic accessibility screenshot requires Android 11+; use selected image OCR").apply();
        }
    }

    private void inspect(AccessibilityNodeInfo node,StringBuilder out,int[] count,int depth){
        if(node==null || depth>28 || count[0]>=750 || out.length()>45000)return;
        count[0]++;
        try{
            out.append('#').append(count[0]).append(' ');
            if(node.getClassName()!=null)out.append(node.getClassName()).append(' ');
            if(node.getViewIdResourceName()!=null)
                out.append("id=").append(shorten(node.getViewIdResourceName())).append(' ');
            if(node.isPassword())out.append("[password redacted]");
            else{
                if(node.getText()!=null)out.append("text=").append(shorten(node.getText().toString())).append(' ');
                if(node.getContentDescription()!=null)out.append("description=")
                    .append(shorten(node.getContentDescription().toString())).append(' ');
                if(node.getHintText()!=null)out.append("hint=")
                    .append(shorten(node.getHintText().toString())).append(' ');
                if(Build.VERSION.SDK_INT>=30 && node.getStateDescription()!=null)
                    out.append("state=").append(shorten(node.getStateDescription().toString())).append(' ');
            }
            Rect rect=new Rect(); node.getBoundsInScreen(rect);
            out.append("bounds=").append(rect.toShortString()).append('\n');
            for(int i=0;i<node.getChildCount();i++){
                if(out.length()>45000||count[0]>=750)break;
                AccessibilityNodeInfo child=node.getChild(i);
                if(child!=null)inspect(child,out,count,depth+1);
            }
        }catch(Exception ignored){out.append("[node inaccessible]\n");}
    }

    private void attemptScreenshot(SharedPreferences prefs){
        int tryCount=prefs.getInt("screenshot_attempts",0)+1;
        prefs.edit().putInt("screenshot_attempts",tryCount)
            .putString("screenshot_status","Screen capture attempt "+tryCount).apply();
        try{
            takeScreenshot(Display.DEFAULT_DISPLAY,command->new Handler(getMainLooper()).post(command),
                new TakeScreenshotCallback(){
                    @Override public void onSuccess(ScreenshotResult result){
                        HardwareBuffer buffer=result.getHardwareBuffer();
                        try{
                            Bitmap hw=Bitmap.wrapHardwareBuffer(buffer,result.getColorSpace());
                            if(hw==null)throw new IllegalStateException("Empty screenshot");
                            Bitmap image=hw.copy(Bitmap.Config.ARGB_8888,false);
                            if(image==null)throw new IllegalStateException("Bitmap conversion failed");
                            prefs.edit().putString("screenshot_status","Screenshot captured; running local OCR").apply();
                            TextRecognizer rec=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
                            rec.process(InputImage.fromBitmap(image,0))
                                .addOnSuccessListener(txt->{
                                    String value=txt.getText();
                                    String previous=prefs.getString("ocr_capture","");
                                    if(Parser.hasCode(value)||!Parser.hasCode(previous)){
                                        prefs.edit().putString("ocr_capture",
                                            value.substring(0,Math.min(value.length(),23000)))
                                            .putLong("capture_at",System.currentTimeMillis()).apply();
                                    }
                                    prefs.edit().putString("screenshot_status",
                                        "OCR success; recognized "+value.length()+" characters").apply();
                                    image.recycle();rec.close();
                                }).addOnFailureListener(err->{
                                    prefs.edit().putString("screenshot_status",
                                        "OCR failed: "+err.getClass().getSimpleName()).apply();
                                    image.recycle();rec.close();
                                });
                        }catch(Exception ex){
                            prefs.edit().putString("screenshot_status",
                                "Screenshot decode failed: "+ex.getClass().getSimpleName()).apply();
                        }finally{if(buffer!=null)buffer.close();}
                    }
                    @Override public void onFailure(int errorCode){
                        String explanation=errorCode==6?"SECURE_WINDOW: Paytm/Android prevents screenshot":
                            "Android screenshot API error "+errorCode;
                        prefs.edit().putString("screenshot_status",explanation).apply();
                    }
                });
        }catch(Exception ex){
            prefs.edit().putString("screenshot_status",
                "Screenshot not permitted: "+ex.getClass().getSimpleName()).apply();
        }
    }
    private static String shorten(String s){
        s=s.replace('\n',' ').replace('\r',' ');
        return s.substring(0,Math.min(s.length(),350));
    }
    @Override public void onInterrupt(){}
    @Override public void onDestroy(){
        getSharedPreferences("local",MODE_PRIVATE).edit().putBoolean("service_connected",false).apply();
        super.onDestroy();
    }
}
