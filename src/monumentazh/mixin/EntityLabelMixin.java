package monumentazh.mixin;

import monumentazh.MonumentaZh;
import monumentazh.HologramLayout;
import net.minecraft.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = class_897.class, remap = false)
public abstract class EntityLabelMixin {
    @Unique private boolean monumentazh$drawing;
    @Shadow protected abstract void method_3926(class_1297 entity,class_2561 text,class_4587 matrices,class_4597 consumers,int light);
    @Inject(method="method_3926",at=@At("HEAD"),cancellable=true,remap=false)
    private void monumentazh$label(class_1297 entity,class_2561 original,class_4587 matrices,class_4597 consumers,int light,CallbackInfo callback) {
        if(monumentazh$drawing||entity instanceof class_1657)return;
        boolean stacked=HologramLayout.preferUpper(original.getString());
        boolean fixed=HologramLayout.fixed(entity)&&!stacked;
        class_2561 translated=MonumentaZh.display(original,fixed?"hologram_label":"entity_label");
        class_2561 upper=fixed?null:MonumentaZh.npcNameAbove(original);
        if(upper!=null&&!stacked&&!HologramLayout.allowsUpper(entity)) {
            upper=null;translated=MonumentaZh.display(original,"hologram_label");
        }
        if(translated==original&&upper==null)return;
        monumentazh$drawing=true;
        try {
            if(upper!=null) {
                matrices.method_22903();
                try {matrices.method_22904(0,0.25,0);method_3926(entity,upper,matrices,consumers,light);}
                finally {matrices.method_22909();}
            }
            method_3926(entity,translated,matrices,consumers,light);
        }finally {monumentazh$drawing=false;}
        callback.cancel();
    }
}
