package com.gk969.UltimateImgSpider;

public class StaticValue
{
    public final static String      BUNDLE_KEY_SOURCE_URL = "sourceUrl";
    public final static String      BUNDLE_KEY_CMD        = "cmd";
    public final static String      BUNDLE_KEY_PRJ_PATH   = "projectPath";

    public final static int  CMD_NOTHING           = 0;
    public final static int  CMD_JUST_STOP         = 1;
    public final static int  CMD_PAUSE             = 2;
    public final static int  CMD_CONTINUE          = 3;
    public final static int  CMD_RESTART           = 4;
    public final static int  CMD_STOP_STORE        = 5;
    public final static int  CMD_START             = 6;

    public static final int   MAX_IMG_FILE_PER_DIR=500;

    public final static String THUMBNAIL_DIR_NAME="thumbnail";
    public final static int THUMBNAIL_SIZE = 300;

}