package com.autospend.ai;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // NotificationListenerService starts automatically by Android
        // This receiver ensures the app is initialized after reboot
    }
}
