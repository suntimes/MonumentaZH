package monumentazh;
import net.minecraft.*;
import java.util.function.*;

/** Native settings controls with a compact two-column layout. */
public final class SettingsScreen extends class_437 {
 public record State(boolean enabled,boolean bilingualNames,boolean bilingualPlaces,boolean signTranslation,boolean capture,boolean signCapture,String counts) {}
 private final class_437 parent; private final Supplier<State> state; private final Consumer<String> action;
 private boolean tools; private int left,top,width,headingY,toolY;
 public SettingsScreen(class_437 parent,Supplier<State> state,Consumer<String> action) {
  super(class_2561.method_43470("Monumenta 汉化设置"));this.parent=parent;this.state=state;this.action=action;
 }
 private void button(String label,int x,int y,int w,Runnable click) {
  method_37063(class_4185.method_46430(class_2561.method_43470(label),b->click.run()).method_46434(x,y,Math.max(20,w),20).method_46431());
 }
 private void choice(String label,boolean value,String yesText,String noText,String yes,String no,int x,int y,int w) {
  button(label+"："+(value?yesText:noText),x,y,w,()->{action.accept(value?no:yes);rebuild();});
 }
 private void rebuild(){method_37067();method_25426();}
 @Override protected void method_25426() {
  width=Math.min(field_22789-16,Math.max(224,Math.min(390,field_22789*55/100)));
  left=(field_22789-width)/2;

  headingY=Math.max(4,field_22790/10-4);
  // Anchor the heading near 23.4% and the footer near 82% of the screen.
  // Keep the existing control sizes and spacing on both settings pages.
  int blockHeight=tools?63:90;
  int minimumFirst=headingY+9+35;
  int first=Math.max(minimumFirst,field_22790*234/1000+15);
  int finishY=Math.min(field_22790-24,Math.max(field_22790*82/100,first+blockHeight+8));
  first=Math.min(first,finishY-blockHeight-8);
  top=first-15;
  State s=state.get();
  int half=(width-3)/2,third=(width-6)/3,y=first;
  if(!tools) {
   choice("汉化显示",s.enabled(),"中文","原文","on","off",left,y,half);
   choice("NPC 原名",s.bilingualNames(),"显示","隐藏","names_on","names_off",left+half+3,y,half);
   y+=22;
   choice("地点原名",s.bilingualPlaces(),"显示","隐藏","places_on","places_off",left,y,half);
   choice("告示牌汉化",s.signTranslation(),"开启","关闭","signs_on","signs_off",left+half+3,y,half);
   y+=22;
   button("使用说明",left,y,third,()->action.accept("about"));
   button("按键说明",left+third+3,y,third,()->action.accept("keys"));
   button("重载词库",left+2*(third+3),y,width-2*(third+3),()->action.accept("reload"));
   method_37063(new DisclosureButton(left,y+26,width,
       class_2561.method_43470("▶ 译者工具").method_10862(class_2583.field_24360.method_36139(0xFFAA00)),
       b->{tools=true;rebuild();}));
  } else {
   choice("自动收集",s.capture(),"开启","暂停","capture_on","capture_off",left,y,half);
   choice("告示牌采集",s.signCapture(),"开启","暂停","sign_capture_on","sign_capture_off",left+half+3,y,half);
   toolY=y+26;
   button("导出文本",left,y+43,width,()->action.accept("export"));
  }
  int finishW=Math.min(110,width/2);
  button(tools?"返回玩家设置":"完成",(field_22789-finishW)/2,finishY,finishW,this::method_25419);
 }
 private static final class DisclosureButton extends class_4185 {
  DisclosureButton(int x,int y,int width,class_2561 text,class_4241 press) {
   super(x,y,width,20,text,press,field_40754);
  }
  @Override public void method_48589(class_332 context,class_327 font,int color) {
   context.method_27535(font,method_25369(),method_46426()+8,method_46427()+6,color);
  }
 }
 /** The custom backdrop is drawn in render; prevent Screen from covering it. */
 @Override public void method_25420(class_332 context,int mouseX,int mouseY,float delta) {}
 @Override public void method_25394(class_332 context,int mouseX,int mouseY,float delta) {
  blurWorld(delta);
  context.method_25294(0,0,field_22789,field_22790,0x79090910);
  context.method_25300(field_22793,"Monumenta 汉化设置",field_22789/2,headingY,0xFFFFFF);
  context.method_25300(field_22793,tools?"译者工具":"玩家设置",field_22789/2,top,tools?0xFFAA00:0xFFFFFF);
  if(tools)context.method_25300(field_22793,state.get().counts(),field_22789/2,toolY,0xDDDDDD);
  super.method_25394(context,mouseX,mouseY,delta);
 }
 private class_279 blur;
 private boolean blurUnavailable;
 private int frameWidth, frameHeight;
 private void blurWorld(float delta) {
  if(blurUnavailable)return;
  var target=field_22787.method_1522();
  try {
   if(blur==null)blur=new class_279(field_22787.method_1531(),field_22787.method_1478(),target,
     new class_2960("monumentazh","shaders/post/settings_blur.json"));
   if(frameWidth!=target.field_1482 || frameHeight!=target.field_1481) {
    frameWidth=target.field_1482; frameHeight=target.field_1481;
    blur.method_1259(frameWidth,frameHeight);
   }
   com.mojang.blaze3d.systems.RenderSystem.disableBlend();
   com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
   blur.method_1258(delta);
  } catch(java.io.IOException | RuntimeException e) {
   blurUnavailable=true;
   System.err.println("[MonumentaZH] Settings blur unavailable: "+e);
  } finally {
   target.method_1235(true);
   com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
   com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1,1,1,1);
  }
 }
 @Override public void method_25432(){
  if(blur!=null){blur.close();blur=null;frameWidth=0;frameHeight=0;}
  super.method_25432();
 }
 @Override public void method_25419(){
  if(tools){tools=false;rebuild();}
  else field_22787.method_1507(parent);
 }
 @Override public boolean method_25421(){return false;}
}
