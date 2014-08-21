package com.example.android_final_proj;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.app.DialogFragment;
/**
 * Shows a dialogue asking the user to turn on the wifi-p2p. On positive response: Takes the user to the Android Settings screen.
 *
 */
public class EnableWifiDirectDialog extends DialogFragment 
{
	 Context mContext;
	 
    public EnableWifiDirectDialog() {
        mContext = getActivity();
    }
	    
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Use the Builder class for convenient dialog construction
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setMessage("The WiFi Direct option is currently disabled and is essential to this application." +
        		"\r\nWould you like to turn it on now?")
               .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                   @Override
				public void onClick(DialogInterface dialog, int id) 
                   {
                   startActivity(new Intent(Settings.ACTION_SETTINGS)); //take the user to the settings screen  
                   }
               })
               .setNegativeButton("No", new DialogInterface.OnClickListener() {
                   @Override
				public void onClick(DialogInterface dialog, int id) 
                   {
                       // User cancelled the dialog
                   }
               });
        
        // Create the AlertDialog object and return it
        return builder.create();
    }
}