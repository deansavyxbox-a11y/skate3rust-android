package com.skate3rust.android;
import android.content.Context;
import android.graphics.*;
import android.view.MotionEvent;
import android.view.View;
final class LaunchScreenView extends View {
    private final Bitmap brand;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Runnable close;
    private int stage;
    LaunchScreenView(Context context,Runnable close){super(context);this.close=close;brand=BitmapFactory.decodeResource(getResources(),R.drawable.skate_brand);setContentDescription("Skate 3 Built in Rust loading screen");}
    void stage(int value){stage=value;invalidate();}
    @Override protected void onDraw(Canvas canvas){
        canvas.drawColor(Color.rgb(16,17,19));
        float size=Math.min(getWidth()*.48f,getHeight()*.64f);
        float x=(getWidth()-size)/2,y=getHeight()*.04f;
        canvas.drawBitmap(brand,null,new RectF(x,y,x+size,y+size),paint);
        paint.setTypeface(Typeface.DEFAULT_BOLD);paint.setTextAlign(Paint.Align.CENTER);paint.setColor(Color.rgb(236,239,241));paint.setTextSize(getHeight()*.05f);
        canvas.drawText("BUILT IN RUST",getWidth()/2f,getHeight()*.77f,paint);
        String[] phases={"Starting engine…","Loading map…","Loading animations…","Building physics…","Preparing graphics…","Ready"};
        paint.setTypeface(Typeface.DEFAULT);paint.setTextSize(getHeight()*.033f);paint.setColor(Color.rgb(185,192,197));
        canvas.drawText(stage==255?"Could not load gameplay. Close and check the installation.":phases[Math.max(0,Math.min(stage,5))],getWidth()/2f,getHeight()*.85f,paint);
        paint.setColor(Color.rgb(231,79,39));
        if(stage==255){paint.setTextSize(getHeight()*.037f);canvas.drawText("CLOSE",getWidth()/2f,getHeight()*.95f,paint);}
        else {paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(4);float r=getHeight()*.02f;float cx=getWidth()/2f,cy=getHeight()*.94f;canvas.drawArc(cx-r,cy-r,cx+r,cy+r,(android.os.SystemClock.uptimeMillis()/4)%360,240,false,paint);paint.setStyle(Paint.Style.FILL);}
    }
    @Override public boolean onTouchEvent(MotionEvent event){if(stage==255 && event.getActionMasked()==MotionEvent.ACTION_UP && event.getY()>getHeight()*.89f)close.run();return true;}
}
