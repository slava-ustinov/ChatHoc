package com.example.android_final_proj;

import java.util.ArrayList;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.NavUtils;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.Button;
import android.widget.EditText;

/**
 * Displays an ongoing chat activity. Shows incoming messages and allows the users to write and send messages.
 *
 */
public class ChatActivity extends ListActivity
{
	ChatRoomDetails mChatRoomInfo=null;					     //reference to this room's details
	private  ArrayList<ChatMessage> mListContent = null; 	 //the list's content
	private  CustomChatAdapter mListAdapter=null;		     //the list's adapter
	ServiceMsgReceiver mServiceBroadcastReceiver = null;     //reference to a broadcast receiver that listens to broadcasts from our service
	LocalService mService = ChatSearchScreenFrag.mService;   //reference to the service
	Handler mHandler=null;									 //a handler used to receive messages from a 'FileHnadlerThread'
	ProgressDialog historyLoadDialog, PeerConnectDialog;     //progress dialogs
	String mRoomNameAsWrittenInHistory=null;			     //the room's name as it currentlt appears in the history log
	static boolean mIsActive=false;                          //will note if a user can or cannot send messages
	static ArrayList<ChatMessage> mMsgsWaitingForSendResult=null;  //made static so it won't be reset when changing to landscape view
	private boolean isHostedChatRoom = false;				 //indicates whether this is a hosted public room or not
	AlertDialog mDialog=null;								 //an alert dialog holder
	boolean mIsTimedOut=false;
	private final int Handler_WHAT_valueForPeerConnect = 10;
	
	@SuppressLint("HandlerLeak")
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_chat);
		//init all needed variables and parameters:
		
		mService = ChatSearchScreenFrag.mService; //get a reference to the service
		Bundle extras = getIntent().getExtras();
		String ChatRoomID = extras.getString(Constants.HASH_MAP_KEY_SEARCH_FRAG_CHAT_ROOM_UNIQUE); //get the chat room ID from the intent

		if(mChatRoomInfo==null)
			mChatRoomInfo = mService.mDiscoveredChatRoomsHash.get(ChatRoomID);  //get the room's info if it's not a hosted chat room
		
		if(mChatRoomInfo==null) //if the info is still null, it means that this room is a hosted room
		{
			mChatRoomInfo = mService.mActiveChatRooms.get(ChatRoomID).mRoomInfo;  //get the room's info if it's a hosted chat room
			mIsActive=true;
		}
		
		setupActionBar();
		
		if(mMsgsWaitingForSendResult==null)
			mMsgsWaitingForSendResult = new ArrayList<ChatMessage>();
		
		mServiceBroadcastReceiver = new ServiceMsgReceiver(this);   //create a new b-cast receiver for handling service events
		registerReceiver(mServiceBroadcastReceiver, new IntentFilter(Constants.SERVICE_BROADCAST)); //register
		
		InitAdapter();
		
		mHandler = new Handler(){ //define a new message handler for the file thread
			//Here we'll receive the content of the history file that was read by a thread
			@Override
			public void handleMessage(Message msg) {
				//this is a TO on a connection attempt
				if (msg.what==Handler_WHAT_valueForPeerConnect)
				{
					 //if we weren't able to connect yet 
					if (!mIsActive)
					{
						try
						{
						ChatActivity.this.DismissDialog(PeerConnectDialog);
						ChatActivity.this.ShowSingleButtonDialogAndFinishActivity("Peer unresponsive!", 
								"Unable to establish communication with the target peer! closing.");
						}
						catch (Exception e) {
		
						}
					}
				}
				else
				{
					//parse the data and update the list view
					if (msg.getData().getBoolean(Constants.FILE_THREAD_WAS_DATA_READ_KEY, false)) //if a history exists
						ParseHistoryFileDataAndUpdateListView((msg.getData().getString(Constants.FILE_THREAD_DATA_CONTENT_KEY, null)));
					else
						InitHistoryFile(mChatRoomInfo.RoomID,null, mChatRoomInfo.Name, mChatRoomInfo.isPrivateChatRoom, getApplication());       //write 2 required lines at the beginning of the file
					//dismiss the progress dialog
					if (historyLoadDialog.isShowing())
					historyLoadDialog.dismiss();
				}
			}
		};
			
	}//end of onCreate()
	

	/**
	 * Loads history and establishes a connection with the peer
	 */
	@Override
	protected void onResume()
	{
		super.onResume();
		//load the history
		historyLoadDialog = new ProgressDialog(this);
		historyLoadDialog.setTitle("Loading history...");
		historyLoadDialog.setMessage("Please Wait.");
		historyLoadDialog.show();
	
		new FileHandlerThread(mChatRoomInfo.RoomID, mHandler, true,this).start(); //launch the history file reader
		
		mService.isChatActivityActive=true;   //mark that the chat activity is active
		mService.DisplayedAtChatActivity=mChatRoomInfo;  //set the details of the displayed room
		
		 //set the window's title to be the chat's name:
		if (mChatRoomInfo.isPrivateChatRoom)
			setTitle(mChatRoomInfo.Users.get(0).name); 
		else
			setTitle(mChatRoomInfo.Name);
		
		//set icon
		if (mChatRoomInfo.isPrivateChatRoom)
			getActionBar().setIcon(R.drawable.private_chat_icon);
		else
			getActionBar().setIcon(R.drawable.public_chat_icon);
		
		isHostedChatRoom = FindOutIfHostedChatRoom();
		
		if (!isHostedChatRoom) //if this isn't a hosted room
		{
			PeerConnectDialog = new ProgressDialog(this);
			PeerConnectDialog.setTitle("Approving with peer...");
			PeerConnectDialog.setMessage("Please Wait.");
			PeerConnectDialog.show();
			
			//if this is a public chat room, requires a password and inactive:
			if (!mChatRoomInfo.isPrivateChatRoom && mChatRoomInfo.Password!=null && mService.mActiveChatRooms.get(mChatRoomInfo.RoomID)==null)
				ShowPasswordRequestDialogForPublicChat(true); //show the password request dialog
			else
			//Now try and establish a handshake with the peer:
			mService.EstablishChatConnection(mChatRoomInfo.RoomID, null, mChatRoomInfo.isPrivateChatRoom); //try and establish a connection
			mHandler.sendEmptyMessageDelayed(Handler_WHAT_valueForPeerConnect, 3000); //set a TO in 3 seconds from now
		}
		else //this is a hosted public chat room
		{
			mIsActive=true;                        //this room is always active
			registerForContextMenu(getListView()); //register this activity for context menu
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		// Inflate the menu.
		getMenuInflater().inflate(R.menu.chat_prv_room_menu, menu);
		return true;
	}//end of onCreateOptionsMenu()	
	
	
	/**
	 * Used to modify menu item according to the app's state
	 */
	@Override
	public boolean onPrepareOptionsMenu(Menu menu)
	{
		super.onPrepareOptionsMenu(menu);
        menu.clear(); //Clear view of previous menu
        MenuInflater inflater = getMenuInflater();
        //switch between 2 different menus, according to if it's a private chat or not
        if(mChatRoomInfo.isPrivateChatRoom)
        {
            inflater.inflate(R.menu.chat_prv_room_menu, menu);
            if (mService.mBannedFromPrivateChatUsers.containsKey(mChatRoomInfo.RoomID)) //if this user is currently ignored
        	{
	        	//show the unignore option
	        	menu.findItem(R.id.action_unignore_user).setVisible(true);
	        	menu.findItem(R.id.action_ignore_user).setVisible(false);
        	}
            else
        	{
	        	//show the ignore option
	           	menu.findItem(R.id.action_unignore_user).setVisible(false);
	        	menu.findItem(R.id.action_ignore_user).setVisible(true);
        	}
        }
        else //this is a public chat room
            inflater.inflate(R.menu.chat_pub_room_menu, menu);
              
        return true;
       
	}//end of onPrepareOptionsMenu()
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		 super.onOptionsItemSelected(item);
		 switch (item.getItemId())
			{
				case R.id.action_settings://setting was clicked
				{
					startActivity(new Intent(this, QuickPrefsActivity.class));
					break;
				}
				case R.id.action_clear_view: //clear view was clicked
				{
					mListContent.clear();
					mListAdapter.notifyDataSetChanged();
					break;
				}
				case R.id.action_ignore_user: //ignore user was clicked (prv chat)
				{
					mService.mBannedFromPrivateChatUsers.put(mChatRoomInfo.RoomID, "true"); //update the ignore list
					
					//now we want to broadcast an intent that'll force the "search fragment" to refresh it's list view
		    		Intent intent = mService.CreateBroadcastIntent();
		    		intent.putExtra(Constants.SERVICE_BROADCAST_OPCODE_KEY, Constants.SERVICE_BROADCAST_OPCODE_ACTION_CHAT_ROOM_LIST_CHANGED);
		    		 sendBroadcast(intent); //send the intent
		    		 
					finish();  //close the activity
					break;
				}
				case R.id.action_unignore_user: //ignore user was clicked (prv chat)
				{
					mService.mBannedFromPrivateChatUsers.remove(mChatRoomInfo.RoomID); //update the ignore list
					
					//now we want to broadcast an intent that'll force the "search fragment" to refresh it's list view
		    		Intent intent = mService.CreateBroadcastIntent();
		    		intent.putExtra(Constants.SERVICE_BROADCAST_OPCODE_KEY, Constants.SERVICE_BROADCAST_OPCODE_ACTION_CHAT_ROOM_LIST_CHANGED);
		    		sendBroadcast(intent); //send the intent
					break;
				}
				case R.id.action_close_room: //close chat room was clicked
				{
					if (mService.mActiveChatRooms.get(mChatRoomInfo.RoomID).isHostedGroupChat) //trying to close a hosted public chat
						{
						ShowCloseHostedRoomDialog(); //the dialog will call the service and close this room if necessary
						}
					else   //this chat room isn't hosted. send disconnection control message and close
						{
						mService.CloseNotHostedPublicChatRoom(mChatRoomInfo);
						finish();
						}
					break;
				}
				case android.R.id.home:
				{
					   NavUtils.navigateUpFromSameTask(this);
					   break;
				}
			}//switch
		 
		 return true;
	}//end of onOptionsItemSelected()
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,ContextMenuInfo menuInfo)
	{
		super.onCreateContextMenu(menu, v, menuInfo);
	    AdapterContextMenuInfo selectedRow = (AdapterContextMenuInfo) menuInfo; //get the current selected item
	    ChatMessage message = mListContent.get((int)selectedRow.position);
	    if (!message.mIsMine)
	    {
			if (isHostedChatRoom) //we need a context menu only for a hosted chat room
			{
		        MenuInflater inflater = getMenuInflater();
		        inflater.inflate(R.menu.chat_activity_hosted_room_context_menu, menu);		
			}
	    }
	}//end of onCreateContextMenu
	
	@Override
	public boolean onContextItemSelected(MenuItem item)
	{
		 super.onContextItemSelected(item);
			AdapterContextMenuInfo selectedRow = (AdapterContextMenuInfo) item.getMenuInfo(); //get the current selected item
			//get the unique id of the user who's message was selected
			String userUnique  = mListContent.get((int)selectedRow.position).mUserUnique;
			
			switch(item.getItemId()) //switch by the selected operation:
			{
				case R.id.action_kick_user:
				{
					mService.KickOrBanUserFromHostedChat(mChatRoomInfo, userUnique, false);
					return true;
				}
				case R.id.action_ban_user:
				{
					mService.KickOrBanUserFromHostedChat(mChatRoomInfo, userUnique, true);
					return true;
				}
			}
		 
		 return true;
	}//end of onContextItemSelected()
	
	@Override
	public boolean onMenuItemSelected(int featureId, MenuItem item)
	{
	  super.onMenuItemSelected(featureId, item);
		 switch (item.getItemId())
		{
			case R.id.action_clear_view://clear list view was clicked
			{
				mListContent.clear();
				mListAdapter.notifyDataSetChanged();
				break;
			}
			case R.id.action_clear_banned_users://clear banned users was clicked
			{
				mService.ClearBannedUsersListInPublicRoom(mChatRoomInfo);
				break;
			}
			case R.id.action_close_room://close room was clicked
			{
				if (isHostedChatRoom)
				{
					ShowCloseHostedRoomDialog();
				}
				else
				{
					mService.CloseNotHostedPublicChatRoom(mChatRoomInfo);
				}
				break;
			}
		}
		 
		 return true;
	}//end of onMenuItemSelected()
	
	/**
	 * Shows an alert dialog when a user tries to close a hosted chat room
	 */
	private void ShowCloseHostedRoomDialog()
	{
		new AlertDialog.Builder(this)
	    .setTitle("Closing hosted public chat")
	    .setIcon(R.drawable.alert_icon)
	    .setMessage("You are the host of this chat room. Closing it will disconnect all participating peers. Close anyway?")
	
	    //yes button setter
	    .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
	        public void onClick(DialogInterface dialog, int which) { 
	    		
			mService.CloseHostedPublicChatRoom(mChatRoomInfo);
			finish();
	        
	        }//onClick-Yes
	     })//setPositive
	
	     //no button setter
	     .setNegativeButton("No", new DialogInterface.OnClickListener() {
	        public void onClick(DialogInterface dialog, int which) { 
	        		//do nothing.
	        }//onClick-No
	     })//setNegative
	     .show();

	}//end of ShowCloseHostedRoomDialog()
	
	/**
	 * Shows an alert dialog when a we are being ignored by the target peer
	 */
	private void ShowPeerIsIgnoringDialog()
	{
		new AlertDialog.Builder(this)
	    .setTitle("Unable start chat")
	    .setIcon(R.drawable.alert_icon)
	    .setMessage("You are blocked by the hosting peer!")
	
	    //yes button setter
	    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
	        public void onClick(DialogInterface dialog, int which) { 
	    		
			finish();  //close the activity
	        
	        }//onClick-Yes
	     })//setPositive
	     .setOnCancelListener(new OnCancelListener()
		{
			
			@Override
			public void onCancel(DialogInterface dialog)
			{
				finish();
				
			}
		})
	     .show();

	}//end of ShowPeerIsIgnoringDialog()
	
	
	/**
	 * Shows an alert dialog when this room is no longer available
	 */
	private void ShowRoomHasTimedOutDialog()
	{
		new AlertDialog.Builder(this)
	    .setTitle("Chat has timed out!")
	    .setIcon(R.drawable.alert_icon)
	    .setMessage("Lost connection with peer. This chat is no longer available.")
	
	    //yes button setter
	    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
	        public void onClick(DialogInterface dialog, int which) {       
	        }//onClick-Yes
	     })//setPositive
	     .show();

	}//end of ()
	
	
	/**
	 * Shows an alert dialog that'll finish this activity no matter what
	 * @param title - the dialog's desired title 
	 * @param message - the dialog' desired message
	 */
	private void ShowSingleButtonDialogAndFinishActivity(String title, String message)
	{
		new AlertDialog.Builder(this)
	    .setTitle(title)
	    .setIcon(R.drawable.alert_icon)
	    .setMessage(message)
	
	    //yes button setter
	    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
	        public void onClick(DialogInterface dialog, int which) { 
	    		
			finish();  //close the activity
	        
	        }//onClick-Yes
	     })//setPositive
	     .setOnCancelListener(new OnCancelListener()
		{
			
			@Override
			public void onCancel(DialogInterface dialog)
			{
				finish();
				
			}
		})
	     .show();

	}//end of ShowChatRoomWasClosedDialog()
	
	/**
	 * Initializes a text file with the first 2 mandatory lines needed for a valid history file
	 * @param unique - that chat room's unique number
	 * @param handler - this handler will be notified with an empty message when the writing is complete
	 * @param name - the chat room's name
	 * @param isPrivate - indicating whether this room is private or not
	 * @param con - a context from this application
	 */
	public static void InitHistoryFile (String unique,Handler handler, String name, boolean isPrivate, Context con)
	{
		StringBuilder data = new StringBuilder();
		data.append(name+"\r\n");
		if (isPrivate)
			data.append("private"+"\r\n");
		else
			data.append("public"+"\r\n");
		
		FileHandlerThread fh = new FileHandlerThread(unique, handler, false, con); //create a new file handler
		fh.UpdateDataToWriteBuffer(data.toString());  //set the data to write
		fh.start(); //run the thread
		fh.Kill();  //exit the thread gracefully
		
	}//end of InitHistoryFile()
	
	/**
	 * Since our history file is read as a single string, this function parses it into messages and displays them
	 * in the list view
	 * @param data - the text as it was read from the history file
	 */
	private void ParseHistoryFileDataAndUpdateListView(String data)
	{
		String[] parsedHistory = data.split("["+Constants.STANDART_FIELD_SEPERATOR+"]"); //parse the string by the separator char
		int length = parsedHistory.length;
		
		//get the room's name as it's written in the history file:
		mRoomNameAsWrittenInHistory = parsedHistory[0]; 
		
		for (int i=2; i<length ;i++) //for each msg string
		{
			String[] parsedSingleMsg = parsedHistory[i].split("["+Constants.CHAT_MSG_ENTRY_SEPARATOR_CHAR+"]"); //parse by the inner separator
     		ChatMessage msg = new ChatMessage(parsedSingleMsg[1], parsedSingleMsg[2].replace(Constants.ENTER_REPLACEMENT_CHAR, '\n')
     				, parsedSingleMsg[0], parsedSingleMsg[3], 
     				parsedSingleMsg[1].equalsIgnoreCase(MainScreenActivity.UniqueID));
     		mListContent.add(msg);
		}//for
		
		mListAdapter.notifyDataSetChanged();
		getListView().setSelection(mListContent.size()-1);
			
	}//end of ParseHistoryFileDataAndUpdateListView()
	
	/**
	 * This class is the broadcast receiver for all broadcasts coming from the service
	 */
	private class ServiceMsgReceiver extends BroadcastReceiver 
	{
		Activity mActivity = null;
		
		public ServiceMsgReceiver(Activity act)
		{
			super();
			mActivity=act;
		}
		
	@Override
	   public void onReceive(Context context, Intent intent) 
	   {    
	       String action = intent.getAction();
	       if(action.equalsIgnoreCase(Constants.SERVICE_BROADCAST))
	       {    
	    	   Bundle extras = intent.getExtras(); //get extras
	    	   String msgDst = extras.getString(Constants.SINGLE_SEND_THREAD_KEY_UNIQUE_ROOM_ID);
	    	   //if the broadcast isn't targeted for this room:
	    	   if (msgDst==null || !msgDst.equalsIgnoreCase(ChatActivity.this.mChatRoomInfo.RoomID))
	    		   return;
	    		   
	          int opcode =extras.getInt(Constants.SERVICE_BROADCAST_OPCODE_KEY); //get the opcode
			  switch (opcode) 
				{
			  		//a result for a send attempt is received (simply indicating if the message was sent via socket or the socket has crashed): 
					case Constants.SERVICE_BROADCAST_OPCODE_JOIN_SENDING_RESULT:
						{
							//if the send has failed, no connection could be established. notify and block the user
							if (extras.getString(Constants.SINGLE_SEND_THREAD_KEY_RESULT).equals(Constants.SINGLE_SEND_THREAD_ACTION_RESULT_FAILED))
							{
								DismissDialog(PeerConnectDialog);
								Constants.showBubble("SEND FAILED! SOCK CRASHED!",mActivity);
								
								if (mIsActive) //if a send failed after this chat is active, it means that a message has failed to be sent
								{
									if (!mMsgsWaitingForSendResult.isEmpty())
										mMsgsWaitingForSendResult.remove(0);   //remove from the msg stack
								}
				
								
								if (mChatRoomInfo.Password!=null && !mIsActive) //if we've failed to send a join request to a pw protected chat room
								{
									ShowSingleButtonDialogAndFinishActivity("Unable to establish connection!", 
											"Failed while trying to contact the host.");
								}
							}
							else //A positive physical send result
							{
								if (mMsgsWaitingForSendResult.size()!=0 && !isHostedChatRoom) //if we've successfully sent a message
								{
									ChatMessage msg = mMsgsWaitingForSendResult.get(0);  //get the msg
									
									AddNewMessage(msg);  //add to list view
									if (!mMsgsWaitingForSendResult.isEmpty())
										mMsgsWaitingForSendResult.remove(0);   //remove from the msg stack
									
									ActiveChatRoom room = mService.mActiveChatRooms.get(mChatRoomInfo.RoomID); //get the active chat room
									WriteSelfMessageToHistoryFile(msg.mMessage.replace('\n',Constants.ENTER_REPLACEMENT_CHAR), room); //write this message to the file
								}//if
							}
						break;
						}
					//a result for a connection request has come, or a denial reply was received
					case Constants.SERVICE_BROADCAST_OPCODE_JOIN_REPLY_RESULT:
						{
							//if the connection was denied:
							if (extras.getString(Constants.SINGLE_SEND_THREAD_KEY_RESULT).equals(Constants.SINGLE_SEND_THREAD_ACTION_RESULT_FAILED))
							{
								DismissDialog(PeerConnectDialog);
								mIsActive=false;
								if (extras.getString(Constants.SINGLE_SEND_THREAD_KEY_REASON).equalsIgnoreCase(Constants.SERVICE_NEGATIVE_REPLY_FOR_JOIN_REQUEST_REASON_KICKED))
								{
									mIsActive=false;
									mService.RemoveActiveRoomOnKickOrBan(mChatRoomInfo);
									ShowSingleButtonDialogAndFinishActivity("Kicked out!", 
											"The host has kicked you out of the chat room!");
								}
								//if the peer has banned us:
								if (extras.getString(Constants.SINGLE_SEND_THREAD_KEY_REASON).equalsIgnoreCase(Constants.SERVICE_NEGATIVE_REPLY_FOR_JOIN_REQUEST_REASON_BANNED))
								{	
									mIsActive=false;
									mService.RemoveActiveRoomOnKickOrBan(mChatRoomInfo);
									ShowPeerIsIgnoringDialog(); //show dialog and close the activity
								}
								//
								if (extras.getString(Constants.SINGLE_SEND_THREAD_KEY_REASON).equalsIgnoreCase(Constants.SERVICE_NEGATIVE_REPLY_FOR_JOIN_REQUEST_REASON_WRONG_PW))
								{
									mIsActive = false;
									PeerConnectDialog.show();
									ShowPasswordRequestDialogForPublicChat(false);
								}
								if (extras.getString(Constants.SINGLE_SEND_THREAD_KEY_REASON).equalsIgnoreCase(Constants.SERVICE_NEGATIVE_REPLY_FOR_JOIN_REQUEST_REASON_NON_EXITISING_ROOM))
								{
									ShowSingleButtonDialogAndFinishActivity("Invalid Room",
											"The requested room no longer exists!");
									mService.RemoveFromDiscoveredChatRooms(msgDst); //msgDst is the room's unique
								}
								if (extras.getString(Constants.SINGLE_SEND_THREAD_KEY_REASON).equalsIgnoreCase(Constants.SERVICE_NEGATIVE_REPLY_REASON_ROOM_CLOSED))
								{
									ShowSingleButtonDialogAndFinishActivity("Room was closed!",
											"The hosting peer has closed the chat room!");
									mService.RemoveFromDiscoveredChatRooms(msgDst); //msgDst is the room's unique
								}

							}
						    //if the connection was approved:
							if (extras.getString(Constants.SINGLE_SEND_THREAD_KEY_RESULT).equals(Constants.SINGLE_SEND_THREAD_ACTION_RESULT_SUCCESS))
							{
								DismissDialog(PeerConnectDialog);
								mIsActive=true;
						//		Constants.showBubble("JOIN SUCCEEDED! PEER HAS ACCEPTED", mActivity);
							}
						    //if the connection already exists
							if (extras.getString(Constants.SINGLE_SEND_THREAD_KEY_RESULT).equals(Constants.SINGLE_SEND_THREAD_ACTION_RESULT_ALREADY_CONNECTED))
							{
								DismissDialog(PeerConnectDialog);
								mIsActive=true;
						//		Constants.showBubble("CHAT ROOM IS ALREADY CONNECTED!", mActivity);
							}
						break;
						}
						
					case Constants.CONNECTION_CODE_NEW_CHAT_MSG: //a new chat message has arrived
						{
							String[] content = extras.getStringArray(Constants.SERVICE_BROADCAST_MSG_CONTENT_KEY);
							//create a ChatMessage from the received socket-string
							ChatMessage msg = new ChatMessage(content[2], 
									content[4].replace(Constants.ENTER_REPLACEMENT_CHAR,'\n'),  //convert the 'enter's back
									content[1], Constants.getTimeString(), false);
							AddNewMessage(msg); //add to the list view
							
							if (ChatActivity.this.mChatRoomInfo.isPrivateChatRoom) //if this is a private chat room
							{
								//update the title with the peer's currant name
								ChatActivity.this.setTitle(ChatActivity.this.mChatRoomInfo.Users.get(0).name);
							}
						break;
						}
						
					case Constants.SERVICE_BROADCAST_OPCODE_ROOM_TIMED_OUT: //the displayed room has timed out
					{
			
					if (mIsTimedOut==false)
					{
						mIsTimedOut=true;
						ShowRoomHasTimedOutDialog();
						DisableButtonAndEditText();
						mIsActive=false;
						break;
					}
					}
						
				}//switch   
	       }//if
	   }
	}//end of ServiceMsgReceiver.class
	
	private void DisableButtonAndEditText()
	{
		EditText text = (EditText) findViewById(R.id.MsgText);
		text.setText("");  //clear the user's edit-text
		text.setEnabled(false);  //disable the edit-text
		
		Button button = (Button) findViewById(R.id.chat_activity_send_button);
		button.setEnabled(false);  //disable the button
	}//end of DisableButtonAndEditText()
	
	/**
	 * Updates the history file with message that was sent by this user
	 * @param msg - the msg text
	 * @param room - the ActiveChatRoom that this message is targeted to
	 */
	private void WriteSelfMessageToHistoryFile(String msg, ActiveChatRoom room)
	{
		String[] temp; 
		
		if (room!=null)
		{
		    temp = new String[4];
			temp[0] = MainScreenActivity.UserName;
			temp[1] = MainScreenActivity.UniqueID;
			temp[2] = msg;
			temp[3] = Constants.getTimeString(); 
			
			room.UpdateFileWithNewMsg( Constants.StringArrayToStringWithSeperators(temp, Constants.CHAT_MSG_ENTRY_SEPARATOR_CHAR));
		}//if
	}
	
	/**
	 * Dismisses a dialog, if it's shown.
	 * @param d - dialog
	 */
	private void DismissDialog(Dialog d)
	{
		if (d!=null && d.isShowing())
			d.dismiss();
	}
	
	/**
	 * Initializes the content, list adapter and performs a peer scan for the the 1st run only.
	 */
	protected void InitAdapter()
	{
	   if (mListContent==null){mListContent = new ArrayList<ChatMessage>();} //create a new array list that'll hold all the data
	   if (mListAdapter==null)
	   		{
		    mListAdapter = new CustomChatAdapter(this,mListContent);  //create a new adapter		    
			setListAdapter(mListAdapter);   						//set the content
		   	}
	}//end of InitAdapter()
	
	@Override
	protected void onPause()
	{
		super.onPause();
		mService.DisplayedAtChatActivity=null;
		
		if (mIsTimedOut)
			mService.RemoveSingleTimedOutRoom(mChatRoomInfo, false); //remove this room from the service
		
		mService.isChatActivityActive=false; //mark that this activity is no longer active
		mChatRoomInfo.hasNewMsg=false;  //lower the messages flag
		//if this is a private chat room and the peer has changed his name:
		if (mChatRoomInfo.isPrivateChatRoom &&  !mChatRoomInfo.Users.get(0).name.equalsIgnoreCase(mRoomNameAsWrittenInHistory))
		{
			mService.UpdatePrivateChatRoomNameInHistoryFile(mChatRoomInfo);
		}
	}//end of onPause()
	
	@Override
	protected void onDestroy()
	{
		super.onDestroy();
		if (mServiceBroadcastReceiver!=null) //unregister receiver
		unregisterReceiver(mServiceBroadcastReceiver);
	}//end of onDestroy()
	
	/**
	 * Called when the user presses the 'send' button, meaning he wants to send a text message
	 * @param v - the view
	 */
	public void OnSendButtonClicked(View v)
	{
		EditText text = (EditText) findViewById(R.id.MsgText);
		String newMessage = text.getText().toString().trim();  //get the user's text msg
		if(newMessage.length() > 0) //if this is a valid message
		{
			text.setText("");  //clear the user's edit-text
			ChatMessage m = new ChatMessage(MainScreenActivity.UniqueID,newMessage,MainScreenActivity.UserName,
					Constants.getTimeString(),true);
			if (!isHostedChatRoom) //if this isn't a hosted public chat
			{
				//add the msg to the head of the waiting for result list
				if(mMsgsWaitingForSendResult.size()!=0)
					mMsgsWaitingForSendResult.add(0,m);
				else
					mMsgsWaitingForSendResult.add(m);
			}
			else //this is a hosted public chat. We don't queue up messages. We assume that they're always successfully sent
			{
				AddNewMessage(m);  //add to list view
				ActiveChatRoom room = mService.mActiveChatRooms.get(mChatRoomInfo.RoomID); //get the active chat room
				//replace all '\n' chars and write this message to the file
				WriteSelfMessageToHistoryFile(m.mMessage.replace('\n', Constants.ENTER_REPLACEMENT_CHAR), room); 
			}
			//replace all 'enter's in the text with our special char, since 'enter's will fuck our logic up.
			newMessage = newMessage.replace('\n',Constants.ENTER_REPLACEMENT_CHAR); 
			
			mService.SendMessage(newMessage, mChatRoomInfo.RoomID); //call the service to send the message to the peer
		}//if
	}//end of OnSendButtonClicked()
	
	/**
	 * Checks if this chat room is a hosted public chat room
	 * @return true if so, false otherwise
	 */
	private boolean FindOutIfHostedChatRoom()
	{
		ActiveChatRoom room = mService.mActiveChatRooms.get(mChatRoomInfo.RoomID);
		
		return (room!=null && room.isHostedGroupChat);
	}//end of FindOutIfHostedChatRoom()
	
	/**
	 * Adds a new message to the list and scrolls the display downwards
	 * @param m - the message to be added to the list view
	 */
	private void AddNewMessage(ChatMessage m)
	{
		mListContent.add(m);      				//add the new message
		mListAdapter.notifyDataSetChanged();   //update the list view	
		getListView().setSelection(mListContent.size()-1); //scroll down so that the new message will be visible
	}//end of AddNewMessage()
	
	/**
	 * Shows a password request dialog.
	 * If the user has added a password, tries to establish connection with  the target room
	 */
	private void ShowPasswordRequestDialogForPublicChat(boolean isFirstTry)
	{
        // adding custom layout to an AlertDialog
        LayoutInflater factory = LayoutInflater.from(this);
        final View textEntryView = factory.inflate(R.layout.alert_dialog_text_entry, null);
        mDialog = new AlertDialog.Builder(this)
            .setIconAttribute(android.R.attr.alertDialogIcon)
            .setTitle(isFirstTry? "This room requires a password" : "Wrong password! try again")
            .setView(textEntryView)
            .setIcon(R.drawable.key_icon)
            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {

                EditText ed = (EditText) mDialog.findViewById(R.id.password_edit);
                String pw = ed.getText().toString();  //get the pw
                mService.EstablishChatConnection(mChatRoomInfo.RoomID, pw, mChatRoomInfo.isPrivateChatRoom); //try and establish a connection
                }
            })
            .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {

                 /* User clicked cancel so do some stuff */
                	ChatActivity.this.finish();  //the user chose not to enter a password. close the activity
                }
            })
            .setOnCancelListener(new OnCancelListener()
    		{
    			
    			@Override
    			public void onCancel(DialogInterface dialog)
    			{
    				ChatActivity.this.finish();  //the user chose not to enter a password. close the activity
    			}
    		})
            .create();
        mDialog.show();
    }//end of ShowPasswordRequestDialog()
	
	 private void setupActionBar()
	 {
	  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
	  {
	   // enables the activity icon as a 'home' button. required if "android:targetSdkVersion" > 14
	   getActionBar().setHomeButtonEnabled(true);
	   getActionBar().setDisplayHomeAsUpEnabled(true);
	  }
	 }
	
}//end of class
