package com.gk969.UltimateImgSpider;

import com.gk969.UltimateImgSpider.IRemoteSpiderServiceCallback;

interface IRemoteSpiderService {
    void registerCallback(IRemoteSpiderServiceCallback cb);
    void unregisterCallback(IRemoteSpiderServiceCallback cb);
}
