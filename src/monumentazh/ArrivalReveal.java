package monumentazh;

import java.util.ArrayList;
import java.util.List;

/** Timestamped server progress, replayed continuously at 80% for the Chinese heading. */
public final class ArrivalReveal {
    private record Step(long elapsed, int visible) {}
    private static final long BUFFER=100_000_000L;
    private final List<Step> steps=new ArrayList<>();
    private String heading;
    private int previousVisible;
    private long started;
    public void reset(){steps.clear();heading=null;previousVisible=0;}
    public int update(String fullHeading,int visible,long now){return (int)updateSmooth(fullHeading,visible,now);}
    public double updateSmooth(String fullHeading,int visible,long now) {
        visible=Math.max(0,visible);
        if(heading==null||!fullHeading.startsWith(heading)||visible<previousVisible||now<started) {
            reset();started=now;steps.add(new Step(0,0));
        }
        heading=fullHeading;
        long elapsed=now-started;
        if(visible!=previousVisible)steps.add(new Step(elapsed+BUFFER,visible));
        previousVisible=visible;
        double replay=elapsed*0.8d;
        Step previous=steps.get(0);
        for(int i=1;i<steps.size();i++) {
            Step next=steps.get(i);
            if(replay<next.elapsed()) {
                double t=Math.max(0,Math.min(1,(replay-previous.elapsed())/Math.max(1d,next.elapsed()-previous.elapsed())));
                return previous.visible()+(next.visible()-previous.visible())*t;
            }
            previous=next;
        }
        return previous.visible();
    }
}
