package net.cjlucas.boombox.provider;

import android.media.MediaExtractor;
import android.media.MediaFormat;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

public class AudioFileDataProvider extends AudioDataProvider
{
	private File file;
	private FileInputStream inStream;
	private long duration;

	public AudioFileDataProvider(File file, Object id)
	{
		super(id);
		this.file = file;
	}

	public AudioFileDataProvider(File file)
	{
		this( file, file.getName() );
	}

	@Override
	public long getDuration()
	{
		if (this.duration == 0) {
			MediaExtractor ext = new MediaExtractor();
			try {
				ext.setDataSource( this.file.getAbsolutePath() );

				MediaFormat mf = ext.getTrackFormat(0);
				this.duration = mf.getLong(MediaFormat.KEY_DURATION) / 1000;
			} catch (IOException e) {
			}

			ext.release();
		}

		return this.duration;
	}

	@Override
	public long getLength()
	{
		return this.file.length();
	}

	public boolean prepare()
	{
		try {
			this.inStream = new FileInputStream(this.file);
			return true;
		} catch (FileNotFoundException e) {
			return false;
		}
	}

	public int provideData(byte[] buffer)
	{
		try {
			int size = this.inStream.read(buffer);
			return size;
		} catch (IOException e) {
			return STATUS_ERROR_OCCURED;
		}
	}

	public void release()
	{
		if (this.inStream == null) {
			return;
		}

		try {
			this.inStream.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
