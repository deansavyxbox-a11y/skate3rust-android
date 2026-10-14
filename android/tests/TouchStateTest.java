package com.skate3rust.android;
public final class TouchStateTest {
    private static void require(boolean b) { if(!b) throw new AssertionError(); }
    public static void main(String[] args) {
        TouchState s=new TouchState();
        s.down(7,"left"); s.move(7,2,-2);
        require(Math.abs(s.axes[0]-.70710677f)<.0001f && s.axes[1]<0);
        s.down(9,"right"); s.move(9,0,1); s.down(11,"a");
        require(s.axes[3]==1 && s.buttons==0x1000);
        s.up(7); require(s.axes[0]==0 && s.axes[3]==1 && s.buttons==0x1000);
        s.down(12,"a"); s.up(12); require(s.buttons==0x1000);
        s.up(11); require(s.buttons==0);
        s.down(13,"lt"); require(s.axes[4]==1);
        s.clear(); require(s.axes[3]==0 && s.axes[4]==0 && s.buttons==0);
        s.move(9,1,1); require(s.axes[2]==0);
        s.down(1,"menu",1000);s.up(1);
        require(s.publishedButtons(1020)==0x10 && s.publishedButtons(1065)==0);
        s.down(2,"a",2000);s.clear();require(s.publishedButtons(2010)==0);
        System.out.println("TouchState multitouch, clamp, ownership, release and cancellation passed");
    }
}
