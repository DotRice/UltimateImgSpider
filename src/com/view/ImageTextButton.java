package com.view;

import com.UltimateImgSpider.R;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.RelativeLayout;
import android.widget.ImageView;
import android.widget.TextView;

public class ImageTextButton extends RelativeLayout
{
    ImageView img;
    TextView text;
    
    public ImageTextButton(Context context, AttributeSet attrs)
    {
        super(context, attrs);
        // TODO Auto-generated constructor stub
        
        LayoutInflater inflater=(LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.image_text_button, this);
        
        img=(ImageView)findViewById(R.id.buttonImg);
        img.setImageResource(attrs.getAttributeResourceValue(null, "image",R.drawable.cancel));
        
        text=(TextView)findViewById(R.id.buttonText);
        text.setText(attrs.getAttributeResourceValue(null, "text",R.string.notSet));
    }
    
}