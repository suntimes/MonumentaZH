package monumentazh.mixin;

import monumentazh.MonumentaZh;
import net.minecraft.class_8816;
import net.minecraft.class_437;
import net.minecraft.class_332;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Only this MOD's introduction returns directly to the connected world. */
@Mixin(value=class_8816.class,remap=false)
public abstract class IntroductionPopupMixin {
    @Inject(method="method_25419",at=@At("HEAD"),cancellable=true,remap=false)
    private void monumentazh$closeIntroduction(CallbackInfo ci) {
        if(MonumentaZh.closeIntroductionPopup((class_8816)(Object)this))ci.cancel();
    }

    @Redirect(method="method_25420",at=@At(value="INVOKE",target="Lnet/minecraft/class_437;method_25394(Lnet/minecraft/class_332;IIF)V"),remap=false)
    private void monumentazh$introductionBackground(class_437 background,class_332 context,int mouseX,int mouseY,float delta) {
        if(!MonumentaZh.isIntroductionPopup((class_8816)(Object)this))background.method_25394(context,mouseX,mouseY,delta);
    }
}
