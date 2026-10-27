package monumentazh;

import net.minecraft.*;
import java.util.*;

/** Fixed server hologram rows cannot accept another line. Nearby checks run at most once a second. */
public final class HologramLayout {
    private HologramLayout() {}
    private record Clearance(long time,double x,double y,double z,boolean clear) {}
    private static final Map<class_1297,Clearance> cache=new LinkedHashMap<>(64,0.75f,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<class_1297,Clearance> e){return size()>512;}
    };
    private static class_1937 world;
    public static void clear(){cache.clear();world=null;}

    public static boolean fixed(class_1297 entity) {return entity instanceof class_1531||entity instanceof class_8113;}

    /** The instance-switching service is Chinese-only, unlike ordinary NPC labels. */
    public static boolean chineseOnly(String raw) {
        String label=TranslationEngine.normalize(raw);
        return label.equals("Overworld Instance")||label.equals("主世界实例");
    }

    public static boolean preferUpper(String raw) {
        return false;
    }

    public static boolean allowsUpper(class_1297 entity) {
        if(fixed(entity))return false;
        class_1937 current=entity.method_37908();
        if(current==null)return true;
        if(world!=current){cache.clear();world=current;}
        long time=current.method_8510();
        double x=entity.method_23317(),y=entity.method_23318()+entity.method_51152(),z=entity.method_23321();
        Clearance old=cache.get(entity);
        if(old!=null&&time>=old.time()&&time-old.time()<20&&old.x()==x&&old.y()==y&&old.z()==z)return old.clear();
        boolean clear=true;
        for(class_1297 nearby:current.method_8333(entity,new class_238(x-1.25,y-3,z-1.25,x+1.25,y+1.25,z+1.25),
            e->e instanceof class_8113.class_8123||!(e instanceof class_1657)&&e.method_5805())) {
            double otherY=nearby.method_23318()+nearby.method_51152();
            // Text displays have their own scale/transform; conservatively reserve their nearby footprint.
            if(nearby instanceof class_8113||blocksUpper(x,y,z,nearby.method_23317(),otherY,nearby.method_23321())) {clear=false;break;}
        }
        cache.put(entity,new Clearance(time,x,y,z,clear));return clear;
    }

    static boolean blocksUpper(double x,double y,double z,double otherX,double otherY,double otherZ) {
        double dx=x-otherX,dz=z-otherZ,dy=otherY-y;
        // Vanilla label scale is 0.025 blocks/pixel; a 9px row plus padding occupies about 0.25 blocks.
        return dx*dx+dz*dz<=1.25*1.25&&dy>=-0.10&&dy<=0.75;
    }
}
