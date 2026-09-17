package uy.federico.mispartidos;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        AlarmScheduler.scheduleAll(context);
        BackgroundSyncScheduler.schedule(context);
        DailySummaryScheduler.schedule(context);
        MatchWidgetProvider.updateAll(context);
    }
}
