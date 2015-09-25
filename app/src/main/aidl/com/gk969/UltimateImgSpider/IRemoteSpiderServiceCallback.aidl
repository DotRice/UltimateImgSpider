package com.gk969.UltimateImgSpider;

/**
 * Example of a callback interface used by IRemoteService to send
 * synchronous notifications back to its clients.  Note that this is a
 * one-way interface so the server does not block waiting for the client.
 */
oneway interface IRemoteSpiderServiceCallback {
    void reportStatus(String value);
}
