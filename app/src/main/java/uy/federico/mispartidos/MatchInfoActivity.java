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
    private boolean finished;
    private MatchSummary lastSummary;

    @Override protected void onCreate(Bundle state){super.onCreate(state);try{match=Match.fromJson(new JSONObject(getIntent().getStringExtra("match")));}catch(Exception e){finish();return;}finished=getIntent().getBooleanExtra("finished",false);build();lastSummary=new AppStore(this).cachedMatchSummary(match.fixtureId);if(lastSummary!=null)render(lastSummary);else if(finished)renderFinishedWithoutSummary();else load(false);}

    private void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.rgb(248,250,252));
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(8),dp(8),dp(12),dp(8));bar.setBackgroundColor(Color.rgb(15,23,42));
        Button back=button("‹");back.setTextSize(28);back.setTextColor(Color.WHITE);back.setBackgroundColor(Color.TRANSPARENT);back.setOnClickListener(v->finish());
        TextView title=text("Información del partido",20,Color.WHITE,true);bar.addView(back,new LinearLayout.LayoutParams(dp(54),dp(54)));bar.addView(title,new LinearLayout.LayoutParams(0,-2,1));root.addView(bar);
        LinearLayout actions=new LinearLayout(this);actions.setPadding(dp(12),dp(8),dp(12),dp(8));Button google=button("Buscar en Google");google.setOnClickListener(v->searchGoogle());refresh=button("Actualizar información");refresh.setOnClickListener(v->load(true));refresh.setVisibility(finished?View.GONE:View.VISIBLE);actions.addView(google,new LinearLayout.LayoutParams(0,-2,1));actions.addView(refresh,new LinearLayout.LayoutParams(0,-2,1));root.addView(actions);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setIndeterminate(true);progress.setVisibility(View.GONE);root.addView(progress,new LinearLayout.LayoutParams(-1,dp(3)));
        ScrollView scroll=new ScrollView(this);content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(18),dp(18),dp(18),dp(26));scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
    }

    private void load(boolean force){
        if(finished){if(lastSummary!=null)render(lastSummary);else renderFinishedWithoutSummary();return;}
        if(loading)return;
        loading=true;refresh.setEnabled(false);progress.setVisibility(View.VISIBLE);
        if(lastSummary==null)showLoading(force);
        ApiClient.matchSummary(this,match,force,(summary,error)->{
            loading=false;refresh.setEnabled(true);progress.setVisibility(View.GONE);
            if("TAVILY_MONTHLY_LIMIT".equals(error)){
                if(lastSummary!=null){render(lastSummary);addNotice("No se pudo actualizar: se alcanzó la cuota mensual de Tavily. Se conserva la información anterior.");}
                else renderError("Se agotó la cuota mensual estimada de Tavily.");
                new android.app.AlertDialog.Builder(this)
                    .setTitle("Cuota mensual de Tavily agotada")
                    .setMessage("Se alcanzó la cuota mensual configurada. Para evitar gastos adicionales, no se realizarán nuevas búsquedas hasta el próximo mes. Podés seguir consultando información guardada.")
                    .setPositiveButton("Entendido",(dialog,which)->{})
                    .show();
                return;
            }
            if(error!=null&&error.startsWith("TAVILY_DAILY_LIMIT")){
                if(lastSummary!=null){render(lastSummary);addNotice("No se pudo actualizar: se alcanzó el límite diario de Tavily. Se conserva la información anterior.");}
                else renderError("Se alcanzó el presupuesto diario de Tavily.");
                String aviso="Se alcanzó el presupuesto de búsquedas disponible para hoy.";
                String[] datos=error.split("\\|");
                if(datos.length==4){
                    try{
                        int usados=Integer.parseInt(datos[1]),total=Integer.parseInt(datos[2]),disponibles=Integer.parseInt(datos[3]);
                        if(usados>=0&&total>0&&disponibles>=0)
                            aviso+="\n\nConsumo estimado del mes: "+usados+" de "+total+" créditos. Disponibles según el presupuesto de hoy: "+disponibles+".";
                    }catch(NumberFormatException ignored){}
                }
                new android.app.AlertDialog.Builder(this)
                    .setTitle("Límite diario de Tavily")
                    .setMessage(aviso)
                    .setPositiveButton("Entendido",(dialog,which)->{})
                    .show();
                return;
            }
            if(summary!=null){lastSummary=summary;render(lastSummary);}
            else if(lastSummary!=null){render(lastSummary);addNotice((error==null?"No se pudo actualizar.":error)+" Se conserva la información anterior.");}
            else renderError(error);
        });
    }
    private void renderFinishedWithoutSummary(){
        content.removeAllViews();addHeader();
        addNotice("Este encuentro figura entre los resultados recientes. No se consulta la IA para reconstruir la previa.");
        section("Resultado y resumen","Usá Buscar en Google para consultar el marcador, los goles y el resumen del partido.");
    }
    private void showLoading(boolean refreshRequest){content.removeAllViews();addHeader();TextView value=text(refreshRequest?"Actualizando información…":"Buscando información del partido…",16,Color.DKGRAY,false);value.setGravity(Gravity.CENTER);value.setPadding(0,dp(38),0,dp(38));content.addView(value);}
    private void addHeader(){
        TextView game=text(match.local()+" vs. "+match.visitante(),24,Color.rgb(15,23,42),true);
        game.setGravity(Gravity.CENTER);content.addView(game);
        String phase;
        long now=System.currentTimeMillis();
        if(finished)phase="Resultado reciente · seguimiento en Google";
        else if(AppStore.isInProgress(match))phase="En juego · seguimiento en Google";
        else if(now<match.kickoff)phase="Próximo partido";
        else if(match.hasScore())phase="Resultado registrado · consultar estado en Google";
        else phase="Partido programado o pendiente de resultado";
        TextView state=text(phase,14,Color.rgb(37,99,235),true);
        state.setGravity(Gravity.CENTER);state.setPadding(0,dp(8),0,dp(2));content.addView(state);
        if(match.hasScore()){
            TextView score=text(match.homeScore+" - "+match.awayScore,30,Color.rgb(15,23,42),true);
            score.setGravity(Gravity.CENTER);content.addView(score);
        }
        TextView meta=text(match.competition+"\n"+dateText(),15,Color.GRAY,false);
        meta.setGravity(Gravity.CENTER);meta.setPadding(0,dp(7),0,dp(16));content.addView(meta);
    }
    private String dateText(){SimpleDateFormat f=new SimpleDateFormat("EEEE d 'de' MMMM · HH:mm",new Locale("es","UY"));f.setTimeZone(java.util.TimeZone.getTimeZone("America/Montevideo"));String s=f.format(new Date(match.kickoff));return s.substring(0,1).toUpperCase(new Locale("es","UY"))+s.substring(1);}

    private void render(MatchSummary s){content.removeAllViews();addHeader();showUpdateStatus(s);if(!s.found){TextView empty=text("Todavía no encontramos información suficiente sobre este partido.",17,Color.rgb(51,65,85),false);empty.setGravity(Gravity.CENTER);empty.setPadding(dp(8),dp(30),dp(8),dp(30));content.addView(empty);addUpdated(s.updated);return;}if(s.fallback&&s.updateReason.isEmpty())addNotice(s.fallbackReason!=null&&!s.fallbackReason.trim().isEmpty()?s.fallbackReason+" Se muestra la información guardada.":"No se pudo actualizar la información. Se muestra la última información disponible.");else if(s.informationScore()<7)addNotice("La información disponible para este partido es limitada.");
        boolean started=System.currentTimeMillis()>=match.kickoff;
        if(started)addNotice("Estos datos corresponden a la previa del encuentro. Para consultar el resultado, los goles y el desarrollo actualizado, usá Google.");
        section(started?"Contexto previo al partido":"Contexto",valueOrFallback(s.context));
        section((started?"Cómo llegaba ":"Cómo llega ")+nameOr(s.home.name,match.local()),teamText(s.home));
        section((started?"Cómo llegaba ":"Cómo llega ")+nameOr(s.away.name,match.visitante()),teamText(s.away));
        if(!finished){
            String titulo=AppStore.isInProgress(match)?"📺 Dónde verlo ahora":"📺 Dónde verlo";
            if(!s.broadcastText.trim().isEmpty())section(titulo,s.broadcastText+(s.broadcastCountry.trim().isEmpty()?"":"\nDisponibilidad: "+s.broadcastCountry)+(s.broadcastPreserved?"\n"+(s.broadcastReason.isEmpty()?"Se conserva una confirmación anterior.":s.broadcastReason):""));
            else section(titulo,"Transmisión no confirmada para Uruguay.");
        }
        if(!s.news.isEmpty()){StringBuilder b=new StringBuilder();for(String n:s.news)b.append("• ").append(n).append('\n');section(started?"Novedades consultadas":"Últimas novedades",trim(b));}
        if(s.absences.isEmpty())section(started?"Bajas reportadas antes del partido":"Bajas reportadas","No se encontraron bajas verificables en las fuentes consultadas. Esto no significa que ambos equipos tengan el plantel completo.");else{java.util.LinkedHashMap<String,java.util.ArrayList<MatchSummary.Absence>> grupos=new java.util.LinkedHashMap<>();for(MatchSummary.Absence a:s.absences){String equipo=a.team==null||a.team.trim().isEmpty()?"Equipo no especificado":a.team.trim();if(!grupos.containsKey(equipo))grupos.put(equipo,new java.util.ArrayList<>());grupos.get(equipo).add(a);}title(started?"Bajas reportadas antes del partido":"Bajas reportadas");for(java.util.Map.Entry<String,java.util.ArrayList<MatchSummary.Absence>> grupo:grupos.entrySet()){TextView encabezado=text(grupo.getKey(),16,Color.rgb(15,23,42),true);encabezado.setPadding(0,dp(12),0,dp(4));content.addView(encabezado);StringBuilder lista=new StringBuilder();for(MatchSummary.Absence a:grupo.getValue()){lista.append("• ").append(a.player==null||a.player.trim().isEmpty()?"Jugador sin especificar":a.player);if(a.reason!=null&&!a.reason.trim().isEmpty())lista.append(" — ").append(a.reason);lista.append('\n');}TextView detalle=text(trim(lista),16,Color.rgb(51,65,85),false);detalle.setLineSpacing(0,1.15f);detalle.setPadding(dp(12),0,0,dp(8));content.addView(detalle);}}
        if(!s.sources.isEmpty()){title("Fuentes");for(MatchSummary.Source source:s.sources)addSource(source);}
        addUpdated(s.updated);
    }
    private void showUpdateStatus(MatchSummary s){
        if(s.updateStatus==null||s.updateStatus.isEmpty())return;
        String status=s.updateStatus;
        if("informacion_actualizada".equals(status))addNotice("Información actualizada. "+(s.updateReason==null?"":s.updateReason));
        else if("sin_informacion_nueva".equals(status))addNotice("Sin información nueva. "+(s.updateReason==null?"":s.updateReason));
        else if("informacion_anterior".equals(status)||s.fallback)addNotice("Se conserva información anterior. "+(s.updateReason==null||s.updateReason.isEmpty()?s.fallbackReason:s.updateReason));
        else if("parcial".equals(status))addNotice("Actualización parcial. "+(s.updateReason==null?"":s.updateReason));
        else if("cache".equals(status)||s.fromCache)addNotice("Información guardada. "+(s.updateReason==null?"":s.updateReason));
    }
    private void renderError(String error){content.removeAllViews();addHeader();TextView value=text(error==null?"No pudimos obtener la información del partido. Intentá nuevamente más tarde.":error,17,Color.rgb(51,65,85),false);value.setGravity(Gravity.CENTER);value.setPadding(dp(8),dp(30),dp(8),dp(30));content.addView(value);}
    private void section(String heading,String value){title(heading);TextView body=text(value,16,Color.rgb(51,65,85),false);body.setLineSpacing(0,1.15f);body.setPadding(0,dp(4),0,dp(12));content.addView(body);}
    private void title(String value){TextView t=text(value,18,Color.rgb(15,23,42),true);t.setPadding(0,dp(12),0,dp(2));content.addView(t);}
    private void addNotice(String value){TextView n=text(value,14,Color.rgb(146,64,14),false);n.setPadding(dp(12),dp(10),dp(12),dp(10));android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();bg.setColor(Color.rgb(255,247,237));bg.setCornerRadius(dp(10));n.setBackground(bg);content.addView(n);}
    private void addSource(MatchSummary.Source s){TextView link=text(s.title.isEmpty()?"Abrir fuente":s.title,15,Color.rgb(37,99,235),true);if(!s.date.isEmpty())link.setText(link.getText()+"\n"+s.date);link.setPadding(dp(10),dp(10),dp(10),dp(10));link.setOnClickListener(v->openUrl(s.url));content.addView(link);}
    private void addUpdated(String iso){
        long cachedAt=new AppStore(this).cachedMatchSummaryTime(match.fixtureId);
        String display=cachedAt>0?formatUpdated(Instant.ofEpochMilli(cachedAt).toString()):formatUpdated(iso);
        String message=cachedAt>0?(lastSummary!=null&&lastSummary.fromCache?"Fecha del resumen guardado en el servidor: ":"Última consulta de información: "):"Fecha informada por el servicio: ";
        if(display.isEmpty()){display="no disponible";message="Fecha de consulta: ";}
        TextView value=text(message+display+" · Información guardada en el dispositivo",13,Color.GRAY,false);
        value.setGravity(Gravity.CENTER);value.setPadding(0,dp(24),0,dp(8));content.addView(value);
    }
    private String formatUpdated(String iso){try{ZoneId zone=ZoneId.of("America/Montevideo");java.time.ZonedDateTime z=Instant.parse(iso).atZone(zone);LocalDate today=LocalDate.now(zone);String time=z.format(DateTimeFormatter.ofPattern("HH:mm",new Locale("es","UY")));if(z.toLocalDate().equals(today))return"hoy "+time;return z.format(DateTimeFormatter.ofPattern("dd/MM/yyyy · HH:mm",new Locale("es","UY")));}catch(Exception e){return iso==null?"":iso;}}
    private String teamText(MatchSummary.TeamInfo t){return t.form.trim().isEmpty()||"sin_informacion".equalsIgnoreCase(t.quality)?"No encontramos información reciente suficiente.":t.form;}
    private String valueOrFallback(String value){return value==null||value.trim().isEmpty()?"No encontramos información reciente suficiente.":value;}
    private String nameOr(String value,String fallback){return value==null||value.trim().isEmpty()?fallback:value;}
    private String trim(StringBuilder b){return b.toString().trim();}
    private void searchGoogle(){
        String local=match.local(),visitor=match.visitante();
        String query=(visitor==null||visitor.trim().isEmpty()?local:local+" vs "+visitor).replaceAll("\\s+"," ").trim();
        openUrl("https://www.google.com/search?q="+Uri.encode(query));
    }
    private void openUrl(String value){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(value)));}catch(Exception e){Toast.makeText(this,"No encontré un navegador",Toast.LENGTH_LONG).show();}}
    private TextView text(String s,int size,int color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);return b;}
    private int dp(int n){return(int)(n*getResources().getDisplayMetrics().density+.5f);}
}
