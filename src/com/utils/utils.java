package com.utils;

public class utils
{
    static public int strSimilarity(String s1, String s2)
    {
        int len=(s1.length()<s2.length())?s1.length():s2.length();
        
        int i;
        for(i=0; i<len; i++)
        {
            if(s1.charAt(i)!=s2.charAt(i))
            {
                break;
            }
        }
        
        return i;
    }
}
