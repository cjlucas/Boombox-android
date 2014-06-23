package net.cjlucas.boombox;

import android.util.Log;

import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;

public class ProxyServer extends Thread {
    private static final String TAG = "ProxyServer";
    private String mHost;
    private int mPort;
    private ServerSocket mServerSocket;
    private DataForwarder mCurrentForwarder;
    private long mContentLength;
    private boolean mRunning;
    private WeakReference<Listener> mListener;

    public interface Listener {
        void clientDidConnect(ProxyServer proxyServer);
        void clientDidDisconnect(ProxyServer proxyServer);
    }

    private class DataForwarder extends Thread {
        private Socket mConn;
        private boolean mHalted;

        public DataForwarder(Socket conn) {
            setPriority(Thread.MIN_PRIORITY);
            mConn = conn;
        }

        public void forwardData(byte[] data) {
            if (mHalted) {
                return;
            }

            try {
                mConn.getOutputStream().write(data, 0, data.length);
            } catch (IOException e) {
                Log.e(TAG, "IOException caught, halting data forwarder");
                halt();
            }
        }

        public void halt() {
            mHalted = true;
        }

        public void run() {
            mHalted = false;

            while (!mHalted) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    stopServer();
                }
            }
        }
    }

    public ProxyServer() {
        mHost = "127.0.0.1";
        mPort = 0;
    }

    public ProxyServer(int port) {
        this();
        mPort = port;
    }

    public void registerListener(Listener listener) {
        mListener = new WeakReference<>(listener);
    }

    public void unregisterListener(Listener listener) {
        mListener = null;
    }

    private Listener getListener() {
        return mListener != null ? mListener.get() : null;
    }

    public URL getURL() {
        try {
            return new URL("http", "0.0.0.0", getPort(), "/");
        } catch (MalformedURLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public int getPort() {
        return mServerSocket.getLocalPort();
    }

    public boolean isRunning() {
        return mRunning;
    }

    public void setContentLength(long contentLength) {
        mContentLength = contentLength;
    }

    public boolean hasConnection() {
        return mCurrentForwarder != null;
    }

    public void sendData(byte[] data) {
        if (mCurrentForwarder == null) {
            Log.v(TAG, "No forwarder available");
            return;
        }

        mCurrentForwarder.forwardData(data);
    }

    public void runForever() {

        mRunning = true;

        while (mRunning) {
            Socket s = accept();
            if (getListener() != null) {
                getListener().clientDidConnect(this);
            }
            Log.v(TAG, "ProxyServer: got a connection!");
            write(s, "HTTP/1.1 200 OK\r\n");
            write(s, "Accept-Ranges: none\r\n");
            if (mContentLength > 0) {
                write(s, "Content-Length: " + mContentLength + "\r\n");
            }

            write(s, "\r\n");

            try {
                InputStreamReader inStream = new InputStreamReader(s.getInputStream());

                char[] reqData = new char[16 * 1024];
                inStream.read(reqData);
            } catch (IOException e) {
                Log.e(TAG, "error occured while reading req data", e);
                close(s);
                continue;
            }

            mCurrentForwarder = new DataForwarder(s);
            mCurrentForwarder.start();
            try {
                mCurrentForwarder.join();
            } catch (InterruptedException e) {
                Log.e(TAG, "current forwarder was interrupted");
                stopServer();
            } finally {
                mCurrentForwarder = null;
            }

            close(s);

            if (getListener() != null) {
                getListener().clientDidDisconnect(this);
            }
        }

        tearDown();
    }

    private void write(Socket s, String str) {
        try {
            Log.v(TAG, "ProxyServer: writing: " + str);
            s.getOutputStream().write(str.getBytes(), 0, str.length());
        } catch (IOException e) {
            Log.e(TAG, "could not write to output stream", e);
            stopServer();
        }
    }

    public void run() {
        runForever();
    }

    public boolean startServer() {
        Log.d(TAG, "Starting proxy server");
        try {
            mServerSocket = new ServerSocket(mPort);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public void stopServer() {
        Log.d(TAG, "Stopping proxy server");
        if (mCurrentForwarder != null) {
            mCurrentForwarder.halt();
        }

        mRunning = false;
    }

    private Socket accept() {
        try {
            return mServerSocket.accept();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    private void close(Socket socket) {
        Log.d(TAG, "closing socket");
        try {
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void tearDown() {
        try {
            if (mServerSocket != null) {
                mServerSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
