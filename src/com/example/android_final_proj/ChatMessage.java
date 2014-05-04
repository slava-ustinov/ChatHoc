package com.example.android_final_proj;

/**
 * Message is a custom object to encapsulate message information/fields
 *
 */
public class ChatMessage {

	String mUserUnique;
	String mMessage;
	String mUserName;
	String mTime;
	boolean mIsMine; 
	boolean isStatusMessage;
	
	/**
	 * Constructor to make a Message object
	 */
	public ChatMessage(String message, boolean isMine) {
		super();
		this.mMessage = message;
		this.mIsMine = isMine;
		this.isStatusMessage = false;
	}
	/**
	 * Constructor to make a status Message object
	 * consider the parameters are swaped from default Message constructor,
	 *  not a good approach but have to go with it.
	 */
	public ChatMessage(boolean status, String message) {
		super();
		this.mMessage = message;
		this.mIsMine = false;
		this.isStatusMessage = status;
	}
	
	public ChatMessage(String userUnique,String msg,String userName,String time,boolean isMine){
		this(msg,isMine);
		 mUserUnique =userUnique;
		 mTime=time;
		 mUserName=userName;
		
	}
	public String getMessage() {return mMessage;}
	public void setMessage(String message) {this.mMessage = message;}
	public boolean isMine() {return mIsMine;}
	public void setMine(boolean isMine) {this.mIsMine = isMine;}
	public boolean isStatusMessage() {return isStatusMessage;}
	public void setStatusMessage(boolean isStatusMessage) {this.isStatusMessage = isStatusMessage;}
	public String getUserName(){return mUserName;}
	public void setUserName(String UN){mUserName=UN;}
	public String getUserUnique(){return mUserUnique;}
	public void setUserUnique(String id){mUserUnique=id;}
	public String getTime(){return mTime;}
	public void setTime(String time){mTime=time;}
	

}
