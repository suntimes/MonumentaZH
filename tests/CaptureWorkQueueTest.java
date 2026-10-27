package monumentazh;
import java.util.*;
import java.util.function.*;

public final class CaptureWorkQueueTest {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args) {
        CaptureWorkQueue<Object> q=new CaptureWorkQueue<>();List<String> pages=new ArrayList<>();
        for(int i=0;i<100;i++)pages.add("page "+i);
        Object same=new Object();for(int i=0;i<1000;i++)q.request(same,"hover");
        int[] items={0},calls={0};BiFunction<Object,String,Boolean> item=(s,t)->{items[0]++;return q.book(pages);};
        Consumer<String> page=s->{calls[0]++;};check(calls[0]==0&&items[0]==0,"Render request ran work");
        for(int i=0;i<150;i++){int before=calls[0];q.tick(item,page);check(calls[0]-before<=1,"Unbounded page work");}
        check(items[0]==1&&calls[0]==100,"Identity queue/page completion");
        for(int i=0;i<54;i++)q.request(new Object(),"duplicate book");q.drain(item,page);
        check(items[0]==55&&calls[0]==100,"Duplicate copies reparsed");
        pages.set(12,"changed page");q.request(new Object(),"updated source");q.drain(item,page);
        check(calls[0]==101,"Changed source missed or unchanged pages reprocessed");
        CaptureWorkQueue<Object> backlog=new CaptureWorkQueue<>();Map<Object,List<String>> sources=new IdentityHashMap<>();
        for(int b=0;b<54;b++){Object book=new Object();List<String> content=new ArrayList<>();for(int i=0;i<100;i++)content.add(b+"/"+i);sources.put(book,content);backlog.request(book,"large chest");}
        Set<String> all=new HashSet<>();backlog.drain((s,t)->backlog.book(sources.get(s)),all::add);
        check(all.size()==5400,"Backpressure/export lost pages");
        CaptureWorkQueue<Object> snapshot=new CaptureWorkQueue<>();List<String> original=new ArrayList<>(List.of("before"));snapshot.book(original);original.set(0,"after");List<String> saved=new ArrayList<>();snapshot.drain((s,t)->true,saved::add);check(saved.equals(List.of("before")),"Mutable book key");
        System.out.println("CAPTURE_QUEUE_OK: no render work, 1 uncached page/tick, 54 duplicate copies shared, mutable source detected, 5400-page export/backpressure, immutable snapshots");
    }
}
