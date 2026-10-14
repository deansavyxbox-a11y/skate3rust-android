package com.skate3rust.android;
import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.Intent;
import android.view.View;
/** Draw branding before NativeActivity begins its potentially long initialization. */
public final class LaunchActivity extends Activity {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private LaunchScreenView screen;
    private boolean started;
    private final Runnable poll=new Runnable(){public void run(){
        if(isFinishing())return;
        int phase=StartupStatus.stage();screen.stage(phase);
        if(started && phase==5){finish();return;}
        handler.postDelayed(this,100);
    }};
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        screen=new LaunchScreenView(this,this::finishAffinity);setContentView(screen);
        // Wait for the branding view's first draw before starting native loading.
        screen.getViewTreeObserver().addOnDrawListener(new android.view.ViewTreeObserver.OnDrawListener(){
            boolean dispatched;
            public void onDraw(){if(dispatched)return;dispatched=true;screen.post(()->{
                if(isFinishing())return;
                started=true;startActivity(new Intent(LaunchActivity.this,GameActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_NO_ANIMATION));
                handler.post(poll);
            });}
        });
    }
    @Override public void onDestroy(){handler.removeCallbacks(poll);super.onDestroy();}
}
