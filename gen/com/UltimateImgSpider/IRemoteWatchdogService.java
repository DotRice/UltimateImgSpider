/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Original file: D:\\android\\UltimateImgSpider\\src\\com\\UltimateImgSpider\\IRemoteWatchdogService.aidl
 */
package com.UltimateImgSpider;
public interface IRemoteWatchdogService extends android.os.IInterface
{
/** Local-side IPC implementation stub class. */
public static abstract class Stub extends android.os.Binder implements com.UltimateImgSpider.IRemoteWatchdogService
{
private static final java.lang.String DESCRIPTOR = "com.UltimateImgSpider.IRemoteWatchdogService";
/** Construct the stub at attach it to the interface. */
public Stub()
{
this.attachInterface(this, DESCRIPTOR);
}
/**
 * Cast an IBinder object into an com.UltimateImgSpider.IRemoteWatchdogService interface,
 * generating a proxy if needed.
 */
public static com.UltimateImgSpider.IRemoteWatchdogService asInterface(android.os.IBinder obj)
{
if ((obj==null)) {
return null;
}
android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
if (((iin!=null)&&(iin instanceof com.UltimateImgSpider.IRemoteWatchdogService))) {
return ((com.UltimateImgSpider.IRemoteWatchdogService)iin);
}
return new com.UltimateImgSpider.IRemoteWatchdogService.Stub.Proxy(obj);
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
case TRANSACTION_registerAshmem:
{
data.enforceInterface(DESCRIPTOR);
android.os.ParcelFileDescriptor _arg0;
if ((0!=data.readInt())) {
_arg0 = android.os.ParcelFileDescriptor.CREATOR.createFromParcel(data);
}
else {
_arg0 = null;
}
this.registerAshmem(_arg0);
reply.writeNoException();
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
case TRANSACTION_getPid:
{
data.enforceInterface(DESCRIPTOR);
int _result = this.getPid();
reply.writeNoException();
reply.writeInt(_result);
return true;
}
}
return super.onTransact(code, data, reply, flags);
}
private static class Proxy implements com.UltimateImgSpider.IRemoteWatchdogService
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
@Override public void registerAshmem(android.os.ParcelFileDescriptor pfd) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
if ((pfd!=null)) {
_data.writeInt(1);
pfd.writeToParcel(_data, 0);
}
else {
_data.writeInt(0);
}
mRemote.transact(Stub.TRANSACTION_registerAshmem, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
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
@Override public int getPid() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
int _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_getPid, _data, _reply, 0);
_reply.readException();
_result = _reply.readInt();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
}
static final int TRANSACTION_registerAshmem = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
static final int TRANSACTION_getAshmem = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
static final int TRANSACTION_getPid = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
}
public void registerAshmem(android.os.ParcelFileDescriptor pfd) throws android.os.RemoteException;
public android.os.ParcelFileDescriptor getAshmem(java.lang.String name, int size) throws android.os.RemoteException;
public int getPid() throws android.os.RemoteException;
}
