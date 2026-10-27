package monumentazh.mixin;

import monumentazh.MonumentaZh;
import net.minecraft.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Shared read-only page rendering covers held books and lecterns without changing their providers. */
@Mixin(value=class_3872.class,remap=false)
public abstract class BookScreenMixin {
    @Shadow private int field_17121;
    @Unique private long monumentazh$bookRevision=Long.MIN_VALUE;

    @Inject(method="method_25394",at=@At("HEAD"),remap=false)
    private void monumentazh$refreshBook(class_332 context,int mouseX,int mouseY,float delta,CallbackInfo ci) {
        long current=MonumentaZh.displayRevision();
        if(monumentazh$bookRevision!=current){field_17121=-1;monumentazh$bookRevision=current;}
    }

    @Redirect(method="method_25394",at=@At(value="INVOKE",target="Lnet/minecraft/class_3872$class_3931;method_17563(I)Lnet/minecraft/class_5348;"),remap=false)
    private class_5348 monumentazh$bookPage(class_3872.class_3931 contents,int page) {
        return MonumentaZh.displayBookPage(contents.method_17563(page));
    }
}
