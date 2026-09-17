package uy.federico.mispartidos;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class NotificationReceiver extends BroadcastReceiver {
    static final String CHANNEL = "partidos";
    @Override public void onReceive(Context context, Intent intent) {
        String team = intent.getStringExtra("team"), opponent = intent.getStringExtra("opponent");
        int minutes = intent.getIntExtra("minutes", 60);
        long kickoff = intent.getLongExtra("kickoff", 0);
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Avisos de partidos", NotificationManager.IMPORTANCE_HIGH));
        Intent open = new Intent(context, MainActivity.class)
                .putExtra("open_match", true).putExtra("team", team)
                .putExtra("opponent", opponent).putExtra("kickoff", kickoff)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent content = PendingIntent.getActivity(context, (int)(kickoff % Integer.MAX_VALUE), open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String when = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(kickoff));
        boolean insideWindow = intent.getBooleanExtra("inside_window", false);
        long remaining = Math.max(1, (kickoff-System.currentTimeMillis()+59_999)/60_000);
        boolean sharedMatch=opponent==null||opponent.isEmpty();
        String title=sharedMatch?(insideWindow?"⚽ Partido en "+remaining+" min":"⚽ Partido en "+minutes+" minutos"):(insideWindow ? "⚽ " + team + " juega en " + remaining + " min" : "⚽ En " + minutes + " minutos juega " + team);
        String detail=sharedMatch?team+" · "+when:team + " vs. " + opponent + " · " + when;
        android.app.Notification n = new android.app.Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification).setContentTitle(title)
                .setContentText(detail).setAutoCancel(true)
                .setContentIntent(content).build();
        nm.notify((int) (kickoff % Integer.MAX_VALUE), n);
    }
}
