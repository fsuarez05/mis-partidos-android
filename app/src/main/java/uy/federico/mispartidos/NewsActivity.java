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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NewsActivity extends Activity {
    private NewsRepository repository;
    private LinearLayout content;
    private ProgressBar progress;
    private TextView status;
    private Button refresh;
    private final SimpleDateFormat dateFormat=new SimpleDateFormat("dd/MM · HH:mm",new Locale("es","UY"));

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);repository=new NewsRepository(this);buildScreen();
        List<NewsItem> cached=repository.cached();
        if(!cached.isEmpty())render(cached);
        load(false);
    }

    private void buildScreen(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.rgb(248,250,252));
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(10),dp(12),dp(12),dp(12));header.setBackgroundColor(Color.rgb(15,23,42));
        Button back=button("‹");back.setContentDescription("Volver");back.setTextSize(28);back.setTextColor(Color.WHITE);back.setBackgroundColor(Color.TRANSPARENT);back.setOnClickListener(v->finish());
        TextView title=text("Noticias",24,Color.WHITE,true);header.addView(back,new LinearLayout.LayoutParams(dp(52),dp(52)));header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        refresh=button("Actualizar");refresh.setOnClickListener(v->load(true));header.addView(refresh,new LinearLayout.LayoutParams(dp(112),-2));root.addView(header);
        status=text("",13,Color.DKGRAY,false);status.setPadding(dp(16),dp(10),dp(16),dp(8));root.addView(status);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setIndeterminate(true);progress.setVisibility(View.GONE);root.addView(progress,new LinearLayout.LayoutParams(-1,dp(3)));
        ScrollView scroll=new ScrollView(this);content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(12),dp(8),dp(12),dp(28));scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
    }

    private void load(boolean force){
        progress.setVisibility(View.VISIBLE);refresh.setEnabled(false);status.setText("Buscando noticias recientes…");
        repository.load(force,(items,updated,message)->{
            progress.setVisibility(View.GONE);refresh.setEnabled(true);status.setText(message+lastUpdateText());
            render(items);
            if(force)Toast.makeText(this,message,Toast.LENGTH_LONG).show();
        });
    }

    private String lastUpdateText(){
        long value=repository.lastUpdate();return value==0?"":" · "+dateFormat.format(new Date(value));
    }

    private void render(List<NewsItem> items){
        content.removeAllViews();
        if(items.isEmpty()){
            TextView empty=text("No hay noticias disponibles para tus equipos favoritos.",16,Color.DKGRAY,false);empty.setGravity(Gravity.CENTER);empty.setPadding(dp(20),dp(72),dp(20),dp(20));content.addView(empty);return;
        }
        for(NewsItem item:items)content.addView(card(item));
    }

    private View card(NewsItem item){
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(16),dp(14),dp(16),dp(14));
        android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();bg.setColor(Color.WHITE);bg.setCornerRadius(dp(14));bg.setStroke(dp(1),Color.rgb(226,232,240));card.setBackground(bg);
        TextView team=text(item.team,13,Color.rgb(37,99,235),true);
        TextView title=text(item.title,17,Color.rgb(15,23,42),true);title.setPadding(0,dp(6),0,dp(8));
        String details=(item.source.isEmpty()?"Medio no informado":item.source)+" · "+dateFormat.format(new Date(item.publishedAt));
        TextView meta=text(details,13,Color.GRAY,false);
        card.addView(team);card.addView(title);card.addView(meta);card.setOnClickListener(v->open(item));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.setMargins(0,0,0,dp(10));card.setLayoutParams(params);return card;
    }

    private void open(NewsItem item){
        if(item.link.isEmpty()){Toast.makeText(this,"La fuente no proporcionó un enlace",Toast.LENGTH_SHORT).show();return;}
        try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(item.link)));}catch(Exception e){Toast.makeText(this,"No encontré un navegador para abrir la noticia",Toast.LENGTH_LONG).show();}
    }

    private TextView text(String value,int size,int color,boolean bold){TextView view=new TextView(this);view.setText(value);view.setTextSize(size);view.setTextColor(color);if(bold)view.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return view;}
    private Button button(String value){Button button=new Button(this);button.setText(value);button.setAllCaps(false);return button;}
    private int dp(int value){return(int)(value*getResources().getDisplayMetrics().density+.5f);}
}
