package net.cjlucas.boomboxdemo;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

import net.cjlucas.boombox.Boombox;

public class BoomboxService extends Service {
    private Boombox mBoombox;
    private IBinder mBinder = new LocalBinder();

    public class LocalBinder extends Binder {
        public Boombox getBoombox() {
            return mBoombox;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        System.err.println("onStartCommand");
        mBoombox = new Boombox();
        mBoombox.start();
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        System.err.println("on bind");
        return mBinder;
    }
}