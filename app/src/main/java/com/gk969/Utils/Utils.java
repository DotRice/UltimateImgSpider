package com.gk969.Utils;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.TrafficStats;
import android.os.Handler;

import android.content.Context;
import android.os.Environment;
import android.os.SystemClock;
import android.support.v4.content.ContextCompat;
import android.util.Log;

public class Utils {
    static String TAG = "Utils";

    static public int strSimilarity(String s1, String s2) {
        int len = (s1.length() < s2.length()) ? s1.length() : s2.length();

        int i;
        for(i = 0; i < len; i++) {
            if(s1.charAt(i) != s2.charAt(i)) {
                break;
            }
        }

        return i;
    }

    public static boolean isArrayEquals(byte[] array1, byte[] array2) {
        if(array1.length == array2.length) {
            int len = array2.length;

            for(int i = 0; i < len; i++) {
                if(array1[i] != array2[i]) {
                    return false;
                }
            }

            return true;
        }

        return false;
    }

    public static File newFile(String filePath) {
        File file = new File(filePath);

        File dir = new File(file.getParent());
        if(!dir.exists()) {
            dir.mkdirs();
        }

        return file;
    }

    public static boolean deleteDir(File dirFile) {
        if(dirFile == null) {
            return false;
        }

        if(!dirFile.exists() || !dirFile.isDirectory()) {
            return false;
        }

        File[] files = dirFile.listFiles();
        if(files != null) {
            for(File file : files) {
                if(file.isFile()) {
                    if(!file.delete()) {
                        return false;
                    }
                }
                else {
                    if(!deleteDir(file)) {
                        return false;
                    }
                }
            }
        }

        return dirFile.delete();
    }

    public static String byteArrayToHexString(byte[] arrayIn) {
        if(arrayIn == null) {
            return null;
        }

        StringBuilder builder = new StringBuilder(arrayIn.length * 2);

        for(byte oneByte : arrayIn) {
            builder.append(String.format("%02X", oneByte));
        }

        return builder.toString();
    }

    public static String getFileMD5String(String filePath) {
        return byteArrayToHexString(getFileMD5(filePath));
    }

    public static byte[] getFileMD5(String filePath) {
        FileInputStream fileInputStream = null;

        try {
            fileInputStream = new FileInputStream(filePath);
            MessageDigest digester = MessageDigest.getInstance("MD5");
            byte[] bytes = new byte[8192];
            int byteCount;
            while((byteCount = fileInputStream.read(bytes)) > 0) {
                digester.update(bytes, 0, byteCount);
            }

            return digester.digest();
        } catch(FileNotFoundException e) {
            e.printStackTrace();
        } catch(NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch(IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if(fileInputStream != null) fileInputStream.close();
            } catch(IOException e) {
                e.printStackTrace();
            }
        }

        return null;
    }


    public static class NetTrafficCalc {
        public AtomicLong netTrafficPerSec = new AtomicLong(0);
        private long lastTraffic = 0;
        private long refreshTime = 0;
        private Context context;

        private final static int AVERAGE_BUF_SIZE = 3;
        private int avrgBufIndex;
        private long[] averageBuf = new long[AVERAGE_BUF_SIZE];
        private long[] intervalBuf = new long[AVERAGE_BUF_SIZE];

        public NetTrafficCalc(Context ctx) {
            context = ctx;
        }

        public void refreshNetTraffic() {
            long curTraffic = getNetTraffic();
            if(curTraffic != 0) {
                long curTime = SystemClock.uptimeMillis();
                if(lastTraffic != 0) {
                    averageBuf[avrgBufIndex] = (curTraffic - lastTraffic);
                    intervalBuf[avrgBufIndex] = (curTime - refreshTime);

                    avrgBufIndex++;
                    avrgBufIndex %= AVERAGE_BUF_SIZE;

                    long traffic = 0;
                    long time = 0;
                    for(int i = 0; i < AVERAGE_BUF_SIZE; i++) {
                        traffic += averageBuf[i];
                        time += intervalBuf[i];
                    }

                    netTrafficPerSec.set(traffic * 1000 / time);
                }
                refreshTime = curTime;
                lastTraffic = curTraffic;
            }
        }

        private long getNetTraffic() {
            try {
                ApplicationInfo appInfo = context.getPackageManager().getApplicationInfo(
                        context.getPackageName(), PackageManager.GET_ACTIVITIES);
                return TrafficStats.getUidRxBytes(appInfo.uid) + TrafficStats.getUidTxBytes(appInfo.uid);
            } catch(PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }

            return 0;
        }
    }

    public static class ReadWaitLock {
        public AtomicBoolean isLocked = new AtomicBoolean(false);

        public synchronized void waitIfLocked() {
            while(isLocked.get()) {
                try {
                    wait();
                } catch(InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        public synchronized void lock() {
            while(isLocked.get()) {
                try {
                    wait();
                } catch(InterruptedException e) {
                    e.printStackTrace();
                }
            }
            isLocked.set(true);
        }

        public synchronized void unlock() {
            isLocked.set(false);
            notifyAll();
        }
    }

    public static class CubicBezier {
        static final String TAG = "CubicBezier";

        private class Point {
            float x;
            float y;

            public Point(float pointX, float pointY) {
                x = pointX;
                y = pointY;
            }
        }

        private Point controlPointA;
        private Point controlPointB;

        private static final int CALC_POINT_NUM = 6;
        private Point[] midPoints = new Point[CALC_POINT_NUM];

        private final Point cubicStartPoint = new Point(0, 0);
        private final Point cubicEndPoint = new Point(1, 1);

        public CubicBezier(float pointAx, float pointAy, float pointBx, float pointBy) {
            if(pointAx < 0) {
                pointAx = 0;
            }
            else if(pointAx > 1) {
                pointAx = 1;
            }

            if(pointBx < 0) {
                pointBx = 0;
            }
            else if(pointBx > 1) {
                pointBx = 1;
            }

            controlPointA = new Point(pointAx, pointAy);
            controlPointB = new Point(pointBx, pointBy);

            for(int i = 0; i < CALC_POINT_NUM; i++) {
                midPoints[i] = new Point(0, 0);
            }
        }

        private void calculateMidPoint(Point startPoint, Point endPoint, Point midPoint, float progress) {
            midPoint.x = startPoint.x + (endPoint.x - startPoint.x) * progress;
            midPoint.y = startPoint.y + (endPoint.y - startPoint.y) * progress;
        }

        public float calculateYByX(float x) {
            float y;

            if(x <= 0) {
                y = 0;
            }
            else if(x >= 1) {
                y = 1;
            }
            else {
                calculateMidPoint(cubicStartPoint, controlPointA, midPoints[0], x);
                calculateMidPoint(controlPointA, controlPointB, midPoints[1], x);
                calculateMidPoint(controlPointB, cubicEndPoint, midPoints[2], x);

                calculateMidPoint(midPoints[0], midPoints[1], midPoints[3], x);
                calculateMidPoint(midPoints[1], midPoints[2], midPoints[4], x);

                calculateMidPoint(midPoints[3], midPoints[4], midPoints[5], x);

                y = midPoints[5].y;
            }
            return y;
        }

        static void test() {
            String str = "";
            Utils.CubicBezier cubicBezier = new Utils.CubicBezier(0.5f, 0f, 0.5f, 1f);

            float x = 0;
            for(int i = 0; i < 50; i++) {
                str += String.format("%.3f ", cubicBezier.calculateYByX(x));
                x += 0.02f;
            }
            Log.i(TAG, str);
        }
    }

    public static String byteSizeToString(long size) {
        String[] sizeUnitName = new String[]{"GB", "MB", "KB"};
        int[] sizeUnit = new int[]{1 << 30, 1 << 20, 1 << 10, 1};

        for(int i = 0; i < sizeUnitName.length; i++) {
            if(size >= sizeUnit[i]) {
                size /= sizeUnit[i + 1];
                float sizeInFloat = (float) size / 1024;
                return String.format("%.2f%s", sizeInFloat, sizeUnitName[i]);
            }
        }

        return size + "B";
    }

    public static void stringToFile(String str, String filePath) {
        try {
            FileOutputStream fileOut = new FileOutputStream(filePath);
            fileOut.write(str.getBytes());
            fileOut.close();
        } catch(FileNotFoundException e) {
            e.printStackTrace();
        } catch(IOException e) {
            e.printStackTrace();
        }
    }


    public static void saveBitmapToFile(Bitmap bmp, File file) {
        FileOutputStream fileOut;
        try {
            fileOut = new FileOutputStream(file);
            bmp.compress(Bitmap.CompressFormat.JPEG, 90, fileOut);
            fileOut.flush();
            fileOut.close();
        } catch(FileNotFoundException e) {
            e.printStackTrace();
        } catch(IOException e) {
            e.printStackTrace();
        }
    }

    public static String fileToString(String filePath) {
        File file = new File(filePath);
        String string = null;

        if(file.isFile()) {
            byte[] buf = new byte[(int) file.length()];
            try {
                FileInputStream fileIn = new FileInputStream(file);
                fileIn.read(buf);
                fileIn.close();
            } catch(FileNotFoundException e) {
                e.printStackTrace();
            } catch(IOException e) {
                e.printStackTrace();
            }
            string = new String(buf);
        }

        Log.i(TAG, "fileToString " + filePath + " " + string);
        return string;
    }

    public static LinkedList<String> exeShell(String cmd) {
        LinkedList<String> ret = new LinkedList<String>();

        try {
            Process proc = Runtime.getRuntime().exec(cmd);
            String line;
            BufferedReader inputStream = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            while((line = inputStream.readLine()) != null) {
                ret.add(line);
            }
        } catch(IOException e) {
            e.printStackTrace();
        }


        return ret;
    }

    public static boolean mayBeUrl(String str){
        int posOdDot=str.lastIndexOf(".");
        return posOdDot>0&&posOdDot<(str.length()-2);
    }

    public static class LogRecorder extends Thread {
        private AtomicBoolean isRunning = new AtomicBoolean(true);
        private File recordFile;

        public void stopThread() {
            isRunning.set(false);
        }

        public LogRecorder(String appName) {
            setDaemon(true);
            File logDir=StorageUtils.getDirInSto(appName + "/log");
            if(logDir!=null) {
                recordFile = new File(logDir.getPath()+"/log.txt");
                start();
            }
        }

        public void run() {
            Process logcatProcess;

            try {
                Runtime.getRuntime().exec("logcat -c").waitFor();
                logcatProcess = Runtime.getRuntime().exec("logcat");

                BufferedReader logStream = null;
                FileOutputStream logFileOut = null;
                try {
                    logStream = new BufferedReader(
                            new InputStreamReader(logcatProcess.getInputStream()));

                    logFileOut = new FileOutputStream(recordFile.getPath());

                    String logLine;

                    while((logLine = logStream.readLine()) != null) {
                        logLine += "\r\n";
                        logFileOut.write(logLine.getBytes());

                        if(!isRunning.get()) {
                            break;
                        }

                        yield();
                    }
                } finally {
                    if(logStream != null) {
                        logStream.close();
                    }

                    if(logFileOut != null) {
                        logFileOut.flush();
                        logFileOut.close();
                    }
                }
            } catch(InterruptedException e) {
                e.printStackTrace();
            } catch(IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static String getSDKVersion() {
        return android.os.Build.VERSION.RELEASE;
    }

    public static String getPhoneType() {
        return android.os.Build.MODEL;
    }

    public static boolean isNetworkEffective() {
        String stableWebUrl[] = {"http://www.baidu.com", "http://www.qq.com"};

        for(String webUrl : stableWebUrl) {
            try {
                URL url = new URL(webUrl);

                HttpURLConnection urlConn = (HttpURLConnection) url
                        .openConnection();

                try {
                    urlConn.setConnectTimeout(10000);
                    urlConn.setReadTimeout(5000);

                    if(urlConn.getResponseCode() == 200) {
                        urlConn.disconnect();
                        Log.i(TAG, "isNetworkEffective " + webUrl);
                        return true;
                    }
                } finally {
                    if(urlConn != null)
                        urlConn.disconnect();
                }
            } catch(MalformedURLException e) {
                e.printStackTrace();
            } catch(IOException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    /**
     * dp、sp 转换为 px 的工具类
     */
    public static class DisplayUtil {
        /**
         * 将px值转换为dip或dp值，保证尺寸大小不变
         */
        public static int pxToDip(Context context, float pxValue) {
            final float scale = context.getResources().getDisplayMetrics().density;
            return (int) (pxValue / scale + 0.5f);
        }

        /**
         * 将dip或dp值转换为px值，保证尺寸大小不变
         */
        public static int dipToPx(Context context, float dipValue) {
            final float scale = context.getResources().getDisplayMetrics().density;
            return (int) (dipValue * scale + 0.5f);
        }

        /**
         * 将px值转换为sp值，保证文字大小不变
         */
        public static int pxToSp(Context context, float pxValue) {
            final float fontScale = context.getResources().getDisplayMetrics().scaledDensity;
            return (int) (pxValue / fontScale + 0.5f);
        }

        /**
         * 将sp值转换为px值，保证文字大小不变
         */
        public static int spToPx(Context context, float spValue) {
            final float fontScale = context.getResources().getDisplayMetrics().scaledDensity;
            return (int) (spValue * fontScale + 0.5f);
        }

        public static int attrToPx(Context context, String attr) {
            int px = 0;
            try {
                int attrVal = Integer.parseInt(attr.substring(0,
                        attr.length() - 2));
                if(attr.endsWith("px")) {
                    px = attrVal;
                }
                else if(attr.endsWith("dp")) {
                    px = dipToPx(context, attrVal);
                }
                else if(attr.endsWith("sp")) {
                    px = spToPx(context, attrVal);
                }
            } catch(NumberFormatException e) {
                e.printStackTrace();
            }
            return px;
        }
    }
}
