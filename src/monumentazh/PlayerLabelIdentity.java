package monumentazh;

import net.minecraft.*;

/** Render-only evidence for auxiliary player name rows; never changes the identity catalogue. */
public final class PlayerLabelIdentity {
    private PlayerLabelIdentity() {}
    private static final java.util.regex.Pattern ACCOUNT_ROW=java.util.regex.Pattern.compile("[A-Za-z0-9_]{1,16}");

    public static boolean preserve(class_1297 owner, class_2561 label) {
        if (owner instanceof class_1657) return true;
        if (!(owner instanceof class_1531 || owner instanceof class_8113.class_8123) || label == null) return false;
        var client = class_310.method_1551();
        if (client == null || client.method_1562() == null || owner.method_37908() == null) return false;
        String row = TranslationEngine.normalize(label.getString());
        // Auxiliary rows require a unique online owner and a co-axial account row.
        
        if (row.isBlank()) return false;
        double x=owner.method_23317(), y=owner.method_23318(), z=owner.method_23321();
        class_1657 selected=null;
        // The client already maintains the complete player list. Avoid allocating and
        // filtering a general entity query for every rendered hologram in a crowded market.
        boolean clientWorld=owner.method_37908()==client.field_1687;
        Iterable<? extends class_1297> candidates=clientWorld?client.field_1687.method_18456():
            owner.method_37908().method_8333(owner,
                new class_238(x-0.8,y-3.2,z-0.8,x+0.8,y-1.3,z+0.8), e -> e instanceof class_1657);
        for (class_1297 nearby : candidates) {
            if(clientWorld&&!nearby.method_5829().method_1003(x-0.8,y-3.2,z-0.8,x+0.8,y-1.3,z+0.8))continue;
            if (!inHeadSpace(x,y,z,nearby.method_23317(),nearby.method_23318(),nearby.method_23321())) continue;
            // Overlapping players make ownership ambiguous, even if only one name happens to match.
            if (selected != null) return false;
            selected=(class_1657)nearby;
        }
        if (selected == null || selected.method_7334() == null) return false;
        if (!sameRow(row,selected.method_7334().getName()) && (Math.abs(x-selected.method_23317())>0.15 || Math.abs(z-selected.method_23321())>0.15)) return false;
        var profile=selected.method_7334();
        if(profile.getId()==null)return false;
        var entry=client.method_1562().method_2871(profile.getId());
        var online=entry==null?null:entry.method_2966();
        return online!=null&&profile.getId().equals(online.getId())
            &&(sameRow(row,online.getName())||hasNameCompanion(owner,selected));
    }

    private static boolean hasNameCompanion(class_1297 owner, class_1657 player) {
        double x=owner.method_23317(), y=owner.method_23318(), z=owner.method_23321();
        for (class_1297 row : owner.method_37908().method_8333(owner,
                new class_238(x-0.15,y-0.55,z-0.15,x+0.15,y+0.55,z+0.15), e -> e instanceof class_1531 || e instanceof class_8113.class_8123)) {
            class_2561 name = row instanceof class_8113.class_8123 display && display.method_49784()!=null ? display.method_49784().comp_1334() : row.method_5797();
            if (name != null && Math.abs(row.method_23317()-x)<=0.15
                    && Math.abs(row.method_23321()-z)<=0.15
                    && Math.abs(row.method_23318()-y)<=0.55
                    && sameRow(TranslationEngine.normalize(name.getString()), player.method_7334().getName())) return true;
        }
        return false;
    }

    static boolean sameRow(String row,String profile) {
        return profile != null && row.equals(profile) && ACCOUNT_ROW.matcher(row).matches();
    }
    static boolean inHeadSpace(double x,double y,double z,double px,double py,double pz) {
        double dx=x-px,dz=z-pz,dy=y-py;
        return dx*dx+dz*dz<=0.8*0.8 && dy>=1.3 && dy<=3.2;
    }
}




