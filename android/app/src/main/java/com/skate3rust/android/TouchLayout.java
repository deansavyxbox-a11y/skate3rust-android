package com.skate3rust.android;

import java.util.LinkedHashMap;
import java.util.Map;

/** Versioned normalized positions, independent of Android storage and views. */
final class TouchLayout {
    static final String[] KEYS={"a","b","x","y","lt","rt","lb","rb","menu","back","up","down","leftArrow","rightArrow","left","right"};
    private static final float[] X={.40f,.50f,.40f,.50f,.32f,.68f,.32f,.68f,.54f,.44f,.12f,.12f,.075f,.165f,.16f,.84f};
    private static final float[] Y={.77f,.77f,.91f,.91f,.53f,.53f,.37f,.37f,.10f,.10f,.30f,.46f,.38f,.38f,.78f,.78f};
    private final Map<String,float[]> positions=new LinkedHashMap<>();
    TouchLayout(){for(int i=0;i<KEYS.length;i++)positions.put(KEYS[i],new float[]{X[i],Y[i]});}
    float x(String key){return positions.get(key)[0];}
    float y(String key){return positions.get(key)[1];}
    void set(String key,float x,float y){
        if(!positions.containsKey(key)||!Float.isFinite(x)||!Float.isFinite(y)||x<0||x>1||y<0||y>1)throw new IllegalArgumentException("Invalid control position");
        positions.put(key,new float[]{x,y});
    }
    TouchLayout copy(){TouchLayout result=new TouchLayout();for(String key:KEYS)result.set(key,x(key),y(key));return result;}
    String encode(){StringBuilder out=new StringBuilder("1|");for(String key:KEYS)out.append(key).append('=').append(x(key)).append(',').append(y(key)).append(';');return out.toString();}
    static TouchLayout decode(String raw){
        if(raw==null||!raw.startsWith("1|"))throw new IllegalArgumentException("Unsupported touch layout");
        TouchLayout result=new TouchLayout();java.util.Set<String> seen=new java.util.HashSet<>();
        for(String item:raw.substring(2).split(";")){
            String[] parts=item.split("=",-1);if(parts.length!=2||!seen.add(parts[0]))throw new IllegalArgumentException("Malformed touch layout");
            String[] xy=parts[1].split(",",-1);if(xy.length!=2)throw new IllegalArgumentException("Malformed touch position");
            result.set(parts[0],Float.parseFloat(xy[0]),Float.parseFloat(xy[1]));
        }
        if(seen.size()!=KEYS.length)throw new IllegalArgumentException("Incomplete touch layout");
        return result;
    }
}
