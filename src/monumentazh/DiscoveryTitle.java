package monumentazh;

import java.util.*;
import java.util.function.ToIntFunction;
import net.minecraft.*;

/** Bilingual discovery announcement, with the discovery status larger than the place name. */
public final class DiscoveryTitle {
    public record Content(class_2561 english,class_2561 chinese,class_2561 englishPlace,class_2561 chinesePlace) {}
    public record Row(class_2561 text,float scale,float top,int width) {}
    public record Layout(List<Row> rows,float centerY,int availableWidth) {}
    private static final float FONT_HEIGHT=9f;
    private static final float[] SCALES={1.05f,3.5f,1.15f,1.7f};
    private static Content lastContent;
    private static class_327 lastFont;
    private static int lastWidth,lastHeight;
    private static Layout lastLayout;

    public static Layout layout(Content content,int width,int height,ToIntFunction<class_2561> measure) {
        // GUI dimensions already include the user's GUI scale. Sublinear growth
        // keeps that choice meaningful, while avoiding tiny titles on large windows.
        float viewport=Math.min(Math.max(1,width)/640f,Math.max(1,height)/360f);
        float gain=Math.max(0.75f,Math.min(2f,(float)Math.sqrt(viewport)));
        int available=Math.max(1,(int)Math.min(width-24f,Math.min(width*0.70f,height*1.25f)));
        class_2561[] texts={content.english(),content.chinese(),content.englishPlace(),content.chinesePlace()};
        int[] widths=new int[texts.length];
        int count=0;
        for(int i=0;i<texts.length;i++) {
            if(texts[i]!=null&&!texts[i].getString().isBlank()) {
                widths[i]=Math.max(0,measure.applyAsInt(texts[i]));count++;
            } else texts[i]=null;
        }
        if(count==0)return new Layout(List.of(),Math.max(0,height)/2f,available);

        // Fit each bilingual pair together so a long label cannot invert its intended hierarchy.
        float[] fitted=SCALES.clone();
        for(int i=0;i<fitted.length;i++)fitted[i]*=gain;
        for(int group=0;group<texts.length;group+=2) {
            float fit=1f;
            for(int i=group;i<group+2;i++)
                if(texts[i]!=null&&widths[i]>0)fit=Math.min(fit,available/(widths[i]*fitted[i]));
            fitted[group]*=fit;fitted[group+1]*=fit;
        }
        // Keep discovery primary even when only its bilingual pair needs width reduction.
        float maximumPlaceScale=fitted[1]*SCALES[3]/SCALES[1];
        if(fitted[3]>maximumPlaceScale) {
            float placeFit=maximumPlaceScale/fitted[3];
            fitted[2]*=placeFit;fitted[3]*=placeFit;
        }

        float screenHeight=Math.max(1,height);
        float topMargin=Math.min(6f,screenHeight*0.1f);
        // Normal windows reserve the full hotbar/health area; tiny windows retain a usable title area.
        float hudReserve=Math.min(56f,Math.max(0f,screenHeight-topMargin-18f));
        float bottom=screenHeight-hudReserve;
        float usable=Math.max(0.1f,bottom-topMargin);
        float gap=Math.min(2f,usable/(Math.max(1,count-1)*2f));
        List<Row> rows=rows(texts,widths,fitted,gain,1f,gap);
        float density=1f;
        if(span(rows)>usable) {
            float low=0f,high=1f;
            for(int pass=0;pass<22;pass++) {
                float middle=(low+high)/2f;
                if(span(rows(texts,widths,fitted,gain,middle,gap))<=usable)low=middle;else high=middle;
            }
            density=low;
            rows=rows(texts,widths,fitted,gain,density,gap);
        }

        // Prefer the crosshair's natural center, retaining the gap between status and place.
        float center=screenHeight/2f;
        float centeredBottom=bottom-center;
        if(density==1f&&centeredBottom>0f) {
            Row last=rows.get(rows.size()-1);
            float lastBottom=last.top()+FONT_HEIGHT*last.scale();
            float centeredDensity=lastBottom>0f?Math.min(1f,centeredBottom/lastBottom):1f;
            if(centeredDensity>=0.75f&&centeredDensity<1f)
                rows=rows(texts,widths,fitted,gain,centeredDensity,gap);
        }
        Row first=rows.get(0),last=rows.get(rows.size()-1);
        float minimumCenter=topMargin-first.top();
        float maximumCenter=bottom-last.top()-FONT_HEIGHT*last.scale();
        center=Math.max(minimumCenter,Math.min(center,maximumCenter));
        return new Layout(List.copyOf(rows),center,available);
    }

    private static List<Row> rows(class_2561[] texts,int[] widths,float[] fitted,float gain,float density,float gap) {
        List<Row> rows=new ArrayList<>(texts.length);
        float[] heights=new float[texts.length],tops=new float[texts.length];
        for(int i=0;i<texts.length;i++)heights[i]=FONT_HEIGHT*fitted[i]*density;
        float statusGap=Math.max(gap,3.6f*density*Math.min(fitted[0]/SCALES[0],fitted[1]/SCALES[1]));
        float placeGap=Math.max(gap,3.6f*density*Math.min(fitted[2]/SCALES[2],fitted[3]/SCALES[3]));
        float statusBottom=-11.5f*gain*density;
        tops[1]=statusBottom-heights[1];
        tops[0]=(texts[1]==null?statusBottom:tops[1]-statusGap)-heights[0];
        tops[2]=8f*gain*density;
        tops[3]=texts[2]==null?tops[2]:tops[2]+heights[2]+placeGap;
        float previousBottom=-Float.MAX_VALUE;
        for(int i=0;i<texts.length;i++) {
            if(texts[i]==null)continue;
            float scale=fitted[i]*density;
            // Actual fitted glyph heights keep each pair compact even for a long place name.
            float top=Math.max(tops[i],previousBottom+gap);
            rows.add(new Row(texts[i],scale,top,widths[i]));
            previousBottom=top+FONT_HEIGHT*scale;
        }
        return rows;
    }

    private static float span(List<Row> rows) {
        Row last=rows.get(rows.size()-1);
        return last.top()+FONT_HEIGHT*last.scale()-rows.get(0).top();
    }

    public static void draw(class_332 context,class_327 font,Content content,int alpha) {
        if(alpha<=8)return;
        int width=context.method_51421(),height=context.method_51443();
        if(content!=lastContent||font!=lastFont||width!=lastWidth||height!=lastHeight) {
            lastLayout=layout(content,width,height,font::method_27525);
            lastContent=content;lastFont=font;lastWidth=width;lastHeight=height;
        }
        Layout layout=lastLayout;
        if(layout.rows().isEmpty())return;
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        class_4587 matrices=context.method_51448();matrices.method_22903();
        try {
            matrices.method_46416(width/2f,layout.centerY(),0);
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
