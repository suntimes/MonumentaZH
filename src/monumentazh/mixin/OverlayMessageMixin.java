package monumentazh.mixin;

import monumentazh.MonumentaZh;
import monumentazh.ArrivalTitle;
import monumentazh.DiscoveryTitle;
import monumentazh.CinematicTitle;
import net.minecraft.class_2561;
import net.minecraft.class_329;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = class_329.class, remap = false)
public abstract class OverlayMessageMixin {
    @Shadow private class_2561 field_2018;
    @Shadow private class_2561 field_2016;
    @Shadow private class_2561 field_2039;
    @Shadow private int field_2023;
    @Shadow private int field_2037;
    @Shadow private int field_2017;
    @Shadow private int field_2036;
    @Unique private ArrivalTitle.Content monumentazh$arrival;
    @Unique private DiscoveryTitle.Content monumentazh$discovery;
    @Unique private CinematicTitle.Content monumentazh$cinematic;
    @Unique private class_2561 monumentazh$lastTitle,monumentazh$lastSubtitle;
    @Unique private int monumentazh$titleHash,monumentazh$subtitleHash;
    @Unique private long monumentazh$revision=-1;
    @Unique private boolean monumentazh$wasActive;

    @Inject(method="method_34004",at=@At("HEAD"),remap=false)
    private void monumentazh$captureTitle(class_2561 title,CallbackInfo ci) {
        MonumentaZh.captureCentralTitle(title,"title");
    }
    @Inject(method="method_34002",at=@At("HEAD"),remap=false)
    private void monumentazh$captureSubtitle(class_2561 subtitle,CallbackInfo ci) {
        MonumentaZh.captureCentralTitle(subtitle,"subtitle");
    }

    @Inject(method="method_1753",at=@At("HEAD"),remap=false)
    private void monumentazh$prepareArrival(net.minecraft.class_332 context,float delta,CallbackInfo ci) {
        int titleHash=java.util.Objects.hashCode(field_2016),subtitleHash=java.util.Objects.hashCode(field_2039);
        long revision=MonumentaZh.displayRevision();
        boolean active=MonumentaZh.active();
        if(field_2016!=monumentazh$lastTitle||field_2039!=monumentazh$lastSubtitle||titleHash!=monumentazh$titleHash||subtitleHash!=monumentazh$subtitleHash||revision!=monumentazh$revision||active!=monumentazh$wasActive) {
            monumentazh$discovery=MonumentaZh.discoveryTitle(field_2016,field_2039);
            monumentazh$arrival=monumentazh$discovery==null?MonumentaZh.arrivalTitle(field_2016,field_2039):null;
            monumentazh$cinematic=monumentazh$discovery==null&&monumentazh$arrival==null?MonumentaZh.cinematicTitle(field_2016,field_2039):null;
            monumentazh$lastTitle=field_2016;monumentazh$lastSubtitle=field_2039;
            monumentazh$titleHash=titleHash;monumentazh$subtitleHash=subtitleHash;monumentazh$revision=revision;
            monumentazh$wasActive=active;
        }
        // Recheck connection/enable state without invalidating stable, expensive text measurements.
        if(!active){monumentazh$arrival=null;monumentazh$discovery=null;monumentazh$cinematic=null;}
    }
    @Inject(method="method_1753",at=@At("TAIL"),remap=false)
    private void monumentazh$drawArrival(net.minecraft.class_332 context,float delta,CallbackInfo ci) {
        if(monumentazh$arrival!=null)ArrivalTitle.draw(context,net.minecraft.class_310.method_1551().field_1772,monumentazh$arrival,ArrivalTitle.alpha(field_2023,delta,field_2037,field_2017,field_2036));
        if(monumentazh$cinematic!=null)CinematicTitle.draw(context,net.minecraft.class_310.method_1551().field_1772,monumentazh$cinematic,ArrivalTitle.alpha(field_2023,delta,field_2037,field_2017,field_2036));
        if(monumentazh$discovery!=null)DiscoveryTitle.draw(context,net.minecraft.class_310.method_1551().field_1772,monumentazh$discovery,ArrivalTitle.alpha(field_2023,delta,field_2037,field_2017,field_2036));
    }

    @Redirect(method = "method_1753", at = @At(value = "FIELD", target = "Lnet/minecraft/class_329;field_2018:Lnet/minecraft/class_2561;"), remap = false)
    private class_2561 monumentazh$overlay(class_329 hud) { return MonumentaZh.display(field_2018, "actionbar"); }

    @Redirect(method = "method_1753", at = @At(value = "FIELD", target = "Lnet/minecraft/class_329;field_2016:Lnet/minecraft/class_2561;"), remap = false)
    private class_2561 monumentazh$title(class_329 hud) { return monumentazh$arrival!=null||monumentazh$discovery!=null||monumentazh$cinematic!=null?null:MonumentaZh.display(field_2016, "title"); }

    @Redirect(method = "method_1753", at = @At(value = "FIELD", target = "Lnet/minecraft/class_329;field_2039:Lnet/minecraft/class_2561;"), remap = false)
    private class_2561 monumentazh$subtitle(class_329 hud) { return monumentazh$arrival!=null||monumentazh$discovery!=null||monumentazh$cinematic!=null?null:MonumentaZh.display(field_2039, "subtitle"); }
}
