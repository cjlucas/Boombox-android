package net.cjlucas.boombox.players;

import java.lang.ref.WeakReference;

/**
 * Created by chris on 8/16/14.
 */
public abstract class Player {
    public interface Listener {
        void onPrepared(Player player);
        void onPlaybackStart(Player player);
        void onPlaybackComplete(Player player);
    }

    private String mDataSource;
    private WeakReference<Listener> mPlayerListener;

    public Player(String dataSource) {
        mDataSource = dataSource;
    }

    public String getDataSource() {
        return mDataSource;
    }

    public void setListener(Listener listener) {
        mPlayerListener = new WeakReference<Listener>(listener);
    }

    public void removeListener(Listener listener) {
        mPlayerListener = null;
    }

    protected Listener getListener() {
        Listener listener = mPlayerListener.get();
        return listener != null ? listener : new NoopListener();
    }

    protected void notifyPlaybackStart() {
        getListener().onPlaybackStart(this);
    }

    protected void notifyPlaybackComplete() {
        getListener().onPlaybackComplete(this);
    }

    protected void notifyPrepared() {
        getListener().onPrepared(this);
    }

    public abstract boolean prepare();
    public abstract void play();
    public abstract void pause();
    public abstract void stop();

    private static class NoopListener implements Listener {
        @Override
        public void onPrepared(Player player) {}
        @Override
        public void onPlaybackStart(Player player) {}
        @Override
        public void onPlaybackComplete(Player player) {}
    }
}
