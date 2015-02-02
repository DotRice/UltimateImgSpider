package com.UltimateImgSpider;

import com.UltimateImgSpider.IRemoteSpiderServiceCallback;

interface IRemoteSpiderService {
    void registerCallback(IRemoteSpiderServiceCallback cb);
    void unregisterCallback(IRemoteSpiderServiceCallback cb);
    int getPid();
}
