package net.cjlucas.boombox;

import android.media.MediaPlayer;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import net.cjlucas.boombox.provider.AudioDataProvider;

public class Boombox
{
	private List<AudioDataProvider> providersList;
	private Queue<AudioDataProvider> providersQueue;
	private List<MediaPlayer> players;

	public Boombox()
	{
		this.providersList  = new ArrayList<AudioDataProvider>();
		this.providersQueue = new LinkedList<AudioDataProvider>();
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

}
