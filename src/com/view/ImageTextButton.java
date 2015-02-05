package com.View;
 
import com.Utils.Utils;
import com.UltimateImgSpider.R;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.widget.RelativeLayout;
import android.widget.ImageView;
import android.widget.TextView;

public class ImageTextButton extends RelativeLayout
{
    ImageView img;
    TextView text;
    
    private void construct(Context context, AttributeSet attrs)
    {
        LayoutInflater inflater=(LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.image_text_button, this);
        
        img=(ImageView)findViewById(R.id.buttonImg);
        img.setImageResource(attrs.getAttributeResourceValue(null, "image", R.drawable.cancel));
        
        String sizeAttr=attrs.getAttributeValue(null, "image_size");
        if(sizeAttr!=null)
        {
	        int imgSize=Utils.DisplayUtil.attrToPx(context, sizeAttr);
	        
	        android.view.ViewGroup.LayoutParams lp=img.getLayoutParams();
	        lp.width=imgSize;
	        lp.height=imgSize;
	        img.setLayoutParams(lp);
        }
        
        text=(TextView)findViewById(R.id.buttonText);
        text.setText(attrs.getAttributeResourceValue(null, "text", R.string.notSet));
    }
    
    public ImageTextButton(Context context, AttributeSet attrs)
    {
        super(context, attrs);
        // TODO Auto-generated constructor stub
        construct(context, attrs);
    }
    
    public ImageTextButton(Context context, AttributeSet attrs, int defStyle)
    {
        super(context, attrs, defStyle);
        construct(context, attrs);
    }
}