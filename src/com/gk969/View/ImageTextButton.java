package com.gk969.View;
 
import com.gk969.Utils.Utils;
import com.gk969.UltimateImgSpider.R;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.widget.RelativeLayout;
import android.widget.ImageView;
import android.widget.TextView;

public class ImageTextButton extends RelativeLayout
{
    public ImageView imageView;
    public TextView textView;
    
    private void construct(Context context, AttributeSet attrs)
    {
        LayoutInflater inflater=(LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.image_text_button, this);
        
        imageView=(ImageView)findViewById(R.id.buttonImg);
        imageView.setImageResource(attrs.getAttributeResourceValue(null, "image", R.drawable.cancel));
        
        String sizeAttr=attrs.getAttributeValue(null, "image_size");
        if(sizeAttr!=null)
        {
	        int imgSize=Utils.DisplayUtil.attrToPx(context, sizeAttr);
	        
	        android.view.ViewGroup.LayoutParams lp=imageView.getLayoutParams();
	        lp.width=imgSize;
	        lp.height=imgSize;
	        imageView.setLayoutParams(lp);
        }
        
        textView=(TextView)findViewById(R.id.buttonText);
        textView.setText(attrs.getAttributeResourceValue(null, "text", R.string.notSet));
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
    
    public void changeView(int imgResId, int textResId)
    {
    	imageView.setImageResource(imgResId);
    	textView.setText(textResId);
    }
    
}