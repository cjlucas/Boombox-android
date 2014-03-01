package net.cjlucas.boombox.provider;

import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

public class AudioFileDataProvider extends AudioDataProvider {
    private static final String TAG = "AudioFileDataProvider";

    private File mFile;
    private FileInputStream mInStream;
    private long mDuration;

    public AudioFileDataProvider(File file, Object id) {
        super(id);
        mFile = file;
    }

    public AudioFileDataProvider(File file) {
        this(file, file.getName());
    }

    @Override
    public long getDuration() {
        if (mDuration == 0) {
            MediaExtractor ext = new MediaExtractor();
            try {
                ext.setDataSource(mFile.getAbsolutePath());

                MediaFormat mf = ext.getTrackFormat(0);
                mDuration = mf.getLong(MediaFormat.KEY_DURATION) / 1000;
            } catch (IOException e) {
                Log.e(TAG, "Couldn't get duration");
            }

            ext.release();
        }

        return mDuration;
    }

    @Override
    public long getLength() {
        return mFile.length();
    }

    public boolean prepare() {
        try {
            mInStream = new FileInputStream(mFile);
            return true;
        } catch (FileNotFoundException e) {
            return false;
        }
    }

    public int provideData(byte[] buffer) {
        try {
            return mInStream.read(buffer);
        } catch (IOException e) {
            return STATUS_ERROR_OCCURED;
        }
    }

    public void release() {
        if (mInStream == null) {
            return;
        }

        try {
            mInStream.close();
        } catch (IOException e) {
            Log.e(TAG, "IOException when closing input stream");
        }
    }
}
