package com.example.android_final_proj;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.FragmentActivity;

/**
 * 
 * This thread receives a file's name and an empty "history entry" and puts
 * inside the history entry all the info that is needed so that
 * it can be showed as a listView entry
 */


@SuppressLint("HandlerLeak")
public class CreatHistoryEntryFromFileNameThread extends Thread
{
	ChatHistoryScreenFrag.HistoryEntry mEntry=null;
	Handler mWorkerHandler=null;
	Handler mResultHanlder=null;
	FileHandlerThread mFileHandler=null;
	Activity mActivity=null;

	
	public CreatHistoryEntryFromFileNameThread(String fileFullName,ChatHistoryScreenFrag.HistoryEntry entery, FragmentActivity fragmentActivity, Handler resHandler)
	{
		mActivity= fragmentActivity;
		mEntry=entery;
		mEntry.mID= fileFullName.split("[.]")[0]; //name.txt => name
		mResultHanlder = resHandler;
		
		mWorkerHandler = new Handler(){ //define a new message handler for the file thread
			//Here we'll receive the content of the history file that was read by a thread
			@Override
			public void handleMessage(Message msg) {
				//parse the data and update the list view
				Bundle msgData = msg.getData();
				if (msgData.getBoolean(Constants.FILE_THREAD_WAS_DATA_READ_KEY, false)){ //if a history exists
					String tmp= msgData.getString(Constants.FILE_THREAD_DATA_CONTENT_KEY, null);
					String[] data= tmp.split("["+Constants.STANDART_FIELD_SEPERATOR+"]") ;
					//update the relevant data for the display:
					mEntry.mRoomName= new String( data[0] );  //set the room's name
					mEntry.isPrivate= (data[1].equalsIgnoreCase("private"))?true:false;  //set the privacy mode
					mEntry.isEmpty = data.length <= 2;  //if this file is empty, it has only 2 rows in it 
					Message resultMsg = mResultHanlder.obtainMessage();
					mResultHanlder.sendMessage(resultMsg);  //send an empty message, just to notify that a file was read.
				}//if	
			}
		};
	}//end of constructor()

	@Override
	public void run()
	{
		super.run();
		mFileHandler= new FileHandlerThread(mEntry.mID, mWorkerHandler, true,mActivity);
		mFileHandler.start();
	
		//wait for the file handler to finish
		try
		{
			mFileHandler.join();
		}
		catch (InterruptedException e)
		{
			e.printStackTrace();
		}
		
	}
}//end of class
