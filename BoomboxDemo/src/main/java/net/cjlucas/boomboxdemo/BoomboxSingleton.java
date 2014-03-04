package net.cjlucas.boomboxdemo;

import net.cjlucas.boombox.Boombox;

public class BoomboxSingleton {
    private static Boombox mBoombox;

    public synchronized static Boombox getInstance() {
        if (mBoombox == null) {
            mBoombox = new Boombox();
            mBoombox.start();
        }
        return mBoombox;
    }
}
