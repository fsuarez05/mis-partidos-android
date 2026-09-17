package uy.federico.mispartidos;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MatchWidgetProvider extends AppWidgetProvider {
    @Override public void onUpdate(Context context,AppWidgetManager manager,int[]ids){for(int id:ids)update(context,manager,id);}
    static void updateAll(Context context){AppWidgetManager manager=AppWidgetManager.getInstance(context);int[]ids=manager.getAppWidgetIds(new ComponentName(context,MatchWidgetProvider.class));for(int id:ids)update(context,manager,id);}
    private static void update(Context context,AppWidgetManager manager,int id){RemoteViews views=new RemoteViews(context.getPackageName(),R.layout.widget_next_match);List<Match>matches=new AppStore(context).upcoming();if(matches.isEmpty()){views.setTextViewText(R.id.widget_match,"No hay próximos partidos");views.setTextViewText(R.id.widget_time,"Abrí la app para actualizar");views.setTextViewText(R.id.widget_competition,"");}else{Match m=matches.get(0);views.setTextViewText(R.id.widget_match,m.opponent.isEmpty()?m.team:m.team+" vs. "+m.opponent);views.setTextViewText(R.id.widget_time,AppStore.isInProgress(m)?"🔴 Jugando ahora":new SimpleDateFormat("EEE d MMM · HH:mm",new Locale("es","UY")).format(new Date(m.kickoff)));views.setTextViewText(R.id.widget_competition,m.competition);}PendingIntent open=PendingIntent.getActivity(context,0,new Intent(context,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);views.setOnClickPendingIntent(R.id.widget_root,open);manager.updateAppWidget(id,views);}
}
