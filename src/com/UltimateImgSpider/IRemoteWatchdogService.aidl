package com.UltimateImgSpider;

import android.os.ParcelFileDescriptor;

interface IRemoteWatchdogService {
    void registerAshmem(in ParcelFileDescriptor pfd);
    int getPid();
}
