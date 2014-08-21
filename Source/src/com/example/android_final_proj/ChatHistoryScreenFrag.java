
package com.example.android_final_proj;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ListFragment;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
/**
 * The 2nd fragment that is contained in the 'MainScreenActivity'
 * Shows all available chat history logs
 */

public class ChatHistoryScreenFrag extends ListFragment 
{

    ArrayList<HashMap<String, String>> mListContent = null; //the list's content
	SimpleAdapter mListAdapter=null;				 		//the list's adapter
	MainScreenActivity mActivity;  							//reference to the hosting activity
	ProgressDialog historyLoadDialog;						//dialog
	Handler mHandler = new Handler();   					//will be used to receive data from the reader threads
	int mNumOfExistingFiles=0;
	int mNumOfReadFiles=0;
	HistoryEntry[] mEntries; 								//this entries[] array will be filled by threads with history entries
	
	@Override
	public void onAttach(Activity activity)
	{
		super.onAttach(activity);
		 mActivity = (MainScreenActivity)getActivity(); //set a reference to the activity
		mActivity.mHistoryFrag = this;
	}
	
	@SuppressLint("HandlerLeak")
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) 
	{
		View rootView = inflater.inflate(
				R.layout.activity_chat_history_screen_frag, container, false);
		
		InitAdapter();
		
		mHandler = new Handler()
		{
			@Override
			public void handleMessage(Message msg)
			{
				super.handleMessage(msg);
				//if all the files were read
				if (++mNumOfReadFiles == mNumOfExistingFiles)
				{
					//now we have a HistoryEntry[] filled with history entries
					//we need to build a Hash Table out of it ,and give it to the adapter	
					UpdateListView();
					mNumOfReadFiles=0;  //reset 
				}
			}
		};
		return rootView;
	}//end of onCreateView()
	
	@Override
	public void onStart()
	{
		super.onStart();
		ListView lv = (ListView)mActivity.findViewById(android.R.id.list);
		TextView emptyText = (TextView)mActivity.findViewById(android.R.id.empty);
		lv.setEmptyView(emptyText);
		
		registerForContextMenu(getListView()); //enable context menu
	}
	
	@Override
	public void onResume()
	{
		super.onResume();
		loadHistory();  //load all available history  
	}
	
	//handle a user's history file view request
	@Override
	public void onListItemClick(ListView l, View v, int position, long id)
	{
		super.onListItemClick(l, v, position, id);
	    Intent intent = new Intent(getActivity(), HistoryActivity.class); //crate a new intent
	    intent.putExtra(Constants.HASH_MAP_KEY_SEARCH_FRAG_CHAT_ROOM_UNIQUE, 
	    		mListContent.get((int)id).get(Constants.HASH_MAP_KEY_SEARCH_FRAG_CHAT_ROOM_UNIQUE));   //set the room's unique id as extra. 
	    
	    startActivity(intent);  //start the history activity
	}//end of onListItemClick()
	
	
	/**
	 * define the appearance of the context menu
	 */
	@Override
		public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo)
		{
			super.onCreateContextMenu(menu, v, menuInfo);
	        MenuInflater inflater = getActivity().getMenuInflater();
	        inflater.inflate(R.menu.chat_history_frag_context_menu, menu);		
		}
	
	/**
	 * When a context item is selected in this frag's list, the callback is at {@link ChatSearchScreenFrag} and it calls this
	 * method.
	 * @param item
	 */
	public void OnContextMenuItemSelected(MenuItem item)
	{
		AdapterContextMenuInfo selectedRow = (AdapterContextMenuInfo) item.getMenuInfo(); //get the current selected item
		
		switch(item.getItemId()) //switch by the selected operation:
		{
			case R.id.action_delete_history_file:
			{
				//get the unique id of the chat to delete
				String unqiue  = mListContent.get((int)selectedRow.id).get(Constants.HASH_MAP_KEY_SEARCH_FRAG_CHAT_ROOM_UNIQUE);
				
				mListContent.remove((int)selectedRow.id);
				mListAdapter.notifyDataSetChanged();
				
				DeleteSingleHistoryFile (unqiue);
			}
		}
	}//end of OnContextMenuItemSelected()
	
	/**
	 *  Deletes a single history file, whether this chat is active or not
	 */
	public void DeleteSingleHistoryFile (String unqiue)
	{
		if (ChatSearchScreenFrag.mService!=null) //if the service is available
		{
			ActiveChatRoom room  = ChatSearchScreenFrag.mService.mActiveChatRooms.get(unqiue);
			if (room!=null)  //this chat room is active
			{
				room.DeleteHistory();
			}
			else
			{
				DeleteFile(unqiue);
			}
		}
		else //if the service is unavailable
		{
			DeleteFile(unqiue);
		}
	}//end of DeleteSingleHistoryFile()
	
	/**
	 * Deletes a single file
	 * @param unqiue - the room's unique ID
	 * @return true if the file was deleted, false otherwise
	 */
	private boolean DeleteFile(String unqiue)
	{
		String path  = mActivity.getFilesDir().getPath()+ "/" + unqiue + ".txt";
		File f = new File(path);
		return f.delete();
	}//end of DeleteFile()
	
	/**
	 * Initializes the content, list adapter and performs a peer scan for the the 1st run only.
	 */
	private void InitAdapter()
	{
	   if (mListContent==null){mListContent = new ArrayList< HashMap<String,String>>();} //create a new array list that'll hold all the data
	   if (mListAdapter==null)
	   		{
		    mListAdapter = new SimpleAdapter(getActivity(),
				    mListContent,
					R.layout.chat_search_list_item,
					new String[]{ Constants.HASH_MAP_KEY_SEARCH_FRAG_CHAT_NAME,
		    					  Constants.HASH_MAP_KEY_SEARCH_FRAG_PARTICIPANTS,
				   				  Constants.HASH_MAP_KEY_SEARCH_FRAG_ICON },
					new int[]{R.id.search_list_item_TV1,R.id.search_list_item_TV2,R.id.search_list_item_icon});  		    
			setListAdapter(mListAdapter);
		   	}
	}//end of InitAdapter()	
	
	/**
	 * Once all history files were read, this function is called by the handled and updates the list view
	 */
	private void UpdateListView()
	{
		mListContent.clear(); //clear the current list content
		for (HistoryEntry entry : mEntries) //for each chat room
		{
			if(!entry.isEmpty)
			{
				HashMap<String, String> singleChatEntryView = new HashMap<String, String>(); //create a new hash map
				if (entry.isPrivate) //if this is a private chat
				{
					//set the 1st field to be the user's name
					singleChatEntryView.put(Constants.HASH_MAP_KEY_SEARCH_FRAG_CHAT_NAME, entry.mRoomName);  
					singleChatEntryView.put(Constants.HASH_MAP_KEY_SEARCH_FRAG_PARTICIPANTS, "Private chat");  
					singleChatEntryView.put(Constants.HASH_MAP_KEY_SEARCH_FRAG_ICON, Integer.toString(R.drawable.private_chat_icon));
					singleChatEntryView.put(Constants.HASH_MAP_KEY_SEARCH_FRAG_CHAT_ROOM_UNIQUE, entry.mID);
				}
				else //this is a public chat room
				{
					singleChatEntryView.put(Constants.HASH_MAP_KEY_SEARCH_FRAG_CHAT_NAME, entry.mRoomName);  
					singleChatEntryView.put(Constants.HASH_MAP_KEY_SEARCH_FRAG_PARTICIPANTS, "Public chat");  
					singleChatEntryView.put(Constants.HASH_MAP_KEY_SEARCH_FRAG_ICON, Integer.toString(R.drawable.public_chat_icon));
					singleChatEntryView.put(Constants.HASH_MAP_KEY_SEARCH_FRAG_CHAT_ROOM_UNIQUE, entry.mID);
				}
				mListContent.add(singleChatEntryView); //add the hash map to the content list
			}//if not empty
		}//for
		
		mListAdapter.notifyDataSetChanged(); //notify the adapter that that the content has changed
	}//end of UpdateListView()
	
	
//	file format:
//		fileName.txt
//			-------------------------------------
//			roomName\r\n
//			private(/ public)\r\n
//			msg1\r\n
//			msg2\r\n
//			..
//			..
//			..
//			--------------------------------------

	/**
	 * Launches file handlers to read all relevant files
	 */
	public void loadHistory()
	{
		File f = getActivity().getFilesDir();
		String[] fileList= f.list();	//hold a list of all the files in the current dir
		
		mNumOfExistingFiles = fileList.length;
		//this entries[] array will be filled by threads with history entries
		mEntries= new HistoryEntry[mNumOfExistingFiles];
		
		for(int i=0; i<mNumOfExistingFiles ;i++){
			mEntries[i]= new HistoryEntry();
			CreatHistoryEntryFromFileNameThread th = new CreatHistoryEntryFromFileNameThread(fileList[i], mEntries[i], getActivity(), mHandler); 
			th.start();
		}//for
	}//end of loadHistory()

/**
 * Deletes a single history file and updated the list view.
 */
public void DeleteAllHistory()
{
	File f = mActivity.getFilesDir();
	String[] fileList= f.list();	//hold a list of all the files in the current dir
	
	for (String fileName : fileList)
	{
		String unique= fileName.split("[.]")[0]; //name.txt => name
		DeleteSingleHistoryFile(unique);
	}

	mListContent.clear();
	mListAdapter.notifyDataSetChanged();
}//end of DeleteAllHistory()

		
	public class HistoryEntry{
		String mID=null;
		String mRoomName=null;
		boolean isPrivate=true;
		boolean isEmpty=true;
	}		
		

}//end of class
