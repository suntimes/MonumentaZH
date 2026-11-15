package monumentazh;

import java.util.List;
import net.minecraft.*;

/** Paginated help/operation result, read with the client's own font and GUI scale. */
final class SettingsInfoScreen extends class_437 {
    private final class_437 parent;
    private final class_2561 body;
    private int page, capacity, width, left, top, height;
    private List<class_5481> lines;
    SettingsInfoScreen(class_437 parent, String title, class_2561 body) {
        super(class_2561.method_43470(title));
        this.parent=parent;
        this.body=body;
    }
    @Override protected void method_25426() {
        width=Math.min(400,field_22789-24);
        height=Math.min(270,field_22790-16);
        left=(field_22789-width)/2;
        top=(field_22790-height)/2;
        capacity=Math.max(1,(height-65)/10);
        lines=field_22793.method_1728(body,width-24);
        int last=Math.max(0,(lines.size()-1)/capacity);
        page=Math.min(page,last);
        int third=(width-32)/3;
        if(last>0) {
            button("上一页",left+10,third,()->flip(-1)).field_22763=page>0;
            button("下一页",left+15+third,third,()->flip(1)).field_22763=page<last;
        }
        button("返回设置",left+width-third-10,third,this::method_25419);
    }
    private void flip(int by) {page+=by;method_37067();method_25426();}
    private class_4185 button(String label,int x,int w,Runnable action) {
        return method_37063(class_4185.method_46430(class_2561.method_43470(label),b->action.run())
            .method_46434(x,top+height-26,w,20).method_46431());
    }
    @Override public void method_25420(class_332 g,int x,int y,float delta) {}
    @Override public void method_25394(class_332 g,int mx,int my,float delta) {
        g.method_25294(0,0,field_22789,field_22790,0x70000000);
        g.method_25294(left,top,left+width,top+height,0xFA252525);
        g.method_25300(field_22793,method_25440().getString(),field_22789/2,top+8,0xFFFFFF);
        for(int i=0;i<capacity&&page*capacity+i<lines.size();i++)
            g.method_35720(field_22793,lines.get(page*capacity+i),left+12,top+25+i*10,0xFFFFFF);
        super.method_25394(g,mx,my,delta);
    }
    @Override public void method_25419(){field_22787.method_1507(parent);}
    @Override public boolean method_25421(){return false;}
}
