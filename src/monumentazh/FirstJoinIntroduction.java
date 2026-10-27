package monumentazh;

/** At most three introductions, once per connection; no identities are persisted. */
public final class FirstJoinIntroduction {
    private int shownCount;
    private Object popupConnection;
    private boolean shownThisConnection;
    private int readyTicks;
    private Object connection;
    private int connectionTicks;
    private boolean statusShown;
    public boolean tick(Object currentConnection,boolean ready) {
        if(popupConnection!=currentConnection){popupConnection=currentConnection;shownThisConnection=false;readyTicks=0;}
        if(currentConnection==null||shownThisConnection||shownCount>=3)return false;
        if(!ready){readyTicks=0;return false;}
        if(++readyTicks<40)return false;
        ++shownCount;shownThisConnection=true;return true;
    }
    public int shownCount(){return shownCount;}
    public void shownCount(int value){shownCount=value;readyTicks=0;}
    public boolean statusTick(Object currentConnection) {
        if(connection!=currentConnection){connection=currentConnection;connectionTicks=0;statusShown=false;}
        if(connection==null||statusShown||++connectionTicks<40)return false;
        statusShown=true;return true;
    }
}
