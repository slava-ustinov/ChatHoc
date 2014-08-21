package com.example.android_final_proj;

import java.util.ArrayList;
import java.util.Date;
/**
 * A data structure to hold information about a discovered chat room
 */
public class ChatRoomDetails
{

	String RoomID=null;
	String Name=null;
	String Password=null;  //a suggested password by a user trying to connect to this room
	Date LastSeen=null;
	boolean isPrivateChatRoom;
	ArrayList<User> Users=null;
	public String UserNamesString = null;  //will be used to hold the users string for not-hosted chat rooms
	boolean hasNewMsg=false;
	
	public ChatRoomDetails(String RoomID,String Name,Date LastSeen,ArrayList<User> mUsers,String Password,boolean isPrivate)
	{
		
		this.Password=Password;
		this.RoomID=RoomID;
		this.Name=Name;
		this.LastSeen=LastSeen;
		this.Users=mUsers;
		this.isPrivateChatRoom=isPrivate;
	}
	
	public ChatRoomDetails(String RoomID,String Name,Date LastSeen,ArrayList<User> mUsers,boolean isPrivate)
	{
		this(RoomID, Name, LastSeen, mUsers,null,isPrivate);
	}
	
	public ChatRoomDetails(String RoomID,String Name,Date LastSeen,ArrayList<User> mUsers,String isPwRequired,String usersString)
	{
		
		this.Password= isPwRequired.equals("true")? "yup" : null;
		this.RoomID=RoomID;
		this.Name=Name;
		this.LastSeen=LastSeen;
		this.Users=mUsers;
		this.isPrivateChatRoom=false;
		this.UserNamesString=usersString;
	}
	
}//end of class
