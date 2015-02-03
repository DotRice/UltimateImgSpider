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
	 * dp��sp ת��Ϊ px �Ĺ�����
	 * 
	 * 
	 */
	public static class DisplayUtil
	{
		/**
		 * ��pxֵת��Ϊdip��dpֵ����֤�ߴ��С����
		 * 
		 * @param pxValue
		 * @param scale
		 *            ��DisplayMetrics��������density��
		 * @return
		 */
		public static int pxToDip(Context context, float pxValue)
		{
			final float scale = context.getResources().getDisplayMetrics().density;
			return (int) (pxValue / scale + 0.5f);
		}
		
		/**
		 * ��dip��dpֵת��Ϊpxֵ����֤�ߴ��С����
		 * 
		 * @param dipValue
		 * @param scale
		 *            ��DisplayMetrics��������density��
		 * @return
		 */
		public static int dipToPx(Context context, float dipValue)
		{
			final float scale = context.getResources().getDisplayMetrics().density;
			return (int) (dipValue * scale + 0.5f);
		}
		
		/**
		 * ��pxֵת��Ϊspֵ����֤���ִ�С����
		 * 
		 * @param pxValue
		 * @param fontScale
		 *            ��DisplayMetrics��������scaledDensity��
		 * @return
		 */
		public static int pxToSp(Context context, float pxValue)
		{
			final float fontScale = context.getResources().getDisplayMetrics().scaledDensity;
			return (int) (pxValue / fontScale + 0.5f);
		}
		
		/**
		 * ��spֵת��Ϊpxֵ����֤���ִ�С����
		 * 
		 * @param spValue
		 * @param fontScale
		 *            ��DisplayMetrics��������scaledDensity��
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
