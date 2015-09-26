/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Original file: D:\\android\\UltimateImgSpider\\app\\src\\main\\aidl\\com\\gk969\\UltimateImgSpider\\IRemoteSpiderServiceCallback.aidl
 */
package com.gk969.UltimateImgSpider;
/**
 * Example of a callback interface used by IRemoteService to send
 * synchronous notifications back to its clients.  Note that this is a
 * one-way interface so the server does not block waiting for the client.
 */
public interface IRemoteSpiderServiceCallback extends android.os.IInterface
{
/** Local-side IPC implementation stub class. */
public static abstract class Stub extends android.os.Binder implements com.gk969.UltimateImgSpider.IRemoteSpiderServiceCallback
{
private static final java.lang.String DESCRIPTOR = "com.gk969.UltimateImgSpider.IRemoteSpiderServiceCallback";
/** Construct the stub at attach it to the interface. */
public Stub()
{
this.attachInterface(this, DESCRIPTOR);
}
/**
 * Cast an IBinder object into an com.gk969.UltimateImgSpider.IRemoteSpiderServiceCallback interface,
 * generating a proxy if needed.
 */
public static com.gk969.UltimateImgSpider.IRemoteSpiderServiceCallback asInterface(android.os.IBinder obj)
{
if ((obj==null)) {
return null;
}
android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
if (((iin!=null)&&(iin instanceof com.gk969.UltimateImgSpider.IRemoteSpiderServiceCallback))) {
return ((com.gk969.UltimateImgSpider.IRemoteSpiderServiceCallback)iin);
}
return new com.gk969.UltimateImgSpider.IRemoteSpiderServiceCallback.Stub.Proxy(obj);
}
@Override public android.os.IBinder asBinder()
{
return this;
}
@Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
{
switch (code)
{
case INTERFACE_TRANSACTION:
{
reply.writeString(DESCRIPTOR);
return true;
}
case TRANSACTION_reportStatus:
{
data.enforceInterface(DESCRIPTOR);
java.lang.String _arg0;
_arg0 = data.readString();
this.reportStatus(_arg0);
return true;
}
}
return super.onTransact(code, data, reply, flags);
}
private static class Proxy implements com.gk969.UltimateImgSpider.IRemoteSpiderServiceCallback
{
private android.os.IBinder mRemote;
Proxy(android.os.IBinder remote)
{
mRemote = remote;
}
@Override public android.os.IBinder asBinder()
{
return mRemote;
}
public java.lang.String getInterfaceDescriptor()
{
return DESCRIPTOR;
}
@Override public void reportStatus(java.lang.String value) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeString(value);
mRemote.transact(Stub.TRANSACTION_reportStatus, _data, null, android.os.IBinder.FLAG_ONEWAY);
}
finally {
_data.recycle();
}
}
}
static final int TRANSACTION_reportStatus = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
}
public void reportStatus(java.lang.String value) throws android.os.RemoteException;
}
