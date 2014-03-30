package net.cjlucas.boomboxdemo;

import android.app.Service;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import net.cjlucas.boombox.Boombox;
import net.cjlucas.boombox.BoomboxInfoListener;
import net.cjlucas.boombox.provider.AudioDataProvider;

public class BoomboxService extends Service
        implements AudioManager.OnAudioFocusChangeListener, BoomboxInfoListener {
    private static final String TAG = "BoomboxService";

    private Boombox mBoombox;
    private IBinder mBinder = new LocalBinder();



    public class LocalBinder extends Binder {
        public Boombox getBoombox() {
            return mBoombox;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");
        mBoombox = new Boombox();
        mBoombox.registerInfoListener(this);
        mBoombox.start();


        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind");
        return mBinder;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        mBoombox.release();
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        Log.d(TAG, "onAudioFocusChange: " + focusChange);
        if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
            mBoombox.pause();
        }
    }

    @Override
    public void onPlaybackStart(Boombox boombox, AudioDataProvider provider) {
        AudioManager am = (AudioManager)getSystemService(AUDIO_SERVICE);
        am.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
    }

    @Override
    public void onPlaybackCompletion(Boombox boombox, AudioDataProvider completedProvider, AudioDataProvider nextProvider) {

    }

    @Override
    public void onPlaylistCompletion(Boombox boombox) {
        AudioManager am = (AudioManager)getSystemService(AUDIO_SERVICE);
        am.abandonAudioFocus(this);
    }

    @Override
    public void onBufferingStart(Boombox boombox, AudioDataProvider provider) {

    }

    @Override
    public void onBufferingEnd(Boombox boombox, AudioDataProvider provider) {

    }

    @Override
    public void onBufferingUpdate(Boombox boombox, AudioDataProvider provider, int percentComplete) {

    }
}