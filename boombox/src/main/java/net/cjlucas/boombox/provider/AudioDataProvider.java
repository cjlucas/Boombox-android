package net.cjlucas.boombox.provider;

public abstract class AudioDataProvider {
    public static final int STATUS_EOF_REACHED = -1;
    public static final int STATUS_ERROR_OCCURED = -2;

    private Object id;
    private boolean mIsPrepeared;

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

    final public boolean isPrepared() {
        return mIsPrepeared;
    }

    /**
     * The superclass implementation must be called.
     * @return Whether the provider was successfully prepared
     */
    public boolean prepare() {
        mIsPrepeared = true;
        return true;
    };

    public abstract int provideData(byte[] buffer);

    /**
     * The superclass implementation must be called.
     */
    public void release() {
        mIsPrepeared = false;
    };
}
