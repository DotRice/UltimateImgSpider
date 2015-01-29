package com.UltimateImgSpider;

import com.UltimateImgSpider.IRemoteSpiderServiceCallback;

interface IRemoteSpiderService {

    void registerCallback(IRemoteSpiderServiceCallback cb);
  
    void unregisterCallback(IRemoteSpiderServiceCallback cb);
    
    void setSpiderSrcUrl(String url);

    int getPid();
}
