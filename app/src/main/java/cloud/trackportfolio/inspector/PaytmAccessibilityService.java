package cloud.trackportfolio.inspector;

import android.accessibilityservice.AccessibilityService;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.Locale;

public class PaytmAccessibilityService extends AccessibilityService {
    private static final String PAYTM="net.one97.paytm";
    private long lastTime=0L;
    @Override public void onAccessibilityEvent(AccessibilityEvent ev) {
        if(ev==null||ev.getPackageName()==null||!PAYTM.contentEquals(ev.getPackageName()))return;
        SharedPreferences p=getSharedPreferences("local",MODE_PRIVATE);
        long now=System.currentTimeMillis();
        if(!p.getBoolean("consent",false)||now>=p.getLong("armed_until",0)||now-lastTime<900)return;
        lastTime=now;
        try {
            AccessibilityNodeInfo root=getRootInActiveWindow();
            if(root==null||root.getPackageName()==null||!PAYTM.contentEquals(root.getPackageName()))return;
            StringBuilder out=new StringBuilder("Visible Paytm accessibility nodes (NOT private app storage):\n");
            int[] count={0};
            walk(root,out,count,0);
            String s=out.toString();
            String prev=p.getString("ui","");
            int score=(Parser.hasCode(s)?1000:0)+(Parser.hasRef(s)?200:0)+(s.contains("₹")?30:0);
            int prevScore=(Parser.hasCode(prev)?1000:0)+(Parser.hasRef(prev)?200:0)+(prev.contains("₹")?30:0);
            if(score>=prevScore){
                p.edit().putString("ui",s).apply();
            }
            if(Parser.hasCode(s)&&Parser.hasRef(s))p.edit().putLong("armed_until",0).apply();
        } catch(Exception e) {
            p.edit().putString("ui_error",e.getClass().getSimpleName()).apply();
        }
    }
    private void walk(AccessibilityNodeInfo n,StringBuilder out,int[] count,int depth) {
        if(n==null||depth>22||count[0]>=350||out.length()>=24000)return;
        count[0]++;
        try {
            out.append("#").append(count[0]).append(" ");
            if(n.getClassName()!=null)out.append(n.getClassName()).append(" ");
            if(n.getViewIdResourceName()!=null)out.append("id=").append(shorten(n.getViewIdResourceName())).append(" ");
            if(n.isPassword())out.append("[password field redacted]");
            else {
                if(n.getText()!=null)out.append("text=").append(shorten(n.getText().toString())).append(" ");
                if(n.getContentDescription()!=null)out.append("description=").append(shorten(n.getContentDescription().toString())).append(" ");
                if(n.getHintText()!=null)out.append("hint=").append(shorten(n.getHintText().toString())).append(" ");
            }
            Rect rect=new Rect();n.getBoundsInScreen(rect);
            out.append("bounds=").append(rect.toShortString()).append("\n");
            for(int i=0;i<n.getChildCount();i++) {
                AccessibilityNodeInfo c=n.getChild(i);
                if(c!=null)walk(c,out,count,depth+1);
            }
        }catch(Exception ignored){}
    }
    private String shorten(String s){
        s=s.replace('\n',' ').replace('\r',' ');
        return s.substring(0,Math.min(400,s.length()));
    }
    @Override public void onInterrupt(){}
}
