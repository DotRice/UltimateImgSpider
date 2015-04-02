package com.UltimateImgSpider;

import android.os.ParcelFileDescriptor;

interface IRemoteWatchdogService {
    void registerAshmem(in ParcelFileDescriptor pfd);
    ParcelFileDescriptor getAshmem(String name, int size);
    int getPid();
}
