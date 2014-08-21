package com.example.android_final_proj;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

/**
 * Opens a socket to a peer and sends a single string. Used to send control messages and user text messages
 * If given a valid Handler, returns a message containing information about whether the sending was successful or not.
 */
public class SendSingleStringViaSocketThread extends Thread
{
	Socket mSocket;
	Handler mHandler;
	String mMsg;
	String mPeerIP;
	String mRoomUniqueID;  //needed only if we're using a handler
	
	public SendSingleStringViaSocketThread(Handler h, String PeerIP, String msg, String RoomUniqueID)
	{
		mHandler = h;
		mPeerIP = PeerIP;
		mMsg = msg;
		mRoomUniqueID=RoomUniqueID;
	}
	
	public SendSingleStringViaSocketThread(String PeerIP, String msg)
	{
		this(null,PeerIP,msg,null);
	}

	@Override
	public void run()
	{
		PrintWriter mOut=null;
		try
		{
		    /**
		     * Create a client socket with the host,
		     * port, and timeout information.
		     */
			mSocket = new Socket();
			mSocket.bind(null);
		    mSocket.connect((new InetSocketAddress(mPeerIP, Constants.WELCOME_SOCKET_PORT)), 3000);
		    mOut =  new PrintWriter(new BufferedWriter(new OutputStreamWriter(mSocket.getOutputStream())), true);
		}

		catch (IOException e)
		{
			SendMessageViaHandler(Constants.SINGLE_SEND_THREAD_ACTION_RESULT_FAILED); //notify that the send has failed
			e.printStackTrace();
			return;
		}
			
		mOut.println(mMsg); //send via socket
		mOut.flush();
		mOut.close();
		
		if (mHandler!=null) //if we have a handler to return the result to
			SendMessageViaHandler(Constants.SINGLE_SEND_THREAD_ACTION_RESULT_SUCCESS); //notify that the send was successful
	}//run()
	
	private void SendMessageViaHandler(String result)
	{
		if (mHandler!=null)
		{
			Message msg = mHandler.obtainMessage();
			Bundle data = new Bundle();
			data.putString(Constants.SINGLE_SEND_THREAD_KEY_UNIQUE_ROOM_ID, mRoomUniqueID);  //set the room's ID
			data.putString(Constants.SINGLE_SEND_THREAD_KEY_RESULT, result);  //put the result in the data
			msg.setData(data);
			mHandler.sendMessage(msg);
		}//if
	}//end of SendMessageViaHandler()

}//class
