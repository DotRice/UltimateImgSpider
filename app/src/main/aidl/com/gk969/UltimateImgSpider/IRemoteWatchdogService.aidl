package com.gk969.UltimateImgSpider;

import android.os.ParcelFileDescriptor;
import com.gk969.UltimateImgSpider.IRemoteWatchdogServiceCallback;

interface IRemoteWatchdogService {
    ParcelFileDescriptor getAshmem(String name, int size);
    void registerCallback(IRemoteWatchdogServiceCallback cb);
    void unregisterCallback(IRemoteWatchdogServiceCallback cb);
}
