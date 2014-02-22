package net.cjlucas.boombox;

import android.media.MediaPlayer;

import java.io.IOException;
import java.net.URL;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
				int size = this.provider.onNeedData(buffer);

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
		this.playlistCursor = 0;
		this.shuffleMode    = false;
		this.continuousMode = false;
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
	}

	// Providers Management

	private int getNextPlaylistCursor()
	{
		int newCursor = this.playlistCursor + 1;

		return this.continuousMode ? newCursor % this.playlist.size() : -1;
	}

	private int getPreviousPlaylistCursor()
	{
		int newCursor = this.playlistCursor - 1;

		if (newCursor < 0) {
			return this.continuousMode ? this.playlist.size() - 1 : -1;
		} else {
			return newCursor;
		}
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
			ProviderProcessor pp = setupProcessor(getCurrentProvider(), true);
			mp = queueMediaPlayer( pp.getProxyURL() );

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
		}
	}

	public void playPrevious()
	{
		if ( hasPrevious() ) {
			resetPlayers();

			this.playlistCursor = getPreviousPlaylistCursor();
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

	// Helpers

	private MediaPlayer createMediaPlayer()
	{
		MediaPlayer player = new MediaPlayer();
		player.setOnBufferingUpdateListener(this);
		player.setOnCompletionListener(this);
		player.setOnErrorListener(this);
		player.setOnInfoListener(this);
		player.setOnPreparedListener(this);
		player.setOnSeekCompleteListener(this);

		return player;
	}

	private MediaPlayer queueMediaPlayer(String dataSource)
	{
		MediaPlayer player = createMediaPlayer();
		try {
			player.setDataSource(dataSource);
			player.prepareAsync();
		} catch (IOException e) {
			// TODO: do something?
		}
		this.players.add(player);

		int index = this.players.indexOf(player);
		if (index > 0) {
			this.players.get(index - 1).setNextMediaPlayer(player);
		}

		return player;
	}

	private MediaPlayer queueMediaPlayer(URL url)
	{
		return queueMediaPlayer( url.toString() );
	}

	private ProviderProcessor setupProcessor(AudioDataProvider provider,
	                                         boolean           start)
	{
		ProviderProcessor pp = new ProviderProcessor(provider);
		if (start) {
			pp.start();
		}

		this.processors.add(pp);

		return pp;
	}

	// MediaPlayer Callbacks

	public void onBufferingUpdate(MediaPlayer player, int percent)
	{
		String s = String.format("onBufferingUpdate player: %s, percent: %d",
		                         player, percent);
		System.err.println(s);

		if (percent == 100) {
			/*
			 * TODO: queue up the next data source
			 *
			 * note: this method gets called multiple times
			 * even after percent reaches 100%. So ensure
			 * we don't blindly queue up data sources
			 * everytime this block is reached
			 */
		}
	}

	public void onCompletion(MediaPlayer player)
	{
		String s = String.format("onCompletion player: %s", player);
		System.err.println(s);

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

		player.start();
	}

	public void onSeekComplete(MediaPlayer player)
	{
		String s = String.format("onSeekComplete player: %s", player);
		System.err.println(s);
	}

}
