package com.skate3rust.android;

import java.util.HashMap;
import java.util.Map;

/** Pointer-owned controller state, independent of Android rendering. */
final class TouchState {
    final float[] axes=new float[6];
    int buttons;
    private final Map<Integer,String> owners=new HashMap<>();
    private final long[] pulseUntil=new long[16];
    static int bit(String key) {
        switch(key) {
            case "up":return 1; case "down":return 2; case "leftArrow":return 4; case "rightArrow":return 8;
            case "menu":return 0x10; case "back":return 0x20;
            case "lb":return 0x100; case "rb":return 0x200;
            case "a":return 0x1000; case "b":return 0x2000; case "x":return 0x4000; case "y":return 0x8000;
            default:return 0;
        }
    }
    void down(int pointer,String key) {
        down(pointer,key,0);
    }
    void down(int pointer,String key,long now) {
        if(owners.containsKey(pointer) || owners.containsValue(key))return;
        owners.put(pointer,key);
        if(key.equals("lt")) axes[4]=1;
        else if(key.equals("rt")) axes[5]=1;
        else {
            int mask=bit(key);buttons|=mask;
            if(mask!=0)pulseUntil[Integer.numberOfTrailingZeros(mask)]=now+64;
        }
    }
    void move(int pointer,float x,float y) {
        String key=owners.get(pointer);
        if(!"left".equals(key) && !"right".equals(key))return;
        if(!Float.isFinite(x)||!Float.isFinite(y)){x=y=0;}
        float length=(float)Math.sqrt(x*x+y*y);
        if(length>1){x/=length;y/=length;}
        int offset=key.equals("left")?0:2;
        axes[offset]=x; axes[offset+1]=y;
    }
    void up(int pointer) {
        String key=owners.remove(pointer); if(key==null)return;
        if(key.equals("left")){axes[0]=axes[1]=0;}
        else if(key.equals("right")){axes[2]=axes[3]=0;}
        else if(key.equals("lt"))axes[4]=0;
        else if(key.equals("rt"))axes[5]=0;
        else buttons&=~bit(key);
    }
    int publishedButtons(long now){
        int value=buttons;
        for(int i=0;i<pulseUntil.length;i++)if(pulseUntil[i]>now)value|=1<<i;
        return value;
    }
    void clear(){owners.clear();buttons=0;java.util.Arrays.fill(axes,0);java.util.Arrays.fill(pulseUntil,0);}
    boolean held(String key){return owners.containsValue(key);}
    String control(int pointer){return owners.get(pointer);}
}
