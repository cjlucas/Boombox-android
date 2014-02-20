package net.cjlucas.boombox.provider;

abstract class AudioDataProvider extends Thread
{
    public static final int STATUS_EOF_REACHED = -1;
    public static final int STATUS_ERROR_OCCURED = -2;

    abstract boolean prepare();
    abstract int onNeedData(byte[] buffer);
    abstract void release();
}
