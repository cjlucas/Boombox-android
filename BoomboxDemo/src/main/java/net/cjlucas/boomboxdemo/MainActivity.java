package net.cjlucas.boomboxdemo;

import android.app.Activity;

import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import net.cjlucas.boombox.Boombox;
import net.cjlucas.boombox.BoomboxInfoListener;
import net.cjlucas.boombox.provider.*;

public class MainActivity extends Activity
implements
BoomboxInfoListener,
RadioGroup.OnCheckedChangeListener
{
	private static final String TAG             = "MainActivity";
	private static final int UPDATE_UI_INTERVAL = 50;

	private Boombox boombox;
	private Timer updateUITimer;

	private Button prevButton;
	private Button nextButton;
	private Button playPauseButton;
	private TextView currentlyPlayingTextView;
	private TextView upNextTextView;
	private TextView progressTextView;
	private TextView durationTextView;
	private RadioGroup continuousModeGroup;

	private Runnable updateUIRunnable = new Runnable() {
		public void run()
		{
			updateUI();
		}
	};

	private TimerTask updateUITask = new TimerTask() {
		public void run()
		{
			runOnUiThread(updateUIRunnable);
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		System.out.println("MainActivity: onCreate");
		getBoombox();

	}

	@Override
	protected void onPostCreate(Bundle savedInstanceState)
	{
		super.onPostCreate(savedInstanceState);

		prevButton               = (Button)findViewById(R.id.prev_btn);
		nextButton               = (Button)findViewById(R.id.next_btn);
		playPauseButton          = (Button)findViewById(R.id.play_pause_btn);
		currentlyPlayingTextView = (TextView)findViewById(R.id.currently_playing);
		upNextTextView           = (TextView)findViewById(R.id.up_next);
		progressTextView         = (TextView)findViewById(R.id.progress);
		durationTextView         = (TextView)findViewById(R.id.duration);
		continuousModeGroup      = (RadioGroup)findViewById(R.id.continuous_group);

		continuousModeGroup.setOnCheckedChangeListener(this);

		this.updateUITimer = new Timer();
		this.updateUITimer.schedule(this.updateUITask, 0, UPDATE_UI_INTERVAL);

		updateUI();
	}

	@Override
	protected void onStop()
	{
		this.updateUITimer.cancel();
		this.updateUITimer = null;

		super.onStop();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	public Boombox getBoombox()
	{
		if (this.boombox == null) {
			this.boombox = new Boombox(this);
			this.boombox.start();


			try {
				BufferedReader in = new BufferedReader( new InputStreamReader( getResources().openRawResource(R.raw.sources) ) );
				String line       = null;

				while (true) {
					line = in.readLine();
					if (line == null) {
						break;
					}

					URL url = new URL(line);
					this.boombox.addProvider( new HttpAudioDataProvider(url) );
				}

			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		return this.boombox;
	}

	public List<AudioDataProvider> getProviders()
	{
		return getBoombox().getProviders();
	}

	private void updateUI()
	{
		prevButton.setEnabled( this.boombox.hasPrevious() );
		nextButton.setEnabled( this.boombox.hasNext() );

		AudioDataProvider currentProvider = this.boombox.getCurrentProvider();

		String currentlyPlaying = String.format( Locale.getDefault(), "%s: %s",
		                                         getResources().getString(R.string.currently_playing),
		                                         currentProvider == null ? "None" : currentProvider.getId() );

		currentlyPlayingTextView.setText(currentlyPlaying);

		AudioDataProvider nextProvider = this.boombox.getNextProvider();

		String upNext = String.format( Locale.getDefault(), "%s: %s",
		                               getResources().getString(R.string.up_next),
		                               nextProvider == null ? "None" : nextProvider.getId() );

		upNextTextView.setText(upNext);

		String progress = String.format(Locale.getDefault(), "%s: %fs",
		                                getResources().getString(R.string.progress),
		                                this.boombox.getCurrentPosition() / 1000.0);
		progressTextView.setText(progress);

		String duration = String.format(Locale.getDefault(), "%s: %fs",
		                                getResources().getString(R.string.duration),
		                                this.boombox.getDuration() / 1000.0);

		durationTextView.setText(duration);

		switch( this.boombox.getContinuousMode() ) {
		    case NONE:
			    continuousModeGroup.check(R.id.continuous_none);
			    break;
		    case SINGLE:
			    continuousModeGroup.check(R.id.continuous_single);
			    break;
		    case PLAYLIST:
			    continuousModeGroup.check(R.id.continuous_playlist);
			    break;
		}
	}

	public void togglePlayPauseClicked(View v)
	{
		this.boombox.togglePlayPause();
		runOnUiThread(this.updateUIRunnable);
	}

	public void playNextClicked(View view)
	{
		this.boombox.playNext();
		runOnUiThread(this.updateUIRunnable);
	}

	public void playPreviousClicked(View view)
	{
		this.boombox.playPrevious();
		runOnUiThread(this.updateUIRunnable);
	}

	public void shuffleModeSwitchChanged(View view)
	{
		this.boombox.setShuffleMode( ( (Switch)view ).isChecked() );
	}

	// BoomboxInfoListener Methods

	@Override
	public void onPlaybackStart(Boombox boombox, AudioDataProvider provider)
	{
		Log.i(TAG, "onPlaybackStart");
		runOnUiThread(this.updateUIRunnable);
	}

	@Override
	public void onPlaybackCompletion(Boombox boombox,
	                                 AudioDataProvider completedProvider, AudioDataProvider nextProvider)
	{
		Log.i(TAG, "onPlaybackCompletion");
		runOnUiThread(this.updateUIRunnable);

	}

	@Override
	public void onPlaylistCompletion(Boombox boombox)
	{
		Log.i(TAG, "onPlaylistCompletion");
		runOnUiThread(this.updateUIRunnable);
	}

	public void onBufferingUpdate(Boombox boombox, AudioDataProvider provider, int percentComplete)
	{
		//Log.i(TAG, "onBufferingUpdate: " + percentComplete);
	}

    public void onBufferingStart(Boombox boombox, AudioDataProvider provider)
    {
        Log.i(TAG, "onBufferingStart");
    }

    public void onBufferingEnd(Boombox boombox, AudioDataProvider provider)
    {
        Log.i(TAG, "onBufferingEnd");
    }

	// RadioGroup.OnCheckedChangeListener method
	public void onCheckedChanged(RadioGroup radioGroup, int id)
	{
		Boombox.ContinuousMode mode = null;

		switch(id) {
		    case R.id.continuous_none:
			    mode = Boombox.ContinuousMode.NONE;
			    break;
		    case R.id.continuous_single:
			    mode = Boombox.ContinuousMode.SINGLE;
			    break;
		    case R.id.continuous_playlist:
			    mode = Boombox.ContinuousMode.PLAYLIST;
			    break;
		    default:
			    break;
		}

		this.boombox.setContinuousMode(mode);
		runOnUiThread(this.updateUIRunnable);
	}
}
