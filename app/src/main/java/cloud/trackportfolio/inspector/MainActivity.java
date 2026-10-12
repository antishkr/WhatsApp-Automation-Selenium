package cloud.trackportfolio.inspector;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.provider.Settings;
import android.graphics.Color;
import android.view.View;
import android.widget.*;
import android.app.NotificationManager;
import android.view.accessibility.AccessibilityManager;
import android.accessibilityservice.AccessibilityServiceInfo;

public class MainActivity extends Activity {
    private static final int BG=Color.rgb(13,20,33),FG=Color.rgb(235,241,249);
    private static final String PREF="local";
    private SharedPreferences prefs;
    private TextView status, results;
    private LinearLayout layout;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs=getSharedPreferences(PREF,MODE_PRIVATE);
        getWindow().setStatusBarColor(BG);
        ScrollView scroll=new ScrollView(this);
        layout=new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(24,28,24,40);
        layout.setBackgroundColor(BG);
        scroll.addView(layout);
        setContentView(scroll);

        heading("PAYTM DEEP INSPECTOR V2");
        text("Read-only diagnostic for your receiving Paytm app. No internet permission, no root, no reading Paytm private databases, no payment actions.");
        text("PRIVACY: With your permission, this app can read visible text from Paytm for 180 seconds after you tap Arm, and Paytm notification extras while Notification access is enabled. Financial details stay in this app's private storage and can be deleted.");
        CheckBox consent=new CheckBox(this);
        consent.setText("I consent to local inspection on my own phone");
        consent.setTextColor(FG);
        consent.setChecked(prefs.getBoolean("consent",false));
        consent.setOnCheckedChangeListener((b,c)->{
            prefs.edit().putBoolean("consent",c).apply();
            if(!c) prefs.edit().putLong("armed_until",0L).apply();
        });
        layout.addView(consent);
        button("1. Enable Accessibility Service",()->settings(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        button("2. Enable Notification Access (optional)",()->settings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        text("Sideloaded apps on Android 13+ may need Settings → Apps → Paytm Note Inspector → Allow restricted settings before Accessibility can be enabled. Only enable if you trust this app.");
        status=text("Not armed");
        button("3. ARM 180-SECOND CAPTURE",()->{
            if(!allowed()) return;
            prefs.edit().putLong("armed_until",System.currentTimeMillis()+90000L).remove("ui").remove("ui_error").apply();
            toast("Armed. Open a RECEIVED transaction in Paytm.");
            refresh();
        });
        button("4. Open Paytm",()->{
            if(!allowed())return;
            Intent i=getPackageManager().getLaunchIntentForPackage("net.one97.paytm");
            if(i==null)toast("Paytm not found. Open it manually.");
            else startActivity(i);
        });
        button("Stop capturing",()->{prefs.edit().putLong("armed_until",0L).apply();refresh();});
        button("Inspect a screenshot image using offline OCR",()->{
            if(!allowed())return;
            Intent image=new Intent(Intent.ACTION_OPEN_DOCUMENT);
            image.addCategory(Intent.CATEGORY_OPENABLE);
            image.setType("image/*");
            startActivityForResult(image,901);
        });
        button("Refresh observations",this::refresh);
        results=text("");
        button("Copy redacted summary",()->{
            String t="UI\n"+Parser.describe(prefs.getString("ui",""))+"\nNOTIFICATION\n"+Parser.describe(prefs.getString("notification",""));
            copy(t);
        });
        button("Copy full diagnostics (sensitive)",()->new AlertDialog.Builder(this)
            .setTitle("Sensitive financial information")
            .setMessage("Raw screen and notification data may include names, bank identifiers and transaction details. Keep private and redact before sharing.")
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Copy locally",(d,w)->copy("UI\n"+prefs.getString("ui","")+"\nNOTIFICATION\n"+prefs.getString("notification",""))).show());
        button("Delete captured data",()->{
            prefs.edit().remove("ui").remove("notification").remove("ui_error").putLong("armed_until",0).apply();
            refresh();
            toast("Local data deleted");
        });
        text("This is a diagnostic only. Finding a code in Paytm is not independent proof of bank settlement.");
        refresh();
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request!=901||result!=RESULT_OK||data==null||data.getData()==null||!allowed())return;
        try{
            com.google.mlkit.vision.common.InputImage img=
                com.google.mlkit.vision.common.InputImage.fromFilePath(this,data.getData());
            com.google.mlkit.vision.text.TextRecognizer r=
                com.google.mlkit.vision.text.TextRecognition.getClient(
                  com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS);
            r.process(img).addOnSuccessListener(t->{
                prefs.edit().putString("ocr_capture",t.getText())
                    .putString("screenshot_status","Imported image OCR complete").apply();
                r.close();refresh();
            }).addOnFailureListener(e->{
                prefs.edit().putString("screenshot_status","OCR failed: "+e.getClass().getSimpleName()).apply();
                r.close();refresh();
            });
        }catch(Exception e){
            prefs.edit().putString("screenshot_status","Image unreadable: "+e.getClass().getSimpleName()).apply();
            refresh();
        }
    }
    private boolean allowed(){
        if(prefs.getBoolean("consent",false))return true;
        toast("Tick consent first");return false;
    }
    private void settings(String action){
        if(!allowed())return;
        try {startActivity(new Intent(action));}catch(Exception ex){toast("Unable to open Settings");}
    }
    private void refresh(){
        if(status==null)return;
        long left=Math.max(0,prefs.getLong("armed_until",0)-System.currentTimeMillis());
        status.setText(left>0?"Capture ARMED: "+(left/1000)+" seconds":"Capture not armed");
        if(results==null)return;
        String ui=prefs.getString("ui","");
        String nt=prefs.getString("notification","");
        results.setText("ACCESSIBILITY SERVICE CONNECTED: "+prefs.getBoolean("service_connected",false)+
          "\\nPAYTM EVENTS: "+prefs.getInt("paytm_events",0)+
          "\\nNODE COUNT: "+prefs.getInt("node_count",0)+
          "\\nWINDOW COUNT: "+prefs.getInt("window_count",0)+
          "\\nSCREENSHOT STATUS: "+prefs.getString("screenshot_status","Not attempted")+
          "\\nSCREENSHOT ATTEMPTS: "+prefs.getInt("screenshot_attempts",0)+
          "\\nRECEIVING PAYTM SCREEN\\n"+Parser.describe(ui)+
          "\\nEVENT TEXT\\n"+Parser.describe(prefs.getString("event_capture",""))+
          "\\nOCR\\n"+Parser.describe(prefs.getString("ocr_capture",""))+
          "\\nPAYTM NOTIFICATION\\n"+Parser.describe(nt)+
            "\n\nCaptured Paytm UI nodes:\n"+(ui.isEmpty()?"No screen captured. Arm, then open a received transaction.":ui)+
            "\\n\\nEvent text:\\n"+prefs.getString("event_capture","")+
            "\\n\\nOCR text:\\n"+prefs.getString("ocr_capture","")+
            "\\n\\nLatest Paytm notification:\\n"+(nt.isEmpty()?"No notification available. Old dismissed notifications cannot be recovered.":nt)+
            "\n\nError: "+prefs.getString("ui_error",""));
    }
    private TextView text(String value){
        TextView t=new TextView(this);t.setText(value);t.setTextColor(FG);t.setTextSize(14f);
        t.setTextIsSelectable(true); t.setPadding(0,12,0,12);
        layout.addView(t);return t;
    }
    private void heading(String s){TextView t=text(s);t.setTextSize(22f);}
    private void button(String title,Runnable r){
        Button b=new Button(this);b.setText(title);b.setAllCaps(false);
        layout.addView(b);b.setOnClickListener(v->r.run());
    }
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private void copy(String s){
        ClipboardManager cb=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        cb.setPrimaryClip(ClipData.newPlainText("Paytm diagnostic",s));
        toast("Copied locally; redact private details");
    }
    @Override protected void onResume(){super.onResume();if(status!=null)refresh();}
}
