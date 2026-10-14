package com.skate3rust.android;

import android.app.NativeActivity;
import android.os.Bundle;
import android.view.View;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.hardware.input.InputManager;
import android.media.AudioManager;
import android.media.AudioFocusRequest;
import android.media.AudioAttributes;

/** Bevy owns the native surface and Android lifecycle. */
public final class GameActivity extends NativeActivity implements InputManager.InputDeviceListener {
    static { System.loadLibrary("skate3rust_android"); }
    private native void controller(float lx,float ly,float rx,float ry,float lt,float rt,int buttons);
    private native void clearInput();
    private native void audioActive(boolean active);
    private InputManager input;
    private AudioManager audio;
    private AudioFocusRequest focus;
    private boolean resumed, hasAudioFocus, windowFocused;
    private int device=-1, buttons, hat;
    private final float[] axes=new float[6];
    private TouchControlsView touch;
    private android.widget.PopupWindow touchWindow;
    private LaunchScreenView loading;
    private int startupStage;
    private final android.os.Handler uiHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable pollUi=new Runnable(){public void run(){
        if(!resumed)return;
        int next=StartupStatus.stage();if(next==5 && startupStage!=5)reset();startupStage=next;
        loading.stage(next);loading.setVisibility(next==5?View.GONE:View.VISIBLE);
        touch.setVisibility(next==5 && !physicalController?View.VISIBLE:View.GONE);
        updateTouchWindow();
        if(next==5 && StartupStatus.takeLayoutRequest()){
            reset();View viewport=getWindow().getDecorView();startActivity(new android.content.Intent(GameActivity.this,TouchLayoutActivity.class).putExtra("aspect",(float)viewport.getWidth()/Math.max(1,viewport.getHeight())).putExtra("gameWidth",viewport.getWidth()));
        }
        if(resumed)uiHandler.postDelayed(this,100);
    }};
    private boolean physicalController;
    @Override public void onCreate(Bundle state) {
        NativeSession.begin();
        super.onCreate(state);
        getWindow().takeInputQueue(null);
        touch=new TouchControlsView(this,this::sendTouch);
        loading=new LaunchScreenView(this,this::finishAffinity);
        android.widget.FrameLayout panel=new android.widget.FrameLayout(this);
        panel.addView(touch,new android.widget.FrameLayout.LayoutParams(-1,-1));panel.addView(loading,new android.widget.FrameLayout.LayoutParams(-1,-1));
        // NativeActivity presents Vulkan into its window surface. Keep the
        // Android overlay on a separate application-panel surface above it.
        touchWindow=new android.widget.PopupWindow(panel,android.view.ViewGroup.LayoutParams.MATCH_PARENT,android.view.ViewGroup.LayoutParams.MATCH_PARENT,false);
        touchWindow.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        touchWindow.setInputMethodMode(android.widget.PopupWindow.INPUT_METHOD_NOT_NEEDED);
        touchWindow.setClippingEnabled(false);
        touch.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        input=(InputManager)getSystemService(INPUT_SERVICE);
        input.registerInputDeviceListener(this,null);
        audio=(AudioManager)getSystemService(AUDIO_SERVICE);
        focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setOnAudioFocusChangeListener(change -> {hasAudioFocus=change==AudioManager.AUDIOFOCUS_GAIN; if(!hasAudioFocus) reset(); publishFocus();}).build();
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        android.util.Log.i("SKATE_ANDROID", "Activity created; native runtime starting");
    }
    private static boolean gamepad(InputDevice d) { return d!=null && ((d.getSources() & InputDevice.SOURCE_GAMEPAD)==InputDevice.SOURCE_GAMEPAD || (d.getSources() & InputDevice.SOURCE_JOYSTICK)==InputDevice.SOURCE_JOYSTICK); }
    private void reset() { buttons=hat=0; java.util.Arrays.fill(axes,0); if(touch!=null)touch.clear(); clearInput(); }
    private void findController() {
        reset(); device=-1;
        physicalController=false;
        for(int id:InputDevice.getDeviceIds()) if(gamepad(InputDevice.getDevice(id)) && !InputDevice.getDevice(id).isVirtual()) {device=id;physicalController=true;break;}
        touch.setVisibility(startupStage==5 && !physicalController?View.VISIBLE:View.GONE);
        updateTouchWindow();
        android.util.Log.i("SKATE_ANDROID","Touch controls "+(physicalController?"hidden: physical controller":"visible"));
        if(physicalController)send();else sendTouch();
    }
    private void sendTouch(){
        if(startupStage==5 && !physicalController && resumed && hasAudioFocus && windowFocused){
            TouchState s=touch.state;
            controller(s.axes[0],s.axes[1],s.axes[2],s.axes[3],s.axes[4],s.axes[5],s.publishedButtons(android.os.SystemClock.uptimeMillis()));
        }else if(!resumed || !hasAudioFocus || !windowFocused)reset();
    }
    private void updateTouchWindow(){
        if(touchWindow==null)return;
        if(!resumed || (physicalController && startupStage==5)){touchWindow.dismiss();return;}
        getWindow().getDecorView().post(()->{
            if(resumed && (!physicalController || startupStage!=5) && !isFinishing() && getWindow().getDecorView().getWindowToken()!=null && !touchWindow.isShowing())
                touchWindow.showAtLocation(getWindow().getDecorView(),android.view.Gravity.TOP|android.view.Gravity.START,0,0);
        });
    }
    private void sendInput() { if(startupStage==5 && resumed && hasAudioFocus && windowFocused) controller(axes[0],axes[1],axes[2],axes[3],axes[4],axes[5],buttons|hat); }
    private void send() { if(device>=0) sendInput(); }
    private void publishFocus() {audioActive(resumed && hasAudioFocus && windowFocused); if(physicalController)send();else if(touch!=null)sendTouch();}
    @Override public void onWindowFocusChanged(boolean focused) {super.onWindowFocusChanged(focused); windowFocused=focused; if(!focused)reset(); publishFocus();if(focused)updateTouchWindow();}
    @Override public void onResume() {super.onResume(); resumed=true;touch.reloadLayout();startupStage=StartupStatus.stage(); windowFocused=getWindow().getDecorView().hasWindowFocus(); hasAudioFocus=audio.requestAudioFocus(focus)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED; publishFocus(); findController();uiHandler.removeCallbacks(pollUi);uiHandler.post(pollUi);}
    @Override public void onPause() {resumed=false;uiHandler.removeCallbacks(pollUi); reset();updateTouchWindow(); audioActive(false); audio.abandonAudioFocusRequest(focus); super.onPause();}
    @Override public void onDestroy() {touchWindow.dismiss();input.unregisterInputDeviceListener(this);if(isFinishing())NativeSession.end();super.onDestroy();}
    @Override public void onInputDeviceAdded(int id) { if(gamepad(InputDevice.getDevice(id))) findController(); }
    @Override public void onInputDeviceChanged(int id) {if(id==device || gamepad(InputDevice.getDevice(id))) findController();}
    @Override public void onInputDeviceRemoved(int id) {findController();}
    @Override public boolean dispatchGenericMotionEvent(MotionEvent e) {
        if((gamepad(e.getDevice()) || e.isFromSource(InputDevice.SOURCE_JOYSTICK)) && e.getAction()==MotionEvent.ACTION_MOVE) {
            // Consume inactive events before they can refill the cache sent on resume.
            if(!resumed || !hasAudioFocus || !windowFocused) {reset(); return true;}
            if(device!=e.getDeviceId()) {reset(); device=e.getDeviceId();}
            axes[0]=e.getAxisValue(MotionEvent.AXIS_X); axes[1]=e.getAxisValue(MotionEvent.AXIS_Y);
            axes[2]=e.getAxisValue(MotionEvent.AXIS_Z); axes[3]=e.getAxisValue(MotionEvent.AXIS_RZ);
            axes[4]=Math.max(e.getAxisValue(MotionEvent.AXIS_LTRIGGER),e.getAxisValue(MotionEvent.AXIS_BRAKE));
            axes[5]=Math.max(e.getAxisValue(MotionEvent.AXIS_RTRIGGER),e.getAxisValue(MotionEvent.AXIS_GAS));
            float hx=e.getAxisValue(MotionEvent.AXIS_HAT_X),hy=e.getAxisValue(MotionEvent.AXIS_HAT_Y);
            hat=(hy<-.5?1:hy>.5?2:0)|(hx<-.5?4:hx>.5?8:0); sendInput(); return true;
        }
        return super.dispatchGenericMotionEvent(e);
    }
    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if(gamepad(e.getDevice()) || e.isFromSource(InputDevice.SOURCE_GAMEPAD)) {
            if(!resumed || !hasAudioFocus || !windowFocused) {reset(); return true;}
            if(device!=e.getDeviceId()) {reset();device=e.getDeviceId();}
            int bit;
            switch(e.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_UP:bit=1;break;case KeyEvent.KEYCODE_DPAD_DOWN:bit=2;break;
                case KeyEvent.KEYCODE_DPAD_LEFT:bit=4;break;case KeyEvent.KEYCODE_DPAD_RIGHT:bit=8;break;
                case KeyEvent.KEYCODE_BUTTON_START:bit=0x10;break;case KeyEvent.KEYCODE_BUTTON_SELECT:bit=0x20;break;
                case KeyEvent.KEYCODE_BUTTON_THUMBL:bit=0x40;break;case KeyEvent.KEYCODE_BUTTON_THUMBR:bit=0x80;break;
                case KeyEvent.KEYCODE_BUTTON_L1:bit=0x100;break;case KeyEvent.KEYCODE_BUTTON_R1:bit=0x200;break;
                case KeyEvent.KEYCODE_BUTTON_A:bit=0x1000;break;case KeyEvent.KEYCODE_BUTTON_B:bit=0x2000;break;
                case KeyEvent.KEYCODE_BUTTON_X:bit=0x4000;break;case KeyEvent.KEYCODE_BUTTON_Y:bit=0x8000;break;
                case KeyEvent.KEYCODE_BUTTON_L2:axes[4]=e.getAction()==KeyEvent.ACTION_DOWN?1:0;sendInput();return true;
                case KeyEvent.KEYCODE_BUTTON_R2:axes[5]=e.getAction()==KeyEvent.ACTION_DOWN?1:0;sendInput();return true;
                default:return super.dispatchKeyEvent(e);
            }
            if(e.getAction()==KeyEvent.ACTION_DOWN) buttons|=bit; else if(e.getAction()==KeyEvent.ACTION_UP) buttons&=~bit;
            sendInput(); return true;
        }
        return super.dispatchKeyEvent(e);
    }
}
