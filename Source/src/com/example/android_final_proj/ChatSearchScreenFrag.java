package com.example.android_final_proj;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.os.Bundle;
import android.os.IBinder;
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

import com.example.android_final_proj.LocalService.LocalBinder;

/**
 * The 1st fragment that is contained in the 'MainScreenActivity'
 * Shows all available chat rooms and allows to engage in a chat activity
 */
public class ChatSearchScreenFrag extends ListFragment 
{
	
	public static LocalService mService=null;  				 //our service
	MainScreenActivity mActivity;  							 //reference to the hosting activity
	boolean mIsServiceBound=false;						     //indicating whether the service is bound
	private  ArrayList<HashMap<String, String>> mListContent = null; //the list's content
	private  SimpleAdapter mListAdapter=null;				 //the list's adapter
	public static boolean mIsWifiDirectEnabled=false;		 //indicating whether the wifi-direct is enabled
	public static boolean mIsConnectedToGroup=false;
	private boolean mIsWasWifiDirectDialogShown=false;       //in case the wifi-direct is turned off, we want show a dialogue
															 //only once. This variable indicated whether it was shown already
	private EnableWifiDirectDialog mWifiEnableDialog = null;
	
	ServiceMsgReceiver mServiceBroadcastReceiver = null;     //reference to a broadcast receiver that listens to broadcasts from our service
	public static WifiP2pManager mManager=null;				 //a wifi p2p manager object
	public static Channel mChannel=null;					 //required for working with the manager

	
	/**
	 * When this frag is displayed, we want the activity to know of it.
	 */
	@Override
	public void onAttach(Activity activity)
	{
		super.onAttach(activity);
	    mActivity = (MainScreenActivity)getActivity(); //set a reference to the activity
	    
	    
		mIsWifiDirectEnabled=false;  //reset the wifi flag
	    
		if (mServiceBroadcastReceiver==null) //if the b-cast receiver that works with the service wasn't initialized
		{
			mServiceBroadcastReceiver = new ServiceMsgReceiver();
			mActivity.registerReceiver(mServiceBroadcastReceiver, new IntentFilter(Constants.SERVICE_BROADCAST)); //register
		}
		mActivity.mSearchFrag = this;
	}//end of onAttach()

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		if (mManager==null && mChannel==null)
		{
			mManager = (WifiP2pManager) mActivity.getSystemService(Context.WIFI_P2P_SERVICE); 	//get a wifip2pmanager
		    mChannel = mManager.initialize(mActivity, mActivity.getMainLooper(), null); 		//get a channel
		}
	}//end of onCreate()
	
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		View rootView = inflater.inflate(R.layout.activity_chat_search_screen_frag, container, false); //inflate the view
	    //init the adapter and perform a peer search. Changes will be made only if it's the 1st time this fragment is run
	    InitAdapter();
	    	    
		return rootView;
	}//end of onCreateView()
	
	@Override
	public void onStart() 
	{
		super.onStart();
		
        // Bind to LocalService
        Intent intent = new Intent(mActivity, LocalService.class);
        mActivity.bindService(intent, mConnection, Context.BIND_AUTO_CREATE); //async
        
        if (mService!=null) //done for cases of fragment refresh done by the system
        	UpdateListView();
        
		registerForContextMenu(getListView()); //enable context menu
	}//end of onStart()
	
	@Override
	public void onResume() 
	{
	    super.onResume();
		
		if (mService!=null)
			UpdateListView();
		
	}//end of onResume()
	
	@Override
	public void onStop() 
	{
		super.onStop();
		// Unbind from the service
	    if (mIsServiceBound) 
	    	{
	        mActivity.unbindService(mConnection);
	        mIsServiceBound = false;
	    	}
	}//end of onStop()
	
	
	/**
	 * For this context menu, we'de like to display a single option only if a private chat item was selected.
	 * If a public chat item was selected, no context menu should be displayed
	 * @param menu
	 * @param v
	 * @param menuInfo
	 */
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo)
	{
	    super.onCreateContextMenu(menu, v, menuInfo);
	    
	    AdapterContextMenuInfo selectedRow = (AdapterContextMenuInfo) menuInfo; //get the current selected item
	    boolean isPrivateChat = mListContent.get((int)selectedRow.id).
	    		get(Constants.HASH_MAP_KEY_SEARCH_FRAG_PARTICIPANTS).equalsIgnoreCase("Private chat");
	    
	    MenuInflater inflater = mActivity.getMenuInflater();
	    inflater.inflate(R.menu.chat_search_frag_context_menu, menu);
	    
	    boolean isIgnored=false;
	
	    if (isPrivateChat) //a context should be shown only for a private chat
	    {
	    	isIgnored = mListContent.get((int)selectedRow.id).get(Constants.HASH_MAP_KEY_SEARCH_FRAG_ICON)
	    			.equalsIgnoreCase(Integer.toString(R.drawable.ignored_user_icon));
	    	
	    	 menu.findItem(R.id.action_ignore_user).setVisible(!isIgnored);  //set visibility for the ignore option 
	    	 menu.findItem(R.id.action_unignore_user).setVisible(isIgnored);  //set visibility the unignore option 
	    }
	    else //if this is a public chat, we don't no context menu
	    {
	    	 menu.findItem(R.id.action_ignore_user).setVisible(false);  //set visibility for the ignore option 
	    	 menu.findItem(R.id.action_unignore_user).setVisible(false);  //set visibility the unignore option 
	    }
	}//end of onCreateContextMenu()
	
	/**
	 * Define an item selection handler for the context menu
	 */
	@Override
		public boolean onContextItemSelected(MenuItem item)
		{
			AdapterContextMenuInfo selectedRow = (AdapterContextMenuInfo) item.getMenuInfo(); //get the current selected item
		
			switch(item.getItemId()) //switch by the selected operation:
			{
				case R.id.action_ignore_user:
				{
					//get the unique id of the user to ignore
					String unique  = mListContent.get((int)selectedRow.id).get(Constants.HASH_MAP_KEY_SEARCH_FRAG_CHAT_ROOM_UNIQUE);
					
					//we'de like to change the icon
					mListContent.get((int)selectedRow.id).put(Constants.HASH_MAP_KEY_SEARCH_FRAG_ICON, Integer.toString(R.drawable.ignored_user_icon));
					mListAdapter.notifyDataSetChanged();
					
					mService.mBannedFromPrivateChatUsers.put(unique, "true"); //update the ignore list
					return true;
				}
				case R.id.action_unignore_user:
				{
					//get the unique id of the user to ignore
					String unqiue  = mListContent.get((int)selectedRow.id).get(Constants.HASH_MAP_KEY_SEARCH_FRAG_CHAT_ROOM_UNIQUE);
					
					//we'de like to change the icon
					mListContent.get((int)selectedRow.id).put(Constants.HASH_MAP_KEY_SEARCH_FRAG_ICON, Integer.toString(R.drawable.private_chat_icon));
					mListAdapter.notifyDataSetChanged();
					
					mService.mBannedFromPrivateChatUsers.remove(unqiue); //remove the user from the ignore list
					return true;
				}
				//Context menu choices from the history frag are received here, so we just call the frag to handle this event
				case R.id.action_delete_history_file:
				{
					mActivity.mHistoryFrag.OnContextMenuItemSelected(item);
					return true;
				}
			}
			return true;
		}//end of onContextItemSelected()

	/**
	 * Launches the chat activity with all needed parameters
	 */
    @Override
    public void onListItemClick(ListView l, View v, int position, long id)
    {
    	super.onListItemClick(l, v, position, id);
    	String uniqueID;
		synchronized (mListContent)
		{
			HashMap<String, String> RoomInfo =  mListContent.get((int)id);
			uniqueID = RoomInfo.get(Constants.HASH_MAP_KEY_SEARCH_FRAG_CHAT_ROOM_UNIQUE);  //get the selected chat item's unique id
		}
	    Intent intent = new Intent(mActivity, ChatActivity.class); //crate a new intent
	    intent.putExtra(Constants.HASH_MAP_KEY_SEARCH_FRAG_CHAT_ROOM_UNIQUE, uniqueID);   //set the room's unique id as extra.  
	    
	    startActivity(intent);  //send
    }//end of onListItemClick()
    
	/**
	 * This class is the broadcast receiver for all broadcasts coming from the service
	 */
	private class ServiceMsgReceiver extends BroadcastReceiver 
	{
	@Override
	   public void onReceive(Context context, Intent intent) 
	   {    
	       String action = intent.getAction();
	       if(action.equalsIgnoreCase(Constants.SERVICE_BROADCAST))
	       {    
	    	   Bundle extras = intent.getExtras(); //get extras
	          int opcode =extras.getInt(Constants.SERVICE_BROADCAST_OPCODE_KEY); //get the opcode
			  switch (opcode) 
				{
					case Constants.SERVICE_BROADCAST_OPCODE_ACTION_WIFI_EVENT_VALUE:
						HandleWifiP2pEvent(extras.getInt(Constants.SERVICE_BROADCAST_WIFI_EVENT_KEY),
											extras.getInt(Constants.SERVICE_BROADCAST_WIFI_EVENT_FAIL_REASON_KEY));
						break;
					case Constants.SERVICE_BROADCAST_OPCODE_ACTION_DO_TOAST:
						{
						//show a toast with the received message
				//		LocalService.showBubble(extras.getString(Constants.SERVICE_BROADCAST_TOAST_STRING_KEY), mActivity);
						break;
						}
						
//					case Constants.SERVICE_BROADCAST_OPCODE_ACTION_DO_SHOW_MSG:
//					{
//					//show received message on editText
//						String msg = extras.getString(Constants.SERVICE_BROADCAST_SHOW_MSG_KEY);
//						showMsgOnScreen(msg);
//					
//					break;
//					}
					
					case Constants.SERVICE_BROADCAST_OPCODE_ACTION_CHAT_ROOM_LIST_CHANGED:
					{
					//Update the content of the list view
						UpdateListView();
					break;
					}
					case Constants.SERVICE_BROADCAST_WELCOME_SOCKET_CREATE_FAIL:
					{
						ShowWelcomeSockErrorDialog();
					break;
					}
				
				}//switch
	       }//if
	   }//end of onReceive()
	}//end of ServiceMsgReceiver()
	
	
	/**
	 * Shows an alert dialog when a user tries to close a hosted chat room
	 */
	private void ShowWelcomeSockErrorDialog()
	{
		new AlertDialog.Builder(mActivity)
	    .setTitle("Critical error")
	    .setMessage("Unable to open a welcome socket! Shutting down")
	    .setIcon(R.drawable.alert_icon)
	    //yes button setter
	    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
	        public void onClick(DialogInterface dialog, int which) { 
	    		
			mActivity.kill();  //close the entire app
	        
	        }//onClick-Yes
	     })//setPositive
	     .setOnCancelListener(new OnCancelListener()
		{
			
			@Override
			public void onCancel(DialogInterface dialog)
			{
				mActivity.kill();  //close the entire app
			}
		})
	     .show();
	}//end of ShowCloseHostedRoomDialog()
	
	/**
	 * Used by the ServiceMsgReceiver class to handle events regarding wifi-p2p events sent by the service
	 * @param eventCode - The event code. Codes are taken from  Constants.class
	 * @param failReasonCode - Wifi-p2p event fail code, if occurred
	 */
	private void HandleWifiP2pEvent(int eventCode, int failReasonCode)
	{
	  switch (eventCode) //figure out which wifi p2p event has occurred
		{
			case Constants.SERVICE_BROADCAST_WIFI_EVENT_P2P_ENABLED:
			{
                mIsWifiDirectEnabled=true;  
				break;
			}
			case Constants.SERVICE_BROADCAST_WIFI_EVENT_P2P_DISABLED:
			{
				//if the wifi-direct was shutdown by the user in the middle of runtime
				if (mIsWifiDirectEnabled==true)
				{
					kill();
					//we want to relaunch the application:
					Intent intent = new Intent(mActivity,MainScreenActivity.class);
					intent.putExtra(Constants.WIFI_BCAST_RCVR_WIFI_OFF_EVENT_INTENT_EXTRA_KEY, true);
					intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
					startActivity(intent);
					return;
				}
				mIsWifiDirectEnabled=false;
				ShowWifiDirectDialogWhenStarted();
				break;
			}
			case Constants.SERVICE_BROADCAST_WIFI_EVENT_PEER_CHANGED:
			{
		//		LocalService.showBubble("peers have changed",mActivity);
				break;
			}
			case Constants.SERVICE_BROADCAST_WIFI_EVENT_PEERS_AVAILABLE:
			{
       //         LocalService.showBubble("peers Available",mActivity);
				break;
			}
			case Constants.SERVICE_BROADCAST_WIFI_EVENT_PEER_DISCOVER_SUCCESS:
			{
	  //		LocalService.showBubble("Peer discovery was successful!",mActivity);
				break;
			}
			case Constants.SERVICE_BROADCAST_WIFI_EVENT_PEER_DISCOVER_FAILED:
			{
				switch(failReasonCode)
				{
				case 0:
					{
			//	    Constants.showBubble("Peer discovery failed! Unknown error!", mActivity);
					break;
					}
				case 1:
					{
					Constants.showBubble("Peer discovery failed! Wifi Direct isn't supported by this device!", mActivity);
					break;
					}
				case 2:
					{
			//		Constants.showBubble("Peer discovery failed! Channel is busy, please wait.", mActivity);
					break;
					}
				}
			}
		}//switch
	}//end of HandleWifiP2pEvent()
	
	/**
	 * Clears the ignored-for-private-chat users list
	 */
	public void ClearIgnoredUsersList()
	{
		if (mService!=null)
		{
		mService.mBannedFromPrivateChatUsers.clear(); //clear the banned users list
		UpdateListView();
		}
	}//end of ClearIgnoredUsersList()
	

	/**
	 * Called by the activity when the refresh button is clicked
	 */
	public void onRefreshButtonClicked (View v)
		{
		if (mIsWifiDirectEnabled==false) //wifi direct is disabled
			{
			if (mWifiEnableDialog==null)
				mWifiEnableDialog= new EnableWifiDirectDialog();
			
			if (!mWifiEnableDialog.isVisible())
				mWifiEnableDialog.show(getFragmentManager(),"MyDialog");
			}
		
	    if(mIsServiceBound && mService!=null)  //wifi direct is enabled
			{
			mService.OnRefreshButtonclicked();
			UpdateListView();
			}
	    
		}//end of onRefreshButtonClicked()
	

	/**
	 * Terminates the service
	 */
	public void kill()
	{
		if (mService!=null)
			mService.kill();
		
		if (mServiceBroadcastReceiver!=null){
			if(mActivity!=null){
				mActivity.unregisterReceiver(mServiceBroadcastReceiver);  //unregister the receiver only when the app is closed
			}//if
			mServiceBroadcastReceiver=null;
		}
		
		mManager.removeGroup(mChannel, null);
	}//end of kill()

	/**
	 * Initializes the content, list adapter and performs a peer scan for the the 1st run only.
	 */
	private void InitAdapter()
	{
	   if (mListContent==null){mListContent = new ArrayList< HashMap<String,String>>();} //create a new array list that'll hold all the data
	   if (mListAdapter==null)
	   		{
		    mListAdapter = new SimpleAdapter(mActivity,
				    mListContent,
					R.layout.chat_search_list_item,
					new String[]{ Constants.HASH_MAP_KEY_SEARCH_FRAG_CHAT_NAME,
				   				  Constants.HASH_MAP_KEY_SEARCH_FRAG_PARTICIPANTS,
				   				  Constants.HASH_MAP_KEY_SEARCH_HOSTED_PUBLIC_ROOM_ICON,
				   				  Constants.HASH_MAP_KEY_SEARCH_LOCKED_PUBLIC_ROOM_ICON,
				   				  Constants.HASH_MAP_KEY_SEARCH_NEW_MSG_ICON,
				   				  Constants.HASH_MAP_KEY_SEARCH_FRAG_ICON },
					new int[]{R.id.search_list_item_TV1,R.id.search_list_item_TV2, R.id.search_list_item_hosted_icon,
		    		R.id.search_list_item_lock_icon,R.id.search_list_item_new_msg_icon,R.id.search_list_item_icon});  		    
			setListAdapter(mListAdapter);
		   	}
	}//end of InitAdapter()
	
	/**
	 * Gets a new chat room list from the service and displays it in the list view
	 */
	private void UpdateListView()
	{
		if (mService==null) //if we don't have a reference to the service yet
			return;
		
		synchronized (mListContent)
		{
			mListContent.clear(); //clear the current list content
			
			if (!mService.mActiveChatRooms.isEmpty()) //we want to look up all hosted chat rooms on this device
			{
				Collection<ActiveChatRoom> chatRooms = mService.mActiveChatRooms.values();  //get all available hosted chat rooms
				for (ActiveChatRoom room : chatRooms) //for each chat room
				{
					if (room.isHostedGroupChat) //if this is a hosted group chat
					{
						HashMap<String, String> singleChatEntryView = new HashMap<String, String>(); //create a new hash map
						//set the 1st field to be the user's name
						singleChatEntryView.put(Constants.HASH_MAP_KEY_SEARCH_FRAG_CHAT_NAME, room.mRoomInfo.Name);  
						singleChatEntryView.put(Constants.HASH_MAP_KEY_SEARCH_FRAG_PARTICIPANTS, Constants.UserListToString(room.mRoomInfo.Users));
						//messing around with the layout's icons:
						if (room.mRoomInfo.Password!=null)
						{
							singleChatEntryView.put(Constants.HASH_MAP_KEY_SEARCH_HOSTED_PUBLIC_ROOM_ICON, Integer.toString(R.drawable.hosted_icon));
							singleChatEntryView.put(Constants.HASH_MAP_KEY_SEARCH_LOCKED_PUBLIC_ROOM_ICON, Integer.toString(R.drawable.lock_icon_orange));
						}
						else
						{
							singleChatEntryView.put(Constants.HASH_MAP_KEY_SEARCH_LOCKED_PUBLIC_ROOM_ICON, Integer.toString(R.drawable.hosted_icon));
						}
						if (room.mRoomInfo.hasNewMsg)
							singleChatEntryView.put(Constants.HASH_MAP_KEY_SEARCH_NEW_MSG_ICON, Integer.toString(R.drawable.msg_icon));
						singleChatEntryView.put(Constants.HASH_MAP_KEY_SEARCH_FRAG_ICON, Integer.toString(R.drawable.public_chat_icon));
						singleChatEntryView.put(Constants.HASH_MAP_KEY_SEARCH_FRAG_CHAT_ROOM_UNIQUE, room.mRoomInfo.RoomID);
						
						mListContent.add(singleChatEntryView); //add the hash map to the content list
					}//if hosted public chat
				}//for
			}//if 
			
			if (!mService.mDiscoveredChatRoomsHash.isEmpty()) //if there's a valid discovered chat room list available
			{
				Collection<ChatRoomDetails> chatRooms = mService.mDiscoveredChatRoomsHash.values();  //get all discovered chat rooms
				for (ChatRoomDetails room : chatRooms) //for each chat room
				{
					HashMap<String, String> singleChatEntryView = new HashMap<String, String>(); //create a new hash map
					if (room.isPrivateChatRoom) //if this is a private chat
					{
						//set the 1st field to be the user's name
						singleChatEntryView.put(Constants.HASH_MAP_KEY_SEARCH_FRAG_CHAT_NAME, room.Users.get(0).name);  
						singleChatEntryView.put(Constants.HASH_MAP_KEY_SEARCH_FRAG_PARTICIPANTS, "Private chat");
						//now we'de like to check if this user is ignored
						if (room.hasNewMsg)
							singleChatEntryView.put(Constants.HASH_MAP_KEY_SEARCH_NEW_MSG_ICON, Integer.toString(R.drawable.msg_icon));
						if (mService.mBannedFromPrivateChatUsers.containsKey(room.RoomID))
							singleChatEntryView.put(Constants.HASH_MAP_KEY_SEARCH_FRAG_ICON, Integer.toString(R.drawable.ignored_user_icon));
						else
						singleChatEntryView.put(Constants.HASH_MAP_KEY_SEARCH_FRAG_ICON, Integer.toString(R.drawable.private_chat_icon));
						
						singleChatEntryView.put(Constants.HASH_MAP_KEY_SEARCH_FRAG_CHAT_ROOM_UNIQUE, room.RoomID);
					}//if
					else //this is a public chat room, NOT hosted by us
					{
						//set the 1st field to be the user's name
						singleChatEntryView.put(Constants.HASH_MAP_KEY_SEARCH_FRAG_CHAT_NAME, room.Name);  
						singleChatEntryView.put(Constants.HASH_MAP_KEY_SEARCH_FRAG_PARTICIPANTS,room.UserNamesString);
						if (room.Password!=null) //if this room requires a pw
						{
							//if we've connected already to this public room
							if (mService.mActiveChatRooms.containsKey(room.RoomID))
							singleChatEntryView.put(Constants.HASH_MAP_KEY_SEARCH_LOCKED_PUBLIC_ROOM_ICON, 
									Integer.toString(R.drawable.lock_icon_green));
							else //we haven't connected yet
								singleChatEntryView.put(Constants.HASH_MAP_KEY_SEARCH_LOCKED_PUBLIC_ROOM_ICON, 
										Integer.toString(R.drawable.lock_icon_red));
						}
						//if we're connected to a public chat which is not hosted by us
						if (mService.mActiveChatRooms.containsKey(room.RoomID))
								singleChatEntryView.put(Constants.HASH_MAP_KEY_SEARCH_LOCKED_PUBLIC_ROOM_ICON, 
										Integer.toString(R.drawable.plug_icon));					
						if (room.hasNewMsg)
							singleChatEntryView.put(Constants.HASH_MAP_KEY_SEARCH_NEW_MSG_ICON, Integer.toString(R.drawable.msg_icon));
						singleChatEntryView.put(Constants.HASH_MAP_KEY_SEARCH_FRAG_ICON, Integer.toString(R.drawable.public_chat_icon));
						singleChatEntryView.put(Constants.HASH_MAP_KEY_SEARCH_FRAG_CHAT_ROOM_UNIQUE, room.RoomID);
					}//else
					mListContent.add(singleChatEntryView); //add the hash map to the content list
				}//for
			}//if 
			
			mListAdapter.notifyDataSetChanged(); //notify the adapter that that the content has changed
		}//synchronized (mListContent)
	}//end of UpdateListView()
	
	
	/**
	 * If the wifi direct is disabled, show a dialog only the 1st time this app is run.
	 * This method is called by the wifi b-cast receiver
	*/
	public void ShowWifiDirectDialogWhenStarted ()
	{
		if (mIsWasWifiDirectDialogShown==false)
		{
			if (mWifiEnableDialog==null)
			mWifiEnableDialog = new EnableWifiDirectDialog();
			

			if (!mWifiEnableDialog.isVisible())
				mWifiEnableDialog.show(getFragmentManager(),"MyDialog");
			
			mIsWasWifiDirectDialogShown=true;
		}
	}
	

    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            LocalBinder binder = (LocalBinder) service;
            mService = binder.getService();
            mIsServiceBound = true;
            UpdateListView(); //refresh the discovered room view
            ChatSearchScreenFrag.this.mActivity.invalidateOptionsMenu();  //force the menu to be rebuilt the next time it's opened
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
        	mIsServiceBound = false;
        }
    };        
	        
}//end of class
