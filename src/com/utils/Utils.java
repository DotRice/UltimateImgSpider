package com.Utils;

import android.content.Context;

public class Utils
{
	static public int strSimilarity(String s1, String s2)
	{
		int len = (s1.length() < s2.length()) ? s1.length() : s2.length();
		
		int i;
		for (i = 0; i < len; i++)
		{
			if (s1.charAt(i) != s2.charAt(i))
			{
				break;
			}
		}
		
		return i;
	}
	
	/**
	 * dp、sp 转换为 px 的工具类
	 * 
	 * 
	 */
	public static class DisplayUtil
	{
		/**
		 * 将px值转换为dip或dp值，保证尺寸大小不变
		 * 
		 * @param pxValue
		 * @param scale
		 *            （DisplayMetrics类中属性density）
		 * @return
		 */
		public static int pxToDip(Context context, float pxValue)
		{
			final float scale = context.getResources().getDisplayMetrics().density;
			return (int) (pxValue / scale + 0.5f);
		}
		
		/**
		 * 将dip或dp值转换为px值，保证尺寸大小不变
		 * 
		 * @param dipValue
		 * @param scale
		 *            （DisplayMetrics类中属性density）
		 * @return
		 */
		public static int dipToPx(Context context, float dipValue)
		{
			final float scale = context.getResources().getDisplayMetrics().density;
			return (int) (dipValue * scale + 0.5f);
		}
		
		/**
		 * 将px值转换为sp值，保证文字大小不变
		 * 
		 * @param pxValue
		 * @param fontScale
		 *            （DisplayMetrics类中属性scaledDensity）
		 * @return
		 */
		public static int pxToSp(Context context, float pxValue)
		{
			final float fontScale = context.getResources().getDisplayMetrics().scaledDensity;
			return (int) (pxValue / fontScale + 0.5f);
		}
		
		/**
		 * 将sp值转换为px值，保证文字大小不变
		 * 
		 * @param spValue
		 * @param fontScale
		 *            （DisplayMetrics类中属性scaledDensity）
		 * @return
		 */
		public static int spToPx(Context context, float spValue)
		{
			final float fontScale = context.getResources().getDisplayMetrics().scaledDensity;
			return (int) (spValue * fontScale + 0.5f);
		}
		
		public static int attrToPx(Context context, String attr)
		{
			int px = 0;
			try
			{
				int attrVal = Integer.parseInt(attr.substring(0,
				        attr.length() - 2));
				if (attr.endsWith("px"))
				{
					px = attrVal;
				}
				else if (attr.endsWith("dp"))
				{
					px = dipToPx(context, attrVal);
				}
				else if (attr.endsWith("sp"))
				{
					px = spToPx(context, attrVal);
				}
			}
			catch (NumberFormatException e)
			{
				
			}
			return px;
		}
	}
	
}
