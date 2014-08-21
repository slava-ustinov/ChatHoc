package com.example.android_final_proj;

import java.util.ArrayList;

import android.annotation.SuppressLint;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.NavUtils;
import android.view.MenuItem;

/**
 * Displays the content of a history file, with a similar view to that of a 'ChatActivity'
 *
 */

public class HistoryActivity extends ListActivity
{
	private  ArrayList<ChatMessage> mListContent = null; //the list's content
	private  CustomChatAdapter mListAdapter=null;				 //the list's adapter
	Handler mHandler=null;
	ProgressDialog historyLoadDialog;
	
	String mChatRoomID = null;

	@SuppressLint("HandlerLeak")
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_history);
		
		Bundle extras = getIntent().getExtras();
		mChatRoomID = extras.getString(Constants.HASH_MAP_KEY_SEARCH_FRAG_CHAT_ROOM_UNIQUE); //get the chat room ID from the intent
		
		setupActionBar();
		
		InitAdapter();
		
		mHandler = new Handler(){ //define a new message handler for the file thread
			//Here we'll receive the content of the history file that was read by a thread
			@Override
			public void handleMessage(Message msg) {
				//parse the data and update the list view
				if (msg.getData().getBoolean(Constants.FILE_THREAD_WAS_DATA_READ_KEY, false)) //if a history exists
					ParseHistoryFileDataAndUpdateListView((msg.getData().getString(Constants.FILE_THREAD_DATA_CONTENT_KEY, null)));
				else
				{
					//TODO DISPLAY AN ERROR. NO WAY THIS FILE DOESN'T EXIST
				}
				
				//dismiss the progress dialog
				if (historyLoadDialog.isShowing())
				historyLoadDialog.dismiss();
			}
		};
	}//end of onCreate()
	
	@Override
	protected void onResume()
	{
		super.onResume();
		//load the history
		historyLoadDialog = new ProgressDialog(this);
		historyLoadDialog.setTitle("Loading history...");
		historyLoadDialog.setMessage("Please Wait.");
		historyLoadDialog.show();
	
		new FileHandlerThread(mChatRoomID, mHandler, true,this).start(); //launch the history file reader
	}//end of onResume()
	
	private void ParseHistoryFileDataAndUpdateListView(String data)
	{
		String[] parsedHistory = data.split("["+Constants.STANDART_FIELD_SEPERATOR+"]"); //parse the string by the separator char
		int length = parsedHistory.length;
		
		setTitle(parsedHistory[0]);   //set the window's title to be the chat's name
		//set icon
		if (parsedHistory[1].equalsIgnoreCase("private"))
			getActionBar().setIcon(R.drawable.private_chat_icon);
		else
			getActionBar().setIcon(R.drawable.public_chat_icon);
		
		
		for (int i=2; i<length && length>2 ;i++) //for each msg string
		{
			String[] parsedSingleMsg = parsedHistory[i].split("["+Constants.CHAT_MSG_ENTRY_SEPARATOR_CHAR+"]"); //parse by the inner separator
     		ChatMessage msg = new ChatMessage(parsedSingleMsg[1], parsedSingleMsg[2].replace(Constants.ENTER_REPLACEMENT_CHAR, '\n'),
     				parsedSingleMsg[0], parsedSingleMsg[3], 
     				parsedSingleMsg[1].equalsIgnoreCase(MainScreenActivity.UniqueID));
     		mListContent.add(msg);
		}//for
		
		mListAdapter.notifyDataSetChanged();
		getListView().setSelection(mListContent.size()-1);
			
	}//end of ParseHistoryFileDataAndUpdateListView()
	
	/**
	 * Initializes the content, list adapter and performs a peer scan for the the 1st run only.
	 */
	private void InitAdapter()
	{
	   if (mListContent==null){mListContent = new ArrayList<ChatMessage>();} //create a new array list that'll hold all the data
	   if (mListAdapter==null)
	   		{
		    mListAdapter = new CustomChatAdapter(this,mListContent);  //create a new adapter		    
			setListAdapter(mListAdapter);   						//set the content
		   	}
	}//end of InitAdapter()

	 private void setupActionBar()
	 {
	  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
	  {
	   // enables the activity icon as a 'home' button. required if "android:targetSdkVersion" > 14
	   getActionBar().setHomeButtonEnabled(true);
	   getActionBar().setDisplayHomeAsUpEnabled(true);
	  }
	 }
	 
	 public boolean onOptionsItemSelected(MenuItem item)
	 {
	  switch (item.getItemId())
	  {
	   case android.R.id.home:
	    NavUtils.navigateUpFromSameTask(this);
	  } 
	  return true;
	 }
	
}//end of class()
