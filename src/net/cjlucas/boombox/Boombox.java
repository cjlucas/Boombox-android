package net.cjlucas.boombox;

import android.media.MediaPlayer;

import java.io.IOException;
import java.net.URL;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.cjlucas.boombox.provider.AudioDataProvider;

public class Boombox
implements
MediaPlayer.OnBufferingUpdateListener,
MediaPlayer.OnCompletionListener,
MediaPlayer.OnErrorListener,
MediaPlayer.OnInfoListener,
MediaPlayer.OnPreparedListener,
MediaPlayer.OnSeekCompleteListener
{
	private List<AudioDataProvider> providers;
	private List<AudioDataProvider> playlist;
	private List<MediaPlayer> players;
	private List<ProviderProcessor> processors;
	private Map<MediaPlayer, AudioDataProvider> playerProviderMap;
	private int playlistCursor;
	private boolean shuffleMode;
	private boolean continuousMode;

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

			this.provider.prepare();
			this.proxyServer.setContentLength( this.provider.getLength() );

			this.proxyServer.startServer();
			this.proxyServer.start();

			System.err.println( "Starting proxy server @ " + getProxyURL() );
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
				try { Thread.sleep(50); } catch (Exception e) {
				}
			}

			while (!this.shouldHalt) {
				byte[] buffer = new byte[BUFFER_SIZE];
				int size = this.provider.provideData(buffer);
				//				System.out.println("size received: " + size);

				if (size > 0) {
					this.proxyServer.sendData( shrinkBuffer(buffer, size) );
				} else if (size == AudioDataProvider.STATUS_EOF_REACHED) {
					System.out.println("EOF REACHED");
					this.proxyServer.stopServer();
					halt();
				}
			}

			this.proxyServer.stopServer();
			this.provider.release();
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
		this.playerProviderMap = new ConcurrentHashMap<MediaPlayer, AudioDataProvider>();
		this.playlistCursor    = 0;
		this.shuffleMode       = false;
		this.continuousMode    = false;
	}

	// Clean up

	private void resetPlayers()
	{
		synchronized (this.players) {
			for (MediaPlayer mp : this.players) {
				mp.release();
			}
		}
		this.players.clear();

		synchronized (this.providers) {
			for (ProviderProcessor pp : this.processors) {
				pp.halt();
				try {
					pp.join();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}

		this.providers.clear();
	}

	private void releasePlayer(MediaPlayer player)
	{
		player.release();
		this.players.remove(player);
		this.playerProviderMap.remove(player);
	}

	// Providers Management

	private int getFollowingPlaylistIndex(int index)
	{
		int newIndex = index + 1;

		if ( !this.continuousMode && newIndex >= this.playlist.size() ) {
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
			return this.continuousMode ? this.playlist.size() - 1 : -1;
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
		return this.playlist.get(playlistCursor);
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
		pp.start();

		MediaPlayer mp = createMediaPlayer();
		mp.setOnBufferingUpdateListener(this);
		mp.setOnCompletionListener(this);
		mp.setOnErrorListener(this);
		mp.setOnInfoListener(this);
		mp.setOnPreparedListener(this);
		mp.setOnSeekCompleteListener(this);

		try {
			mp.setDataSource( pp.getProxyURL().toString() );
			mp.prepareAsync();
		} catch (IOException e) {
			System.err.println("queueProvider failed");
			return;
		}

		this.processors.add(pp);
		this.players.add(player);
		this.playerProviderMap.put(player, pp);
	}

	// Playback Controls

	private boolean hasCurrentPlayer()
	{
		return this.players.size() > 0;
	}

	private MediaPlayer getCurrentPlayer()
	{
		MediaPlayer player = null;

		if ( hasCurrentPlayer() ) {
			player = this.players.get(0);
		}

		return player;
	}

	public void play()
	{
		MediaPlayer mp = getCurrentPlayer();

		// lazy load the media player
		if (mp == null) {
			queueProvider( getCurrentProvider() );

			// start is called by the onPrepared listener
		} else {
			mp.start();
		}
	}

	public void pause()
	{
		MediaPlayer mp = getCurrentPlayer();

		if (mp != null) {
			mp.pause();
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
			resetPlayers();

			this.playlistCursor = getNextPlaylistCursor();
			play();
		}
	}

	public void playPrevious()
	{
		if ( hasPrevious() ) {
			resetPlayers();

			this.playlistCursor = getPreviousPlaylistCursor();
			play();
		}
	}

	public void setShuffleMode(boolean shuffle)
	{
		boolean oldMode = isShuffleModeEnabled();
		this.shuffleMode = shuffle;

		// prevent reshuffling if already shuffled
		if (oldMode == false) {
			// do some shuffling
		}
	}

	public void setContinuousMode(boolean continuous)
	{
		this.continuousMode = continuous;
	}

	public boolean isShuffleModeEnabled()
	{
		return this.shuffleMode;
	}

	public boolean isContinuousModeEnabled()
	{
		return this.continuousMode;
	}

	// MediaPlayer Callbacks

	public void onBufferingUpdate(MediaPlayer player, int percent)
	{
		String s = String.format("onBufferingUpdate player: %s, percent: %d",
		                         player, percent);
		System.err.println(s);

		MediaPlayer tailPlayer = this.players.get(this.players.size() - 1);

		AudioDataProvider tailPlayerProvider = this.playerProviderMap.get(tailPlayer);
		AudioDataProvider nextProvider       = getProviderAfter(tailPlayerProvider);

		/*
		 * This method gets called multiple times even after it reaches 100%,
		 * so we ensure we don't blindly queue up data sources everytime this
		 * block is reached.
		 */
		if ( percent == 100
		     && player == tailPlayer
		     && nextProvider != null
		     ) {
			System.out.println("queueing the next provider");
			queueProvider(nextProvider);
		}
	}

	public void onCompletion(MediaPlayer player)
	{
		String s = String.format("onCompletion player: %s", player);
		System.err.println(s);

		/*
		 * TODO: In the case of having the next media player being buffered
		* at this point, we need to queue up another media player
		 */

		releasePlayer(player);
	}

	public boolean onError(MediaPlayer player, int what, int extra)
	{
		String s = String.format("onInfo player: %s what: %d, extra: %d",
		                         player, what, extra);
		System.err.println(s);

		// TODO: error handling
		return false;
	}

	public boolean onInfo(MediaPlayer player, int what, int extra)
	{
		String s = String.format("onInfo player: %s what: %d, extra: %d",
		                         player, what, extra);
		System.err.println(s);

		// TODO: info handling
		return false;
	}

	public void onPrepared(MediaPlayer player)
	{
		String s = String.format("onPrepared player: %s", player);
		System.err.println(s);

		/*
		 * If given player is at the head of the list, immediately start
		 * playing. Otherwise, queue the player to the tail.
		 */
		int index = this.players.indexOf(player);
		if (index == 0) {
			player.start();
		} else {
			this.players.get(index - 1).setNextMediaPlayer(player);
		}
	}

	public void onSeekComplete(MediaPlayer player)
	{
		String s = String.format("onSeekComplete player: %s", player);
		System.err.println(s);
	}
}
