package alizarchik.alex.photogallery.broadcasts_and_services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import alizarchik.alex.photogallery.dbs.QueryPreferences;

/**
 * Created by aoalizarchik.
 */

public class StartupReceiver extends BroadcastReceiver {

    private static final String TAG = "StartupReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {

        boolean isOn = QueryPreferences.isAlarmOn(context);
        PollService.setServiceAlarm(context, isOn);

        Log.i(TAG, "Received broadcast intent: " + intent.getAction());
    }
}
