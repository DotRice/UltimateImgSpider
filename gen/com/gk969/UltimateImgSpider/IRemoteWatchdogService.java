/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Original file: D:\\android\\UltimateImgSpider\\src\\com\\gk969\\UltimateImgSpider\\IRemoteWatchdogService.aidl
 */
package com.gk969.UltimateImgSpider;
public interface IRemoteWatchdogService extends android.os.IInterface
{
/** Local-side IPC implementation stub class. */
public static abstract class Stub extends android.os.Binder implements com.gk969.UltimateImgSpider.IRemoteWatchdogService
{
private static final java.lang.String DESCRIPTOR = "com.gk969.UltimateImgSpider.IRemoteWatchdogService";
/** Construct the stub at attach it to the interface. */
public Stub()
{
this.attachInterface(this, DESCRIPTOR);
}
/**
 * Cast an IBinder object into an com.gk969.UltimateImgSpider.IRemoteWatchdogService interface,
 * generating a proxy if needed.
 */
public static com.gk969.UltimateImgSpider.IRemoteWatchdogService asInterface(android.os.IBinder obj)
{
if ((obj==null)) {
return null;
}
android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
if (((iin!=null)&&(iin instanceof com.gk969.UltimateImgSpider.IRemoteWatchdogService))) {
return ((com.gk969.UltimateImgSpider.IRemoteWatchdogService)iin);
}
return new com.gk969.UltimateImgSpider.IRemoteWatchdogService.Stub.Proxy(obj);
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
case TRANSACTION_getAshmem:
{
data.enforceInterface(DESCRIPTOR);
java.lang.String _arg0;
_arg0 = data.readString();
int _arg1;
_arg1 = data.readInt();
android.os.ParcelFileDescriptor _result = this.getAshmem(_arg0, _arg1);
reply.writeNoException();
if ((_result!=null)) {
reply.writeInt(1);
_result.writeToParcel(reply, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
}
else {
reply.writeInt(0);
}
return true;
}
}
return super.onTransact(code, data, reply, flags);
}
private static class Proxy implements com.gk969.UltimateImgSpider.IRemoteWatchdogService
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
@Override public android.os.ParcelFileDescriptor getAshmem(java.lang.String name, int size) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
android.os.ParcelFileDescriptor _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeString(name);
_data.writeInt(size);
mRemote.transact(Stub.TRANSACTION_getAshmem, _data, _reply, 0);
_reply.readException();
if ((0!=_reply.readInt())) {
_result = android.os.ParcelFileDescriptor.CREATOR.createFromParcel(_reply);
}
else {
_result = null;
}
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
}
static final int TRANSACTION_getAshmem = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
}
public android.os.ParcelFileDescriptor getAshmem(java.lang.String name, int size) throws android.os.RemoteException;
}
