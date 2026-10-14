package com.skate3rust.android;
public final class TouchLayoutTest {
    private static void require(boolean b){if(!b)throw new AssertionError();}
    public static void main(String[] args){
        TouchLayout original=new TouchLayout();TouchLayout draft=original.copy();
        draft.set("a",.7f,.6f);draft.set("right",.75f,.7f);
        require(original.x("a")==.4f);
        TouchLayout saved=TouchLayout.decode(draft.encode());
        require(saved.x("a")==.7f && saved.y("right")==.7f);
        for(String bad:new String[]{"2|",draft.encode().replace("0.7","NaN"),draft.encode()+"a=.2,.3;"}){
            try{TouchLayout.decode(bad);throw new AssertionError();}catch(IllegalArgumentException expected){}
        }
        try{draft.set("left",Float.NaN,0);throw new AssertionError();}catch(IllegalArgumentException expected){}
        require(new TouchLayout().x("a")==.4f);
        System.out.println("Touch layout copy, save/load, reset defaults and malformed data checks passed");
    }
}
