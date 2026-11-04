package com.gastonpg.pgcontrol;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Base64;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    EditText host, token;
    TextView status;
    LinearLayout apps;
    ExecutorService io = Executors.newSingleThreadExecutor();
    String iconPackage = "", iconData = "";

    public void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this); root.setOrientation(1); root.setPadding(20,20,20,20); root.setBackgroundColor(0xff0b1120);
        ScrollView scroll = new ScrollView(this); LinearLayout page = new LinearLayout(this); page.setOrientation(1);
        TextView title = label("PG CONTROL",25,true); page.addView(title);
        host = new EditText(this); host.setSingleLine(); host.setHint("http://IP-DE-LA-TV:8765"); page.addView(host);
        token = new EditText(this); token.setSingleLine(); token.setHint("Token del televisor"); token.setInputType(129); page.addView(token);
        android.content.SharedPreferences p=getSharedPreferences("connection",0); host.setText(p.getString("host","")); token.setText(p.getString("token",""));
        Button connect=button("Conectar y cargar aplicaciones"); page.addView(connect); connect.setOnClickListener(v->connect());
        status=label("Conectá ambos dispositivos a la misma Wi-Fi.",13,false); page.addView(status);
        TextView layoutTitle=label("Distribución de aplicaciones",14,true); page.addView(layoutTitle);
        LinearLayout layoutOptions=new LinearLayout(this); layoutOptions.setOrientation(LinearLayout.HORIZONTAL);
        String[] layoutNames={"Fila","Cuadrícula","Categorías ↔","Categorías ▦"};
        String[] layoutValues={"horizontal","grid","category_rows","category_grid"};
        for(int li=0;li<layoutNames.length;li++){final String layoutValue=layoutValues[li];Button option=button(layoutNames[li]);option.setTextSize(10);layoutOptions.addView(option,new LinearLayout.LayoutParams(0,-2,1));option.setOnClickListener(v->applyLayout(layoutValue));}
        page.addView(layoutOptions);
        apps=new LinearLayout(this); apps.setOrientation(1); page.addView(apps);
        scroll.addView(page); root.addView(scroll); setContentView(root);
    }

    void connect() {
        String h=host.getText().toString().trim(), t=token.getText().toString().trim();
        getSharedPreferences("connection",0).edit().putString("host",h).putString("token",t).apply();
        status.setText("Conectando…");
        call("GET","/v1/device",null,r->{status.setText("Conectado · "+r.optString("model")+" · Android "+r.optString("androidRelease"));loadApps();});
    }

    void applyLayout(String layout) {
        try {
            JSONObject body=new JSONObject(); body.put("layout",layout);
            status.setText("Cambiando distribución…");
            call("POST","/v1/launcher/layout",body,r->status.setText("Distribución aplicada: "+layoutLabel(layout)));
        } catch(Exception e) { status.setText("No se pudo cambiar la distribución."); }
    }
    String layoutLabel(String layout) {
        if("grid".equals(layout))return "Cuadrícula";
        if("category_rows".equals(layout))return "Categorías en filas";
        if("category_grid".equals(layout))return "Categorías en cuadrícula";
        return "Fila horizontal";
    }

    void loadApps() {
        call("GET","/v1/launcher/app-configs",null,configResult ->
            call("GET","/v1/packages",null,r->{
                apps.removeAllViews(); JSONArray a=r.optJSONArray("apps");
                JSONObject configs=configResult.optJSONObject("configs");
                if(a==null)return;
                for(int i=0;i<a.length();i++){
                    JSONObject x=a.optJSONObject(i);
                    if(x!=null)addApp(x, configs==null?new JSONObject():configs.optJSONObject(x.optString("packageName")));
                }
                status.setText("Conectado · "+a.length()+" aplicaciones");
            })
        );
    }

    void addApp(JSONObject x, JSONObject saved) {
        String pkg=x.optString("packageName"), defaultName=x.optString("label",pkg);
        JSONObject cfg=saved==null?new JSONObject():saved;
        String name=cfg.optString("label",defaultName);
        LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setPadding(12,12,12,12);card.setBackgroundColor(0xff1b2940);
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.bottomMargin=12;apps.addView(card,cp);
        card.addView(label(name,16,true));card.addView(label(pkg,10,false));
        EditText customName=new EditText(this);customName.setSingleLine();customName.setText(name);customName.setHint("Nombre");card.addView(customName);
        LinearLayout dims=new LinearLayout(this);
        EditText width=number("Ancho dp",String.valueOf(cfg.optInt("cardWidthDp",128))),height=number("Alto dp",String.valueOf(cfg.optInt("cardHeightDp",66)));
        dims.addView(width,new LinearLayout.LayoutParams(0,-2,1));dims.addView(height,new LinearLayout.LayoutParams(0,-2,1));card.addView(dims);
        LinearLayout presets=new LinearLayout(this);presets.setOrientation(LinearLayout.HORIZONTAL);
        String[] presetNames={"XS","S","M","L","XL"};
        int[][] presetSizes={{88,48},{108,56},{128,66},{160,82},{192,100}};
        for(int presetIndex=0;presetIndex<presetNames.length;presetIndex++){
            final int selected=presetIndex;
            Button preset=button(presetNames[presetIndex]);preset.setTextSize(11);
            presets.addView(preset,new LinearLayout.LayoutParams(0,-2,1));
            preset.setOnClickListener(v->{width.setText(String.valueOf(presetSizes[selected][0]));height.setText(String.valueOf(presetSizes[selected][1]));});
        }
        card.addView(presets);
        TextView appearance=label("Aspecto de tarjeta",13,true);card.addView(appearance);
        String[] styleNames={"Glass","Sólida","Contorno","Minimal"}, styleValues={"glass","solid","outline","minimal"};
        String[] selectedStyle={cfg.optString("cardStyle","glass")};
        LinearLayout styles=new LinearLayout(this);styles.setOrientation(LinearLayout.HORIZONTAL);
        for(int si=0;si<styleNames.length;si++){final int ix=si;Button opt=button(styleNames[si]);opt.setTextSize(10);styles.addView(opt,new LinearLayout.LayoutParams(0,-2,1));opt.setAlpha(styleValues[si].equals(selectedStyle[0])?1f:0.55f);opt.setOnClickListener(v->{selectedStyle[0]=styleValues[ix];for(int j=0;j<styles.getChildCount();j++)styles.getChildAt(j).setAlpha(j==ix?1f:0.55f);});}
        card.addView(styles);
        TextView radiusTitle=label("Esquinas",13,true);card.addView(radiusTitle);
        String[] radiusNames={"Recta","Suave","Redonda","Píldora"};int[] radiusValues={0,8,16,28};
        int currentRadius=cfg.optInt("cardRadiusDp",16), selectedIndex=2;for(int j=0;j<radiusValues.length;j++)if(radiusValues[j]==currentRadius)selectedIndex=j;
        final int[] selectedRadius={selectedIndex};LinearLayout radii=new LinearLayout(this);radii.setOrientation(LinearLayout.HORIZONTAL);
        for(int ri=0;ri<radiusNames.length;ri++){final int ix=ri;Button opt=button(radiusNames[ri]);opt.setTextSize(10);radii.addView(opt,new LinearLayout.LayoutParams(0,-2,1));opt.setAlpha(ri==selectedIndex?1f:0.55f);opt.setOnClickListener(v->{selectedRadius[0]=ix;for(int j=0;j<radii.getChildCount();j++)radii.getChildAt(j).setAlpha(j==ix?1f:0.55f);});}
        card.addView(radii);
        EditText order=number("Orden (0 primero)",String.valueOf(cfg.optInt("order",10000)));card.addView(order);
        EditText category=new EditText(this);category.setSingleLine();category.setText(cfg.optString("category",""));category.setHint("Categoría (vacío = automática)");card.addView(category);
        CheckBox hidden=new CheckBox(this);hidden.setText("Ocultar esta aplicación del launcher");hidden.setTextColor(Color.WHITE);hidden.setChecked(cfg.optBoolean("hidden",false));card.addView(hidden);
        Button choose=button("Elegir icono");card.addView(choose);choose.setOnClickListener(v->{iconPackage=pkg;Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("image/*");startActivityForResult(i,88);});
        Button applyIcon=button("Aplicar solo el icono");card.addView(applyIcon);
        applyIcon.setOnClickListener(v->{
            if(!pkg.equals(iconPackage)||iconData.isEmpty()){status.setText("Primero elegí un icono para esta aplicación.");return;}
            try{
                JSONObject body=new JSONObject();body.put("packageName",pkg);body.put("iconBase64",iconData);
                status.setText("Aplicando solo el icono de "+name+"…");
                call("POST","/v1/launcher/app-config",body,r->{status.setText("Icono actualizado · "+name);iconData="";});
            }catch(Exception e){status.setText("No se pudo preparar el icono.");}
        });
        Button save=button("Aplicar todos los cambios");card.addView(save);
        save.setOnClickListener(v->{
            try {
                JSONObject body=new JSONObject();body.put("packageName",pkg);body.put("label",customName.getText().toString());
                body.put("cardWidthDp",Integer.parseInt(width.getText().toString()));body.put("cardHeightDp",Integer.parseInt(height.getText().toString()));body.put("cardStyle",selectedStyle[0]);body.put("cardRadiusDp",radiusValues[selectedRadius[0]]);body.put("order",Integer.parseInt(order.getText().toString()));body.put("hidden",hidden.isChecked());body.put("category",category.getText().toString().trim());
                if(pkg.equals(iconPackage)&&!iconData.isEmpty())body.put("iconBase64",iconData);
                status.setText("Aplicando "+name+"…");
                call("POST","/v1/launcher/app-config",body,r->{status.setText("Cambios aplicados a "+name);if(pkg.equals(iconPackage))iconData="";});
            }catch(Exception e){status.setText("Revisá las medidas.");}
        });
    }

    EditText number(String hint,String value){EditText e=new EditText(this);e.setSingleLine();e.setHint(hint);e.setText(value);e.setInputType(2);return e;}

    protected void onActivityResult(int req,int result,Intent data){
        super.onActivityResult(req,result,data);
        if(req!=88||result!=RESULT_OK||data==null||data.getData()==null)return;
        try(InputStream in=getContentResolver().openInputStream(data.getData())){
            Bitmap b=BitmapFactory.decodeStream(in);if(b==null)throw new Exception("Imagen inválida");
            float scale=Math.min(1f,256f/Math.max(b.getWidth(),b.getHeight()));
            Bitmap small=Bitmap.createScaledBitmap(b,Math.max(1,(int)(b.getWidth()*scale)),Math.max(1,(int)(b.getHeight()*scale)),true);
            ByteArrayOutputStream out=new ByteArrayOutputStream();small.compress(Bitmap.CompressFormat.PNG,100,out);
            iconData=Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP);
            if(small!=b)small.recycle();b.recycle();status.setText("Icono listo para "+iconPackage+". Ahora tocá Aplicar.");
        }catch(Exception e){status.setText("No se pudo leer el icono.");}
    }

    interface Done{void accept(JSONObject r);}
    void call(String method,String path,JSONObject body,Done done){
        final String base=host.getText().toString().trim().replaceAll("/+$","");
        final String auth=token.getText().toString().trim();
        io.execute(()->{
            HttpURLConnection c=null;
            try{
                c=(HttpURLConnection)new URL(base+path).openConnection();c.setRequestMethod(method);c.setConnectTimeout(7000);c.setReadTimeout(12000);
                c.setRequestProperty("Authorization","Bearer "+auth);c.setRequestProperty("Accept","application/json");
                if(body!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");try(OutputStream o=c.getOutputStream()){o.write(body.toString().getBytes(StandardCharsets.UTF_8));}}
                int code=c.getResponseCode();InputStream in=code<400?c.getInputStream():c.getErrorStream();
                ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[4096];int n;while((n=in.read(buf))>0)out.write(buf,0,n);
                JSONObject response=new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));
                runOnUiThread(()->{if(code>=200&&code<300)done.accept(response);else status.setText("Error "+code+": "+response.optString("error"));});
            }catch(Exception e){runOnUiThread(()->status.setText("No se pudo conectar: "+e.getMessage()));}
            finally{if(c!=null)c.disconnect();}
        });
    }

    TextView label(String s,int size,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(Color.WHITE);if(bold)t.setTypeface(null,1);return t;}
    Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(Color.WHITE);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff315b91));return b;}
    public void onDestroy(){super.onDestroy();io.shutdownNow();}
}
