package net.cjlucas.boombox;

import android.media.MediaPlayer;

import android.os.Looper;
import android.os.Handler;
import android.os.Message;

import android.util.Log;

import java.io.IOException;
import java.net.URL;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import net.cjlucas.boombox.players.AudioTrackPlayer;
import net.cjlucas.boombox.players.Player;
import net.cjlucas.boombox.provider.AudioDataProvider;

public class Boombox extends Thread implements Handler.Callback, Player.Listener {
    private static final String TAG = "Boombox";

    private final List<AudioDataProvider> mProviders;
    private final List<AudioDataProvider> mPlaylist;
    private final List<Player> mPlayers;
    private final Map<Player, ProviderProcessor> mPlayerProcessorMap;
    private final Map<Player, AudioDataProvider> mPlayerProviderMap;
    private final Map<Player, PlayerState> mPlayerStateMap;

    private int mPlaylistCursor;

    private BoomboxInfoListenerList mInfoListeners;

    private boolean mShuffleMode;
    private ContinuousMode mContinuousMode;

    private Handler mHandler;

    public enum ContinuousMode {
        NONE, SINGLE, PLAYLIST
    }

    enum PlayerState {
        IDLE, INITIALIZED, PREPARING, PREPARED, STARTED, PAUSED, STOPPING,
        STOPPED, RELEASING, RELEASED;

        public boolean isPrepared() {
            return this == PREPARED || this == STARTED || this == PAUSED;
        }

        public boolean isPlaying() {
            return this == STARTED || this == PAUSED;
        }
    }

    enum MessageType {
        RELEASE_PLAYER(1),
        RELEASE_PROCESSOR(1 << 1), // unused
        PLAY_PROVIDER(1 << 2),
        SHUFFLE_PLAYLIST(1 << 3),
        RESET_PLAYLIST(1 << 4);

        public static MessageType forValue(int value) {
            for (MessageType type : MessageType.values()) {
                if (type.value == value)
                    return type;
            }
            return null;
        }

        public final int value;

        MessageType(int value) {
            this.value = value;
        }

        public boolean in(MessageType... types) {
            int bitmask = 0;
            for (MessageType type : types) {
                bitmask = bitmask | type.value;
            }

            return (value & bitmask) > 0;
        }
    }

    public Boombox() {
        mProviders = Collections.synchronizedList(new ArrayList<AudioDataProvider>());
        mPlaylist = Collections.synchronizedList(new ArrayList<AudioDataProvider>());
        mPlayers = Collections.synchronizedList(new ArrayList<Player>());
        mPlayerProcessorMap = new ConcurrentHashMap<>();
        mPlayerProviderMap = new ConcurrentHashMap<>();
        mPlayerStateMap = new ConcurrentHashMap<>();
        mPlaylistCursor = 0;
        mShuffleMode = false;
        mContinuousMode = ContinuousMode.NONE;
        mInfoListeners = new BoomboxInfoListenerList();
    }

    /**
     * Register for Boombox updates.
     * @param infoListener
     */
    public void registerInfoListener(BoomboxInfoListener infoListener) {
        if (infoListener == null) {
            throw new IllegalArgumentException("infoListener cannot be null");
        }
        mInfoListeners.add(infoListener);
    }

    /**
     * Unregister from Boombox updates.
     * @param infoListener
     */
    public void unregisterInfoListener(BoomboxInfoListener infoListener) {
        mInfoListeners.remove(infoListener);
    }

    @Override
    public void run() {
        Looper.prepare();
        mHandler = new Handler(this);

        Looper.loop();
    }

    // Clean up

    /**
     * Release and clear all MediaPlayer and ProviderProcessor objects.
     */
    private void resetPlayers() {
        synchronized (mPlayers) {
            ArrayList<Player> players = new ArrayList<>(mPlayers);
            for (Player player : players)
                releasePlayer(player);
        }
    }

    /**
     * Stop and release a ProviderProcessor object.
     * @param pp
     */
    private void releaseProcessor(ProviderProcessor pp) {
        pp.halt();

        try {
            logi("joining");
            pp.join(500);
            logi("done joining");
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            pp.interrupt();
        }
    }

    /**
     * Stop and release a MediaPlayer object.
     * @param player
     */
    private void releasePlayer(Player player) {
        synchronized (player) {
            setPlayerState(player, PlayerState.RELEASING);
            player.stop();
            setPlayerState(player, PlayerState.RELEASED);

            // release associated ProviderProcessor
            releaseProcessor(mPlayerProcessorMap.get(player));

            mPlayers.remove(player);
            mPlayerProcessorMap.remove(player);
            mPlayerProviderMap.remove(player);
            mPlayerStateMap.remove(player);
        }
    }

    /**
     * Reset Boombox. This stops all playback and removes all providers.
     */
    public void reset() {
        resetPlayers();
        mPlaylist.clear();
        mProviders.clear();
        mPlaylistCursor = 0;
    }

    public void release() {
        reset();
    }

    private void setPlayerState(Player player, PlayerState state) {
        if (player == null) return;

        mPlayerStateMap.put(player, state);
    }

    // Providers Management

    /**
     * Get the index of the next playlist item.
     *
     * @param index the current index
     * @return the following index
     */
    private int getFollowingPlaylistIndex(int index) {
        if (mContinuousMode == ContinuousMode.SINGLE) {
            return index;
        }

        int newIndex = index + 1;

        if (mContinuousMode != ContinuousMode.PLAYLIST
                && newIndex >= mPlaylist.size()) {
            return -1;
        }

        return newIndex % mPlaylist.size();
    }
    //region Blah

    /**
     * Get the index of the previous playlist item.
     *
     * @param index the current index
     * @return the previous index
     */
    private int getPrecedingPlaylistIndex(int index) {
        if (mContinuousMode == ContinuousMode.SINGLE) {
            return index;
        }

        int newIndex = index - 1;

        if (newIndex < 0) {
            return mContinuousMode == ContinuousMode.PLAYLIST ? mPlaylist.size() - 1 : -1;
        } else {
            return newIndex;
        }
    }

    //endregion

    /**
     * Add AudioDataProvider to playlist.
     * @param provider
     */
    public void addProvider(AudioDataProvider provider) {
        mProviders.add(provider);
        mPlaylist.add(provider);
    }

    public AudioDataProvider getCurrentProvider() {
        return mPlaylist.get(mPlaylistCursor);
    }

    public AudioDataProvider getNextProvider() {
        int nextCursor = getFollowingPlaylistIndex(mPlaylistCursor);

        return nextCursor == -1 ? null : mPlaylist.get(nextCursor);
    }

    public AudioDataProvider getPreviousProvider() {
        int prevCursor = getPrecedingPlaylistIndex(mPlaylistCursor);

        return prevCursor == -1 ? null : mPlaylist.get(prevCursor);
    }

    public List<AudioDataProvider> getProviders() {
        return new ArrayList<AudioDataProvider>(mProviders);
    }

    public List<AudioDataProvider> getPlaylist() {
        return new ArrayList<AudioDataProvider>(mPlaylist);
    }

    private AudioDataProvider getProviderAfter(AudioDataProvider provider) {
        int nextIndex = getFollowingPlaylistIndex(mPlaylist.indexOf(provider));
        return nextIndex == -1 ? null : mPlaylist.get(nextIndex);
    }

    private void queueProvider(AudioDataProvider provider) {
        ProviderProcessor processor = new ProviderProcessor(provider);

        // if data mProvider could not be prepared, skip to the next track
        if (!processor.prepare()) {
            if (hasNext()) {
                playNext();
            } else {
                notifyPlaylistCompletion();
            }
            return;
        }
        processor.start();

        Player player = new AudioTrackPlayer(processor.getProxyURL().toString());
        player.setListener(this);

        mPlayers.add(player);
        mPlayerProcessorMap.put(player, processor);
        mPlayerProviderMap.put(player, provider);

        // TODO: find an alternative to this
//        if (mContinuousMode == ContinuousMode.SINGLE) mp.setLooping(true);

        setPlayerState(player, PlayerState.INITIALIZED);
        setPlayerState(player, PlayerState.PREPARING);
        player.prepare();
    }

    // Playback Controls

    private Player getCurrentPlayer() {
        Player player = null;

        if (mPlayers.size() > 0) {
            player = mPlayers.get(0);
        }

        return player;
    }

    public void play() {
        Player player = getCurrentPlayer();

        // lazy load the player
        if (player == null) {
            reqPlayProvider(getCurrentProvider());
        } else {
            player.play();
            setPlayerState(player, PlayerState.STARTED);
        }
    }

    public void play(int position) {
        mPlaylistCursor = position;
        reqPlayProvider(mPlaylist.get(position));
    }

    public void play(AudioDataProvider provider) {
        play(mPlaylist.indexOf(provider));
    }

    public void play(Object id) {
        synchronized (mPlaylist) {
            int index = 0;

            for (AudioDataProvider provider : mPlaylist) {
                if (provider.getId().equals(id)) break;
                index++;
            }

            if (index < mPlaylist.size()) {
                mPlaylistCursor = index;
                reqPlayProvider(mPlaylist.get(index));
            } else {
                throw new RuntimeException("No provider found with the given id");
            }
        }
    }

    public void pause() {
        Player player = getCurrentPlayer();

        if (player != null) {
            synchronized (player) {
                player.pause();
                setPlayerState(player, PlayerState.PAUSED);
            }
        }
    }

    public void togglePlayPause() {
        Player player = getCurrentPlayer();

        // TODO: find an alternative to MediaPlayer.isPlaying()
        if (player == null /* || !mp.isPlaying() */) {
            play();
        } else {
            pause();
        }
    }

    public boolean hasNext() {
        return getFollowingPlaylistIndex(mPlaylistCursor) != -1;
    }

    public boolean hasPrevious() {
        return getPrecedingPlaylistIndex(mPlaylistCursor) != -1;
    }

    public void playNext() {
        if (hasNext()) {
            mPlaylistCursor = getFollowingPlaylistIndex(mPlaylistCursor);
            reqPlayProvider(mPlaylist.get(mPlaylistCursor));
        }
    }

    public void playPrevious() {
        if (hasPrevious()) {
            mPlaylistCursor = getPrecedingPlaylistIndex(mPlaylistCursor);
            reqPlayProvider(mPlaylist.get(mPlaylistCursor));
        }
    }

    private void shufflePlaylist() {
        AudioDataProvider currentProvider = null;

        // release queued mPlayers
        if (mPlayers.size() > 0) {
            // TODO: find an alternative to this
//            mPlayers.get(0).setNextMediaPlayer(null);
            for (int i = 1; i < mPlayers.size(); i++) {
                releasePlayer(mPlayers.get(i));
            }

            currentProvider = mPlayerProviderMap.get(mPlayers.get(0));
        }

        synchronized (mPlaylist) {
            List<AudioDataProvider> providers = new ArrayList<AudioDataProvider>(mProviders);

            mPlaylist.clear();

            // put current mProvider at the top of the shuffled mPlaylist
            if (currentProvider != null) {
                mPlaylist.add(currentProvider);
                providers.remove(currentProvider);
            }

            Random random = new Random();
            for (int i = 0; i < providers.size(); i++) {
                int index = random.nextInt(providers.size());

                AudioDataProvider p = providers.get(index);
                mPlaylist.add(p);
                providers.remove(p);
            }
        }

        mPlaylistCursor = 0;
    }

    private void resetPlaylist() {
        synchronized (mPlaylist) {
            AudioDataProvider currentProvider = getCurrentProvider();
            mPlaylist.clear();
            logi("mProvider size: %d", mProviders.size());

            for (AudioDataProvider provider : mProviders) {
                mPlaylist.add(provider);
            }

            logi("mPlaylist size %d", mPlaylist.size());

            mPlaylistCursor = mPlaylist.indexOf(currentProvider);
        }
    }

    public void setShuffleMode(boolean shuffle) {
        boolean oldMode = isShuffleModeEnabled();

        // Don't do anything if mode is the same
        if (oldMode == shuffle) return;

        mShuffleMode = shuffle;

        if (mShuffleMode) {
            reqShufflePlaylist();
        } else {
            reqResetPlaylist();
        }
    }

    public boolean isShuffleModeEnabled() {
        return mShuffleMode;
    }

    public void setContinuousMode(ContinuousMode continuousMode) {
        if (mContinuousMode == continuousMode) return;

        mContinuousMode = continuousMode;

        // Release queued players
        if (mContinuousMode == ContinuousMode.SINGLE && !mPlayers.isEmpty()) {
            synchronized (mPlayers) {
                for (Player player : mPlayers.subList(1, mPlaylist.size())) {
                    reqReleasePlayer(player);
                }
            }
        }
    }

    public ContinuousMode getContinuousMode() {
        return mContinuousMode;
    }

    public long getCurrentPosition() {
        Player player = getCurrentPlayer();

        if (player == null) {
            return 0;
        }

        synchronized (player) {
            PlayerState state = mPlayerStateMap.get(player);
            return state.isPlaying() ? player.getPlaybackPosition() : 0;
        }
    }

    public int getDuration() {
        Player player = getCurrentPlayer();
        // Fall back to AudioDataProvider.getDuration() if
        // we can't get the duration from MediaPlayer
        int providerDuration = (int) getCurrentProvider().getDuration();

        if (player == null) {
            return providerDuration;
        }

        synchronized (player) {
            PlayerState state = mPlayerStateMap.get(player);

            // TODO: find an alternative to getDuration()
//            return (!state.isPrepared() || player.getDuration() == -1)
//                    ? providerDuration : player.getDuration();
            return providerDuration;
        }
    }

    /**
     * Check the current playback status
     * @return
     */
    public boolean isPlaying() {
        // TODO: find a better alternative than checking PlayerState
//        MediaPlayer mp = getCurrentPlayer();
//        return mp != null && mp.isPlaying();
        Player player = getCurrentPlayer();
        return player != null && mPlayerStateMap.get(player) == PlayerState.STARTED;
    }

    public void onProviderProcessorCompleted(ProviderProcessor processor) {
        Player player = null;
        synchronized (mPlayerProviderMap) {
            for (Player p : mPlayerProviderMap.keySet()) {
                if (mPlayerProviderMap.get(p) == processor.getProvider()) {
                    player = p;
                    break;
                }
            }
        }

        logi("onProviderProcessorCompleted: %s", player);

        Player tailPlayer = mPlayers.get(mPlayers.size() - 1);
        AudioDataProvider tailPlayerProvider = mPlayerProviderMap.get(tailPlayer);
        AudioDataProvider nextProvider = getProviderAfter(tailPlayerProvider);

        logi("tailPlayer: %s", tailPlayer);
        logi("nexProvider: %s", nextProvider);

        if (player == tailPlayer && nextProvider != null) {
            logi("queueing the next provider");
            queueProvider(nextProvider);
        }
    }

    // MediaPlayer Callbacks

    @Override
    public void onPlaybackStart(Player player) {

    }

    @Override
    public void onPlaybackComplete(Player player) {
        logi("onCompletion player: %s", player);

        notifyPlaybackCompletion(player, mPlayerProviderMap.get(player));
        setPlayerState(player, PlayerState.STOPPED);
        releasePlayer(player); // we want this synchronous so next code is valid

        Player currentPlayer = getCurrentPlayer();
        setPlayerState(currentPlayer, PlayerState.STARTED);

        if (currentPlayer != null) {
            logi("playin da next");
            currentPlayer.play();
        }

        // for safety, if there wasn't a queued player but there is a next provider, queue it now.
        if (currentPlayer == null && hasNext()) {
            playNext();
        } else {
            // update mPlaylist cursor, or reset if mPlaylist is complete
            mPlaylistCursor = Math.max(0, getFollowingPlaylistIndex(mPlaylistCursor));
        }
    }

    @Override
    public void onPrepared(Player player) {
        logi("onPrepared player: %s", player);
        setPlayerState(player, PlayerState.PREPARED);

        if (mPlayers.indexOf(player) == 0) {
            player.play();
            setPlayerState(player, PlayerState.STARTED);
            notifyPlaybackStart(mPlayerProviderMap.get(player));
        }
    }

    // Request senders

    private Message obtainMessage(MessageType type, Object obj) {
        return mHandler.obtainMessage(type.value, obj);
    }

    private boolean hasMessages(MessageType type) {
        return mHandler.hasMessages(type.value);
    }

    private void reqReleasePlayer(Player player) {
        Message msg = obtainMessage(MessageType.RELEASE_PLAYER, player);
        mHandler.sendMessage(msg);
    }

    private void reqReleaseAllPlayers() {
        synchronized (mPlayers) {
            for (Player player : mPlayers) {
                reqReleasePlayer(player);
            }
        }
    }

    private void reqPlayProvider(AudioDataProvider provider) {
        reqReleaseAllPlayers();

        Message msg = obtainMessage(MessageType.PLAY_PROVIDER, provider);
        mHandler.sendMessage(msg);
    }

    private void reqShufflePlaylist() {
        Message msg = obtainMessage(MessageType.SHUFFLE_PLAYLIST, null);
        mHandler.sendMessage(msg);
    }

    private void reqResetPlaylist() {
        Message msg = obtainMessage(MessageType.RESET_PLAYLIST, null);
        mHandler.sendMessage(msg);
    }

    // Message handlers

    @Override
    public boolean handleMessage(Message message) {
        switch (MessageType.forValue(message.what)) {
            case RELEASE_PLAYER:
                handleReleasePlayer(message);
                break;
            case RELEASE_PROCESSOR:
                handleReleaseProcessor(message);
                break;
            case PLAY_PROVIDER:
                handlePlayProvider(message);
                break;
            case SHUFFLE_PLAYLIST:
                handleShufflePlaylist(message);
                break;
            case RESET_PLAYLIST:
                handleResetPlaylist(message);
                break;
            default:
                throw new RuntimeException("Unhandled message: " + message);
        }

        return true;
    }

    private void handleReleasePlayer(Message msg) {
        releasePlayer((Player) msg.obj);
    }

    private void handleReleaseProcessor(Message msg) {
        releaseProcessor((ProviderProcessor) msg.obj);
    }

    private void handlePlayProvider(Message msg) {
        // Don't bother playing if we have another play request coming
        if (hasMessages(MessageType.PLAY_PROVIDER)) {
            return;
        }

        queueProvider((AudioDataProvider) msg.obj);
    }

    private void handleShufflePlaylist(Message msg) {
        shufflePlaylist();
    }

    private void handleResetPlaylist(Message msg) {
        resetPlaylist();
    }

    // BoomboxInfoListener helpers

    private void notifyPlaybackStart(AudioDataProvider provider) {
        for (BoomboxInfoListener infoListener : mInfoListeners) {
            infoListener.onPlaybackStart(this, provider);
        }
    }

    private void notifyPlaylistCompletion() {
        for (BoomboxInfoListener infoListener : mInfoListeners) {
            infoListener.onPlaylistCompletion(this);
        }
    }

    /**
     * Helper for notifying BoomboxInfoListener about playback status.
     */
    private void notifyPlaybackCompletion(Player player,
                                          AudioDataProvider provider) {
        AudioDataProvider nextProvider = getProviderAfter(provider);

        for (BoomboxInfoListener infoListener : mInfoListeners) {
            infoListener.onPlaybackCompletion(this, provider, nextProvider);
        }

        /*
         * If there is no next mProvider, we can assume the mPlaylist is complete,
         * otherwise we notify the playback of the next mProvider.
         */
        if (nextProvider == null) {
            notifyPlaylistCompletion();
        } else {
            notifyPlaybackStart(nextProvider);
        }
    }

    private void notifyBufferingUpdate(Player player, boolean waitingForData) {
        AudioDataProvider provider = this.mPlayerProviderMap.get(player);

        for (BoomboxInfoListener infoListener : mInfoListeners) {
            if (waitingForData) {
                infoListener.onBufferingStart(this, provider);
            } else {
                infoListener.onBufferingEnd(this, provider);
            }
        }
    }

    private void notifyBufferingUpdate(AudioDataProvider provider, int percent) {
        for (BoomboxInfoListener infoListener : mInfoListeners) {
            infoListener.onBufferingUpdate(this, provider, percent);
        }
    }

    // Log helpers

    private void loge(String fmt, Object... args) {
        Log.e(TAG, String.format(Locale.getDefault(), fmt, args));
    }

    @SuppressWarnings("unused")
    private void logv(String fmt, Object... args) {
        Log.v(TAG, String.format(Locale.getDefault(), fmt, args));
    }

    @SuppressWarnings("unused")
    private void logd(String fmt, Object... args) {
        Log.d(TAG, String.format(Locale.getDefault(), fmt, args));
    }

    @SuppressWarnings("unused")
    private void logw(String fmt, Object... args) {
        Log.w(TAG, String.format(Locale.getDefault(), fmt, args));
    }

    private void logi(String fmt, Object... args) {
        Log.i(TAG, String.format(Locale.getDefault(), fmt, args));
    }

    // Nested classes

    private class ProviderProcessor extends Thread implements ProxyServer.Listener {
        private static final int BUFFER_SIZE = 64 * 1024;

        private AudioDataProvider mProvider;
        private ProxyServer mProxyServer;
        private boolean mShouldHalt;

        public ProviderProcessor(AudioDataProvider provider) {
            mProvider = provider;
            mProxyServer = new ProxyServer();
            mShouldHalt = false;
        }

        public boolean prepare() {
            if (prepareProvider()) {
                mProxyServer.startServer();
                mProxyServer.registerListener(this);
                mProxyServer.setContentLength(mProvider.getLength());
                mProxyServer.start();

                logi("Starting proxy server @ " + getProxyURL());
                return true;
            }

            tearDown();
            return false;
        }

        private void tearDown() {
            mProxyServer.stopServer();
            mProxyServer.unregisterListener(this);
            releaseProvider();
            releaseProcessor(this);
            onProviderProcessorCompleted(this);
        }

        public URL getProxyURL() {
            return mProxyServer.getURL();
        }

        public AudioDataProvider getProvider() {
            return mProvider;
        }

        public void halt() {
            mShouldHalt = true;
        }

        public void run() {
            while (!mShouldHalt) {
                if (!mProxyServer.isRunning()) {
                    halt();
                }

                if (!mProxyServer.hasConnection()) {
                    continue;
                }

                byte[] buffer = new byte[BUFFER_SIZE];
                int size = mProvider.provideData(buffer);

                if (size > 0) {
                    mProxyServer.sendData(shrinkBuffer(buffer, size));
                } else if (size == AudioDataProvider.STATUS_EOF_REACHED) {
                    logi("ProviderProcessor: EOF_REACHED");
                    halt();
                } else if (size == AudioDataProvider.STATUS_ERROR_OCCURED) {
                    loge("ProviderProcessor: ERROR_OCCURED");
                    halt();
                }
            }

            tearDown();
        }

        private byte[] shrinkBuffer(byte[] buffer, int size) {
            byte[] newBuffer = new byte[size];
            System.arraycopy(buffer, 0, newBuffer, 0, size);

            return newBuffer;
        }

        @Override
        public void clientDidConnect(ProxyServer proxyServer) {
            logd("clientDidConnect");
            prepareProvider();
        }

        @Override
        public void clientDidDisconnect(ProxyServer proxyServer) {
            logd("clientDidDisconnect");
            releaseProvider();
        }

        private boolean prepareProvider() {
            if (mProvider.isPrepared()) {
                return true;
            }

            synchronized (mProvider) {
                return mProvider.prepare();
            }
        }

        private void releaseProvider() {
            if (!mProvider.isPrepared()) {
                return;
            }

            synchronized (mProvider) {
                mProvider.release();
            }
        }
    }
}
