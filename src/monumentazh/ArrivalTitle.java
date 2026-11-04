package monumentazh;

import java.util.*;
import net.minecraft.*;

/** Separate English reference, primary Chinese label and short description, fitted to GUI dimensions. */
public final class ArrivalTitle {
    public record Content(class_2561 english,class_2561 chinese,class_2561 description,class_2561 englishDescription,
                          int headingVisible,int descriptionVisible) {
        public Content(class_2561 english,class_2561 chinese,class_2561 description,class_2561 englishDescription) {
            this(english,chinese,description,englishDescription,length(english),length(englishDescription));
        }
    }
    public record Row(class_2561 text,float scale,float top,int width) {}
    public record Layout(List<Row> rows,float centerY,int availableWidth) {}
    private static List<String> lastContentKey;
    private static Content lastVisibleContent;
    private static int lastVisibleHash;
    private static List<String> visibleContentKey;
    private static List<class_2561> lastVisibleRows;
    private static class_327 lastFont;
    private static int lastWidth,lastHeight;
    private static Layout lastLayout;

    public static Layout layout(Content content,int width,int height,java.util.function.ToIntFunction<class_2561> measure) {
        CinematicTitle.Layout shared=CinematicTitle.layout(new CinematicTitle.Content(
            content.english(),content.chinese(),content.description(),content.englishDescription()),width,height,measure,3.2f);
        List<Row> rows=shared.rows().stream().map(r->new Row(r.text(),r.scale(),r.top(),r.width())).toList();
        return new Layout(rows,shared.centerY(),shared.availableWidth());
    }
    private static int length(class_2561 text) {
        if(text==null)return 0;String value=text.getString();return value.codePointCount(0,value.length());
    }
    private static int proportional(int visible,int english,int chinese) {
        if(english<=0||visible>=english)return chinese;
        return (int)((long)Math.max(0,visible)*chinese/english);
    }
    // Snapshot full serialized styles as well as wording; mutable Text changes invalidate the cache.
    static List<String> layoutKey(Content content) {
        List<String> key=new ArrayList<>(4);
        for(class_2561 line:new class_2561[]{content.english(),content.chinese(),content.description(),content.englishDescription()})
            key.add(line==null?"null":class_2561.class_2562.method_10868(line).toString());
        return key;
    }
    static List<class_2561> visibleRows(Content content) {
        class_2561[] full={content.english(),content.chinese(),content.description(),content.englishDescription()};
        int heading=length(content.english()),description=length(content.englishDescription());
        int[] counts={content.headingVisible(),proportional(content.headingVisible(),heading,length(content.chinese())),
            content.englishDescription()==null?length(content.description()):proportional(content.descriptionVisible(),description,length(content.description())),content.descriptionVisible()};
        List<class_2561> rows=new ArrayList<>();
        for(int i=0;i<full.length;i++)if(full[i]!=null&&!full[i].getString().isBlank())rows.add(prefix(full[i],counts[i]));
        return rows;
    }
    static class_2561 prefix(class_2561 full,int codePoints) {
        if(codePoints>=length(full))return full;
        class_5250 result=class_2561.method_43473();int[] remaining={Math.max(0,codePoints)};
        full.method_27658((style,value)->{
            if(remaining[0]<=0)return Optional.empty();
            int take=Math.min(remaining[0],value.codePointCount(0,value.length()));
            if(take>0)result.method_10852(class_2561.method_43470(value.substring(0,value.offsetByCodePoints(0,take))).method_10862(style));
            remaining[0]-=take;return Optional.empty();
        },class_2583.field_24360);
        return result;
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
        int contentHash=content.hashCode();
        if(lastVisibleContent==null||contentHash!=lastVisibleHash
            ||content!=lastVisibleContent&&!content.equals(lastVisibleContent)) {
            visibleContentKey=layoutKey(content);lastVisibleRows=visibleRows(content);
            lastVisibleHash=contentHash;
        }
        lastVisibleContent=content;
        List<String> contentKey=visibleContentKey;
        if(!contentKey.equals(lastContentKey)||font!=lastFont||width!=lastWidth||height!=lastHeight) {
            lastLayout=layout(content,width,height,font::method_27525);lastContentKey=contentKey;lastFont=font;lastWidth=width;lastHeight=height;
        }
        Layout layout=lastLayout;
        List<class_2561> visible=lastVisibleRows;int index=0;
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        class_4587 matrices=context.method_51448();matrices.method_22903();
        try {
            matrices.method_46416(context.method_51421()/2f,layout.centerY(),0);
            for(Row row:layout.rows()) {
                class_2561 shown=visible.get(index++);
                if(shown.getString().isEmpty())continue;
                int shownWidth=font.method_27525(shown);
                matrices.method_22903();
                try {
                    matrices.method_46416(0,row.top(),0);matrices.method_22905(row.scale(),row.scale(),1);
                    context.method_27535(font,shown,-shownWidth/2,0,(alpha<<24)|0xffffff);
                } finally {matrices.method_22909();}
            }
        } finally {matrices.method_22909();com.mojang.blaze3d.systems.RenderSystem.disableBlend();}
    }
}
