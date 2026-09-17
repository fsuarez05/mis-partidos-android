package uy.federico.mispartidos;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import java.util.Calendar;

class DailySummaryScheduler {
    private static PendingIntent intent(Context context){return PendingIntent.getBroadcast(context,90231,new Intent(context,DailySummaryReceiver.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    static void schedule(Context context){AlarmManager alarm=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);alarm.cancel(intent(context));AppStore store=new AppStore(context);if(!store.dailySummaryEnabled())return;Calendar next=Calendar.getInstance();next.set(Calendar.HOUR_OF_DAY,store.dailySummaryHour());next.set(Calendar.MINUTE,store.dailySummaryMinute());next.set(Calendar.SECOND,0);next.set(Calendar.MILLISECOND,0);if(next.getTimeInMillis()<=System.currentTimeMillis())next.add(Calendar.DAY_OF_YEAR,1);alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next.getTimeInMillis(),intent(context));}
}
