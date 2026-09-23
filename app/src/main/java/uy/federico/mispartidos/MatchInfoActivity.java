package uy.federico.mispartidos;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;

public class MatchInfoActivity extends Activity {
    private Match match;
    private LinearLayout content;
    private Button refresh;
    private ProgressBar progress;
    private boolean loading;
    private MatchSummary lastSummary;

    @Override protected void onCreate(Bundle state){super.onCreate(state);try{match=Match.fromJson(new JSONObject(getIntent().getStringExtra("match")));}catch(Exception e){finish();return;}build();lastSummary=new AppStore(this).cachedMatchSummary(match.fixtureId);if(lastSummary!=null)render(lastSummary);load(false);}

    private void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.rgb(248,250,252));
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(8),dp(8),dp(12),dp(8));bar.setBackgroundColor(Color.rgb(15,23,42));
        Button back=button("‹");back.setTextSize(28);back.setTextColor(Color.WHITE);back.setBackgroundColor(Color.TRANSPARENT);back.setOnClickListener(v->finish());
        TextView title=text("Información del partido",20,Color.WHITE,true);bar.addView(back,new LinearLayout.LayoutParams(dp(54),dp(54)));bar.addView(title,new LinearLayout.LayoutParams(0,-2,1));root.addView(bar);
        ScrollView scroll=new ScrollView(this);content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(18),dp(18),dp(18),dp(26));scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout actions=new LinearLayout(this);actions.setPadding(dp(12),dp(8),dp(12),dp(12));Button google=button("Buscar en Google");google.setOnClickListener(v->searchGoogle());refresh=button("Actualizar información");refresh.setOnClickListener(v->load(true));actions.addView(google,new LinearLayout.LayoutParams(0,-2,1));actions.addView(refresh,new LinearLayout.LayoutParams(0,-2,1));root.addView(actions);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setIndeterminate(true);progress.setVisibility(View.GONE);root.addView(progress,new LinearLayout.LayoutParams(-1,dp(3)));setContentView(root);
    }

    private void load(boolean force){if(loading)return;loading=true;refresh.setEnabled(false);progress.setVisibility(View.VISIBLE);if(lastSummary==null)showLoading(force);ApiClient.matchSummary(this,match,force,(summary,error)->{loading=false;refresh.setEnabled(true);progress.setVisibility(View.GONE);if(summary!=null){if(lastSummary==null||summary.informationScore()>=lastSummary.informationScore())lastSummary=summary;render(lastSummary);}else if(lastSummary!=null){render(lastSummary);Toast.makeText(this,"No se pudo actualizar. Se muestra la última información disponible.",Toast.LENGTH_LONG).show();}else renderError(error);});}
    private void showLoading(boolean refreshRequest){content.removeAllViews();addHeader();TextView value=text(refreshRequest?"Actualizando información…":"Buscando información del partido…",16,Color.DKGRAY,false);value.setGravity(Gravity.CENTER);value.setPadding(0,dp(38),0,dp(38));content.addView(value);}
    private void addHeader(){TextView game=text(match.local()+" vs. "+match.visitante(),24,Color.rgb(15,23,42),true);game.setGravity(Gravity.CENTER);content.addView(game);TextView meta=text(match.competition+"\n"+dateText(),15,Color.GRAY,false);meta.setGravity(Gravity.CENTER);meta.setPadding(0,dp(7),0,dp(16));content.addView(meta);}
    private String dateText(){SimpleDateFormat f=new SimpleDateFormat("EEEE d 'de' MMMM · HH:mm",new Locale("es","UY"));f.setTimeZone(java.util.TimeZone.getTimeZone("America/Montevideo"));String s=f.format(new Date(match.kickoff));return s.substring(0,1).toUpperCase(new Locale("es","UY"))+s.substring(1);}

    private void render(MatchSummary s){content.removeAllViews();addHeader();if(!s.found){TextView empty=text("Todavía no encontramos información suficiente sobre este partido.",17,Color.rgb(51,65,85),false);empty.setGravity(Gravity.CENTER);empty.setPadding(dp(8),dp(30),dp(8),dp(30));content.addView(empty);addUpdated(s.updated);return;}if(s.fallback)addNotice("No se pudo actualizar la información. Se muestra la última información disponible.");else if(s.informationScore()<7)addNotice("La información disponible para este partido es limitada.");
        section("Contexto",valueOrFallback(s.context));section("Cómo llega "+nameOr(s.home.name,match.local()),teamText(s.home));section("Cómo llega "+nameOr(s.away.name,match.visitante()),teamText(s.away));
        if(!s.news.isEmpty()){StringBuilder b=new StringBuilder();for(String n:s.news)b.append("• ").append(n).append('\n');section("Últimas novedades",trim(b));}
        if(s.absences.isEmpty())section("Bajas confirmadas","Sin bajas confirmadas\n\nEsto indica únicamente que no se encontraron bajas confirmadas.");else{StringBuilder b=new StringBuilder();for(MatchSummary.Absence a:s.absences){b.append("• ");if(!a.team.isEmpty())b.append(a.team).append(": ");b.append(a.player.isEmpty()?"Jugador sin especificar":a.player);if(!a.reason.isEmpty())b.append(" — ").append(a.reason);b.append('\n');}section("Bajas confirmadas",trim(b));}
        if(!s.sources.isEmpty()){title("Fuentes");for(MatchSummary.Source source:s.sources)addSource(source);}
        addUpdated(s.updated);
    }
    private void renderError(String error){content.removeAllViews();addHeader();TextView value=text(error==null?"No pudimos obtener la información del partido. Intentá nuevamente más tarde.":error,17,Color.rgb(51,65,85),false);value.setGravity(Gravity.CENTER);value.setPadding(dp(8),dp(30),dp(8),dp(30));content.addView(value);}
    private void section(String heading,String value){title(heading);TextView body=text(value,16,Color.rgb(51,65,85),false);body.setLineSpacing(0,1.15f);body.setPadding(0,dp(4),0,dp(12));content.addView(body);}
    private void title(String value){TextView t=text(value,18,Color.rgb(15,23,42),true);t.setPadding(0,dp(12),0,dp(2));content.addView(t);}
    private void addNotice(String value){TextView n=text(value,14,Color.rgb(146,64,14),false);n.setPadding(dp(12),dp(10),dp(12),dp(10));android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();bg.setColor(Color.rgb(255,247,237));bg.setCornerRadius(dp(10));n.setBackground(bg);content.addView(n);}
    private void addSource(MatchSummary.Source s){TextView link=text(s.title.isEmpty()?"Abrir fuente":s.title,15,Color.rgb(37,99,235),true);if(!s.date.isEmpty())link.setText(link.getText()+"\n"+s.date);link.setPadding(dp(10),dp(10),dp(10),dp(10));link.setOnClickListener(v->openUrl(s.url));content.addView(link);}
    private void addUpdated(String iso){String display=formatUpdated(iso);if(display.isEmpty())return;TextView value=text("Actualizado: "+display,13,Color.GRAY,false);value.setGravity(Gravity.CENTER);value.setPadding(0,dp(24),0,dp(8));content.addView(value);}
    private String formatUpdated(String iso){try{ZoneId zone=ZoneId.of("America/Montevideo");java.time.ZonedDateTime z=Instant.parse(iso).atZone(zone);LocalDate today=LocalDate.now(zone);String time=z.format(DateTimeFormatter.ofPattern("HH:mm",new Locale("es","UY")));if(z.toLocalDate().equals(today))return"hoy "+time;return z.format(DateTimeFormatter.ofPattern("dd/MM/yyyy · HH:mm",new Locale("es","UY")));}catch(Exception e){return iso==null?"":iso;}}
    private String teamText(MatchSummary.TeamInfo t){return t.form.trim().isEmpty()||"sin_informacion".equals(t.quality)?"No encontramos información reciente suficiente.":t.form;}
    private String valueOrFallback(String value){return value==null||value.trim().isEmpty()?"No encontramos información reciente suficiente.":value;}
    private String nameOr(String value,String fallback){return value==null||value.trim().isEmpty()?fallback:value;}
    private String trim(StringBuilder b){return b.toString().trim();}
    private void searchGoogle(){String day=new SimpleDateFormat("dd/MM/yyyy",new Locale("es","UY")).format(new Date(match.kickoff));openUrl("https://www.google.com/search?q="+Uri.encode(match.local()+" vs "+match.visitante()+" "+day));}
    private void openUrl(String value){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(value)));}catch(Exception e){Toast.makeText(this,"No encontré un navegador",Toast.LENGTH_LONG).show();}}
    private TextView text(String s,int size,int color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);return b;}
    private int dp(int n){return(int)(n*getResources().getDisplayMetrics().density+.5f);}
}
