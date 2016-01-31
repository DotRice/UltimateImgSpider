package com.gk969.View;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

public class LoadingAnimationIcon extends View
{
    private final static String TAG="LoadingAnimationIcon";

    private static final float SPEED_MIN=0.2f;
    private static final float SPEED_MAX=0.8f;

    Paint paint=new Paint();

    float spread=0;
    int direction=1;
    int centerTop;
    int centerLeft;
    int boxSize;
    int spreadMax;
    float speedMaxPos;

    public LoadingAnimationIcon(Context context, AttributeSet attrs)
    {
        super(context, attrs);
        construct(context, attrs);
    }

    public LoadingAnimationIcon(Context context, AttributeSet attrs, int defStyle)
    {
        super(context, attrs, defStyle);
        construct(context, attrs);
    }

    private void construct(Context context, AttributeSet attrs)
    {
        paint.setColor(Color.GREEN);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        Log.i(TAG, "onDetachedFromWindow");
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        Log.i(TAG, "onAttachedToWindow");
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        Log.i(TAG, "onSizeChanged "+w+" "+h+" "+oldw+" "+oldh+" ");

        int size=Math.min(w, h);

        centerTop=h/2;
        centerLeft=w/2;
        boxSize=size/4;
        spreadMax=boxSize;
        speedMaxPos=(float)spreadMax/2;

        //Log.i(TAG, centerTop+" "+centerLeft+" "+slotSize);
        //Log.i(TAG, spread+" "+speed+" "+direction+" "+spreadMax+" "+speedMaxPos);
    }

    @Override
    protected void onDraw(Canvas canvas)
    {
        super.onDraw(canvas);

        float speed=direction*(SPEED_MAX-(SPEED_MAX-SPEED_MIN)*Math.abs(spread-speedMaxPos)/speedMaxPos);

        if((direction>0&&spread>=spreadMax)||(direction<0&&spread<=0))
        {
            direction=0-direction;
        }
        spread+=speed;

        int curLeft=(int)(centerLeft+spread);
        int curTop=(int)(centerTop+spread);
        int halfOfBox=boxSize/2;
        int boxCenterLeft=curLeft + halfOfBox;
        int boxCenterTop=curTop + halfOfBox;

        float boxRotate=(direction<0)?(spreadMax*2-spread):spread;
        float boxDegrees=boxRotate * 180 / spreadMax;

        for(int canvasDegrees=0; canvasDegrees<360; canvasDegrees+=90)
        {
            canvas.rotate(canvasDegrees, centerLeft, centerTop);
            canvas.rotate(boxDegrees, boxCenterLeft, boxCenterTop);
            canvas.drawRect(curLeft, curTop, curLeft + boxSize, curTop + boxSize, paint);
            canvas.rotate(0 - boxDegrees, boxCenterLeft, boxCenterTop);
        }
        
        invalidate();
    }
}