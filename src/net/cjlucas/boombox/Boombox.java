package net.cjlucas.boombox;

import android.media.MediaPlayer;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

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
	private int queueCursor;
	private List<MediaPlayer> players;

	public Boombox()
	{
		this.providers = new ArrayList<AudioDataProvider>();
		this.playlist  = new ArrayList<AudioDataProvider>();
		this.players   = new ArrayList<MediaPlayer>();
	}

	// Providers Management

	public boolean hasNextProvider()
	{
		// TODO
		return false;
	}

	public boolean hasPreviousProvider()
	{
		// TODO
		return false;
	}

	public void addProvider(AudioDataProvider provider)
	{
		// TODO
	}

	public AudioDataProvider getCurrentProvider()
	{
		// TODO
		return null;
	}

	public AudioDataProvider getNextProvider()
	{
		// TODO
		return null;
	}

	public AudioDataProvider getPreviousProvider()
	{
		// TODO
		return null;
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
		// TODO
	}

	public void pause()
	{
		// TODO
	}

	public void togglePlayPause()
	{
		// TODO
	}

	public void setShuffleMode(boolean shuffle)
	{
		// TODO
	}

	public void setContinuousMode(boolean continuous)
	{
		// TODO
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
