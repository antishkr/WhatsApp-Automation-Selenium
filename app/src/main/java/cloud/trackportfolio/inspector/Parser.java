package cloud.trackportfolio.inspector;
import java.util.regex.*;
import java.util.*;
public final class Parser {
    private Parser(){}
    public static String describe(String raw) {
        if(raw==null||raw.isEmpty()) return "Nothing captured yet.";
        List<String> codes = unique(raw,"\\bDS-[A-Za-z0-9]{6}\\b",0);
        List<String> amounts = unique(raw,"(?:₹|INR\\s*)([0-9][0-9,]*(?:\\.[0-9]{1,2})?)",1);
        List<String> refs = unique(raw,"(?i)(?:UPI\\s*(?:reference|ref|transaction\\s*ID)|UTR|RRN|ref(?:erence)?\\s*(?:no|number)?)[^0-9]{0,24}([0-9]{12})\\b",1);
        return "DS codes: "+join(codes)+"\nAmounts: "+join(amounts)+"\nLabelled 12-digit references: "+join(refs)+
          "\nCAUTION: Screen/notification data alone is not authenticated bank settlement.";
    }
    private static List<String> unique(String s,String re,int group){
        LinkedHashSet<String> out = new LinkedHashSet<>();
        Matcher m=Pattern.compile(re).matcher(s);
        while(m.find()&&out.size()<10) out.add(m.group(group));
        return new ArrayList<>(out);
    }
    private static String join(List<String> values){ return values.isEmpty()?"NOT FOUND":String.join(", ",values); }
    public static boolean hasCode(String text){return text!=null&&Pattern.compile("\\bDS-[A-Za-z0-9]{6}\\b").matcher(text).find();}
    public static boolean hasRef(String text){return text!=null&&Pattern.compile("(?i)(?:reference|UTR|RRN)[^0-9]{0,24}\\d{12}\\b").matcher(text).find();}
}
