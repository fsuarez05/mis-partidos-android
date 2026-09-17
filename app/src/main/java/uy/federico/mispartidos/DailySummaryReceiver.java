package uy.federico.mispartidos;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DailySummaryReceiver extends BroadcastReceiver {
    private static final String CHANNEL="resumen_diario";
    @Override public void onReceive(Context context,Intent intent){AppStore store=new AppStore(context);if(!store.dailySummaryEnabled())return;SimpleDateFormat date=new SimpleDateFormat("yyyyMMdd",Locale.US),hour=new SimpleDateFormat("HH:mm",Locale.getDefault());String today=date.format(new Date());List<Match> matches=new ArrayList<>();for(Match m:store.upcoming())if(today.equals(date.format(new Date(m.kickoff))))matches.add(m);if(!matches.isEmpty()){StringBuilder detail=new StringBuilder();for(int i=0;i<Math.min(3,matches.size());i++){if(i>0)detail.append(" · ");Match m=matches.get(i);detail.append(hour.format(new Date(m.kickoff))).append(" ").append(m.team);}if(matches.size()>3)detail.append(" · +").append(matches.size()-3);NotificationManager nm=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);nm.createNotificationChannel(new NotificationChannel(CHANNEL,"Resumen diario",NotificationManager.IMPORTANCE_DEFAULT));PendingIntent open=PendingIntent.getActivity(context,90231,new Intent(context,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);android.app.Notification n=new android.app.Notification.Builder(context,CHANNEL).setSmallIcon(R.drawable.ic_notification).setContentTitle(matches.size()==1?"⚽ Tenés un partido hoy":"⚽ Tenés "+matches.size()+" partidos hoy").setContentText(detail.toString()).setStyle(new android.app.Notification.BigTextStyle().bigText(detail.toString())).setContentIntent(open).setAutoCancel(true).build();nm.notify(90231,n);}DailySummaryScheduler.schedule(context);}
}
