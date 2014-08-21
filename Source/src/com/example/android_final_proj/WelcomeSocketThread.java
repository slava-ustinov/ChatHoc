package com.example.android_final_proj;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Maintains an open Welcome-socket for new connections. 
 * On new connection establishment, opens a worker thread to further handle the incoming request.
 */
public class WelcomeSocketThread extends Thread 
{
	
	ServerSocket mServerSocket;
	LocalService mService;
	
	/**
	 * Constructor
	 * @param srv - reference to the LocalService
	 */
	public WelcomeSocketThread(LocalService srv) 
	{
		mService=srv;
		try
		{
			mServerSocket = new ServerSocket(Constants.WELCOME_SOCKET_PORT); //Create a server socket
		}
		catch (IOException e)
		{
			mService.OnWelcomeSocketCreateError(); //notify the service of this error
			e.printStackTrace();
			return;
		}
	}

	
	@Override
	public void run()
	{
		
		while(true)  //the welcome socket thread runs infinitely
		{
			Socket client;		
	       try
			{
				/**
		         * Wait for client connections. This
		         * call blocks until a connection is accepted from a client
		         */
			    client = mServerSocket.accept();
				
			}
			catch (IOException e)
			{
				e.printStackTrace();
				return;
			}
	
	        /**
	         * If this code is reached, a client has connected and transferred data
	         */
	       NewConnectionWorkerThread workerThread = new NewConnectionWorkerThread(mService,client);
	       workerThread.setPriority(MAX_PRIORITY);
	       workerThread.start(); //launch the worker thread.
		}//while(true)     
	}//end of run()
	
	public void kill(){

		if(mServerSocket!=null && (!mServerSocket.isClosed())){
			try
			{
				mServerSocket.close();
			}
			catch (IOException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			mServerSocket=null;
		}//if
	}

}//Class
