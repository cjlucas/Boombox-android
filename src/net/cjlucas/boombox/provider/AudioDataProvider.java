package net.cjlucas.boombox.provider;

public abstract class AudioDataProvider
{
	public static final int STATUS_EOF_REACHED   = -1;
	public static final int STATUS_ERROR_OCCURED = -2;

	public abstract boolean prepare();
	public abstract int onNeedData(byte[] buffer);
	public abstract void release();
}
