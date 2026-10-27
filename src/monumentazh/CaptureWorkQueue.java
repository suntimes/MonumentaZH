package monumentazh;

import java.util.*;
import java.util.function.*;

/** Client-thread work only: callbacks never run from a tooltip/render callback. */
final class CaptureWorkQueue<S> {
    private final IdentityHashMap<S,String> items=new IdentityHashMap<>();
    private final ArrayDeque<S> itemOrder=new ArrayDeque<>();
    private final LinkedHashMap<List<String>,Integer> books=new LinkedHashMap<>();
    private final LinkedHashMap<List<String>,Integer> completedBooks=new LinkedHashMap<>(128,.75f,true);
    private final LinkedHashMap<String,Integer> completedPages=new LinkedHashMap<>(256,.75f,true);
    private int bookCharacters,pageCharacters;
    private static final int CACHE_CHARACTERS=2_000_000;

    void request(S stack,String title) {
        if(items.containsKey(stack)||items.size()>=512)return;
        items.put(stack,title);itemOrder.addLast(stack);
    }

    boolean book(List<String> source) {
        if(completedBooks.get(source)!=null||books.containsKey(source))return true;
        if(books.size()>=32)return false; // Keep its item request queued until capacity is available.
        books.put(List.copyOf(source),0);return true;
    }

    void tick(BiFunction<S,String,Boolean> item,Consumer<String> page) {
        step(item,page,System.nanoTime()+2_000_000L);
    }

    private void step(BiFunction<S,String,Boolean> item,Consumer<String> page,long deadline) {
        // At most one uncached page per tick; cached pages need no parsing/translation.
        int visited=0;
        while(!books.isEmpty()&&visited++<64) {
            var iterator=books.entrySet().iterator();var job=iterator.next();
            List<String> source=job.getKey();int index=job.getValue();
            if(index==source.size()) {
                iterator.remove();rememberBook(source);continue;
            }
            String raw=source.get(index);boolean known=completedPages.get(raw)!=null;
            if(!known){page.accept(raw);rememberPage(raw);}
            job.setValue(index+1);
            if(!known||System.nanoTime()>=deadline)break;
        }
        for(int i=0;i<2&&!itemOrder.isEmpty()&&System.nanoTime()<deadline;i++) {
            S stack=itemOrder.getFirst();
            if(!item.apply(stack,items.get(stack)))break;
            itemOrder.removeFirst();items.remove(stack);
        }
    }

    /** Manual export/shutdown are explicit flush points, so short-lived containers aren't lost. */
    void drain(BiFunction<S,String,Boolean> item,Consumer<String> page) {
        while(!items.isEmpty()||!books.isEmpty())step(item,page,Long.MAX_VALUE);
    }

    private void rememberBook(List<String> source) {
        int length=0;for(String s:source)length+=s.length();
        if(length>CACHE_CHARACTERS)return;
        bookCharacters+=length;completedBooks.put(source,length);
        while(completedBooks.size()>128||bookCharacters>CACHE_CHARACTERS) {
            var iterator=completedBooks.entrySet().iterator();var entry=iterator.next();
            bookCharacters-=entry.getValue();iterator.remove();
        }
    }
    private void rememberPage(String source) {
        if(source.length()>CACHE_CHARACTERS)return;
        pageCharacters+=source.length();completedPages.put(source,source.length());
        while(completedPages.size()>4096||pageCharacters>CACHE_CHARACTERS) {
            var iterator=completedPages.entrySet().iterator();var entry=iterator.next();
            pageCharacters-=entry.getValue();iterator.remove();
        }
    }
}
