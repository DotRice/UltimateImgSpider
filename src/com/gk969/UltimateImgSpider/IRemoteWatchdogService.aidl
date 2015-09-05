package com.gk969.UltimateImgSpider;

import android.os.ParcelFileDescriptor;

interface IRemoteWatchdogService {
    ParcelFileDescriptor getAshmem(String name, int size);
}
