package com.gk969.UltimateImgSpider;

import android.graphics.Bitmap;

import com.gk969.Utils.MemoryInfo;

public class StaticValue {
    public final static String BUNDLE_KEY_PROJECT_PATH = "projectPath";
    public final static String BUNDLE_KEY_SOURCE_URL = "sourceUrl";
    public final static String BUNDLE_KEY_CMD = "cmd";
    public final static String BUNDLE_KEY_PRJ_PATH = "projectPath";

    public final static int CMD_NOTHING = 0;
    public final static int CMD_JUST_STOP = 1;
    public final static int CMD_PAUSE = 2;
    public final static int CMD_RESTART = 3;
    public final static int CMD_STOP_STORE = 4;
    public final static int CMD_START = 5;
    public final static int CMD_JUST_STORE = 6;

    public final static String[] CMD_DESC = {"nothing", "just_stop", "pause", "restart", "stop_store",
            "start", "just_store"};

    public static final int MAX_IMG_FILE_PER_DIR = 1000;

    public final static Bitmap.Config BITMAP_TYPE = Bitmap.Config.RGB_565;

    public final static String THUMBNAIL_DIR_NAME = "thumbnail";

    public final static int TILED_BMP_SLOT_SIZE = 254;
    public final static String SLOT_THUMBNAIL_DIR_NAME = THUMBNAIL_DIR_NAME + "/slot";
    public final static int THUMBNAIL_SIZE = TILED_BMP_SLOT_SIZE;

    public final static String FULL_THUMBNAIL_DIR_NAME = THUMBNAIL_DIR_NAME + "/full";
    public final static int SIZE_TO_CREATE_FULL_THUMBNAIL = 2000;
    public final static int FULL_THUMBNAIL_SIZE = 1200;

    public final static String THUMBNAIL_FILE_EXT = "jpg.thumbnail";
    public final static String[] IMG_FILE_EXT = {"jpg", "png", "gif"};

    public final static String PROJECT_THUMBNAIL = "project.thumbnail";


    public final static String PROJECT_DATA_DIR = "/data";
    public final static String PROJECT_DATA_NAME = "/project.dat";
    public final static String PROJECT_DATA_MD5 = "/hash.dat";
    public final static String PROJECT_DATA_BACKUP_NAME = "/project.dat.backup";
    public final static String PROJECT_DATA_BACKUP_MD5 = "/hash.dat.backup";
    public final static String PROJECT_PARAM_NAME = "/param.json";


    public final static int PARA_TOTAL = 0;
    public final static int PARA_PROCESSED = 1;
    public final static int PARA_HEIGHT = 2;
    public final static int PARA_PAYLOAD = 3;
    public final static int PARA_DOWNLOAD = 4;
    public final static int PARA_TOTAL_SIZE = 5;

    public final static int PAGE_PARA_NUM = 3;
    public final static int IMG_PARA_NUM = 6;

    public final static int INDEX_INVALID = -1;

    public final static int RESULT_SRC_URL = 0;
    public final static String EXTRA_URL_TO_OPEN = "urlToOpen";

    public static int getThumbnailCacheSize() {
        return (int) (MemoryInfo.getTotalMemInMb() / 2048 + 1) * 32;
    }

    public static int getSpiderDownloaderThreadNum() {
        return 10;//(int)(MemoryInfo.getTotalMemInMb()/1024)*5+10;
    }
}