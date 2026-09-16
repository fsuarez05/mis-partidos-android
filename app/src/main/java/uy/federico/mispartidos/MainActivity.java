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
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
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
        Button teams = button("⭐ Equipos"); teams.setOnClickListener(v -> chooseTeams());
        Button notice = button("🔔 Aviso"); notice.setOnClickListener(v -> chooseNotice());
        Button add = button("＋ Partido"); add.setOnClickListener(v -> addManualMatch());
        actions.addView(teams, weight()); actions.addView(notice, weight()); actions.addView(add, weight()); root.addView(actions);
        TextView info = text("Versión de prueba · Los partidos marcados como dato de prueba no provienen aún de una API.", 13, Color.DKGRAY, false);
        info.setPadding(dp(16),dp(6),dp(16),dp(10)); root.addView(info);
        ScrollView scroll = new ScrollView(this); list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); list.setPadding(dp(12),0,dp(12),dp(20)); scroll.addView(list); root.addView(scroll, new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root); refresh();
    }

    private void refresh() {
        list.removeAllViews(); List<Match> matches = store.upcoming();
        if (matches.isEmpty()) { TextView empty = text("No hay próximos partidos. Elegí equipos o agregá uno manualmente.",17,Color.DKGRAY,false); empty.setPadding(dp(14),dp(28),dp(14),dp(14)); list.addView(empty); return; }
        for (Match m : matches) list.addView(matchCard(m));
    }

    private View matchCard(Match m) {
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(16),dp(14),dp(16),dp(14));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable(); bg.setColor(Color.WHITE); bg.setCornerRadius(dp(14)); bg.setStroke(dp(1), Color.rgb(226,232,240)); card.setBackground(bg);
        TextView team = text(m.team,18,Color.rgb(15,23,42),true); TextView versus = text(m.team + "  vs.  " + m.opponent,16,Color.rgb(30,41,59),false);
        TextView date = text(dateFormat.format(new Date(m.kickoff)),19,Color.rgb(180,120,0),true);
        TextView comp = text(m.competition + (m.manual ? " · manual" : "") + "   🔔 " + store.noticeMinutes() + " min antes",13,Color.GRAY,false);
        card.addView(team); card.addView(versus); card.addView(date); card.addView(comp);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,0,0,dp(10)); card.setLayoutParams(lp); return card;
    }

    private void chooseTeams() {
        Set<String> current = store.selectedTeams(); boolean[] checked = new boolean[AppStore.ALL_TEAMS.length];
        for (int i=0;i<checked.length;i++) checked[i]=current.contains(AppStore.ALL_TEAMS[i]);
        new AlertDialog.Builder(this).setTitle("Mis equipos").setMultiChoiceItems(AppStore.ALL_TEAMS, checked, (d,w,c)->checked[w]=c)
                .setPositiveButton("Guardar",(d,w)->{ Set<String> selected=new HashSet<>(); for(int i=0;i<checked.length;i++) if(checked[i]) selected.add(AppStore.ALL_TEAMS[i]); store.saveTeams(selected); refresh(); AlarmScheduler.scheduleAll(this); })
                .setNegativeButton("Cancelar",null).show();
    }

    private void chooseNotice() {
        String[] labels={"15 minutos antes","30 minutos antes","1 hora antes","2 horas antes"}; int[] values={15,30,60,120}; int selected=2;
        for(int i=0;i<values.length;i++) if(values[i]==store.noticeMinutes()) selected=i;
        new AlertDialog.Builder(this).setTitle("¿Cuándo avisar?").setSingleChoiceItems(labels,selected,null)
                .setPositiveButton("Guardar",(d,w)->{ int pos=((AlertDialog)d).getListView().getCheckedItemPosition(); store.saveNoticeMinutes(values[pos]); refresh(); AlarmScheduler.scheduleAll(this); })
                .setNegativeButton("Cancelar",null).show();
    }

    private void addManualMatch() {
        if (store.selectedTeams().isEmpty()) {
            Toast.makeText(this, "Primero elegí al menos un equipo", Toast.LENGTH_SHORT).show();
            chooseTeams();
            return;
        }
        LinearLayout form=new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(dp(20),dp(4),dp(20),0);
        Spinner team=new Spinner(this); team.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new ArrayList<>(store.selectedTeams())));
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
