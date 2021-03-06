package net.cjlucas.boombox;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;

public class ProxyServer extends Thread {
    private String mHost;
    private int mPort;
    private ServerSocket mServerSocket;
    private DataForwarder mCurrentForwarder;
    private long mContentLength;
    private boolean mRunning;

    private class DataForwarder extends Thread {
        private Socket mConn;
        private boolean mWaitingForData;

        public DataForwarder(Socket conn) {
            setPriority(Thread.MIN_PRIORITY);
            mConn = conn;
        }

        public void forwardData(byte[] data) {
            try {
                //System.out.println( String.format("DataForwarder: writing %d bytes to outputStream", data.length) );
                mConn.getOutputStream().write(data, 0, data.length);
            } catch (IOException e) {
                e.printStackTrace();
                stopServer();
            }
        }

        public void close() {
            mWaitingForData = false;
        }

        public void run() {
            mWaitingForData = true;

            while (mWaitingForData) {
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
            System.err.println("No forwarder available");
            return;
        }

        mCurrentForwarder.forwardData(data);
    }

    public void runForever() {

        mRunning = true;

        while (mRunning) {
            Socket s = accept();
            System.out.println("ProxyServer: got a connection!");
            write(s, "HTTP/1.1 200 OK\r\n");
            if (mContentLength > 0) {
                write(s, "Content-Length: " + mContentLength + "\r\n");
            }

            write(s, "\r\n");

            try {
                InputStreamReader inStream = new InputStreamReader(s.getInputStream());

                char[] reqData = new char[16 * 1024];
                inStream.read(reqData);

                //              System.out.println("Request Data:");
                //              System.out.println(reqData);
            } catch (IOException e) {
                close(s);
                continue;
            }

            mCurrentForwarder = new DataForwarder(s);
            mCurrentForwarder.start();
            try {
                mCurrentForwarder.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
                stopServer();
            } finally {
                mCurrentForwarder = null;
            }

            close(s);
        }

        tearDown();
    }

    private void write(Socket s, String str) {
        try {
            System.out.println("ProxyServer: writing: " + str);
            s.getOutputStream().write(str.getBytes(), 0, str.length());
        } catch (IOException e) {
            e.printStackTrace();
            stopServer();
        }
    }

    public void run() {
        runForever();
    }

    public boolean startServer() {
        try {
            mServerSocket = new ServerSocket(mPort);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public void stopServer() {
        if (mCurrentForwarder != null) {
            mCurrentForwarder.close();
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
