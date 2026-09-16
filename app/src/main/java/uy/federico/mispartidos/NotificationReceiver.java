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
        Intent open = new Intent(context, MainActivity.class);
        PendingIntent content = PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String when = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(kickoff));
        android.app.Notification n = new android.app.Notification.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("⚽ En " + minutes + " minutos juega " + team)
                .setContentText(team + " vs. " + opponent + " · " + when).setAutoCancel(true)
                .setContentIntent(content).build();
        nm.notify((int) (kickoff % Integer.MAX_VALUE), n);
    }
}
