package com.skate3rust.android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

/** Lightweight translucent overlay; routes every finger to its original control. */
final class TouchControlsView extends View {
    final TouchState state=new TouchState();
    private final Runnable changed;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final String[] KEYS={"a","b","x","y","lt","rt","lb","rb","menu","back","up","down","leftArrow","rightArrow"};
    private static final String[] LABELS={"Push A","Brake B","Push X","Board Y","Grab L","Grab R","LB","RB","Menu","Select","↑","↓","←","→"};
    private TouchLayout layout;
    private boolean editing;
    private String dragged;
    private int dragPointer=-1;
    private float dragOffsetX,dragOffsetY;
    private float previewScale=1;
    TouchControlsView(Context context,Runnable changed){super(context);this.changed=changed;layout=TouchLayoutStore.load(context);setFocusable(false);setContentDescription("Skate touch controller");}
    void reloadLayout(){setLayout(TouchLayoutStore.load(getContext()));}
    void setLayout(TouchLayout value){layout=value.copy();clear();}
    TouchLayout layout(){return layout.copy();}
    void setEditing(boolean value){editing=value;clear();}
    void setPreviewScale(float value){previewScale=value;invalidate();}
    private float stickRadius(){return Math.min(getHeight()*.135f,getWidth()*.105f);}
    private float buttonRadius(){return Math.max(getHeight()*.053f,getResources().getDisplayMetrics().density*24*previewScale);}
    private float radius(String key){return key.equals("left")||key.equals("right")?stickRadius():buttonRadius();}
    private float px(String key){float r=radius(key);return Math.max(r,Math.min(getWidth()-r,layout.x(key)*getWidth()));}
    private float py(String key){float r=radius(key);return Math.max(r,Math.min(getHeight()-r,layout.y(key)*getHeight()));}
    private String hit(float x,float y) {
        for(String key:KEYS)if(Math.hypot(x-px(key),y-py(key))<=buttonRadius())return key;
        for(String key:new String[]{"left","right"})if(Math.hypot(x-px(key),y-py(key))<=stickRadius()*1.25f)return key;
        return null;
    }
    void clear(){state.clear();dragged=null;dragPointer=-1;invalidate();}
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);state.clear();changed.run();}
    @Override public boolean onTouchEvent(MotionEvent event) {
        int action=event.getActionMasked(),index=event.getActionIndex();
        if(editing){
            if(action==MotionEvent.ACTION_DOWN){dragged=hit(event.getX(),event.getY());dragPointer=event.getPointerId(0);if(dragged!=null){dragOffsetX=event.getX()-px(dragged);dragOffsetY=event.getY()-py(dragged);}}
            else if(action==MotionEvent.ACTION_MOVE && dragged!=null){
                int i=event.findPointerIndex(dragPointer);
                if(i>=0){float r=radius(dragged);float x=Math.max(r,Math.min(getWidth()-r,event.getX(i)-dragOffsetX));float y=Math.max(r,Math.min(getHeight()-r,event.getY(i)-dragOffsetY));layout.set(dragged,x/getWidth(),y/getHeight());}
            }else if(action==MotionEvent.ACTION_CANCEL||action==MotionEvent.ACTION_UP||(action==MotionEvent.ACTION_POINTER_UP&&event.getPointerId(index)==dragPointer)){dragged=null;dragPointer=-1;}
            invalidate();return true;
        }
        if(action==MotionEvent.ACTION_CANCEL)state.clear();
        else if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN){
            String key=hit(event.getX(index),event.getY(index));
            if(key!=null){
                state.down(event.getPointerId(index),key,android.os.SystemClock.uptimeMillis());
                // Native input is sampled per frame; retain even a very short
                // button tap through several samples, then publish its release.
                postDelayed(()->{changed.run();invalidate();},65);
            }
        } else if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP)state.up(event.getPointerId(index));
        if(action!=MotionEvent.ACTION_CANCEL){
            for(int i=0;i<event.getPointerCount();i++){
                int id=event.getPointerId(i);
                String key=state.control(id);
                if("left".equals(key)||"right".equals(key))state.move(id,(event.getX(i)-px(key))/stickRadius(),(event.getY(i)-py(key))/stickRadius());
            }
        }
        changed.run();invalidate();return true;
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if(editing){canvas.drawColor(Color.rgb(22,24,26));paint.setColor(Color.rgb(43,46,48));paint.setStrokeWidth(1);for(int x=0;x<getWidth();x+=80)canvas.drawLine(x,0,x,getHeight(),paint);for(int y=0;y<getHeight();y+=80)canvas.drawLine(0,y,getWidth(),y,paint);}
        paint.setTextAlign(Paint.Align.CENTER);paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        for(String key:new String[]{"left","right"}){
            float radius=stickRadius(); paint.setColor(Color.argb(65,20,25,28));canvas.drawCircle(px(key),py(key),radius,paint);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2);paint.setColor(Color.argb(150,230,240,235));canvas.drawCircle(px(key),py(key),radius,paint);paint.setStyle(Paint.Style.FILL);
            int offset=key.equals("left")?0:2;
            paint.setColor(Color.argb(130,170,210,100));canvas.drawCircle(px(key)+state.axes[offset]*radius,py(key)+state.axes[offset+1]*radius,radius*.30f,paint);
            paint.setColor(Color.argb(180,255,255,255));paint.setTextSize(getResources().getDisplayMetrics().scaledDensity*11*previewScale);canvas.drawText(key.equals("left")?"STEER":"FLICK",px(key),py(key)-radius-8,paint);
        }
        for(int i=0;i<KEYS.length;i++){
            paint.setColor(state.held(KEYS[i])||KEYS[i].equals(dragged)?Color.argb(175,115,150,60):editing?Color.rgb(45,49,52):Color.argb(90,20,25,28));canvas.drawCircle(px(KEYS[i]),py(KEYS[i]),buttonRadius(),paint);
            paint.setColor(Color.argb(220,255,255,255));paint.setTextSize(getResources().getDisplayMetrics().scaledDensity*11*previewScale);canvas.drawText(LABELS[i],px(KEYS[i]),py(KEYS[i])+paint.getTextSize()*.35f,paint);
        }
    }
}
