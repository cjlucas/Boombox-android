package net.cjlucas.boombox;

import android.media.MediaPlayer;

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
	private int playlistCursor;
	private List<MediaPlayer> players;
	private boolean shuffleMode;
	private boolean continuousMode;

	public Boombox()
	{
		this.providers      = Collections.synchronizedList( new ArrayList<AudioDataProvider>() );
		this.playlist       = Collections.synchronizedList( new ArrayList<AudioDataProvider>() );
		this.players        = Collections.synchronizedList( new ArrayList<MediaPlayer>() );
		this.playlistCursor = 0;
		this.shuffleMode    = false;
		this.continuousMode = false;
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

	public boolean hasNextProvider()
	{
		return getNextPlaylistCursor() != -1;
	}

	public boolean hasPreviousProvider()
	{
		return getPreviousPlaylistCursor() != -1;
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

		if (mp != null) {
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

	// MediaPlayer Callbacks

	public void onBufferingUpdate(MediaPlayer player, int percent)
	{
		// TODO
	}

	public void onCompletion(MediaPlayer player)
	{
		// TODO
	}

	public boolean onError(MediaPlayer player, int what, int extra)
	{
		// TODO
		return false;
	}

	public boolean onInfo(MediaPlayer player, int what, int extra)
	{
		// TODO
		return false;
	}

	public void onPrepared(MediaPlayer player)
	{
		// TODO
	}

	public void onSeekComplete(MediaPlayer player)
	{
		// TODO
	}

}
