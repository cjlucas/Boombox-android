package net.cjlucas.boombox.provider;

import java.io.BufferedInputStream;
import java.io.IOException;

import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;


public class HttpAudioDataProvider extends AudioDataProvider {
    private static final int TIMEOUT = 5000;

    private URL url;
    private HttpURLConnection conn;
    private BufferedInputStream inStream;

    public HttpAudioDataProvider(URL url, Object id) {
        super(id);
        this.url = url;
    }

    public HttpAudioDataProvider(URL url) {
        this(url, url.getFile());
    }

    public long getLength() {
        return this.conn.getContentLength();
    }

    public boolean prepare() {
        try {
            this.conn = (HttpURLConnection) this.url.openConnection();
            this.conn.setConnectTimeout(TIMEOUT);
            this.conn.setReadTimeout(TIMEOUT);

            this.conn.connect();
            this.inStream = new BufferedInputStream(this.conn.getInputStream());
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
            return this.inStream.read(buffer);
        } catch (IOException e) {
            return STATUS_ERROR_OCCURED;
        }
    }

    public void release() {
        if (this.inStream != null) {
            try {
                this.inStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
