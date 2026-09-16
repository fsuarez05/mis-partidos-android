package uy.federico.mispartidos;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

public class AlarmScheduler {
    static void schedule(Context context, Match match, int minutesBefore) {
        long trigger = match.kickoff - minutesBefore * 60_000L;
        Intent i = new Intent(context, NotificationReceiver.class)
                .putExtra("team", match.team).putExtra("opponent", match.opponent)
                .putExtra("kickoff", match.kickoff).putExtra("minutes", minutesBefore);
        if (match.kickoff <= System.currentTimeMillis()) return;
        if (trigger <= System.currentTimeMillis()) {
            AppStore store = new AppStore(context);
            if (!store.immediateNoticeShown(match, minutesBefore)) {
                context.sendBroadcast(i.putExtra("inside_window", true));
                store.markImmediateNoticeShown(match, minutesBefore);
            }
            return;
        }
        PendingIntent pi = PendingIntent.getBroadcast(context, (int) (match.id % Integer.MAX_VALUE), i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi);
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi);
    }

    static void scheduleAll(Context context) {
        AppStore store = new AppStore(context);
        for (Match match : store.upcoming()) schedule(context, match, store.noticeMinutes());
    }
}
