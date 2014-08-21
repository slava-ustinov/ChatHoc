package com.example.android_final_proj;


import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;

import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.app.TaskStackBuilder;

/**
 * Our service is in charge of managing all the chat rooms and conversations. It'll be started with
 * startService() so that it'll stay up even if the app is no longer active, so that new messaged will still be received
 * and handled.
 * Information relay from the service to the activities is done with intent broadcasts.
 */
@SuppressLint("HandlerLeak")
public class LocalService extends Service
{	
    // Binder given to clients
    private final IBinder mBinder = new LocalBinder();
	WelcomeSocketThread mWelcomeSocketThread = null;  //the welcome socket thread
    Handler mSocketSendResultHandler=null;  		  //used to receive the result of message sending operations
    Handler mRefreshHandler=null;					  //acts a timer to count time between refresh operations
	WiFiDirectBroadcastReceiver mWifiP2PReceiver=null; //b-cast receiver
	IntentFilter mIntentFilter; 					  //an intent filter for the b-cast receiver
	WifiP2pDevice[] mDevices;						  //used to discover new peers
	HashMap<String, ActiveChatRoom> mActiveChatRooms = null;        //all the chat rooms in which this peer is active in
	HashMap<String, ChatRoomDetails> mDiscoveredChatRoomsHash=null; //all discovered chat rooms
	ArrayList<User> mDiscoveredUsers = null;						//all discovered peers 
	HashMap<String, String> mBannedFromPrivateChatUsers = null;	    //peers that are banned form private chat with us
	
	public boolean mIsWifiGroupOwner=false;             //indicates that this device is the group owner, meaning he's the one
														//that always knows all the connected peers
	
	//these variables are maintained to handle the notification logic
	public static NotificationManager mNotificationManager=null;
	public boolean isChatActivityActive = false;
	public ChatRoomDetails DisplayedAtChatActivity = null;
	
	//used to check if the wifi peer we've connected to runs our app.
	private boolean mIsWifiPeerValid=false;    
	public final int Handler_WHAT_valueForActivePeerTO = 10;
	
	@Override
    public void onCreate() 
    {
    	super.onCreate();
    	InitClassMembers();
      	 //create a message handler that'll work with the "SendSingleStringViaSocketThread" threads
    	if (mSocketSendResultHandler==null)
	      	mSocketSendResultHandler = new Handler() 
	      	{
	      		@Override
	      		public void handleMessage(Message msg) 
	      		{
	      			Bundle data = msg.getData();
	      			String result = data.getString(Constants.SINGLE_SEND_THREAD_KEY_RESULT);
	      			String RoomID = data.getString(Constants.SINGLE_SEND_THREAD_KEY_UNIQUE_ROOM_ID);
	      			
	      			HandleSendAttemptAndBroadcastResult(result,RoomID);
	      		}
	      	};
      	
      	//create a handler to send delayed messages to itself and perform a peer refresh operation.
	    //also, it checks if our current wifi p2p peer is active (has our app and runs properly)
      	if (mRefreshHandler==null)
      	{
	      	mRefreshHandler = new Handler() 
	      	{
	      		@Override
	      		public void handleMessage(Message msg) 
	      		{
	      			//if this is a wifi peer validation TO
		      		if (msg.what==Handler_WHAT_valueForActivePeerTO)
		      		{
		      			if (!mIsWifiPeerValid)  //if we haven't received any valid communication from this peer we're connected to
		      			{
		      				ChatSearchScreenFrag.mIsConnectedToGroup=false; //mark that this connection is invalid
		      				ChatSearchScreenFrag.mManager.removeGroup(ChatSearchScreenFrag.mChannel, null); //leave the current group
		      			}
		      		}
		      		else //it's a TO telling us to send discovery messages to all peers
		      		{
		      			if (ChatSearchScreenFrag.mIsWifiDirectEnabled)
		      				LocalService.this.OnRefreshButtonclicked();  //perform a peer refresh	
		      			DeleteTimedOutRooms();  //delete TO'd rooms if necessary
		      			//send a delayed empty message to ourselves
		      			sendEmptyMessageDelayed(0, MainScreenActivity.RefreshPeriodInMs);
		      		}
	      		}
	      	};
	        //send the 1st message to trigger the logical peer-discovery refresh procedure
	      	mRefreshHandler.sendEmptyMessageDelayed(0, 500);
      	}//if
      	
    }//end of onCreate()

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
    	int superReturnedVal = super.onStartCommand(intent, flags, startId);
    	
    	if (mWelcomeSocketThread==null) // if a welcome socket thread wasn't created yet
    	{
    		mWelcomeSocketThread = new WelcomeSocketThread(this); //create a new welcome socket thread
    		mWelcomeSocketThread.start(); //launch the thread
    	}
    	
    	return superReturnedVal;
    }//end of onStartCommand()

    @Override
    public IBinder onBind(Intent intent) 
    {
    	if (mWifiP2PReceiver==null) //if there's no active receiver
    	{
    	    UpdateIntentFilter(); //update mIntentFilter (which is used for the wifi b-cast receiver) that'll be used in onResume()
			//create a new wifip2p b-cast receiver. Registered at onResume()
		    mWifiP2PReceiver = new WiFiDirectBroadcastReceiver(ChatSearchScreenFrag.mManager, ChatSearchScreenFrag.mChannel, this); 
		    getApplication().registerReceiver(mWifiP2PReceiver, mIntentFilter); 	/* register the broadcast receiver with the intent values to be matched */
    	}
        return mBinder;
    }//end of onBind()
    
    
   /**
    * Creates and broadcasts an intent after occurrence of a wifi event
    * @param wifiEventCode - taken from Constants.class
    * @param failReasonCode - as given by the system
    */
   public void CreateAndBroadcastWifiP2pEvent(int wifiEventCode, int failReasonCode)
   {
	   Intent intent = CreateBroadcastIntent();
	   intent.putExtra(Constants.SERVICE_BROADCAST_OPCODE_KEY, Constants.SERVICE_BROADCAST_OPCODE_ACTION_WIFI_EVENT_VALUE);
	   intent.putExtra(Constants.SERVICE_BROADCAST_WIFI_EVENT_KEY, wifiEventCode);
	   intent.putExtra(Constants.SERVICE_BROADCAST_WIFI_EVENT_FAIL_REASON_KEY, failReasonCode);
	   sendBroadcast(intent); //broadcast this intent
   }//end of CreateAndBroadcastWifiP2pEvent()
   
   /**
    * Starts a chat session. An active chat room will be created created according to the reply
    * Protocol: Firstly we only send a 'Join' request message and wait for a reply.
    * We b-cast the result of the physical sending operation so it'll be handled by the activity.
    * WE STILL HAVE TO WAIT ON AN ANSWER FROM THE PEER TO KNOW IF OUR REQUEST IS APPROVED OR NOT
    * @param RoomUniqueID - the chat's unique id value
    */
   public void EstablishChatConnection(String roomUniqueID, String password, boolean isPrivateChat)
   {
	   
	   if (mActiveChatRooms.get(roomUniqueID)==null) //if this chat doesn't exist already (We haven't registered with the other peer)
	   {
		   String msg=null;
		   //now we'll create a join message
		   if (isPrivateChat)
			   msg = ConstructJoinMessage(Constants.CONNECTION_CODE_PRIVATE_CHAT_REQUEST, null, null); //create a connection msg for a private chat
		   else
			   msg = ConstructJoinMessage(Constants.CONNECTION_CODE_JOIN_ROOM_REQUEST, roomUniqueID, password); //create a connection msg for a public chat room
		   
		   String peerIP = mDiscoveredChatRoomsHash.get(roomUniqueID).Users.get(0).IPaddr; //get the target's IP
		   new SendSingleStringViaSocketThread(mSocketSendResultHandler, peerIP, msg, roomUniqueID).start(); //start the thread
	   }//if
	   else //an active room already exists
	   {
		   Intent intent = CreateBroadcastIntent();
		   intent.putExtra(Constants.SERVICE_BROADCAST_OPCODE_KEY, Constants.SERVICE_BROADCAST_OPCODE_JOIN_REPLY_RESULT); //the opcode is connection result event
		   //mark that an active room already exists:
		   intent.putExtra(Constants.SINGLE_SEND_THREAD_KEY_RESULT, Constants.SINGLE_SEND_THREAD_ACTION_RESULT_ALREADY_CONNECTED); 
		   intent.putExtra(Constants.SINGLE_SEND_THREAD_KEY_UNIQUE_ROOM_ID, roomUniqueID); //set the room's ID
		   sendBroadcast(intent);
	   }
   }//end of EstablishChatConnection()
   
   public void OnWelcomeSocketCreateError()
   {
	   Intent intent = CreateBroadcastIntent();
	   intent.putExtra(Constants.SERVICE_BROADCAST_OPCODE_KEY,Constants.SERVICE_BROADCAST_WELCOME_SOCKET_CREATE_FAIL);
   }//end of OnWelcomeSocketCreateError()
   
   /**
    * Called by a worker thread when a new chat message arrives.
    * Arriving messages are checked for source and destination.
    * @param msg - the message as was received via socket
    * @param peerIP - the sender's IP address
    */
   public void OnNewChatMessageArrvial (String[] msg, String peerIP)
   {
	   boolean isPrivateMsg=false;
	   ActiveChatRoom targetRoom=null;
	   if (msg[3].equalsIgnoreCase(MainScreenActivity.UniqueID)) //if the target room is our unique, then it's a private chat message
	   {
		   msg[3] = msg[2];						//change the roomID to be the sender's unique
		   isPrivateMsg=true;
	   }
	
	   targetRoom = mActiveChatRooms.get(msg[3]);

	   //if the chat room exists (Note: if it's a private chat and the user is banned, he's been denied of connection and the room won't exist)
	   if (targetRoom!=null)
	   {
		   targetRoom.ForwardMessage(msg,false);  //Let the room handle this message
		   
		   //update time stamps:
		   if (!targetRoom.isHostedGroupChat)  //we don't want to update time stamps for chat rooms that we host
			   UpdateTimeChatRoomLastSeenTimeStamp(msg[2], msg[3]); //update the time stamps
	   }
	   else //this is the case where a message came for an inactive chat room. 
	   {
		   BypassDiscoveryProcedure(msg,true,isPrivateMsg);   
	   }
	   
	 //if we want to notify the user on new message arrival and the msg came for a chat which not currently displayed
	   if (MainScreenActivity.isToNotifyOnNewMsg && 
			   (DisplayedAtChatActivity==null || !msg[3].equalsIgnoreCase(DisplayedAtChatActivity.RoomID))) 
	   {

		// Creates an explicit intent for an Activity in your app
		   Intent resultIntent = new Intent(this, MainScreenActivity.class);

		   // The stack builder object will contain an artificial back stack for the
		   // started Activity.
		   // This ensures that navigating backward from the Activity leads out of
		   // your application to the Home screen.
		   TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
		   // Adds the back stack for the Intent (but not the Intent itself)
		   stackBuilder.addParentStack(MainScreenActivity.class);
		   // Adds the Intent that starts the Activity to the top of the stack
		   stackBuilder.addNextIntent(resultIntent);
		   PendingIntent resultPendingIntent =
		           stackBuilder.getPendingIntent(
		               0,
		               PendingIntent.FLAG_UPDATE_CURRENT
		           );
		   
		   String notificationString = CreateNewMsgNotificationString();
		   if (notificationString!=null)
			   Constants.ShowNotification(notificationString, resultPendingIntent); //show notification
	   }
	   
	   //broadcast an event that'll tell the search fragment to refresh it's list view
	   BroadcastRoomsUpdatedEvent();
	   
   }//end of OnNewChatMessageArrvial()
   
   private void UpdateTimeChatRoomLastSeenTimeStamp(String peerUnique, String roomUnique)
   {
	   Date currentTime = Constants.GetTime();
	   ChatRoomDetails ChatDetails = mDiscoveredChatRoomsHash.get(peerUnique);
	   
	   if (ChatDetails!=null)
		   ChatDetails.LastSeen=currentTime; //update the last seen time stamp
	   
	   //if this msg is for a public chat room
	   if (!peerUnique.equalsIgnoreCase(roomUnique))
	   {
		   ChatDetails = mDiscoveredChatRoomsHash.get(roomUnique);
		   if (ChatDetails!=null)
			   ChatDetails.LastSeen=currentTime; //update the last seen time stamp
	   }
   }//end of UpdateTimeChatRoomLastSeenTimeStamp()
   
   /**
    * Called when an undiscovered peer sends us a private message, meaning that a proper discovery procedure never happened.
    * @param msg - the msg as it was received via socket
    * @param peerIP - the peer's IP address
    * @param isChatMsg - indicating what kind of message it is. Since we haven't discovered the sending peer properly,
    * this can be a 'chat request message' or a 'chat message'
    */
   public void BypassDiscoveryProcedure (String[] msg, boolean isChatMsg, boolean isPrivateChat)
   {
	   if (isPrivateChat)
	   {
		   //Now we want to create a newly discovered private chat room
			ArrayList<ChatRoomDetails> ChatRooms = new ArrayList<ChatRoomDetails>();
			User host = Constants.CheckIfUserExistsInListByUniqueID(msg[2], mDiscoveredUsers);
			
			//Create an array list to hold this single user
			ArrayList<User> user = new ArrayList<User>(); //a list with a single peer
			user.add(host);
			
			ChatRoomDetails PrivateChatRoom = new ChatRoomDetails(host.uniqueID, host.name, Constants.GetTime(), user,true); //create a new private chat room detail 
			ChatRooms.add(PrivateChatRoom);  //add to the chat room list
			//add a newly discovered chat room
			UpdateChatRoomHashMap(ChatRooms);
			//create an active chat room
			CreateNewPrivateChatRoom(msg);
			//get the active chat room:
			ActiveChatRoom targetRoom = mActiveChatRooms.get(msg[2]);
			
			if (isChatMsg)
				targetRoom.ForwardMessage(msg,false);
	   }//private room
	   else //this message came for a public chat room
	   {
		   ChatRoomDetails details = mDiscoveredChatRoomsHash.get(msg[3]); //get the room's details
		   if (details==null) //this public chat rooms, that we're seemingly a part of, wasn't discovered yet
		   {
			   //we'de like to ignore the current message and restart a discovery procedure
			   OnRefreshButtonclicked();
		   }
		   else //we have the full details of this room. we'de like to create a new active room:
		   {
			   ActiveChatRoom activeRoom = new ActiveChatRoom(this, false, details);  //create a new active chat room
				//init a history file if necessary:
			   InitHistoryFileIfNecessary(details.RoomID,details.Name, false);
			   mActiveChatRooms.put(details.RoomID, activeRoom); //add to the hash map
			   //b-cast an event that'll cause the chat-search-frag to refresh the list view
			   BroadcastRoomsUpdatedEvent();
		   }
	   }//public room
   }//end of BypassDiscoveryProcedure()
   
   /**
    * Sends a message that was created by this user. Bypasses the 'ActiveChatRoom's sending method.
    * @param msg - the text msg as it was typed by the user
    * @param chatRoomUnique
    */
   public void SendMessage(String msg, String chatRoomUnique)
   {
	   String toSend = ConvertMsgTextToSocketStringFormat(msg,chatRoomUnique);
	   
	   ActiveChatRoom activeRoom = mActiveChatRooms.get(chatRoomUnique);
	   
	   if (activeRoom!=null && activeRoom.isHostedGroupChat )  //if this is a hosted chat room
	   {
		   activeRoom.ForwardMessage(toSend.split("["+Constants.STANDART_FIELD_SEPERATOR+"]"),true);
	   }
	   else
	   {
		   String peerIP = mDiscoveredChatRoomsHash.get(chatRoomUnique).Users.get(0).IPaddr; //get the target's IP
		   //create a new sender thread:
		    new SendSingleStringViaSocketThread(mSocketSendResultHandler, peerIP, toSend, chatRoomUnique).start();
	   }
   }//end of SendMessage()
   
   /**
    * Converts a text message to a format that can be passed via socket to a peer
    * @param msg - the text message
    * @param chatRoomUnique - the target chat room's unique ID
    * @return the converted string
    */
   public String ConvertMsgTextToSocketStringFormat(String msg, String chatRoomUnique)
   {
	   StringBuilder toSend = new StringBuilder();
	   toSend.append(Integer.toString(Constants.CONNECTION_CODE_NEW_CHAT_MSG)+Constants.STANDART_FIELD_SEPERATOR); //add opcode
	   toSend.append(MainScreenActivity.UserName+Constants.STANDART_FIELD_SEPERATOR);   //add self name		
	   toSend.append(MainScreenActivity.UniqueID+Constants.STANDART_FIELD_SEPERATOR);	//add unique 
	   toSend.append(chatRoomUnique+Constants.STANDART_FIELD_SEPERATOR);				//add chat room ID	
	   toSend.append(msg+Constants.STANDART_FIELD_SEPERATOR);							//add msg
	   return toSend.toString();  
   }//end of ConvertMsgTextToSocketStringFormat()
   
   /**
    * Will be called by a 'NewConnectionWorkerThread' when a reply comes from a peer we've requested to chat with.
    * Sends a broadcast with the result's details
    * @param isApproved - true / false
    * @param reason - the reason in the case of denial of access
    */
   public void OnReceptionOfChatEstablishmentReply(String RoomID, boolean isApproved, String reason)
   {
	   if (isApproved)  //the peer has approved us
	   {
		   if (mActiveChatRooms.get(RoomID)==null) //if this chat doesn't exist already
		   {
			   ChatRoomDetails details = mDiscoveredChatRoomsHash.get(RoomID); //get the room's details
			   ActiveChatRoom room = new ActiveChatRoom(this,false, details);  //create a new chat room
			   mActiveChatRooms.put(RoomID, room);  //add to the active chats hash
		   }   
	   }
	   
	   //if this message is a 'kick' or 'ban' message
	   if (reason.equalsIgnoreCase(Constants.SERVICE_NEGATIVE_REPLY_FOR_JOIN_REQUEST_REASON_BANNED) ||
			   reason.equalsIgnoreCase(Constants.SERVICE_NEGATIVE_REPLY_FOR_JOIN_REQUEST_REASON_KICKED))
	   {
		   //if this room isn't currently displayed, we want to handle the disconnection
		   if (DisplayedAtChatActivity==null || !RoomID.equalsIgnoreCase(DisplayedAtChatActivity.RoomID))
		   {
			   ActiveChatRoom activeRoom = mActiveChatRooms.get(RoomID);
			   if (activeRoom!=null)
				   RemoveActiveRoomOnKickOrBan(activeRoom.mRoomInfo);
			   
			   BroadcastRoomsUpdatedEvent();
			   return;
		   }
	   }//if 
	
	   
	   //Broadcast the result
	   Intent intent = CreateBroadcastIntent();
	   intent.putExtra(Constants.SERVICE_BROADCAST_OPCODE_KEY, Constants.SERVICE_BROADCAST_OPCODE_JOIN_REPLY_RESULT); //put opcode
	   intent.putExtra(Constants.SINGLE_SEND_THREAD_KEY_RESULT,
			   isApproved? Constants.SINGLE_SEND_THREAD_ACTION_RESULT_SUCCESS : Constants.SINGLE_SEND_THREAD_ACTION_RESULT_FAILED);
	   intent.putExtra(Constants.SINGLE_SEND_THREAD_KEY_REASON, reason);  //add the reason for the case of a failure
	   intent.putExtra(Constants.SINGLE_SEND_THREAD_KEY_UNIQUE_ROOM_ID, RoomID); //set the room's ID
	   
	   sendBroadcast(intent);
   }//end of OnReceptionOfChatEstablishmentReply()
   
  /**
   * Handles the result of the physical attempt to send a connection message.
   * A positive result only means that the message was sent, not that the peer accepted our char request 
   * @param result - the result of the send procedure
   * @param RoomID - the ID of the room this result is regarding
   */
  private void  HandleSendAttemptAndBroadcastResult(String result, String RoomID)
   {
	  Intent intent = CreateBroadcastIntent();
	  intent.putExtra(Constants.SERVICE_BROADCAST_OPCODE_KEY, Constants.SERVICE_BROADCAST_OPCODE_JOIN_SENDING_RESULT); //the opcode is connection result event
  	  intent.putExtra(Constants.SINGLE_SEND_THREAD_KEY_UNIQUE_ROOM_ID, RoomID); //set the room's ID
	
  	  if (result.equalsIgnoreCase(Constants.SINGLE_SEND_THREAD_ACTION_RESULT_SUCCESS)) //if physical connection was established and a request was sent
		   intent.putExtra(Constants.SINGLE_SEND_THREAD_KEY_RESULT,Constants.SINGLE_SEND_THREAD_ACTION_RESULT_SUCCESS); //set a 'success' result
	   else    //connection failed (for physical reasons)
		   intent.putExtra(Constants.SINGLE_SEND_THREAD_KEY_RESULT,Constants.SINGLE_SEND_THREAD_ACTION_RESULT_FAILED); //set a 'failed' result value
	   
	   sendBroadcast(intent); //b-cast
   }//end of HandleSendAttemptAndBroadcastResult()
   
  /**
   * Constructs a 'Join' message that can be sent via socket
   * @param opcode - the desired opcode (join public/private chat room)
   * @param RoomUniqueID - the room's unique ID
   * @param pw - the room's password. NULL indicates that the room doesn't require a password
   * @return - a valid 'Join' string
   */
   private String ConstructJoinMessage (int opcode, String RoomUniqueID, String pw)
   {
	   StringBuilder ans = new StringBuilder();
	   
	   if (opcode==Constants.CONNECTION_CODE_PRIVATE_CHAT_REQUEST)  //this is a connection establishment for a private chat
	   {
		   ans.append(Integer.toString(Constants.CONNECTION_CODE_PRIVATE_CHAT_REQUEST) + Constants.STANDART_FIELD_SEPERATOR); //add the opcode
		   ans.append(MainScreenActivity.UserName + Constants.STANDART_FIELD_SEPERATOR); //add the username
		   ans.append(MainScreenActivity.UniqueID + "\r\n"); //add our uniqueID
	   }
	   else //this is a connection establishment for a public chat
	   {
		   ans.append(Integer.toString(Constants.CONNECTION_CODE_JOIN_ROOM_REQUEST) + Constants.STANDART_FIELD_SEPERATOR); //add the opcode
		   ans.append(MainScreenActivity.UserName + Constants.STANDART_FIELD_SEPERATOR); //add the username
		   ans.append(MainScreenActivity.UniqueID + Constants.STANDART_FIELD_SEPERATOR); //add the user's uniqueID
		   ans.append(RoomUniqueID + Constants.STANDART_FIELD_SEPERATOR); //add the room's uniqueID
		   ans.append((pw==null? "nopw" :pw) + "\r\n"); //add the password for the room
	   }
	   
	   return ans.toString();
   }//end of ConstructJoinMessage()
   
   /**
    * Launches a broadcast intent telling an activity to pop up a toast
    * @param msg - the toast message
    */
   public void CreateAndBroadcastToast(String msg)
   {
//	   Intent intent = CreateBroadcastIntent();
//	   intent.putExtra(Constants.SERVICE_BROADCAST_OPCODE_KEY, Constants.SERVICE_BROADCAST_OPCODE_ACTION_DO_TOAST);
//	   intent.putExtra(Constants.SERVICE_BROADCAST_TOAST_STRING_KEY, msg);
//	   sendBroadcast(intent); //broadcast this intent
	   
   }//end of CreateAndBroadcastToast()
   
   /**
    * Creates a new broadcast intent
    * @return Intent
    */
   public Intent CreateBroadcastIntent()
   {
	   return new Intent(Constants.SERVICE_BROADCAST);
   }//end of CreateBroadcastIntent()

    /**
     * Updates the discovered chat rooms hash map 
     * @param chatRoomList - A discovered chat room (not necessarily new)
     */
    public void UpdateChatRoomHashMap (ArrayList<ChatRoomDetails> chatRoomList)
    {
    	
    	for (ChatRoomDetails refreshed : chatRoomList) //for each discovered chat room
    	{
    		synchronized (mDiscoveredChatRoomsHash)
	    	{
    			ChatRoomDetails existing = mDiscoveredChatRoomsHash.get(refreshed.RoomID);
    			if (existing!=null) //this room already exists, we just need to update the fields
    			{
    				 existing.Name = refreshed.Name;
    				 existing.Password = refreshed.Password;  
    				 existing.LastSeen = refreshed.LastSeen;
    				 existing.Users = refreshed.Users;
    			}
    			else  //this room doesn't exist yet
    			{
    				mDiscoveredChatRoomsHash.put(refreshed.RoomID, refreshed);	//update the hash map with the new chat room
    			}
    						  			
	    	}//end of synchronized block
    	}//for
    	
    	BroadcastRoomsUpdatedEvent();
    		
    }//end of UpdateChatRoomHashMapOnDiscovery()
    
    /**
     * Broadcasts an event informing the activities that the available rooms list was updated
     */
    public void BroadcastRoomsUpdatedEvent()
    {
		Intent intent = CreateBroadcastIntent();
		intent.putExtra(Constants.SERVICE_BROADCAST_OPCODE_KEY, Constants.SERVICE_BROADCAST_OPCODE_ACTION_CHAT_ROOM_LIST_CHANGED);
	    sendBroadcast(intent); //send the intent
    }//end of BroadcastRoomsUpdatedEvent()
    
    /** 
     * Updates the peer list
     * @param peerIP - A string containing the peer's IP address
     * @param unique - the peer's unqiue ID
     * @param name - the peer's name
     */
    public void UpdateDiscoveredUsersList (String peerIP, String unique, String name)
    {
    	mIsWifiPeerValid=true;  //note that we've received a valid discovery message. WE'RE LIVE AND RUNNING!
    	boolean isFound=false;
    	if (unique==null) //if this func is called by the b-cast receiver (the unique is unknown at this state)
    	{
			synchronized (mDiscoveredUsers)
			{
				for (User user: mDiscoveredUsers) //for each existing user
				{
					if (user.IPaddr.equalsIgnoreCase(peerIP)) //if this IP exists
					{
						isFound=true;
						break;
					}
				}//for 
				
				if (!isFound)
				{
					User peer = new User(null, peerIP, null); //create a new peer
					mDiscoveredUsers.add(peer);
				}
			}//synch
    	}//if
    	else //the user's unique is given
    	{
			synchronized (mDiscoveredUsers)
			{
				for (User user: mDiscoveredUsers) //for each existing user
				{
					//if we found the user
					if ( (user.uniqueID!=null && user.uniqueID.equalsIgnoreCase(unique)) || user.IPaddr.equalsIgnoreCase(peerIP))  
					{
						user.IPaddr=peerIP;  //update the IP address
						user.name=name;     //update the name
						user.uniqueID=unique; //update the unique ID
						isFound=true;
						break;
					}
	
				}//for
				
				if(!isFound) //if this peer doesn't exist in our registry at all
				{
					User peer = new User(unique, peerIP, name); //create a new peer
					mDiscoveredUsers.add(peer);
				}
				
			}//synch
    	}//else
    }//end of UpdateDiscoveredUsersList()
    
    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class LocalBinder extends Binder
    {
        LocalService getService() 
        {
            // Return this instance of LocalService so clients can call public methods
            return LocalService.this;
        }
    }//end of LocalBinder()
    
    private long timeStampAtLastResfresh=0;
    private long timeStampAtLastConnect=0;
    
    /**
     * Called by the fragment when the user clicks the refresh button
     * Performs a query against all existing users in the mDiscoveredUsers list
     */
    public void OnRefreshButtonclicked()
    {
    	String allDiscovredUsers=null;
    	
    	if (mIsWifiGroupOwner)
    		allDiscovredUsers = BuildUsersPublicationString();
    	
    	//if the device thinks he's connected when the discovered peer list is empty:
//    	if (ChatSearchScreenFrag.mIsConnectedToGroup && mDiscoveredUsers.isEmpty())
//    	{
//    		ChatSearchScreenFrag.mManager.removeGroup(ChatSearchScreenFrag.mChannel, null);
//    		ChatSearchScreenFrag.mIsConnectedToGroup=false;
//    	}
    	
    	if (!mDiscoveredUsers.isEmpty())
    	{
    	for (User user : mDiscoveredUsers) //for each discovered user
    		{
    		new NewConnectionWorkerThread(this, user, Constants.CONNECTION_CODE_DISCOVER).start(); //start a new query
    		//if this is the group's owner: send a peer publication string
    		if (mIsWifiGroupOwner && allDiscovredUsers!=null)
    			new SendSingleStringViaSocketThread(user.IPaddr, allDiscovredUsers).start(); //send a peer publication message
    		}
    	}
    	
    	long currentTime = Constants.GetTime().getTime();
    	
    	//We want to make sure that the discovery procedure doesn't happen too often, to let the Wifi manager perform the
    	//operation fully.
    	if (( (currentTime-timeStampAtLastResfresh) > Constants.MIN_TIME_BETWEEN_WIFI_DISCOVER_OPERATIONS_IN_MS)
    			&& ChatSearchScreenFrag.mIsConnectedToGroup==false)
    	{
    		timeStampAtLastResfresh=currentTime;  //save the time stamp at the last entry
	    	DiscoverPeers(); //this method will start a query with newly discovered peers
	    	return;       //return. we want go give the wifi manager some time to discover peers
    	}
    	
    	//anyway, if we're not connected,
    	//even if we don't do start a new 'discoverPeers()' procedure, we want to keep trying to connect to a peer
    	if (ChatSearchScreenFrag.mIsConnectedToGroup==false)
    	onPeerDeviceListAvailable(); 
   
    }//end of OnRefreshButtonclicked()
    
	/** Initializes peer discovery. Results are handled by the broadcast receiver. 
	 * When the peer list is available, onPeerDeviceListAvailable() is called by the b-cast receiver
	*/
	@SuppressLint("NewApi")
	public void DiscoverPeers()
	{
		//if the search fragment is bound to this service (the receiver is created in onBind())
		if (mWifiP2PReceiver!=null)
			{
			mWifiP2PReceiver.mIsPeersDiscoveredInCurrentRefresh=false; //lower a flag in the receiver that'll allow peer discovery
			
			//start a new discovery
			ChatSearchScreenFrag.mManager.discoverPeers(ChatSearchScreenFrag.mChannel, new WifiP2pManager.ActionListener() {
			    @Override
			    public void onSuccess() {
			       CreateAndBroadcastWifiP2pEvent(Constants.SERVICE_BROADCAST_WIFI_EVENT_PEER_DISCOVER_SUCCESS, -1);
			    }
		
			    @Override
			    public void onFailure(int reasonCode) {
			    	 CreateAndBroadcastWifiP2pEvent(Constants.SERVICE_BROADCAST_WIFI_EVENT_PEER_DISCOVER_FAILED, reasonCode);
			    }
		
				});
			}//if
	}//end of DiscoverPeers()
	
	private static int index=0;
	
	/**
	 * When this function is called by the b-cast receiver or the service,
	 * a peer list is available and we can try to connect to one of the discovered peers.
	 */
	public void onPeerDeviceListAvailable ()
	{
		boolean isGroupOwnerDiscovered=false;
    	long currentTime = Constants.GetTime().getTime();
		//if the device list is valid
		if (mDevices!=null && mDevices.length!=0 && !ChatSearchScreenFrag.mIsConnectedToGroup
				&& ( (currentTime-timeStampAtLastConnect) > Constants.MIN_TIME_BETWEEN_WIFI_CONNECT_ATTEMPTS_IN_MS))
		{
			timeStampAtLastConnect=currentTime; //save the time stamp at the last entry
			WifiP2pDevice device=null;
			/*Now we want to attempt a connection with another device*/
			synchronized (mDevices)
			{
				//in case one of the peers is the owner of a valid group, he's the one want want to connect to.
				//go over all available devices and try to find a group owner:
				for (WifiP2pDevice dev : mDevices)
				{
					if (dev.isGroupOwner())
					{
						device=dev;
						isGroupOwnerDiscovered=true;
						break;
					}
				}
				
				//If non of the devices is a group owner, just keep on trying to connect with one of them:
				if (!isGroupOwnerDiscovered)
				{
					for (int k=0; k < mDevices.length ;k++ )
					{
						index %= mDevices.length;      //set the index to a legal value
						if (mDevices[index]!=null && mDevices[index].status==WifiP2pDevice.AVAILABLE)
						{
							device = mDevices[index]; //get one of the discovered devices
							break;
						}
						index++;
					}//for
					
					//if we haven't found a device we can connect to, return.
					if (device==null)
						return;
				}//if
			}//synch

			WifiP2pConfig config = new WifiP2pConfig();
			config.deviceAddress = device.deviceAddress;
			config.wps.setup = WpsInfo.PBC;               //set security properties to 'Display push button to approve peer'
			ChatSearchScreenFrag.mManager.connect(ChatSearchScreenFrag.mChannel, config, new ActionListener() {

				    @Override
				    public void onSuccess() {
				    //	ChatSearchScreenFrag.mIsConnectedToGroup=true;
				    }
				    @Override
				    public void onFailure(int reason) {
				    	ChatSearchScreenFrag.mIsConnectedToGroup=false;
				    }
				});

		}//if
	}//end of onPeerDeviceListAvailable()
	
	
	/**
	 * Updates the intent-filter member variable
	 */
	private void UpdateIntentFilter ()
	{
		mIntentFilter = new IntentFilter();
	    mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
	    mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
	    mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
	    mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
	}  //end of UpdateIntentFilter()

	/**
	 * Terminates the Welcome-socket thread and self-kills the service	
	 */
	public void kill(){
		if(mWelcomeSocketThread!=null){
			mWelcomeSocketThread.kill();
			mWelcomeSocketThread.interrupt(); //close the welcome socket thread
		}
		try
		{
			if (mWifiP2PReceiver!=null)
				unregisterReceiver(mWifiP2PReceiver);
		}
		catch (Exception e) {}
		
		stopSelf(); //commit suicide
	}//end of kill()
	
	/**
	 * Initializes member variables
	 */
	private void InitClassMembers()
	{
    	if (mDiscoveredChatRoomsHash==null)
    	{
    		mDiscoveredChatRoomsHash = new HashMap<String, ChatRoomDetails>();
    	}
    	if (mDiscoveredUsers==null)
    	{
    		mDiscoveredUsers = new ArrayList<User>();
    	}
      	if (mActiveChatRooms==null)
    	{
      		mActiveChatRooms = new HashMap<String, ActiveChatRoom>();
    	}
      	if (mBannedFromPrivateChatUsers==null)
    	{
      		mBannedFromPrivateChatUsers= new HashMap<String,String>();
    	}
      	
		if (mNotificationManager==null)
			mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
	}//end of InitClassMembers()
	
	/**
	 * Creates a new hosted public chat room 
	 * @param name - the room's desired name
	 * @param password - the room's desired password. NULL means that there's no password
	 */
	public void CreateNewHostedPublicChatRoom (String name, String password)
	{
		//create new details:
		ChatRoomDetails newDetails = new ChatRoomDetails(
				MainScreenActivity.UniqueID + "_" + (++MainScreenActivity.ChatRoomAccumulatingSerialNumber),
						name, null, new ArrayList<User>(), password, false);
		//create a new active room:
		ActiveChatRoom newActiveRoom = new ActiveChatRoom(this, true, newDetails);
		//init a history file if necessary:
		InitHistoryFileIfNecessary(newActiveRoom.mRoomInfo.RoomID,newActiveRoom.mRoomInfo.Name, false);
		//put the room into the hash map:
		mActiveChatRooms.put(newActiveRoom.mRoomInfo.RoomID, newActiveRoom);
		//b-cast an event that'll cause the chat-search-frag to refresh the list view
		BroadcastRoomsUpdatedEvent();
		//start a new logic discovery procedure to update all peers about the new room list
		OnRefreshButtonclicked();
	}//end of CreateNewPublicChatRoom()
	
	/**
	 * Creates a new active chat room, if it doesn't exist already
	 * @param input - incoming request string after parsing
	 */
	public void CreateNewPrivateChatRoom(String[] input)
	{
		   if (mActiveChatRooms.get(input[2])==null) //if this chat doesn't exist already
		   {
			   ChatRoomDetails details = mDiscoveredChatRoomsHash.get(input[2]); //get a reference to the chat's details
			   ActiveChatRoom room = new ActiveChatRoom(this,false, details);  //create a new chat room
			   
			   //check if a history file should be created for this new room
			   InitHistoryFileIfNecessary(details.RoomID,details.Name, details.isPrivateChatRoom);
			   
			   mActiveChatRooms.put(input[2], room);  //add to the active chats hash
		   }   
	}//end of CreateNewPrivateChatRoom()
	
	/**
	 * Initializes a history file if it doesn't exist already
	 * @param roomID - the chat room's ID
	 * @param RoomName - the chat room's name
	 * @param isPrivate - true if this room is private, false otherwise
	 */
	private void InitHistoryFileIfNecessary(String roomID, String RoomName, boolean isPrivate)
	{
			//We want to know if a history file exists already
			String path  = getFilesDir().getPath()+ "/" +roomID + ".txt";
			File f = new File(path);
		   if (!f.isFile()) //if a history file doesn't exist already
		   {
			   //create a new history file:
			   ChatActivity.InitHistoryFile(roomID, null, RoomName, isPrivate, this);
		   }
	}//end of InitHistoryFileIfNecessary()
	
	/**
	 * Called by {@link ChatActivity} when a peer wants to leave a public chat that we host
	 * @param peerUnique - the peer's unique ID
	 * @param roomUnique - the chat room's unique ID
	 */
	public void OnRequestToRemoveFromHostedChat(String peerUnique, String roomUnique)
	{
		ActiveChatRoom activeRoom = mActiveChatRooms.get(roomUnique);
		if (activeRoom!=null)
			activeRoom.RemoveUserFromTheUsersList(peerUnique);
	}//end of OnRequestToRemoveFromHostedChat()
	
	/**
	 * Called by {@link ChatActivity} when a host wished to kick or ban a participating peer
	 * @param info - the chat room's details
	 * @param userUnique - the peer's unique ID
	 * @param isBanned - true if the user should be banned, false if he should be kicked only
	 */
	public void KickOrBanUserFromHostedChat(ChatRoomDetails info, String userUnique, boolean isBanned)
	{
		ActiveChatRoom activeRoom = mActiveChatRooms.get(info.RoomID);
		if (activeRoom!=null)
		{
			if (isBanned)
				activeRoom.BanUser(userUnique);
			else
				activeRoom.KickUser(userUnique);
		}
	}//end of KickUserFromHostedChat()
	
	/**
	 * The user wished to leave a public chat room which he's not the host of
	 * @param info - the room's details
	 */
	public void CloseNotHostedPublicChatRoom (ChatRoomDetails info)
	{
		ActiveChatRoom activeRoom = mActiveChatRooms.get(info.RoomID);
		if (activeRoom!=null)
		{
			activeRoom.DisconnectFromHostingPeer();
			mActiveChatRooms.remove(activeRoom.mRoomInfo.RoomID);  //remove from the active rooms list
			BroadcastRoomsUpdatedEvent();
		}
	}//end of CloseNotHostedPublicChatRoom()

	/**
	 * The host wished to leave and close a public chat room
	 * @param info - the room's details
	 */
	public void CloseHostedPublicChatRoom (ChatRoomDetails info)
	{
		ActiveChatRoom activeRoom = mActiveChatRooms.get(info.RoomID);
		if (activeRoom!=null)
		{
			activeRoom.CloseRoomAndNotifyUsers();
			mActiveChatRooms.remove(activeRoom.mRoomInfo.RoomID);  //remove from the active chat rooms list
			BroadcastRoomsUpdatedEvent();
		}
	}//end of CloseHostedPublicChatRoom()
	
	/**
	 * Clears the banned users list of a single public chat room
	 * @param info - the room's details
	 */
	public void ClearBannedUsersListInPublicRoom (ChatRoomDetails info)
	{
		ActiveChatRoom room = mActiveChatRooms.get(info.RoomID);
		if (room!=null)
			room.ClearBannedUsersList();
	}//end of ClearBannedUsersListInPublicRoom()
	
	/**
	 * Relevant only for a private chat
	 * Updates the peer's name in the history log file
	 * @param info - the room's details
	 */
	public void UpdatePrivateChatRoomNameInHistoryFile(ChatRoomDetails info)
	{
		ActiveChatRoom room = mActiveChatRooms.get(info.RoomID);
		if (room!=null)
			room.updateUserNameInTheHistoryLogFile();
	}//end of UpdatePrivateChatRoomNameInHistoryFile()
	
	
	/**
	 * Creates a string with all relevant active chat rooms that have new messages.
	 * This string is later shown to the user via notification
	 * @return string
	 */
	private String CreateNewMsgNotificationString ()
	{
		StringBuilder builder = new StringBuilder();
		int numOfRoomsWithNewMsgs=0;
		
		Collection<ActiveChatRoom> chatRooms = mActiveChatRooms.values();  //get all available active chat rooms
		for (ActiveChatRoom room : chatRooms) //for each chat room
		{
			//we want to check every active room, except the one that is displayed by the chat activity, for new messages
			if ( (DisplayedAtChatActivity==null || !room.mRoomInfo.RoomID.equalsIgnoreCase(DisplayedAtChatActivity.RoomID))
					&& room.mRoomInfo.hasNewMsg)
				{
				builder.append(room.mRoomInfo.Name+", "); //add the room's name
				numOfRoomsWithNewMsgs++;
				}
		}//for
		
		if (numOfRoomsWithNewMsgs==0) //if we don't need to raise a notification
			return null;
		
		int length = builder.length();
		builder.delete(length-2, length); //remove the ", " at the end
		
		if (numOfRoomsWithNewMsgs==1)  //if there's only one chat room with new messages
			builder.insert(0, "New messages available: ");
		else //we have new messages in more than 1 room
			builder.insert(0, "At "+Integer.toString(numOfRoomsWithNewMsgs)+
					" chat rooms: ");
		
		return builder.toString();
	}//end of CreateNewMsgNotificationString()
	
	/**
	 * Removes a single chat room from the discovered rooms list
	 * Called by the {@link ChatActivity} when a 'Room closed' or 'Room not exisitng' message is received
	 * @param RoomID
	 */
	public void RemoveFromDiscoveredChatRooms (String RoomID)
	{
		synchronized (mDiscoveredChatRoomsHash)
		{
			//get the room to be removed
			ChatRoomDetails toRemove = mDiscoveredChatRoomsHash.get(RoomID);  
			if (toRemove!=null)
				mDiscoveredChatRoomsHash.remove(RoomID);
		}//synch
		
		BroadcastRoomsUpdatedEvent();
	}//end of RemoveFromDiscoveredChatRooms()
	
	/**
	 * Called by the {@link ChatActivity} when a public room's owner kicks or bans us
	 * @param details
	 */
	public void RemoveActiveRoomOnKickOrBan(ChatRoomDetails details)
	{
		synchronized (mActiveChatRooms)
		{
			if (mActiveChatRooms.containsKey(details.RoomID))
				mActiveChatRooms.remove(details.RoomID);
		}
	}//end of RemoveActiveRoomOnKickOrBan()
	
	/**
	 * Removes a single discovered chat room on the event of timeout
	 * @param details - the room's details
	 * @param isCalledByService - true if called from the service, false otherwise
	 * @param iter - if this method is called by the service, the service iterates over the hash. 
	 */
	public void RemoveSingleTimedOutRoom (ChatRoomDetails details, boolean isCalledByService)
	{
		if (isCalledByService) //if this method was called by the service
		{
			//if no room is displayed or the room to be deleted isn't the displayed room
			if (DisplayedAtChatActivity==null || !DisplayedAtChatActivity.RoomID.equalsIgnoreCase(details.RoomID) )
			{
				synchronized (mActiveChatRooms)
				{
					synchronized (mDiscoveredChatRoomsHash)
					{
					if (mActiveChatRooms.containsKey(details.RoomID))
							mActiveChatRooms.remove(details.RoomID);	
					// remove it from the has as well
					mDiscoveredChatRoomsHash.remove(details.RoomID);  //remove from the discovered chat rooms hash
					}
				}
			}
			else //this room is currently displayed
			{
				Intent intent = CreateBroadcastIntent();
				intent.putExtra(Constants.SINGLE_SEND_THREAD_KEY_UNIQUE_ROOM_ID, details.RoomID);
				intent.putExtra(Constants.SERVICE_BROADCAST_OPCODE_KEY, Constants.SERVICE_BROADCAST_OPCODE_ROOM_TIMED_OUT);
				sendBroadcast(intent);
			}
				
		} //this method is called by the 'ChatActivity'
		else
		{
			synchronized (mActiveChatRooms)
			{
				synchronized (mDiscoveredChatRoomsHash)
				{
				if (mActiveChatRooms.containsKey(details.RoomID))
						mActiveChatRooms.remove(details.RoomID);
					
				mDiscoveredChatRoomsHash.remove(details.RoomID);  //remove from the discovered chat rooms hash
				}
			}
		}
		
		if (!isCalledByService)
			BroadcastRoomsUpdatedEvent();
	}//end of RemoveSingleTimedOutRoom()
	
	/**
	 * Deletes all timed out chat rooms
	 */
	private void DeleteTimedOutRooms()
	{
		Date currentTime = Constants.GetTime();
		long timeDiff=0;
		
		Collection<ChatRoomDetails> chatRooms = mDiscoveredChatRoomsHash.values();  //get all discovered chat rooms
		
		int numOfRooms = chatRooms.size();
		ChatRoomDetails[] allRooms = new ChatRoomDetails[numOfRooms];
		int i=0;
		
		synchronized (mDiscoveredChatRoomsHash)
		{
			//get the hash's content
			for (ChatRoomDetails room : chatRooms) //for each chat room
			{
				allRooms[i++]=room;
			}//for
			
			//go over all of the rooms and check if they've timed out
			for (i=0; i<numOfRooms ;i++)
			{
				timeDiff = currentTime.getTime() - allRooms[i].LastSeen.getTime();  //get the time diff
				if (timeDiff >= MainScreenActivity.RefreshPeriodInMs*Constants.TO_FACTOR) //if this room has time out
				{
					RemoveSingleTimedOutRoom(allRooms[i],true);
				}
			}
		}	//fir
		BroadcastRoomsUpdatedEvent();
	}//end of DeleteTimedOutRooms()
	
	/**
	 * Used by a peer who's also the groups owner. Creates a string of all available users to be sent across the group.
	 * @return valid publication string, NULL if there are no discovered users
	 */
	private String BuildUsersPublicationString()
	{
		//Build the users publication string
		StringBuilder res = new StringBuilder(Integer.toString(Constants.CONNECTION_CODE_PEER_DETAILS_BCAST) + Constants.STANDART_FIELD_SEPERATOR
				+ MainScreenActivity.UserName + Constants.STANDART_FIELD_SEPERATOR
				+ MainScreenActivity.UniqueID + Constants.STANDART_FIELD_SEPERATOR);
		boolean isUserListNotEmpty=false;
		
		for (User user : mDiscoveredUsers) //for each discovered user
		{
			//each entry is: IP$NAME$UNIQUE
			if (user.IPaddr!=null && user.name!=null && user.uniqueID!=null) //we advertise only hosted rooms
			{
				res.append(	user.IPaddr + Constants.STANDART_FIELD_SEPERATOR
					      + user.name + Constants.STANDART_FIELD_SEPERATOR
					      + user.uniqueID + Constants.STANDART_FIELD_SEPERATOR );
				isUserListNotEmpty=true;
			}
		}
		if (!isUserListNotEmpty)  //if there is no info about discovered users
			return null;
		
		return res.toString();
	}//end of BuildUsersPublicationString()
	
}//Class
