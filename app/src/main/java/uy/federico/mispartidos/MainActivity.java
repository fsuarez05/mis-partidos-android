package uy.federico.mispartidos;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {
    private AppStore store;
    private LinearLayout list;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("EEE d MMM · HH:mm", new Locale("es", "UY"));

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); store = new AppStore(this);
        buildScreen(); requestNotificationPermission(); AlarmScheduler.scheduleAll(this);
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.rgb(248,250,252));
        TextView header = text("⚽  Mis Partidos", 25, Color.WHITE, true); header.setPadding(dp(20), dp(20), dp(20), dp(18)); header.setBackgroundColor(Color.rgb(15,23,42));
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout actions = new LinearLayout(this); actions.setPadding(dp(10),dp(8),dp(10),dp(6));
        Button teams = button("⚙️ Configuración"); teams.setOnClickListener(v -> showSettings());
        Button add = button("＋ Partido"); add.setOnClickListener(v -> addManualMatch());
        actions.addView(teams, weight()); actions.addView(add, weight()); root.addView(actions);
        TextView info = text(summaryText()+"\nVersión de prueba · Los partidos marcados como dato de prueba no provienen aún de una API.", 13, Color.DKGRAY, false);
        info.setPadding(dp(16),dp(6),dp(16),dp(10)); root.addView(info);
        ScrollView scroll = new ScrollView(this); list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); list.setPadding(dp(12),0,dp(12),dp(20)); scroll.addView(list); root.addView(scroll, new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root); refresh();
    }

    private void refresh() {
        list.removeAllViews(); List<Match> matches = store.upcoming();
        addSectionTitle("Próximos de mis equipos");
        if (matches.isEmpty()) { TextView empty = text("No hay próximos partidos. Elegí equipos o agregá uno manualmente.",16,Color.DKGRAY,false); empty.setPadding(dp(14),dp(12),dp(14),dp(18)); list.addView(empty); }
        else for (Match m : matches) list.addView(matchCard(m));
        addSectionTitle("Partidos de hoy · competiciones elegidas");
        List<Match> today=store.todayByCompetitions();
        if(today.isEmpty()){TextView empty=text("Elegí competiciones para ver aquí partidos destacados del día.",16,Color.DKGRAY,false);empty.setPadding(dp(14),dp(12),dp(14),dp(18));list.addView(empty);}
        else for(Match m:today)list.addView(matchCard(m));
    }

    private void addSectionTitle(String value){TextView title=text(value,17,Color.rgb(15,23,42),true);title.setPadding(dp(4),dp(14),dp(4),dp(10));list.addView(title);}

    private View matchCard(Match m) {
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(16),dp(14),dp(16),dp(14));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable(); bg.setColor(Color.WHITE); bg.setCornerRadius(dp(14)); bg.setStroke(dp(1), Color.rgb(226,232,240)); card.setBackground(bg);
        TextView team = text(m.team,18,Color.rgb(15,23,42),true); TextView versus = text(m.team + "  vs.  " + m.opponent,16,Color.rgb(30,41,59),false);
        TextView date = text(dateFormat.format(new Date(m.kickoff)),19,Color.rgb(180,120,0),true);
        TextView comp = text(m.competition + (m.manual ? " · manual" : ""),13,Color.GRAY,false);
        card.addView(team); card.addView(versus); card.addView(date); card.addView(comp);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,0,0,dp(10)); card.setLayoutParams(lp); return card;
    }

    private String summaryText() { return "⭐ " + store.selectedTeams().size() + " clubes · " + store.selectedClubCompetitions().size() + " competiciones · " + store.selectedNationalTeams().size() + " selecciones"; }

    private interface SelectionDone { void run(Set<String> values); }
    private void showSettings(){String[]items={"Equipos y competiciones","Tiempo de aviso · "+noticeLabel()};new AlertDialog.Builder(this).setTitle("Configuración").setItems(items,(d,pos)->{if(pos==0)configureWizard();else chooseNotice();}).setNegativeButton("Cerrar",null).show();}
    private String noticeLabel(){int m=store.noticeMinutes();return m<60?m+" min antes":(m/60)+((m==60)?" hora antes":" horas antes");}
    private void configureWizard() { chooseClubTeams(store.selectedTeams()); }

    private void chooseClubTeams(Set<String> current) {
        showSearchPicker("1 de 4 · Buscar clubes",AppStore.CLUBS,current,
                this::chooseClubCompetitions,null);
    }

    private void chooseClubCompetitions(Set<String> clubs) {
        chooseClubCompetitions(clubs,store.selectedClubCompetitions());
    }

    private void chooseClubCompetitions(Set<String> clubs,Set<String> initial) {
        Set<String>suggestions=AppStore.suggestedClubCompetitions(clubs),current=new LinkedHashSet<>(initial);
        if(current.isEmpty())current.addAll(suggestions);
        showCompetitionMenu("2 de 4 · Competiciones de clubes",AppStore.CLUB_COMPETITIONS,suggestions,current,false,
                values->chooseNationalTeams(clubs,values,store.selectedNationalTeams()),()->chooseClubTeams(clubs));
    }

    private void chooseNationalTeams(Set<String> clubs,Set<String> clubCups,Set<String> initial) {
        showSearchPicker("3 de 4 · Buscar selecciones",AppStore.NATIONAL_TEAMS,initial,
                countries->chooseNationalCompetitions(clubs,clubCups,countries),()->chooseClubCompetitions(clubs,clubCups));
    }

    private void chooseNationalCompetitions(Set<String> clubs,Set<String> clubCups,Set<String> countries) {
        Set<String>suggestions=AppStore.suggestedNationalCompetitions(countries),current=new LinkedHashSet<>(store.selectedNationalCompetitions());if(current.isEmpty())current.addAll(suggestions);
        showCompetitionMenu("4 de 4 · Competiciones de selecciones",AppStore.NATIONAL_COMPETITIONS,suggestions,current,true,values->{
            store.saveTeams(clubs);store.saveClubCompetitions(clubCups);store.saveNationalTeams(countries);store.saveNationalCompetitions(values);buildScreen();AlarmScheduler.scheduleAll(this);Toast.makeText(this,"Configuración guardada",Toast.LENGTH_SHORT).show();
        },()->chooseNationalTeams(clubs,clubCups,countries));
    }

    private void showSearchPicker(String title,String[] all,Set<String> initial,SelectionDone done,Runnable back){
        Set<String>chosen=new LinkedHashSet<>(initial);LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(18),0,dp(18),0);
        EditText search=new EditText(this);search.setHint("🔎 Escribí para buscar");ListView results=new ListView(this);results.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        box.addView(search,new LinearLayout.LayoutParams(-1,-2));box.addView(results,new LinearLayout.LayoutParams(-1,dp(390)));
        List<String>visible=new ArrayList<>();
        Runnable redraw=()->{String q=search.getText().toString().trim().toLowerCase(Locale.ROOT);visible.clear();for(String s:all)if(q.isEmpty()||s.toLowerCase(Locale.ROOT).contains(q))visible.add(s);results.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_list_item_multiple_choice,visible));for(int i=0;i<visible.size();i++)results.setItemChecked(i,chosen.contains(visible.get(i)));};
        results.setOnItemClickListener((p,v,pos,id)->{String value=visible.get(pos);if(results.isItemChecked(pos))chosen.add(value);else chosen.remove(value);});
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){redraw.run();}public void afterTextChanged(Editable e){}});redraw.run();
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(title).setView(box).setPositiveButton("Siguiente",(d,w)->done.run(chosen)).setNegativeButton(back==null?"Cancelar":"Atrás",(d,w)->{if(back!=null)back.run();}).create();
        if(back!=null)dialog.setOnCancelListener(d->back.run());dialog.show();
    }

    private void showCompetitionMenu(String title,String[]all,Set<String>suggested,Set<String>chosen,boolean selections,SelectionDone done,Runnable back){
        String[]menu={"★ Sugeridas ("+suggested.size()+")","🏆 Continentales","🌎 América del Sur","🌍 Europa","🌐 Mundo"};
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(title+" · "+chosen.size()+" elegidas").setItems(menu,(d,pos)->{
            if(pos==0)showCompetitionSubset(title,all,suggested,suggested,chosen,selections,done,back);
            else if(pos==1)showCompetitionSubset(title,all,filter(all,"CONMEBOL","UEFA"),suggested,chosen,selections,done,back);
            else if(pos==2){if(selections)showCompetitionSubset(title,all,filter(all,"América del Sur"),suggested,chosen,true,done,back);else chooseCountry(title,all,"América del Sur",suggested,chosen,done,back);}
            else if(pos==3){if(selections)showCompetitionSubset(title,all,filter(all,"Europa"),suggested,chosen,true,done,back);else chooseCountry(title,all,"Europa",suggested,chosen,done,back);}
            else showCompetitionSubset(title,all,filter(all,"Mundo"),suggested,chosen,selections,done,back);
        }).setPositiveButton(selections&&title.startsWith("4")?"Guardar":"Siguiente",(d,w)->done.run(chosen)).setNegativeButton("Atrás",(d,w)->back.run()).create();dialog.setOnCancelListener(d->back.run());dialog.show();
    }

    private void chooseCountry(String title,String[]all,String continent,Set<String>suggested,Set<String>chosen,SelectionDone done,Runnable back){
        Set<String>countries=new LinkedHashSet<>();for(String c:all)if(c.startsWith(continent+" › ")){String[]p=c.split(" › ");if(p.length>2&&!p[1].equals("CONMEBOL")&&!p[1].equals("UEFA"))countries.add(p[1]);}
        String[]items=countries.toArray(new String[0]);AlertDialog dialog=new AlertDialog.Builder(this).setTitle(continent+" · Seleccionar país").setItems(items,(d,pos)->showCompetitionSubset(title,all,filter(all,continent+" › "+items[pos]+" ›"),suggested,chosen,false,done,back)).setNegativeButton("Volver",(d,w)->showCompetitionMenu(title,all,suggested,chosen,false,done,back)).create();dialog.setOnCancelListener(d->showCompetitionMenu(title,all,suggested,chosen,false,done,back));dialog.show();
    }

    private void showCompetitionSubset(String title,String[]all,Set<String>subset,Set<String>suggested,Set<String>chosen,boolean selections,SelectionDone done,Runnable back){
        String[]values=subset.toArray(new String[0]),labels=new String[values.length];boolean[]checked=new boolean[values.length];for(int i=0;i<values.length;i++){labels[i]=AppStore.shortName(values[i]);checked[i]=chosen.contains(values[i]);}
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Seleccionar competiciones").setMultiChoiceItems(labels,checked,(d,pos,on)->{if(on)chosen.add(values[pos]);else chosen.remove(values[pos]);}).setPositiveButton("Listo",(d,w)->showCompetitionMenu(title,all,suggested,chosen,selections,done,back)).setNegativeButton("Volver",(d,w)->showCompetitionMenu(title,all,suggested,chosen,selections,done,back)).create();dialog.setOnCancelListener(d->showCompetitionMenu(title,all,suggested,chosen,selections,done,back));dialog.show();
    }

    private Set<String>filter(String[]all,String...terms){Set<String>r=new LinkedHashSet<>();for(String s:all)for(String t:terms)if(s.contains(t)){r.add(s);break;}return r;}

    private void chooseNotice() {
        String[] labels={"15 minutos antes","30 minutos antes","1 hora antes","2 horas antes"}; int[] values={15,30,60,120}; int selected=2;
        for(int i=0;i<values.length;i++) if(values[i]==store.noticeMinutes()) selected=i;
        new AlertDialog.Builder(this).setTitle("¿Cuándo avisar?").setSingleChoiceItems(labels,selected,null)
                .setPositiveButton("Guardar",(d,w)->{ int pos=((AlertDialog)d).getListView().getCheckedItemPosition(); store.saveNoticeMinutes(values[pos]); refresh(); AlarmScheduler.scheduleAll(this); })
                .setNegativeButton("Cancelar",null).show();
    }

    private void addManualMatch() {
        if (store.selectedTeams().isEmpty() && store.selectedNationalTeams().isEmpty()) {
            Toast.makeText(this, "Primero elegí al menos un club o selección", Toast.LENGTH_SHORT).show();
            configureWizard();
            return;
        }
        LinearLayout form=new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(dp(20),dp(4),dp(20),0);
        List<String> available=new ArrayList<>(store.selectedTeams());available.addAll(store.selectedNationalTeams());
        Spinner team=new Spinner(this); team.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,available));
        EditText rival=new EditText(this); rival.setHint("Rival"); EditText comp=new EditText(this); comp.setHint("Competición (opcional)");
        Button when=button("Elegir fecha y hora"); Calendar chosen=Calendar.getInstance(); chosen.add(Calendar.HOUR_OF_DAY,2);
        when.setOnClickListener(v->pickDateTime(chosen,when)); when.setText(dateFormat.format(chosen.getTime()));
        form.addView(team); form.addView(rival); form.addView(comp); form.addView(when);
        new AlertDialog.Builder(this).setTitle("Agregar partido manual").setView(form).setPositiveButton("Agregar",(d,w)->{
            if(rival.getText().toString().trim().isEmpty()){Toast.makeText(this,"Falta ingresar el rival",Toast.LENGTH_SHORT).show();return;}
            Match m=new Match(System.currentTimeMillis(),team.getSelectedItem().toString(),rival.getText().toString().trim(),comp.getText().toString().trim().isEmpty()?"Partido":comp.getText().toString().trim(),chosen.getTimeInMillis(),true);
            store.addManual(m); AlarmScheduler.schedule(this,m,store.noticeMinutes()); refresh();
        }).setNegativeButton("Cancelar",null).show();
    }

    private void pickDateTime(Calendar c, Button button) {
        new DatePickerDialog(this,(v,y,m,d)->{ c.set(y,m,d); new TimePickerDialog(this,(tv,h,min)->{c.set(Calendar.HOUR_OF_DAY,h);c.set(Calendar.MINUTE,min);c.set(Calendar.SECOND,0);button.setText(dateFormat.format(c.getTime()));},c.get(Calendar.HOUR_OF_DAY),c.get(Calendar.MINUTE),true).show(); },c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void requestNotificationPermission() { if(android.os.Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},7); }
    private TextView text(String s,int size,int color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);return b;}
    private LinearLayout.LayoutParams weight(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(dp(2),0,dp(2),0);return p;}
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
}
