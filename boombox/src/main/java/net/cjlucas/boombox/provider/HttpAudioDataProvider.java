package net.cjlucas.boombox.provider;

import android.util.Log;

import java.io.BufferedInputStream;
import java.io.IOException;

import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;


public class HttpAudioDataProvider extends AudioDataProvider {
    private static final String TAG = "HttpAudioDataProvider";
    private static final int TIMEOUT = 5000;

    private URL mUrl;
    private HttpURLConnection mConn;
    private BufferedInputStream mInStream;

    public HttpAudioDataProvider(URL url, Object id) {
        super(id);
        mUrl = url;
    }

    public HttpAudioDataProvider(URL url) {
        this(url, url.getFile());
    }

    public long getLength() {
        return mConn.getContentLength();
    }

    public boolean prepare() {
        try {
            mConn = (HttpURLConnection) mUrl.openConnection();
            mConn.setConnectTimeout(TIMEOUT);
            mConn.setReadTimeout(TIMEOUT);

            mConn.connect();
            mInStream = new BufferedInputStream(mConn.getInputStream());
        } catch (SocketTimeoutException e) {
            e.printStackTrace();
            return false;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    public int provideData(byte[] buffer) {
        try {
            return mInStream.read(buffer);
        } catch (IOException e) {
            return STATUS_ERROR_OCCURED;
        }
    }

    public void release() {
        if (mInStream != null) {
            try {
                mInStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
