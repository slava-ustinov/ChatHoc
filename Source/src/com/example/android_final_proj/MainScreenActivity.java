package com.example.android_final_proj;

/** 
 * Android final project. 
 * Wifi-Direct based multi-user chat application
 * Presented by 309930006 and 301224283
 */

import java.util.Locale;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.FragmentTransaction;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings.Secure;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.CheckBox;
import android.widget.EditText;

/**
 * The app's main entry point. Holds 2 fragments: {@link ChatSearchScreenFrag} and {@link ChatHistoryScreenFrag}.
 * The 1st fragment offers to scan for new chat groups and users. The 2nd offers to view chat history.
 */
public class MainScreenActivity extends FragmentActivity implements ActionBar.TabListener 
{
	private AlertDialog mDialog=null;			
	private OnClickListener AlertCheckBoxClickListener=null;  //used to handle check-box click events for a dialog
	
	SectionsPagerAdapter mSectionsPagerAdapter;  //adapter for the tab view. Contains all the frags
	ViewPager mViewPager; //a layout widget in which each child view is a separate page (a separate tab) in the layout.
	//both fragment will initialize these references when they're created:
	public ChatHistoryScreenFrag mHistoryFrag = null;    
	public ChatSearchScreenFrag mSearchFrag = null;
	
	boolean isServiceStarted = false;
	boolean wasWifiDialogShown = false;

	static int mDisplayedFragIndex = 0;
	public static long ChatRoomAccumulatingSerialNumber=0;
	public static String UniqueID=null;
	public static String UserName = ":>~";				  //setting a default user name
    static boolean isToNotifyOnNewMsg = false; 			  //defines if notifications should be shown on arrival of new messages
    static int RefreshPeriodInMs = 30000;				  //defines the peer refresh period
    
    private boolean mIsRunForTheFirstTime=false;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) 
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main_screen);

		InitializeTabHandlerAndAdapter();
		
		if (!isServiceStarted && ChatSearchScreenFrag.mService==null)
		{
			startService(new Intent(this, LocalService.class));
			isServiceStarted=true;
		}
		
		getPrefs();  //get the shared prefs
		
		//Happens only when the app is run for the very 1st time on a device
	    if (MainScreenActivity.UniqueID==null)
	    {
			UserName = new String(Secure.getString(getContentResolver(), Secure.ANDROID_ID)); //get a unique id
			UniqueID = new String(UserName);			
		}//if
	    
	    //Remove the title bar for out entire app
	    getActionBar().setDisplayShowTitleEnabled(false);
	    getActionBar().setDisplayShowHomeEnabled(false);
	    
	}//end of onCreate()
	
	@Override
	protected void onResume()
	{
		super.onResume();
		//check if this activity was launched by the b-cast receiver after a wifi shutdown
		boolean isToDisplayWifiDialog = getIntent()
				.getBooleanExtra(Constants.WIFI_BCAST_RCVR_WIFI_OFF_EVENT_INTENT_EXTRA_KEY, false);
		
		if (isToDisplayWifiDialog && !wasWifiDialogShown)
		{
			new EnableWifiDirectDialog().show(getSupportFragmentManager(),"MyDialog"); //show a dialog	
			wasWifiDialogShown=true;
		}
		
		//if this app is run for the very 1st time, we want to launch the settings activity first.
		if (mIsRunForTheFirstTime)
		{
			//launch the preferences activity
			startActivity(new Intent(this, QuickPrefsActivity.class));
			mIsRunForTheFirstTime=false;
		}
	}
	
	
	@Override
	protected void onPause()
	{
		super.onPause();
		savePrefs(); //save the preferences
	}//end of onPause()
	
	private void InitializeTabHandlerAndAdapter()
	{
		// Set up the action bar (enable tab display). 
		final ActionBar actionBar = getActionBar();
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

		// Create the adapter that will return a fragment for each of the two
		// primary sections of the app.
		mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

		// Set up the ViewPager with the sections adapter.
		mViewPager = (ViewPager) findViewById(R.id.pager);
		mViewPager.setAdapter(mSectionsPagerAdapter);
		
		// When swiping between different sections, select the corresponding
		// tab. We can also use ActionBar.Tab#select() to do this if we have
		// a reference to the Tab.
		mViewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() 
		{
					@Override
					//if a tab was changed by a swipe gesture
					public void onPageSelected(int position) {   
						actionBar.setSelectedNavigationItem(position); //update the tab bar to match the selected page
						mDisplayedFragIndex=position;   //update the index of the currently displayed frag
						if (position==1)  //if the view has moved to the history fragment:
						{
							mHistoryFrag.loadHistory(); //reload the history list view
						}
						invalidateOptionsMenu();
					}
				});

		// For each of the sections in the app, add a tab to the action bar.
		for (int i = 0; i < mSectionsPagerAdapter.getCount(); i++) 
		{
			// Create a tab with text corresponding to the page title defined by
			// the adapter. Also specify this Activity object, which implements
			// the TabListener interface, as the callback (listener) for when
			// this tab is selected.
			actionBar.addTab(actionBar.newTab()
					.setText(mSectionsPagerAdapter.getPageTitle(i))
					.setTabListener(this));
		}//for
	}//end of InitializeTabHandlerAndAdapter()

	/**
	 * Used to modify menu item according to the app's state
	 */
	@Override
	public boolean onPrepareOptionsMenu(Menu menu)
	{
		super.onPrepareOptionsMenu(menu);
		//if the wifi-direct is disabled, we want to disable the chat room creation option
		menu.getItem(0).setEnabled(ChatSearchScreenFrag.mIsWifiDirectEnabled);

		//if this menu is opened when the chat search is active:
		if (mDisplayedFragIndex==0)
			{
			//hide the 'delete history option:
			menu.findItem(R.id.action_delete_all_history).setVisible(false);  
			}
		else  //history frag is active:
			{
			//show the 'delete history option:
			menu.findItem(R.id.action_delete_all_history).setVisible(true);
			menu.findItem( R.id.clear_ignore_list).setVisible(false); 
			}
	
		return true;
	}
	
	
	@SuppressLint("HandlerLeak")
	Handler FirstTimeMenuUpdater = new Handler() 
  	{
  		@Override
  		public void handleMessage(Message msg) 
  		{
  			MainScreenActivity.this.invalidateOptionsMenu();
  		}
  	};
		
	
	/**
	 * Called only once when the app starts
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) 
	{
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main_screen_menu, menu);
		
		FirstTimeMenuUpdater.sendEmptyMessageDelayed(0, 500);
		
		return true;
	}//end of onCreateOptionsMenu()
	
	
	 @Override
		public boolean onOptionsItemSelected(MenuItem item) 
		 {
			 switch (item.getItemId())
				{
					case R.id.action_settings://setting was clicked
					{
						startActivity(new Intent(this, QuickPrefsActivity.class));
						break;
					}
					case R.id.action_create_new_chat_room: //exit app was clicked
					{
						
						mDialog =CreatePublicChatCreationDialog();
						mDialog.show();
						
						AlertCheckBoxClickListener= new OnClickListener()
						{
							@Override
							public void onClick(View v)
							{
								
								AlertDialog dialog = MainScreenActivity.this.mDialog;
								EditText ed = (EditText) dialog.findViewById(R.id.choosePassword);
								boolean b= !ed.isEnabled();
								ed.setEnabled(b);
								
							}
						};
						
						CheckBox ch = (CheckBox) mDialog.findViewById(R.id.checkBoxSetPassword); 
						ch.setOnClickListener(AlertCheckBoxClickListener);					
						break;
					}
					case R.id.clear_ignore_list: //exit app was clicked
					{
						if (mSearchFrag!=null)
							mSearchFrag.ClearIgnoredUsersList();
						break;
					}
					case R.id.action_exit: //exit app was clicked
					{
						kill();
						break;
					}
					case R.id.action_delete_all_history: //delete all history was clicked
					{
						mHistoryFrag.DeleteAllHistory();
						break;
					}
				}//switch
		 		
			return true;
		}//end of onOptionsItemSelected()
		
	
	@Override
	public void onTabSelected(ActionBar.Tab tab,FragmentTransaction fragmentTransaction) 
	{
		// When the given tab is selected, switch to the corresponding page in
		// the ViewPager.
		mViewPager.setCurrentItem(tab.getPosition());	
	}//end of onTabSelected()

	@Override
	public void onTabUnselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction)
	{
	}

	@Override
	public void onTabReselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction)
	{
	}

	/**
	 * A FragmentPagerAdapter that returns a fragment corresponding to
	 * one of the sections/tabs/pages.
	 */
	public class SectionsPagerAdapter extends FragmentPagerAdapter 
	{

		public SectionsPagerAdapter(FragmentManager fm) 
		{
			super(fm);
		}

		@Override
		public Fragment getItem(int position) 
		{
			// getItem is called to instantiate the fragment for the given page.
			Fragment fragment=null; //will hold the relevant fragment to be returned
			
			switch (position)
			{
			case 0:
				fragment = new ChatSearchScreenFrag();  //create a new chat search fragment
				break;
			case 1:
				fragment = new ChatHistoryScreenFrag();  //create a new history display fragment
				break;
			}
			
			return fragment;
		}//end of getItem()

		@Override
		public int getCount()
		{
			return 2; 		// Show 2 total pages.
		}

		//Returns the title for each tab
		@Override
		public CharSequence getPageTitle(int position) 
		{
			Locale l = Locale.getDefault();
			switch (position) {
			case 0:
				return getString(R.string.main_screen_tab1_title).toUpperCase(l);
			case 1:
				return getString(R.string.main_screen_tab2_title).toUpperCase(l);
			}
			return null;
		}
	}//end of class
	

	
	/**
	 * Called when the refresh button in the chat search fragment is clicked
	 */
	public void onRefreshButtonClicked (View v)
	{
		mSearchFrag.onRefreshButtonClicked(v); //call the frag's method
	}//end of onRefreshButtonClicked()
	

	/**
	 * Reads the saved preferences
	 */
	protected void getPrefs()
	{		      
	      SharedPreferences prefs  = getPreferences(0);
	      ChatRoomAccumulatingSerialNumber = prefs.getLong(Constants.SHARED_PREF_CHAT_ROOM_SERIAL_NUM, 0);
	      UserName = prefs.getString(Constants.SHARED_PREF_USER_NAME, null);
	      UniqueID = prefs.getString(Constants.SHARED_PREF_UNIQUE_ID, null);
	      isToNotifyOnNewMsg = prefs.getBoolean(Constants.SHARED_PREF_ENABLE_NOTIFICATION, false);
	      RefreshPeriodInMs = prefs.getInt(Constants.SHARED_PREF_REFRESH_PERIOD, 10000);
	      mIsRunForTheFirstTime = prefs.getBoolean(Constants.SHARED_PREF_IS_FIRST_RUN, true);
	}//end of getPrefs(){		
	
	/**
	 * Saved the shared preferences
	 */
	protected void savePrefs()
	{
		  SharedPreferences.Editor editor = getPreferences(0).edit();
	      editor.putLong(Constants.SHARED_PREF_CHAT_ROOM_SERIAL_NUM, ChatRoomAccumulatingSerialNumber); //save to current SN
	      editor.putString(Constants.SHARED_PREF_USER_NAME, UserName);
	      editor.putString(Constants.SHARED_PREF_UNIQUE_ID, UniqueID);
	      editor.putBoolean(Constants.SHARED_PREF_ENABLE_NOTIFICATION, isToNotifyOnNewMsg);
	      editor.putInt(Constants.SHARED_PREF_REFRESH_PERIOD, RefreshPeriodInMs);
	      editor.putBoolean(Constants.SHARED_PREF_IS_FIRST_RUN, false);
	      editor.commit();
	}//end of savePrefs()
	
	/**
	 * Calls the kill() method of {@link ChatSearchScreenFrag}, resets all static variables,
	 * calls the system's garbage collector and finishes.
	 */
	public void kill(){
		savePrefs();	
		mSearchFrag.kill();  //close the entire app (service and welcome socket)
		
		
		//we'de like to reset all static variables in our app:
		ChatActivity.mIsActive=false;
		ChatActivity.mMsgsWaitingForSendResult=null;
		ChatSearchScreenFrag.mService=null;
		ChatSearchScreenFrag.mIsWifiDirectEnabled=false;
		ChatSearchScreenFrag.mIsConnectedToGroup=false;
		ChatSearchScreenFrag.mManager = null;
		ChatSearchScreenFrag.mChannel = null;
		LocalService.mNotificationManager=null;
		
		//Indicates to the VM that it would be a good time to run the garbage collector
		System.gc();	
		
		finish();         //close this activity
	}//kill()
	
	
	private AlertDialog CreatePublicChatCreationDialog()
	{
        // This example shows how to add a custom layout to an AlertDialog
        LayoutInflater factory = LayoutInflater.from(this);
        final View textEntryView = factory.inflate(R.layout.public_chat_creation_dialog, null);
       return new AlertDialog.Builder(this)
    	   
        .setTitle("Create A New Room")
        .setView(textEntryView)
        .setIcon(R.drawable.settings_icon)
            
            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                	boolean isPassword=false;
                	String password="";
                	String roomName=null;
            
					EditText ed = (EditText) mDialog.findViewById(R.id.choosePassword);
					 
					//gets password if exists
					isPassword= ed.isEnabled();
					if(isPassword){password=ed.getText().toString();}
					
					//gets rooms name
					ed = (EditText) mDialog.findViewById(R.id.chooseRoomsName);
					roomName=ed.getText().toString();
					
					//if the room's name is invalid:
					if(roomName==null || roomName.length()<1){
						// pop alert dialog and reload this dialog
								new AlertDialog.Builder(MainScreenActivity.this)
							    .setTitle("Missing name error")
							    .setMessage("A room must have a name")
							
							    //yes button setter
							    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
							        public void onClick(DialogInterface dialog, int which) {mDialog.show();}})//setPositive
							     
							     .setOnCancelListener(new OnCancelListener(){
									public void onCancel(DialogInterface dialog){mDialog.show();}})
							     
								 .show();
						//end of alert dialog 
					}//if
					
					else{//there is a room name				
						//the room is ready to be created
						//call the service and create a new public chat room
						if (password.equalsIgnoreCase(""))
							password=null;
						
						ChatSearchScreenFrag.mService.CreateNewHostedPublicChatRoom(roomName,password);
					
					}//else
                }//onClick dialog listener
            
            
            })
            .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {}
         
            }).create();
    }//end of ShowPublicChatCreationDialog()

}//end of class
