package com.skate3rust.android;
import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
public final class TouchLayoutActivity extends Activity {
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(0xff101113);
        LinearLayout toolbar=new LinearLayout(this);toolbar.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView title=new TextView(this);title.setText("Touch controls\nDrag buttons and sticks to move them");title.setTextColor(0xffeeeeee);title.setTextSize(16);title.setPadding(24,8,8,8);toolbar.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        TouchControlsView editor=new TouchControlsView(this,()->{});editor.setEditing(true);
        for(String action:new String[]{"Save","Cancel","Reset layout"}){
            Button button=new Button(this);button.setText(action);toolbar.addView(button);
            button.setOnClickListener(v->{
                if(action.equals("Save")){if(TouchLayoutStore.save(this,editor.layout()))finish();else Toast.makeText(this,"Could not save layout",Toast.LENGTH_LONG).show();}
                else if(action.equals("Cancel"))finish();else editor.setLayout(new TouchLayout());
            });
        }
        root.addView(toolbar,new LinearLayout.LayoutParams(-1,-2));
        FrameLayout preview=new FrameLayout(this);root.addView(preview,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
        float aspect=getIntent().getFloatExtra("aspect",2.2105f);
        int gameWidth=Math.max(1,getIntent().getIntExtra("gameWidth",getResources().getDisplayMetrics().widthPixels));
        float safeAspect=Float.isFinite(aspect)&&aspect>.5f&&aspect<4?aspect:2.2105f;
        preview.post(()->{int width=Math.min(preview.getWidth(),(int)(preview.getHeight()*safeAspect));int height=(int)(width/safeAspect);editor.setPreviewScale(width/(float)gameWidth);preview.addView(editor,new FrameLayout.LayoutParams(width,height,android.view.Gravity.CENTER));});
    }
}
