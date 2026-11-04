package monumentazh.mixin;
import monumentazh.MonumentaZh;
import net.minecraft.*;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Ordinary and hanging signs share this render path; editable sign data stays original. */
@Mixin(value=class_837.class,remap=false)
public abstract class SignTextMixin {
    @Shadow private class_327 field_27755;
    @org.spongepowered.asm.mixin.Unique
    private final java.util.Map<class_5481,Integer> monumentazh$widths=new java.util.WeakHashMap<>();
    @org.spongepowered.asm.mixin.Unique
    private class_377 monumentazh$font;
    @ModifyVariable(method="method_45798",at=@At("HEAD"),argsOnly=true,ordinal=0,remap=false)
    private class_8242 monumentazh$sign(class_8242 original,class_2338 pos,class_8242 sign,class_4587 matrices,class_4597 consumers,int light,int lineHeight,int maxWidth,boolean front){return MonumentaZh.displaySign(original,maxWidth);}

    // Vanilla keeps only the first wrapped row. Fit Chinese rows to the existing sign width instead.
    @Inject(method="method_45799",at=@At("HEAD"),cancellable=true,remap=false)
    private void monumentazh$wholeRow(int width,class_2561 line,CallbackInfoReturnable<class_5481> callback) {
        if(MonumentaZh.signTranslationActive()&&(line.getString().codePoints().anyMatch(c->Character.UnicodeScript.of(c)==Character.UnicodeScript.HAN)||MonumentaZh.teleportSignLabel(line)))
            callback.setReturnValue(line.method_30937());
    }
    @ModifyArgs(method="method_45798",at=@At(value="INVOKE",target="Lnet/minecraft/class_327;method_22942(Lnet/minecraft/class_5481;FFIZLorg/joml/Matrix4f;Lnet/minecraft/class_4597;Lnet/minecraft/class_327$class_6415;II)I"),remap=false)
    private void monumentazh$fitNormal(Args args,class_2338 pos,class_8242 sign,class_4587 matrices,class_4597 consumers,int light,int lineHeight,int maxWidth,boolean front) {
        monumentazh$fit(args,maxWidth);
    }
    @ModifyArgs(method="method_45798",at=@At(value="INVOKE",target="Lnet/minecraft/class_327;method_37296(Lnet/minecraft/class_5481;FFIILorg/joml/Matrix4f;Lnet/minecraft/class_4597;I)V"),remap=false)
    private void monumentazh$fitGlow(Args args,class_2338 pos,class_8242 sign,class_4587 matrices,class_4597 consumers,int light,int lineHeight,int maxWidth,boolean front) {
        monumentazh$fit(args,maxWidth);
    }
    @org.spongepowered.asm.mixin.Unique
    private void monumentazh$fit(Args args,int maxWidth) {
        if(!MonumentaZh.signTranslationActive())return;
        class_377 font=((TextRendererFontAccessor)field_27755).monumentazh$font(class_2583.field_24360.method_27708());
        if(font!=monumentazh$font){monumentazh$widths.clear();monumentazh$font=font;}
        int width=monumentazh$widths.computeIfAbsent(args.get(0),field_27755::method_30880);
        if(width>maxWidth&&maxWidth>0)args.set(5,new Matrix4f((Matrix4f)args.get(5)).scale(maxWidth/(float)width,1,1));
    }
}
