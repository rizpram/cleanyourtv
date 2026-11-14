package com.gathin.tvhome;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

public class SmartDashboard extends LinearLayout {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SharedPreferences prefs;
    private TextView temp, condition, cameraStatus;
    private FrameLayout cameraFrame;
    private PlayerView video;
    private ExoPlayer player;
    private boolean active = false;
    private boolean full = false;
    private final Runnable weatherTick = new Runnable() {
        @Override public void run() { if (active) { loadWeather(); handler.postDelayed(this, 30 * 60 * 1000L); } }
    };
    public SmartDashboard(Context c, android.util.AttributeSet a) { super(c,a); prefs=c.getSharedPreferences("smart_dashboard",Context.MODE_PRIVATE); }
    private int dp(int n) { return (int)(n*getResources().getDisplayMetrics().density+0.5f); }
    private GradientDrawable bg(String color) { GradientDrawable d=new GradientDrawable(); d.setColor(Color.parseColor(color)); d.setCornerRadius(dp(18)); return d; }
    private TextView text(String value,int size,int color) { TextView t=new TextView(getContext());t.setText(value);t.setTextSize(size);t.setTextColor(color);return t; }
    public void initialize() {
        if (getChildCount()>0) return;
        setOrientation(HORIZONTAL);
        setClipChildren(false);
        LinearLayout weather=new LinearLayout(getContext());weather.setOrientation(VERTICAL);weather.setPadding(dp(16),dp(10),dp(12),dp(8));weather.setBackground(bg("#202833"));
        weather.addView(text("WEATHER  ·  BEKASI",12,0xffaeb9c7));
        temp=text("--°",32,Color.WHITE);weather.addView(temp);
        condition=text("Memuat cuaca…",13,0xffc7d0d8);weather.addView(condition);
        LinearLayout.LayoutParams wl=new LinearLayout.LayoutParams(0,-1,2);wl.rightMargin=dp(12);addView(weather,wl);
        cameraFrame=new FrameLayout(getContext());cameraFrame.setBackground(bg("#18232e"));
        video=new PlayerView(getContext());video.setUseController(false);cameraFrame.addView(video,new FrameLayout.LayoutParams(-1,-1));
        cameraStatus=text("TAPO C500  ·  OK: SETUP",12,Color.WHITE);
        FrameLayout.LayoutParams label=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.LEFT);label.setMargins(dp(14),dp(12),0,0);cameraFrame.addView(cameraStatus,label);
        cameraFrame.setFocusable(true);cameraFrame.setOnClickListener(v->{if(prefs.getString("user","").isEmpty()) configure();else toggleFull();});
        cameraFrame.setOnLongClickListener(v->{configure();return true;});
        addView(cameraFrame,new LinearLayout.LayoutParams(0,-1,3));
    }
    public void onHostResume() { active=true;handler.removeCallbacks(weatherTick);weatherTick.run();startCamera(); }
    public void onHostStop() { active=false;handler.removeCallbacks(weatherTick);stopCamera(); }
    private void loadWeather() {
        new Thread(()->{
            String result="Cuaca tidak tersedia";String degrees="--°";
            HttpURLConnection conn=null;
            try {
                URL url=new URL("https://api.open-meteo.com/v1/forecast?latitude=-6.25&longitude=107.02&current=temperature_2m,weather_code&timezone=Asia%2FJakarta");
                conn=(HttpURLConnection)url.openConnection();conn.setConnectTimeout(7000);conn.setReadTimeout(7000);
                try(InputStream in=conn.getInputStream()) {
                    java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);byte[] data=out.toByteArray();JSONObject cur=new JSONObject(new String(data,StandardCharsets.UTF_8)).getJSONObject("current");
                    degrees=Math.round(cur.getDouble("temperature_2m"))+"°";
                    int code=cur.getInt("weather_code");
                    result=code==0?"Cerah":code<=3?"Berawan":code<=48?"Berkabut":code<=67?"Hujan":code<=77?"Presipitasi":code<=82?"Hujan deras":"Badai / hujan";
                }
            } catch(Exception ignored) {} finally {if(conn!=null)conn.disconnect();}
            String d=degrees,r=result;handler.post(()->{if(active){temp.setText(d);condition.setText(r);}});
        },"weather-fetch").start();
    }
    private void configure() {
        LinearLayout form=new LinearLayout(getContext());form.setOrientation(VERTICAL);form.setPadding(dp(20),dp(8),dp(20),0);
        EditText user=new EditText(getContext());user.setSingleLine(true);user.setHint("Tapo Camera Account username");user.setText(prefs.getString("user",""));form.addView(user);
        EditText pass=new EditText(getContext());pass.setSingleLine(true);pass.setHint("Camera password");pass.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);form.addView(pass);
        new AlertDialog.Builder(getContext()).setTitle("Tapo C500 · 192.168.1.13").setView(form)
            .setPositiveButton("Simpan",(d,w)->{prefs.edit().putString("user",user.getText().toString().trim()).putString("password",pass.getText().toString()).apply();startCamera();})
            .setNegativeButton("Batal",null).show();
    }
    private void startCamera() {
        stopCamera();String user=prefs.getString("user","");String pass=prefs.getString("password","");
        if(user.isEmpty()||pass.isEmpty()) {if(cameraStatus!=null)cameraStatus.setText("TAPO C500 · OK untuk konfigurasi");return;}
        try {
            String uri="rtsp://"+Uri.encode(user)+":"+Uri.encode(pass)+"@192.168.1.13:554/"+(full?"stream1":"stream2");
            player=new ExoPlayer.Builder(getContext()).build();video.setPlayer(player);
            player.setMediaItem(MediaItem.fromUri(uri));player.prepare();player.setPlayWhenReady(true);
            cameraStatus.setText("TAPO C500 · LIVE");
        } catch(Exception e) {cameraStatus.setText("Kamera gagal diputar · tahan OK untuk pengaturan");}
    }
    private void stopCamera() {if(player!=null){video.setPlayer(null);player.release();player=null;}}
    private void toggleFull() {
        full=!full;
        // Keep the camera within the launcher view hierarchy; do not disturb the HOME activity.
        LinearLayout.LayoutParams p=(LinearLayout.LayoutParams)cameraFrame.getLayoutParams();
        p.weight=full?10:3;cameraFrame.setLayoutParams(p);
        LinearLayout.LayoutParams w=(LinearLayout.LayoutParams)getChildAt(0).getLayoutParams();
        w.weight=full?0:2;getChildAt(0).setVisibility(full?GONE:VISIBLE);getChildAt(0).setLayoutParams(w);
        startCamera();
    }
}
