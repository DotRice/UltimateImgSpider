package com.gk969.View;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;

import com.gk969.UltimateImgSpider.R;

/**
 * Created by SongJian on 2016/10/17.
 */

public class RadiusCornerButton extends Button {
    static final String TAG="RadiusCornerButton";

    private void construct(Context context, AttributeSet attrs)
    {
        setPadding(0,0,0,0);
        setGravity(Gravity.CENTER);
        setBackgroundResource(R.drawable.button_background);
        setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                Log.i(TAG, "onTouch "+motionEvent.getAction());
                switch (motionEvent.getAction()){
                    case MotionEvent.ACTION_DOWN:
                        view.setBackgroundResource(R.drawable.button_on_touch_background);
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        view.setBackgroundResource(R.drawable.button_background);
                        break;
                }
                return false;
            }
        });
        setFocusable(true);
        setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean b) {
                view.setBackgroundResource(b?R.drawable.button_focus_backgrou:R.drawable.button_background);
            }
        });
    }

    public RadiusCornerButton(Context context, AttributeSet attrs)
    {
        super(context, attrs);
        construct(context, attrs);
    }

    public RadiusCornerButton(Context context, AttributeSet attrs, int defStyle)
    {
        super(context, attrs, defStyle);
        construct(context, attrs);
    }
}
