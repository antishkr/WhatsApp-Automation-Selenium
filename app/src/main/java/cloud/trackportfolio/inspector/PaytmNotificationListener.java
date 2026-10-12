package cloud.trackportfolio.inspector;

import android.app.Notification;
import android.os.Bundle;
import android.os.Parcelable;
import android.content.SharedPreferences;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public class PaytmNotificationListener extends NotificationListenerService {
    @Override public void onListenerConnected(){
        super.onListenerConnected();
        try{for(StatusBarNotification s:getActiveNotifications()) capture(s);}catch(Exception ignored){}
    }
    @Override public void onNotificationPosted(StatusBarNotification s){capture(s);}
    private void capture(StatusBarNotification sbn){
        if(sbn==null||!"net.one97.paytm".equals(sbn.getPackageName()))return;
        SharedPreferences p=getSharedPreferences("local",MODE_PRIVATE);
        if(!p.getBoolean("consent",false))return;
        Notification n=sbn.getNotification();
        if(n==null)return;
        StringBuilder b=new StringBuilder("Paytm notification\n");
        b.append("key=").append(sbn.getKey()).append("\npostTime=").append(sbn.getPostTime());
        b.append("\nchannel=").append(n.getChannelId()).append("\ncategory=").append(n.category);
        b.append("\nflags=").append(n.flags).append("\n");
        if(n.tickerText!=null)b.append("ticker=").append(shorten(n.tickerText.toString())).append("\n");
        flatten("extras",n.extras,b,0);
        if(n.actions!=null)for(int i=0;i<n.actions.length;i++){
            if(n.actions[i].title!=null)b.append("action").append(i).append("=")
                .append(shorten(n.actions[i].title.toString())).append("\n");
        }
        p.edit().putString("notification",b.substring(0,Math.min(29000,b.length()))).apply();
    }
    @SuppressWarnings("deprecation")
    private void flatten(String prefix,Bundle bundle,StringBuilder b,int depth){
        if(bundle==null||depth>5||b.length()>26000)return;
        for(String key:bundle.keySet()){
            Object v;
            try{v=bundle.get(key);}catch(Exception ex){continue;}
            String path=prefix+"."+key;
            if(v instanceof Bundle) flatten(path,(Bundle)v,b,depth+1);
            else if(v instanceof CharSequence)b.append(path).append("=").append(shorten(v.toString())).append("\n");
            else if(v instanceof CharSequence[]){
                CharSequence[] values=(CharSequence[])v;
                for(int i=0;i<Math.min(values.length,25);i++)
                    if(values[i]!=null)b.append(path).append("[").append(i).append("]=").append(shorten(values[i].toString())).append("\n");
            }else if(v instanceof Parcelable[]){
                Parcelable[] values=(Parcelable[])v;
                for(int i=0;i<Math.min(values.length,25);i++)
                    if(values[i] instanceof Bundle)flatten(path+"["+i+"]",(Bundle)values[i],b,depth+1);
            }else if(v!=null)b.append(path).append(" type=").append(v.getClass().getSimpleName()).append("\n");
            if(b.length()>26000)break;
        }
    }
    private String shorten(String s){s=s.replace('\n',' ').replace('\r',' ');return s.substring(0,Math.min(500,s.length()));}
}
