package com.skate3rust.android;
import android.content.Context;
final class TouchLayoutStore {
    static TouchLayout load(Context context){
        String raw=context.getSharedPreferences("touch_controls",Context.MODE_PRIVATE).getString("layout_v1",null);
        try{return TouchLayout.decode(raw);}catch(IllegalArgumentException e){return new TouchLayout();}
    }
    static boolean save(Context context,TouchLayout layout){return context.getSharedPreferences("touch_controls",Context.MODE_PRIVATE).edit().putString("layout_v1",layout.encode()).commit();}
}
