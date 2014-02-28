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

import net.cjlucas.boombox.provider.AudioDataProvider;

public class Boombox extends Thread
    implements MediaPlayer.OnBufferingUpdateListener,
               MediaPlayer.OnCompletionListener,
               MediaPlayer.OnErrorListener,
               MediaPlayer.OnInfoListener,
               MediaPlayer.OnPreparedListener,
               MediaPlayer.OnSeekCompleteListener
{
    private static final String TAG = "Boombox";

    private List<AudioDataProvider> providers;
    private List<AudioDataProvider> playlist;
    private List<MediaPlayer> players;
    private List<ProviderProcessor> processors;
    private Map<MediaPlayer, AudioDataProvider> playerProviderMap;
    private Map<MediaPlayer, PlayerState> playerStateMap;

    private int playlistCursor;

    private BoomboxInfoListener infoListener;

    private boolean shuffleMode;
    private ContinuousMode continuousMode;

    private Handler handler;

    public enum ContinuousMode
    {
        NONE, SINGLE, PLAYLIST;
    }

    enum PlayerState
    {
        IDLE, INITIALIZED, PREPARING, PREPARED, STARTED, PAUSED, STOPPING,
        STOPPED, RELEASING, RELEASED;

        public boolean isPrepared()
        {
            return this == PREPARED || this == STARTED || this == PAUSED;
        }

        public boolean isPlaying()
        {
            return this == STARTED || this == PAUSED;
        }
    }

    enum MessageType
    {
        RELEASE_PLAYER(1 << 0),
        RELEASE_PROCESSOR(1 << 1),
        PLAY_PROVIDER(1 << 2),
        SHUFFLE_PLAYLIST(1 << 3),
        RESET_PLAYLIST(1 << 4);

        public static MessageType forValue(int value)
        {
            for ( MessageType type : MessageType.values() ) {
                if (type.value == value)
                    return type;
            }
            return null;
        }

        public final int value;
        MessageType(int value)
        {
            this.value = value;
        }

        public boolean in(MessageType ... types)
        {
            int bitmask = 0;
            for (int i = 0; i < types.length; i++) {
                bitmask = bitmask | types[i].value;
            }

            return (this.value & bitmask) > 0;
        }
    }

    public Boombox()
    {
        this.providers = Collections.synchronizedList(
            new ArrayList<AudioDataProvider>() );
        this.playlist = Collections.synchronizedList(
                new ArrayList<AudioDataProvider>() );
        this.players = Collections.synchronizedList(
                new ArrayList<MediaPlayer>() );
        this.processors = Collections.synchronizedList(
                new ArrayList<ProviderProcessor>() );
        this.playerProviderMap =
                new ConcurrentHashMap<MediaPlayer, AudioDataProvider>();
        this.playerStateMap =
                new ConcurrentHashMap<MediaPlayer, PlayerState>();
        this.playlistCursor = 0;
        this.shuffleMode    = false;
        this.continuousMode = ContinuousMode.NONE;
    }

    public Boombox(BoomboxInfoListener infoListener)
    {
        this();
        setInfoListener(infoListener);
    }

    public void setInfoListener(BoomboxInfoListener infoListener)
    {
        this.infoListener = infoListener;
    }

    @Override
    public void run()
    {
        Looper.prepare();

        this.handler = new Handler() {
            public void handleMessage(Message message)
            {
                logi("%s", message);
                switch( MessageType.forValue(message.what) ) {
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
            }
        };

        Looper.loop();
    }

    // Clean up

    private void resetPlayers()
    {
        synchronized (this.players) {
            for (MediaPlayer mp : this.players) {
                releasePlayer(mp);
            }
        }
        this.players.clear();

        synchronized (this.providers) {
            for (ProviderProcessor pp : this.processors) {
                releaseProcessor(pp);
            }
        }

        this.processors.clear();
    }

    private void releaseProcessor(ProviderProcessor pp)
    {
        pp.halt();

        try {
            logi("joining");
            pp.join(500);
            logi("done joining");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        finally {
            pp.interrupt();
            this.processors.remove(pp);
        }
    }

    /*
     *    private void releaseProcessor(AudioDataProvider provider)
     *    {
     *        if (provider == null) {
     *            return;
     *        }
     *
     *        ProviderProcessor ppToRemove = null;
     *
     *        synchronized (this.processors) {
     *            for (ProviderProcessor pp : this.processors) {
     *                if (pp.getProvider() == provider) {
     *                    ppToRemove = pp;
     *                    break;
     *                }
     *            }
     *        }
     *
     *        releaseProcessor(ppToRemove);
     *    }
     */

    private void releasePlayer(MediaPlayer player)
    {
        synchronized (player) {
            setPlayerState(player, PlayerState.RELEASING);
            player.release();
            setPlayerState(player, PlayerState.RELEASED);

            this.players.remove(player);
            this.playerProviderMap.remove(player);
        }
    }

    private void reset()
    {
        resetPlayers();
        this.playlistCursor = 0;
    }

    public void release()
    {
        reset();
        setInfoListener(null);
    }

    private void setPlayerState(MediaPlayer player, PlayerState state)
    {
        this.playerStateMap.put(player, state);
    }

    // Providers Management

    private int getFollowingPlaylistIndex(int index)
    {
        int newIndex = index + 1;

        if ( this.continuousMode != ContinuousMode.PLAYLIST
             && newIndex >= this.playlist.size() ) {
            return -1;
        }

        return newIndex % this.playlist.size();
    }

    private int getNextPlaylistCursor()
    {
        return getFollowingPlaylistIndex(this.playlistCursor);
    }

    private int getPrecedingPlaylistIndex(int index)
    {
        int newIndex = index - 1;

        if (newIndex < 0) {
            return this.continuousMode == ContinuousMode.PLAYLIST
                   ? this.playlist.size() - 1 : -1;
        } else {
            return newIndex;
        }
    }

    private int getPreviousPlaylistCursor()
    {
        return getPrecedingPlaylistIndex(this.playlistCursor);
    }

    public void addProvider(AudioDataProvider provider)
    {
        this.providers.add(provider);
        this.playlist.add(provider);
    }

    public AudioDataProvider getCurrentProvider()
    {
        return this.playlist.get(this.playlistCursor);
    }

    public AudioDataProvider getNextProvider()
    {
        int nextCursor = getNextPlaylistCursor();

        return nextCursor == -1 ? null : this.playlist.get(nextCursor);
    }

    public AudioDataProvider getPreviousProvider()
    {
        int prevCursor = getPreviousPlaylistCursor();

        return prevCursor == -1 ? null : this.playlist.get(prevCursor);
    }

    public List<AudioDataProvider> getProviders()
    {
        return new ArrayList<AudioDataProvider>(this.providers);
    }

    public List<AudioDataProvider> getPlaylist()
    {
        return new ArrayList<AudioDataProvider>(this.playlist);
    }

    private AudioDataProvider getProviderAfter(AudioDataProvider provider)
    {
        int nextIndex = getFollowingPlaylistIndex(
                this.playlist.indexOf(provider) );
        return nextIndex == -1 ? null : this.playlist.get(nextIndex);
    }

    private void queueProvider(AudioDataProvider provider)
    {
        ProviderProcessor pp = new ProviderProcessor(provider);

        // if data provider could not be prepared, skip to the next track
        if (!pp.prepare()) {
            playNext();
            return;
        }
        pp.start();

        MediaPlayer mp = new MediaPlayer();
        mp.setOnBufferingUpdateListener(this);
        mp.setOnCompletionListener(this);
        mp.setOnErrorListener(this);
        mp.setOnInfoListener(this);
        mp.setOnPreparedListener(this);
        mp.setOnSeekCompleteListener(this);
        setPlayerState(mp, PlayerState.IDLE);

        try {
            mp.setDataSource( pp.getProxyURL().toString() );
            setPlayerState(mp, PlayerState.INITIALIZED);
            setPlayerState(mp, PlayerState.PREPARING);
            mp.prepareAsync();
        } catch (IOException e) {
            loge("queueProvider failed", e);
            return;
        }

        this.processors.add(pp);
        this.players.add(mp);
        this.playerProviderMap.put(mp, provider);
    }

    // Playback Controls

    private MediaPlayer getCurrentPlayer()
    {
        MediaPlayer player = null;

        if (this.players.size() > 0) {
            player = this.players.get(0);
        }

        return player;
    }

    public void play()
    {
        MediaPlayer mp = getCurrentPlayer();

        // lazy load the media player
        if (mp == null) {
            reqPlayProvider( getCurrentProvider() );

            // start is called by the onPrepared listener
        } else {
            mp.start();
            setPlayerState(mp, PlayerState.STARTED);
        }
    }

    public void play(AudioDataProvider provider)
    {
        this.playlistCursor = this.playlist.indexOf(provider);
        reqPlayProvider(provider);
    }

    public void play(Object id)
    {
        synchronized (this.playlist) {
            int index = 0;

            for (AudioDataProvider provider : this.playlist) {
                if ( provider.getId().equals(id) ) {
                    break;
                }
                index++;
            }

            // TODO: throw exception if provider was not found

            if ( index < this.playlist.size() ) {
                this.playlistCursor = index;
                reqPlayProvider( this.playlist.get(index) );
            }
        }
    }

    public void pause()
    {
        MediaPlayer mp = getCurrentPlayer();

        if (mp != null) {
            synchronized (mp) {
                mp.pause();
                setPlayerState(mp, PlayerState.PAUSED);
            }
        }
    }

    public void togglePlayPause()
    {
        MediaPlayer mp = getCurrentPlayer();

        if ( mp == null || !mp.isPlaying() ) {
            play();
        } else {
            pause();
        }
    }

    public boolean hasNext()
    {
        return getNextPlaylistCursor() != -1;
    }

    public boolean hasPrevious()
    {
        return getPreviousPlaylistCursor() != -1;
    }

    public void playNext()
    {
        if ( hasNext() ) {
            this.playlistCursor = getNextPlaylistCursor();
            reqPlayProvider( this.playlist.get(this.playlistCursor) );
        }
    }

    public void playPrevious()
    {
        if ( hasPrevious() ) {
            this.playlistCursor = getPreviousPlaylistCursor();
            reqPlayProvider( this.playlist.get(this.playlistCursor) );
        }
    }

    private void shufflePlaylist()
    {
        AudioDataProvider currentProvider = null;

        // release queued players
        if (this.players.size() > 0) {
            this.players.get(0).setNextMediaPlayer(null);
            for (int i = 1; i < this.players.size(); i++) {
                releasePlayer( this.players.get(i) );
            }

            currentProvider = this.playerProviderMap.get( this.players.get(0) );
        }

        synchronized (this.playlist) {
            List<AudioDataProvider> providers =
                    new ArrayList<AudioDataProvider>(this.providers);

            this.playlist.clear();

            // put current provider at the top of the shuffled playlist
            if (currentProvider != null) {
                this.playlist.add(currentProvider);
                providers.remove(currentProvider);
            }

            Random random = new Random();
            for (int i = 0; i < providers.size(); i++) {
                int index = random.nextInt( providers.size() );

                AudioDataProvider p = providers.get(index);
                this.playlist.add(p);
                providers.remove(p);
            }
        }

        this.playlistCursor = 0;
    }

    private void resetPlaylist()
    {
        synchronized (this.playlist) {
            AudioDataProvider currentProvider = getCurrentProvider();
            this.playlist.clear();
            logi( "provider size: %d", this.providers.size() );

            for (AudioDataProvider provider : this.providers) {
                this.playlist.add(provider);
            }

            logi( "playlist size %d", this.playlist.size() );

            this.playlistCursor = this.playlist.indexOf(currentProvider);
        }
    }

    public void setShuffleMode(boolean shuffle)
    {
        boolean oldMode = isShuffleModeEnabled();

        // Don't do anything if mode is the same
        if (oldMode == this.shuffleMode) return;

        this.shuffleMode = shuffle;

        if (this.shuffleMode) {
            reqShufflePlaylist();
        } else {
            reqResetPlaylist();
        }
    }

    public boolean isShuffleModeEnabled()
    {
        return this.shuffleMode;
    }

    public void setContinuousMode(ContinuousMode continuousMode)
    {
        // TODO: handle transitions between old and new modes appropriately
        this.continuousMode = continuousMode;
    }

    public ContinuousMode getContinuousMode()
    {
        return this.continuousMode;
    }

    public int getCurrentPosition()
    {
        MediaPlayer mp = getCurrentPlayer();

        if (mp == null) {
            return 0;
        }

        synchronized (mp) {
            PlayerState state = this.playerStateMap.get(mp);
            return state.isPlaying() ? mp.getCurrentPosition() : 0;
        }
    }

    public int getDuration()
    {
        MediaPlayer mp = getCurrentPlayer();
        // Fall back to AudioDataProvider.getDuration() if
        // we can't get the duration from MediaPlayer
        int providerDuration = (int)getCurrentProvider().getDuration();

        if (mp == null) {
            return providerDuration;
        }

        synchronized (mp) {
            PlayerState state = this.playerStateMap.get(mp);

            return (!state.isPrepared() || mp.getDuration() == -1)
                   ? providerDuration : mp.getDuration();
        }
    }

    // MediaPlayer Callbacks

    public void onBufferingUpdate(MediaPlayer player, int percent)
    {
        logi("onBufferingUpdate player: %s, percent: %d", player, percent);

        MediaPlayer tailPlayer = this.players.get(this.players.size() - 1);

        AudioDataProvider tailPlayerProvider = this.playerProviderMap.get(tailPlayer);
        AudioDataProvider nextProvider       = getProviderAfter(tailPlayerProvider);

        /*
         * This method gets called multiple times even after it reaches 100%,
         * so we ensure we don't blindly queue up data sources everytime this
         * block is reached.
         */
        if (percent == 100 && player == tailPlayer && nextProvider != null) {
            logi("queueing the next provider");
            queueProvider(nextProvider);
        }

        notifyBufferingUpdate(this.playerProviderMap.get(player), percent);
    }

    public void onCompletion(MediaPlayer player)
    {
        logi("onCompletion player: %s", player);

        // update playlist cursor, or reset if playlist is complete
        this.playlistCursor = Math.max( 0, getNextPlaylistCursor() );

        notifyPlaybackCompletion( player, this.playerProviderMap.get(player) );
        setPlayerState(player, PlayerState.STOPPED);
        releasePlayer(player);

        // TODO: figure out when to set started state for player
        // that was set as the next player for the player that just completed
    }

    public boolean onError(MediaPlayer player, int what, int extra)
    {
        logi("onInfo player: %s what: %d, extra: %d", player, what, extra);

        return false;
    }

    public boolean onInfo(MediaPlayer player, int what, int extra)
    {
        logi("onInfo player: %s what: %d, extra: %d", player, what, extra);

        return false;
    }

    public void onPrepared(MediaPlayer player)
    {
        logi("onPrepared player: %s", player);
        setPlayerState(player, PlayerState.PREPARED);

        /*
         * If given player is at the head of the list, immediately start
         * playing. Otherwise, queue the player to the tail.
         */
        int index = this.players.indexOf(player);
        if (index == 0) {
            player.start();
            setPlayerState(player, PlayerState.STARTED);
            notifyPlaybackStart( this.playerProviderMap.get(player) );
        } else {
            this.players.get(index - 1).setNextMediaPlayer(player);
        }
    }

    public void onSeekComplete(MediaPlayer player)
    {
        logi("onSeekComplete player: %s", player);
    }

    // Request senders

    private Message obtainMessage(MessageType type, Object obj)
    {
        return this.handler.obtainMessage(type.value, obj);
    }

    private boolean hasMessages(MessageType type)
    {
        return this.handler.hasMessages(type.value);
    }

    private void reqReleasePlayer(MediaPlayer player)
    {
        Message msg = obtainMessage(MessageType.RELEASE_PLAYER, player);
        this.handler.sendMessage(msg);
    }

    private void reqReleaseAllPlayers()
    {
        synchronized (this.players) {
            for (MediaPlayer player : this.players) {
                reqReleasePlayer(player);
            }
        }
    }

    private void reqReleaseProcessor(ProviderProcessor pp)
    {
        Message msg = obtainMessage(MessageType.RELEASE_PROCESSOR, pp);
        this.handler.sendMessage(msg);
    }

    private void reqReleaseAllProcessors()
    {
        synchronized (this.processors) {
            for (ProviderProcessor pp : this.processors) {
                reqReleaseProcessor(pp);
            }
        }
    }

    private void reqPlayProvider(AudioDataProvider provider)
    {
        reqReleaseAllPlayers();
        reqReleaseAllProcessors();

        Message msg = obtainMessage(MessageType.PLAY_PROVIDER, provider);
        this.handler.sendMessage(msg);
    }

    private void reqShufflePlaylist()
    {
        Message msg = obtainMessage(MessageType.SHUFFLE_PLAYLIST, null);
        this.handler.sendMessage(msg);
    }

    private void reqResetPlaylist()
    {
        Message msg = obtainMessage(MessageType.RESET_PLAYLIST, null);
        this.handler.sendMessage(msg);
    }

    // Message handlers

    private void handleReleasePlayer(Message msg)
    {
        releasePlayer( (MediaPlayer)msg.obj );
    }

    private void handleReleaseProcessor(Message msg)
    {
        releaseProcessor( (ProviderProcessor)msg.obj );
    }

    private void handlePlayProvider(Message msg)
    {
        // Don't bother playing if we have another play request coming
        if ( hasMessages(MessageType.PLAY_PROVIDER) ) {
            return;
        }

        queueProvider( (AudioDataProvider)msg.obj );
    }

    private void handleShufflePlaylist(Message msg)
    {
        shufflePlaylist();
    }

    private void handleResetPlaylist(Message msg)
    {
        resetPlaylist();
    }

    // BoomboxInfoListener helpers

    private void notifyPlaybackStart(AudioDataProvider provider)
    {
        if (this.infoListener == null) {
            return;
        }

        this.infoListener.onPlaybackStart( this, provider);
    }

    /**
     * Helper for notifying BoomboxInfoListener about playback status.
     */
    private void notifyPlaybackCompletion(MediaPlayer       mp,
                                          AudioDataProvider provider)
    {
        if (this.infoListener == null) {
            return;
        }

        AudioDataProvider nextProvider = getProviderAfter(provider);
        this.infoListener.onPlaybackCompletion(this, provider, nextProvider);

        /*
         * If there is no next provider, we can assume the playlist is complete,
         * otherwise we notify the playback of the next provider.
         */
        if (nextProvider == null) {
            this.infoListener.onPlaylistCompletion(this);
        } else {
            notifyPlaybackStart(nextProvider);
        }
    }

    private void notifyBufferingUpdate(AudioDataProvider provider, int percent)
    {
        if (this.infoListener == null) {
            return;
        }

        this.infoListener.onBufferingUpdate(this, provider, percent);
    }

    // Log helpers

    private void loge(String fmt, Object ... args)
    {
        Log.e( TAG, String.format(Locale.getDefault(), fmt, args) );
    }

    @SuppressWarnings("unused")
    private void logv(String fmt, Object ... args)
    {
        Log.v( TAG, String.format(Locale.getDefault(), fmt, args) );
    }

    @SuppressWarnings("unused")
    private void logd(String fmt, Object ... args)
    {
        Log.d( TAG, String.format(Locale.getDefault(), fmt, args) );
    }

    @SuppressWarnings("unused")
    private void logw(String fmt, Object ... args)
    {
        Log.w( TAG, String.format(Locale.getDefault(), fmt, args) );
    }

    private void logi(String fmt, Object ... args)
    {
        Log.i( TAG, String.format(Locale.getDefault(), fmt, args) );
    }

    // Nested classes

    private class ProviderProcessor extends Thread
    {
        private static final int BUFFER_SIZE = 64 * 1024;

        private AudioDataProvider provider;
        private ProxyServer proxyServer;
        private boolean shouldHalt;

        public ProviderProcessor(AudioDataProvider provider)
        {
            this.provider    = provider;
            this.proxyServer = new ProxyServer();
            this.shouldHalt  = false;
        }

        public boolean prepare()
        {
            if (this.provider.prepare()) {
                this.proxyServer.startServer();
                this.proxyServer.setContentLength( this.provider.getLength() );
                this.proxyServer.start();

                logi( "Starting proxy server @ " + getProxyURL() );
                return true;
            }

            tearDown();
            return false;
        }

        private void tearDown()
        {
            this.proxyServer.stopServer();
            this.provider.release();
            releaseProcessor(this);
        }

        public URL getProxyURL()
        {
            return this.proxyServer.getURL();
        }

        public AudioDataProvider getProvider()
        {
            return this.provider;
        }

        public void halt()
        {
            this.shouldHalt = true;
        }

        public void run()
        {
            // TODO: add a timeout mechanism
            // wait for audioProc to connect
            while ( !this.proxyServer.hasConnection() ) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    logi("got interrupted here yo");
                }
            }

            while (!this.shouldHalt) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int size = this.provider.provideData(buffer);
                //              System.out.println("size received: " + size);

                if (size > 0) {
                    this.proxyServer.sendData( shrinkBuffer(buffer, size) );
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

        private byte[] shrinkBuffer(byte[] buffer, int size)
        {
            byte[] newBuffer = new byte[size];
            for (int i = 0; i < size; i++) {
                newBuffer[i] = buffer[i];
            }

            return newBuffer;
        }
    }
}
