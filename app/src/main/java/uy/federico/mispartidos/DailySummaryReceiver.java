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
    @Override public void onReceive(Context context,Intent intent){AppStore store=new AppStore(context);if(!store.dailySummaryEnabled())return;SimpleDateFormat date=new SimpleDateFormat("yyyyMMdd",Locale.US),hour=new SimpleDateFormat("HH:mm",Locale.getDefault());String today=date.format(new Date());List<Match> matches=new ArrayList<>();for(Match m:store.upcoming())if(today.equals(date.format(new Date(m.kickoff))))matches.add(m);if(!matches.isEmpty()){StringBuilder detail=new StringBuilder();android.app.Notification.InboxStyle inbox=new android.app.Notification.InboxStyle();for(int i=0;i<Math.min(5,matches.size());i++){Match m=matches.get(i);String game=m.local()+" vs. "+m.visitante();String line=hour.format(new Date(m.kickoff))+" · "+game;if(i<3){if(detail.length()>0)detail.append(" · ");detail.append(line);}inbox.addLine(line);}if(matches.size()>5)inbox.setSummaryText("+"+(matches.size()-5)+" partidos más");NotificationManager nm=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);nm.createNotificationChannel(new NotificationChannel(CHANNEL,"Resumen diario",NotificationManager.IMPORTANCE_DEFAULT));PendingIntent open=PendingIntent.getActivity(context,90231,new Intent(context,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);android.app.Notification n=new android.app.Notification.Builder(context,CHANNEL).setSmallIcon(R.drawable.ic_notification).setContentTitle(matches.size()==1?"⚽ Tenés un partido hoy":"⚽ Tenés "+matches.size()+" partidos hoy").setContentText(detail.toString()).setStyle(inbox).setContentIntent(open).setAutoCancel(true).build();nm.notify(90231,n);}DailySummaryScheduler.schedule(context);}
}
