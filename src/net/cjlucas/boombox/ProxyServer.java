package net.cjlucas.boombox;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;

public class ProxyServer extends Thread
{
	private String host;
	private int port;
	private ServerSocket serverSocket;
	private DataForwarder currentForwarder;
	private boolean running;

	private class DataForwarder extends Thread
	{
		private Socket conn;
		private boolean waitingForData;

		public DataForwarder(Socket conn)
		{
			setPriority(Thread.MIN_PRIORITY);
			this.conn = conn;
		}

		public void forwardData(byte[] data)
		{
			try {
				System.out.println( String.format("DataForwarder: writing %d bytes to outputStream", data.length) );
				this.conn.getOutputStream().write(data, 0, data.length);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		public void close()
		{
			this.waitingForData = false;
		}

		public void run()
		{
			this.waitingForData = true;

			while(this.waitingForData) {
				try { Thread.sleep(50); } catch (Exception e) {
				}
			}
		}
	}

	public ProxyServer()
	{
		this.host = "127.0.0.1";
		this.port = 0;
	}

	public ProxyServer(int port)
	{
		this();
		this.port = port;
	}

	public URL getURL()
	{
		try {
			return new URL("http", "0.0.0.0", getPort(), "/");
		} catch (MalformedURLException e) {
			e.printStackTrace();
			return null;
		}
	}

	public int getPort()
	{
		return this.serverSocket.getLocalPort();
	}

	public boolean hasConnection()
	{
		return this.currentForwarder != null;
	}

	public void sendData(byte[] data)
	{
		if (this.currentForwarder == null) {
			System.err.println("No forwarder available");
			return;
		}

		this.currentForwarder.forwardData(data);
	}

	public void runForever()
	{

		this.running = true;

		while (this.running) {
			Socket s = accept();
			System.out.println("ProxyServer: got a connection!");
			String ok = "HTTP/1.1 200 OK\r\n\r\n";
			try {
				s.getOutputStream().write( ok.getBytes(), 0, ok.length() );
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			try {
				InputStreamReader inStream = new InputStreamReader( s.getInputStream() );

				char[] reqData = new char[16 * 1024];
				inStream.read(reqData);

				//				System.out.println("Request Data:");
				//				System.out.println(reqData);
			} catch (IOException e) {
				close(s);
				continue;
			}

			this.currentForwarder = new DataForwarder(s);
			this.currentForwarder.start();
			try {
				this.currentForwarder.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			finally {
				this.currentForwarder = null;
			}

			close(s);
		}

		tearDown();
	}

	public void run()
	{
		runForever();
	}

	public boolean startServer()
	{
		try {
			this.serverSocket = new ServerSocket(this.port);
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}

	public void stopServer()
	{
		if (this.currentForwarder != null) {
			this.currentForwarder.close();
		}

		this.running = false;
	}

	private Socket accept()
	{
		Socket s = null;
		try {
			return this.serverSocket.accept();
		} catch (IOException e) {
			e.printStackTrace();
		}

		return s;
	}

	private void close(Socket socket)
	{
		try {
			socket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void tearDown()
	{
		try {
			if (this.serverSocket != null) {
				this.serverSocket.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
