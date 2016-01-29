package com.gk969.UltimateImgSpider;

public class StaticValue
{
    public final static String      BUNDLE_KEY_SOURCE_URL = "sourceUrl";
    public final static String      BUNDLE_KEY_CMD        = "cmd";
    public final static String      BUNDLE_KEY_PRJ_PATH   = "projectPath";

    public final static int  CMD_NOTHING           = 0;
    public final static int  CMD_JUST_STOP         = 1;
    public final static int  CMD_PAUSE             = 2;
    public final static int  CMD_RESTART           = 3;
    public final static int  CMD_STOP_STORE        = 4;
    public final static int  CMD_START             = 5;
    public final static int  CMD_PAUSE_ON_START    = 6;
    public final static int  CMD_JUST_STORE        = 7;

    public static final int   MAX_IMG_FILE_PER_DIR=1000;

    public final static String THUMBNAIL_DIR_NAME="thumbnail";
    public final static int THUMBNAIL_SIZE = 300;

}