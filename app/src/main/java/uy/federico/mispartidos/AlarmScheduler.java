package uy.federico.mispartidos;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import java.util.HashSet;
import java.util.Set;

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
        // Cancela primero todas las alarmas conocidas. Esto evita que un equipo
        // eliminado siga notificando o que sobreviva el horario anterior.
        Set<String> oldIds = new HashSet<>(store.scheduledAlarmIds());
        for (Match match : store.apiFavoriteMatches()) oldIds.add(String.valueOf(match.id));
        for (String value : oldIds) {
            try { cancel(context, Long.parseLong(value)); } catch (NumberFormatException ignored) {}
        }
        Set<String> currentIds = new HashSet<>();
        for (Match match : store.upcoming()) {
            schedule(context, match, store.noticeMinutesFor(match.team));
            currentIds.add(String.valueOf(match.id));
        }
        store.saveScheduledAlarmIds(currentIds);
    }

    private static void cancel(Context context, long matchId) {
        Intent intent = new Intent(context, NotificationReceiver.class);
        PendingIntent pending = PendingIntent.getBroadcast(context,
                (int) (matchId % Integer.MAX_VALUE), intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pending != null) {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            alarmManager.cancel(pending);
            pending.cancel();
        }
    }
}
