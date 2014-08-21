package com.example.android_final_proj;

import java.util.ArrayList;
import java.util.HashMap;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout.LayoutParams;
import android.widget.TextView;
/**
 * CustomChatAdapter is a Custom BaseAdapter to implement custom ListView rows
 * 
 *
 */
public class CustomChatAdapter extends BaseAdapter{
	private Context mContext;
	private ArrayList<ChatMessage> mMessages;
	private HashMap<String, Integer> mColorsForUsers;
	private int[] mColors = null;
	private final int NUM_OF_COLORS = 16;


	public CustomChatAdapter(Context context, ArrayList<ChatMessage> messages) {
		super();
		this.mContext = context;
		this.mMessages = messages;
		this.mColorsForUsers = new HashMap<String, Integer>();
		
		//sets all the possible colors of a MSG 
		mColors = new int[NUM_OF_COLORS];
		int i=0;
		mColors[i++]=R.color.AntiqueWhite;
		mColors[i++]=R.color.Cyan;
		mColors[i++]=R.color.DarkGray;
		mColors[i++]=R.color.Blue;
		mColors[i++]=R.color.Yellow;
		mColors[i++]=R.color.Azure;
		mColors[i++]=R.color.Lavender;
		mColors[i++]=R.color.Magenta;
		mColors[i++]=R.color.Gold;
		mColors[i++]=R.color.Black;
		mColors[i++]=R.color.Green;
		mColors[i++]=R.color.Gray;
		mColors[i++]=R.color.BlanchedAlmond;
		mColors[i++]=R.color.MediumTurquoise;
		mColors[i++]=R.color.PaleGoldenrod;
		mColors[i++]=R.color.Silver;
	}//constructor
	
	@Override
	public int getCount() {return mMessages.size();}
	
	@Override
	public Object getItem(int position) {return mMessages.get(position);}
	
	@SuppressLint("ResourceAsColor")
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		ChatMessage message = (ChatMessage) this.getItem(position);
		int msgColor;
		ViewHolder holder; 
		if(convertView == null)
		{
			holder = new ViewHolder();
			convertView = LayoutInflater.from(mContext).inflate(R.layout.chat_activity_list_item, parent, false); //get the xml layout
			holder.mMessage = (TextView) convertView.findViewById(R.id.message_text);
			holder.mTimeAndUserName =(TextView) convertView.findViewById(R.id.message_time_and_userName);
	
			convertView.setTag(holder);
		}
		else
			holder = (ViewHolder) convertView.getTag();
		
		holder.mMessage.setText(message.getMessage());
		holder.mTimeAndUserName.setText("   "+message.getTime()+"  "+message.getUserName()+"   ");
	
		holder.mTimeAndUserName.setTextColor(R.color.textFieldColor);
		holder.mTimeAndUserName.setTextSize(14);
		
		LayoutParams lp = (LayoutParams) holder.mMessage.getLayoutParams();
		LayoutParams lp2 = (LayoutParams) holder.mTimeAndUserName.getLayoutParams();

		//Check whether message is mine to show green background and align to right
		if(message.isMine())
		{
			holder.mMessage.setBackgroundResource(R.drawable.speech_bubble_green);
			lp.gravity = Gravity.RIGHT;
			lp2.gravity = Gravity.RIGHT;
			holder.mMessage.setTextColor(mContext.getResources().getColor(R.color.Black));

		}
		
		//If not mine then it is from sender to show orange background and align to left
		else
		{
			holder.mMessage.setBackgroundResource(R.drawable.speech_bubble_orange);
			
			 msgColor = getColorForUser(message.mUserUnique);
			
			//sets color to the MSG
			holder.mMessage.setTextColor(mContext.getResources().getColor(msgColor));
			
			lp.gravity = Gravity.LEFT;
			lp2.gravity = Gravity.LEFT;

		}
		holder.mMessage.setLayoutParams(lp);
	
		
		holder.mTimeAndUserName.setLayoutParams(lp2);
		
		return convertView;
	}
	private static class ViewHolder
	{
		TextView mMessage;
		TextView mTimeAndUserName;
		
	}

	@Override
	public long getItemId(int position) {
		//Unimplemented, because we aren't using Sqlite.
		return 0;
	}//getItemId()
	
	public int getColorForUser(String unique){
		Integer color=null;
		int numOfUsersInTheHashTable=0;
		
		//get the color
		color= mColorsForUsers.get(unique);
		
		//if the user was found in the table - return the color
		if(color!=null){return color.intValue();}	//if this user exists return his color 
		
		//if the user wasn't found in the table - get the size of the hash table
		numOfUsersInTheHashTable=mColorsForUsers.size();
	
		//pick a color, the "%" makes sure we won't get index out of boundary exception
		color=mColors[numOfUsersInTheHashTable % mColors.length];
	
		//the <user,color> tuple are entered to the table
		mColorsForUsers.put(unique, color);
		
		
		//return the integer that represents the color of the new MSG
		return color.intValue();
		
	}//end of getColorForUser(String unique)

}//Class