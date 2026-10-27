package monumentazh;

import java.util.*;
import net.minecraft.*;

/** Separate English reference, primary Chinese label and short description, fitted to GUI dimensions. */
public final class ArrivalTitle {
    public record Content(class_2561 english,class_2561 chinese,class_2561 description) {}
    public record Row(class_2561 text,float scale,float top,int width) {}
    public record Layout(List<Row> rows,float centerY,int availableWidth) {}
    private static Content lastContent;
    private static class_327 lastFont;
    private static int lastWidth,lastHeight;
    private static Layout lastLayout;

    public static Layout layout(Content content,int width,int height,java.util.function.ToIntFunction<class_2561> measure) {
        int available=Math.max(1,width-24);
        float density=Math.min(1f,Math.max(0.2f,(height-96f)/90f));
        float center=Math.max(28f,Math.min(height/2f,height-64f));
        List<Row> rows=new ArrayList<>();
        add(rows,content.english(),1.35f*density,-55f*density,available,measure);
        add(rows,content.chinese(),3f*density,-32f*density,available,measure);
        if(content.description()!=null&&!content.description().getString().isBlank())
            add(rows,content.description(),1.5f*density,12f*density,available,measure);
        return new Layout(List.copyOf(rows),center,available);
    }
    private static void add(List<Row> rows,class_2561 text,float desired,float top,int available,java.util.function.ToIntFunction<class_2561> measure) {
        int width=Math.max(0,measure.applyAsInt(text));
        float scale=width==0?desired:Math.min(desired,(float)available/width);
        rows.add(new Row(text,scale,top,width));
    }
    public static int alpha(int remaining,float delta,int fadeIn,int stay,int fadeOut) {
        if(remaining<=0)return 0;
        float left=remaining-delta;
        if(remaining>stay+fadeOut&&fadeIn>0)return Math.max(0,Math.min(255,(int)((fadeIn+stay+fadeOut-left)*255f/fadeIn)));
        if(remaining<=fadeOut&&fadeOut>0)return Math.max(0,Math.min(255,(int)(left*255f/fadeOut)));
        return 255;
    }
    public static void draw(class_332 context,class_327 font,Content content,int alpha) {
        if(alpha<=8)return;
        int width=context.method_51421(),height=context.method_51443();
        if(content!=lastContent||font!=lastFont||width!=lastWidth||height!=lastHeight) {
            lastLayout=layout(content,width,height,font::method_27525);lastContent=content;lastFont=font;lastWidth=width;lastHeight=height;
        }
        Layout layout=lastLayout;
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        class_4587 matrices=context.method_51448();matrices.method_22903();
        try {
            matrices.method_46416(context.method_51421()/2f,layout.centerY(),0);
            for(Row row:layout.rows()) {
                matrices.method_22903();
                try {
                    matrices.method_46416(0,row.top(),0);matrices.method_22905(row.scale(),row.scale(),1);
                    context.method_27535(font,row.text(),-row.width()/2,0,(alpha<<24)|0xffffff);
                } finally {matrices.method_22909();}
            }
        } finally {matrices.method_22909();com.mojang.blaze3d.systems.RenderSystem.disableBlend();}
    }
}
