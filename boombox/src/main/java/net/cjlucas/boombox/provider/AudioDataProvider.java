package net.cjlucas.boombox.provider;

public abstract class AudioDataProvider {
    public static final int STATUS_EOF_REACHED = -1;
    public static final int STATUS_ERROR_OCCURED = -2;

    private Object id;

    public AudioDataProvider(Object id) {
        this.id = id;
    }

    public Object getId() {
        return this.id;
    }

    public long getDuration() {
        return 0;
    }

    public long getLength() {
        return 0;
    }

    public abstract boolean prepare();

    public abstract int provideData(byte[] buffer);

    public abstract void release();
}
