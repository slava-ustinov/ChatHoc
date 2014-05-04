package com.example.android_final_proj;

import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.support.v4.app.NavUtils;
import android.view.MenuItem;

public class QuickPrefsActivity extends PreferenceActivity {
	
    @SuppressWarnings("deprecation")
	@Override
    public void onCreate(Bundle savedInstanceState) {    	
        super.onCreate(savedInstanceState);        
        addPreferencesFromResource(R.xml.preferences);   
        setupActionBar();
    }

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
    
    /**
     * When this activity stops, we'de like to update the relevant fields in MainScreenActivity
     */
    @Override
    protected void onStop()
    {
    	super.onStop();
    	SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);  //get reference to the updated prefs
    	String updatedUserName = sharedPrefs.getString(Constants.SHARED_PREF_USER_NAME, null); //get the user name
    	if (updatedUserName!=null)
    	MainScreenActivity.UserName = updatedUserName; //update the user name
    	//get the notification choice:
    	boolean isToNotify = sharedPrefs.getBoolean(Constants.SHARED_PREF_ENABLE_NOTIFICATION, false); 
    		MainScreenActivity.isToNotifyOnNewMsg = isToNotify; //update the value
    	 //get the refresh period in MS
    	String refreshPeriod = sharedPrefs.getString(Constants.SHARED_PREF_REFRESH_PERIOD, "10000");
    		MainScreenActivity.RefreshPeriodInMs = Integer.parseInt(refreshPeriod); //update the refresh period

    }//end of onStop()
    
}