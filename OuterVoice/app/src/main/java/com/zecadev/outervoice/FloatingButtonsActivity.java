package com.zecadev.outervoice;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Collections;
import org.json.JSONArray;
import org.json.JSONObject;

/** The two compact tabs from the approved interactive prototype. */
public final class FloatingButtonsActivity extends Activity {
    private static final int BG=0xff0f171c,SURFACE=0xff182329,LINE=0xff30414a,WHITE=0xfff6f9fa,MUTED=0xffaebcc7,TEAL=FloatingConfig.TEAL;
    private static final int MICROPHONE=20,OVERLAY=21;
    private FloatingConfig config;
    private FloatingConfig.Item editing;
    private Typeface font,icons;
    private float scale;
    private LinearLayout body,list,customization,preview,minPreview;
    private TextView orderTab,sizeTab;
    private EditText secondsInput;
    private String secondsDraft;
    private boolean sizeSelected,requestingOverlay,saved;
    private int px(float v){return Math.round(v*scale);}
    private LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private TextView text(String s,int size,int color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextColor(color);v.setTextSize(TypedValue.COMPLEX_UNIT_PX,px(size));v.setTypeface(Typeface.create(font,bold?Typeface.BOLD:Typeface.NORMAL));v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private TextView symbol(String s,int size,int color){TextView v=text(s,size,color,false);v.setTypeface(icons);v.setGravity(Gravity.CENTER);return v;}
    private GradientDrawable shape(int color,int outline,boolean circle){GradientDrawable d=new GradientDrawable();d.setColor(color);if(circle)d.setShape(GradientDrawable.OVAL);else d.setCornerRadius(px(10));if(outline!=0)d.setStroke(px(1),outline);return d;}
    private TextView button(String s,boolean primary){TextView v=text(s,20,primary?BG:WHITE,true);v.setGravity(Gravity.CENTER);v.setBackground(shape(primary?TEAL:SURFACE,primary?0:LINE,false));v.setClickable(true);v.setFocusable(true);return v;}
    private SoundIcon icon(FloatingConfig.Item item,int size){return new SoundIcon(this,icons,item.icon,item.isLive(),px(size),item.foreground());}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_FULLSCREEN);
        scale=Math.min(getResources().getDisplayMetrics().widthPixels/1024f,getResources().getDisplayMetrics().heightPixels/600f);
        font=Typeface.createFromAsset(getAssets(),"Inter.ttf");icons=Typeface.createFromAsset(getAssets(),"MaterialIcons-Regular.ttf");
        config=FloatingConfig.load(this);editing=config.items.get(0);secondsDraft=String.valueOf(config.autoSeconds);
        if(state!=null)try{
            JSONArray array=new JSONArray(state.getString("draft","[]"));java.util.ArrayList<FloatingConfig.Item> ordered=new java.util.ArrayList<>();
            for(int i=0;i<array.length();i++){JSONObject value=array.getJSONObject(i);for(FloatingConfig.Item item:config.items)if(item.id.equals(value.getString("id"))){item.selected=item.isLive()||value.getBoolean("selected");item.color=value.getInt("color");item.icon=value.getInt("icon");item.liveColor=value.optInt("live_color",-1);ordered.add(item);}}
            for(FloatingConfig.Item item:config.items)if(!ordered.contains(item))ordered.add(item);config.items.clear();config.items.addAll(ordered);
            config.enabled=state.getBoolean("enabled");config.size=state.getInt("size",88);config.spacing=state.getInt("spacing",12);config.micEnlargement=state.getInt("enlargement",50);
            config.minimizedSize=state.getInt("minimizedSize",88);config.autoMinimize=state.getBoolean("autoMinimize");secondsDraft=state.getString("seconds","30");
            sizeSelected=state.getBoolean("sizeTab");requestingOverlay=state.getBoolean("requestingOverlay");
            for(FloatingConfig.Item item:config.items)if(item.id.equals(state.getString("editing")))editing=item;
        }catch(Exception ignored){}
        stopService(new Intent(this,FloatingPanelService.class));build();
    }
    private void build(){
        LinearLayout root=column();root.setBackgroundColor(BG);setContentView(root);
        LinearLayout header=row();header.setPadding(px(24),0,px(24),0);
        TextView back=symbol("\ue5c4",32,WHITE);back.setContentDescription("Back");back.setOnClickListener(v->cancel());header.addView(back,new LinearLayout.LayoutParams(px(48),px(56)));
        header.addView(text("Floating Panel",34,WHITE,true),new LinearLayout.LayoutParams(0,-1,1));
        CheckBox enabled=new CheckBox(this);enabled.setText("Enable floating panel");enabled.setTextColor(WHITE);enabled.setTypeface(font);enabled.setTextSize(TypedValue.COMPLEX_UNIT_PX,px(19));enabled.setButtonTintList(ColorStateList.valueOf(TEAL));enabled.setChecked(config.enabled);enabled.setOnCheckedChangeListener((v,checked)->config.enabled=checked);
        header.addView(enabled,new LinearLayout.LayoutParams(px(320),-1));root.addView(header,new LinearLayout.LayoutParams(-1,px(68)));
        LinearLayout tabs=row();tabs.setPadding(px(24),px(12),px(24),0);orderTab=button("Buttons & Order",false);sizeTab=button("Size & Minimize",false);
        tabs.addView(orderTab,new LinearLayout.LayoutParams(px(224),px(56)));LinearLayout.LayoutParams second=new LinearLayout.LayoutParams(px(224),px(56));second.leftMargin=px(12);tabs.addView(sizeTab,second);root.addView(tabs,new LinearLayout.LayoutParams(-1,px(68)));
        orderTab.setOnClickListener(v->{sizeSelected=false;renderTab();});sizeTab.setOnClickListener(v->{sizeSelected=true;renderTab();});
        body=row();body.setGravity(Gravity.TOP);body.setPadding(px(24),px(20),px(24),px(8));root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout actions=row();actions.setGravity(Gravity.RIGHT);actions.setPadding(px(24),px(12),px(24),px(14));root.addView(actions,new LinearLayout.LayoutParams(-1,px(86)));
        TextView cancel=button("Cancel",false),save=button("Save Settings",true);actions.addView(cancel,new LinearLayout.LayoutParams(px(200),px(60)));LinearLayout.LayoutParams saveSize=new LinearLayout.LayoutParams(px(244),px(60));saveSize.leftMargin=px(16);actions.addView(save,saveSize);cancel.setOnClickListener(v->cancel());save.setOnClickListener(v->save());
        renderTab();
    }
    private void renderTab(){
        orderTab.setSelected(!sizeSelected);sizeTab.setSelected(sizeSelected);
        for(TextView tab:new TextView[]{orderTab,sizeTab}){tab.setTextColor(tab.isSelected()?TEAL:WHITE);tab.setBackground(shape(tab.isSelected()?0xff113039:SURFACE,tab.isSelected()?TEAL:LINE,false));}
        body.removeAllViews();preview=null;minPreview=null;secondsInput=null;
        if(sizeSelected){buildSizes();return;}
        LinearLayout left=column();body.addView(left,new LinearLayout.LayoutParams(0,-1,1.1f));left.addView(text("Panel buttons",22,WHITE,true),new LinearLayout.LayoutParams(-1,px(30)));left.addView(text("Select sounds. Use arrows to change order.",14,MUTED,false),new LinearLayout.LayoutParams(-1,px(30)));
        ScrollView scroll=new ScrollView(this);scroll.setScrollbarFadingEnabled(false);scroll.setVerticalScrollBarEnabled(true);list=column();scroll.addView(list);left.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        View divider=new View(this);divider.setBackgroundColor(LINE);LinearLayout.LayoutParams d=new LinearLayout.LayoutParams(px(1),-1);d.setMargins(px(24),0,px(24),0);body.addView(divider,d);
        customization=column();body.addView(customization,new LinearLayout.LayoutParams(0,-1,1));buildRows();buildCustomization();
    }
    private void buildRows(){
        list.removeAllViews();for(FloatingConfig.Item item:config.items){
            LinearLayout line=row();line.setPadding(px(6),0,px(6),0);line.setBackground(shape(item==editing?0xff113039:SURFACE,item==editing?TEAL:LINE,false));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,px(42));p.bottomMargin=px(4);list.addView(line,p);
            CheckBox check=new CheckBox(this);check.setButtonTintList(ColorStateList.valueOf(TEAL));check.setContentDescription("Show "+item.name+" in floating panel");check.setChecked(item.selected);check.setEnabled(!item.isLive());line.addView(check,new LinearLayout.LayoutParams(px(36),-1));
            if(!item.isLive())check.setOnCheckedChangeListener((v,on)->{item.selected=on;editing=item;buildRows();buildCustomization();});
            SoundIcon badge=icon(item,19);badge.setBackground(shape(item.buttonColor(),0,true));line.addView(badge,new LinearLayout.LayoutParams(px(28),px(28)));
            TextView name=text(item.name,16,WHITE,true);name.setMaxLines(2);name.setPadding(px(6),0,px(4),0);line.addView(name,new LinearLayout.LayoutParams(0,-1,1));
            View.OnClickListener select=v->{editing=item;buildRows();buildCustomization();};name.setOnClickListener(select);badge.setOnClickListener(select);
            for(int direction:new int[]{-1,1}){TextView arrow=symbol(direction<0?"\ue5d8":"\ue5db",24,WHITE);arrow.setContentDescription((direction<0?"Move up ":"Move down ")+item.name);arrow.setEnabled(item.selected&&neighbor(item,direction)>=0);arrow.setAlpha(arrow.isEnabled()?1:.3f);line.addView(arrow,new LinearLayout.LayoutParams(px(38),px(38)));arrow.setOnClickListener(v->{int target=neighbor(item,direction);if(target>=0){Collections.swap(config.items,config.items.indexOf(item),target);buildRows();}});}
        }
    }
    private int neighbor(FloatingConfig.Item item,int direction){for(int i=config.items.indexOf(item)+direction;i>=0&&i<config.items.size();i+=direction)if(config.items.get(i).selected)return i;return -1;}
    private void buildCustomization(){
        customization.removeAllViews();customization.addView(text("Customize "+editing.name,22,WHITE,true),new LinearLayout.LayoutParams(-1,px(32)));customization.addView(text("Button color",18,WHITE,true),new LinearLayout.LayoutParams(-1,px(28)));
        if(editing.isLive()){
            LinearLayout defaultRow=row();defaultRow.setBackground(shape(editing.liveColor<0?0xff113039:BG,editing.liveColor<0?TEAL:0,false));defaultRow.setPadding(px(4),0,px(10),0);
            TextView dot=text("",10,WHITE,false);dot.setBackground(shape(TEAL,0,true));defaultRow.addView(dot,new LinearLayout.LayoutParams(px(32),px(32)));TextView label=text("App Blue",14,WHITE,true);label.setPadding(px(10),0,px(10),0);defaultRow.addView(label);defaultRow.addView(text("Default color",12,MUTED,false));defaultRow.setContentDescription("Color App Blue");defaultRow.setOnClickListener(v->{editing.liveColor=-1;buildRows();buildCustomization();});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,px(40));p.bottomMargin=px(8);customization.addView(defaultRow,p);
        }
        LinearLayout swatches=row();swatches.setGravity(Gravity.TOP);customization.addView(swatches,new LinearLayout.LayoutParams(-1,px(64)));
        for(int index:FloatingConfig.COLOR_ORDER){LinearLayout swatch=column();swatch.setGravity(Gravity.CENTER_HORIZONTAL);swatches.addView(swatch,new LinearLayout.LayoutParams(0,-1,1));TextView dot=text("",10,BG,false);dot.setBackground(shape(FloatingConfig.COLORS[index],(editing.isLive()?editing.liveColor:editing.color)==index?TEAL:0,true));swatch.addView(dot,new LinearLayout.LayoutParams(px(32),px(32)));TextView label=text(FloatingConfig.COLOR_NAMES[index],10,WHITE,false);label.setSingleLine(true);label.setGravity(Gravity.CENTER);swatch.addView(label,new LinearLayout.LayoutParams(-1,px(20)));swatch.setContentDescription("Color "+FloatingConfig.COLOR_NAMES[index]);swatch.setOnClickListener(v->{if(editing.isLive())editing.liveColor=index;else editing.color=index;buildRows();buildCustomization();});}
        if(editing.isLive()){
            customization.addView(text("Button preview",18,WHITE,true),new LinearLayout.LayoutParams(-1,px(28)));LinearLayout sample=row();sample.setGravity(Gravity.CENTER);sample.setBackground(shape(SURFACE,LINE,false));int d=Math.min(120,config.diameter(true));SoundIcon mic=icon(editing,Math.round(d*.45f));mic.setContentDescription("Fixed microphone icon");mic.setBackground(shape(editing.buttonColor(),0,true));sample.addView(mic,new LinearLayout.LayoutParams(px(d),px(d)));customization.addView(sample,new LinearLayout.LayoutParams(-1,px(126)));return;
        }
        customization.addView(text("Button icon",18,WHITE,true),new LinearLayout.LayoutParams(-1,px(28)));
        for(int r=0;r<3;r++){LinearLayout grid=row();LinearLayout.LayoutParams rowSize=new LinearLayout.LayoutParams(-1,px(60));if(r>0)rowSize.topMargin=px(6);customization.addView(grid,rowSize);for(int c=0;c<4;c++){final int index=r*4+c;LinearLayout tile=column();tile.setGravity(Gravity.CENTER);tile.setBackground(shape(editing.icon==index?0xff113039:SURFACE,editing.icon==index?TEAL:LINE,false));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);if(c>0)p.leftMargin=px(6);grid.addView(tile,p);SoundIcon glyph=new SoundIcon(this,icons,index,false,px(28),editing.icon==index?editing.buttonColor():WHITE);tile.addView(glyph,new LinearLayout.LayoutParams(-1,px(30)));TextView label=text(FloatingConfig.ICON_NAMES[index],12,WHITE,false);label.setGravity(Gravity.CENTER);label.setMaxLines(2);tile.addView(label,new LinearLayout.LayoutParams(-1,px(28)));tile.setContentDescription("Icon "+FloatingConfig.ICON_NAMES[index]);tile.setOnClickListener(v->{editing.icon=index;buildRows();buildCustomization();});}}
    }
    private LinearLayout card(float weight){LinearLayout v=column();v.setPadding(px(18),px(16),px(18),px(16));v.setBackground(shape(SURFACE,LINE,false));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,weight);if(body.getChildCount()>0)p.leftMargin=px(18);body.addView(v,p);return v;}
    private SeekBar slider(LinearLayout parent,String label,String description,int min,int max,int step,int value,String unit,java.util.function.IntConsumer changed){
        LinearLayout line=row();parent.addView(line,new LinearLayout.LayoutParams(-1,px(43)));TextView caption=text(label,17,WHITE,true);caption.setMaxLines(2);line.addView(caption,new LinearLayout.LayoutParams(px(172),-1));SeekBar bar=new SeekBar(this);bar.setMax((max-min)/step);bar.setProgress((value-min)/step);bar.setProgressTintList(ColorStateList.valueOf(TEAL));bar.setThumbTintList(ColorStateList.valueOf(TEAL));bar.setContentDescription(description);line.addView(bar,new LinearLayout.LayoutParams(0,px(36),1));TextView out=text(value+unit,20,TEAL,true);line.addView(out,new LinearLayout.LayoutParams(px(62),-1));bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}public void onProgressChanged(SeekBar b,int n,boolean user){int current=min+n*step;out.setText(current+unit);changed.accept(current);buildPreview();}});return bar;
    }
    private void buildSizes(){
        LinearLayout controls=card(1.15f);LinearLayout heading=row();heading.addView(text("Panel layout",21,WHITE,true),new LinearLayout.LayoutParams(0,px(34),1));TextView reset=button("Reset size",false);reset.setTextSize(TypedValue.COMPLEX_UNIT_PX,px(15));heading.addView(reset,new LinearLayout.LayoutParams(px(94),px(32)));controls.addView(heading,new LinearLayout.LayoutParams(-1,px(42)));
        SeekBar base=slider(controls,"Button size","Floating button size",64,144,4,config.size,"px",n->config.size=n);reset.setOnClickListener(v->base.setProgress(6));
        slider(controls,"Button spacing","Floating button spacing",0,40,1,config.spacing,"px",n->config.spacing=n);
        slider(controls,"Enlarge Live Speak button","Live Speak button enlargement percent",0,100,1,config.micEnlargement,"%",n->config.micEnlargement=n);
        TextView minimize=text("Minimize",19,WHITE,true);LinearLayout.LayoutParams minHeading=new LinearLayout.LayoutParams(-1,px(32));minHeading.topMargin=px(8);controls.addView(minimize,minHeading);
        slider(controls,"Minimized button size","Minimized button size",64,144,4,config.minimizedSize,"px",n->config.minimizedSize=n);
        LinearLayout auto=row();controls.addView(auto,new LinearLayout.LayoutParams(-1,px(44)));CheckBox on=new CheckBox(this);on.setText("Auto minimize");on.setTextColor(WHITE);on.setTypeface(font);on.setTextSize(TypedValue.COMPLEX_UNIT_PX,px(17));on.setButtonTintList(ColorStateList.valueOf(TEAL));on.setChecked(config.autoMinimize);auto.addView(on,new LinearLayout.LayoutParams(px(166),-1));
        secondsInput=new EditText(this);secondsInput.setSingleLine(true);secondsInput.setPadding(px(8),0,px(8),0);secondsInput.setGravity(Gravity.CENTER_VERTICAL);secondsInput.setIncludeFontPadding(false);secondsInput.setBackground(shape(BG,LINE,false));secondsInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);secondsInput.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(4)});secondsInput.setText(secondsDraft);secondsInput.setTextColor(WHITE);secondsInput.setTypeface(font);secondsInput.setTextSize(TypedValue.COMPLEX_UNIT_PX,px(20));secondsInput.setContentDescription("Auto minimize time in seconds");secondsInput.setEnabled(config.autoMinimize);auto.addView(secondsInput,new LinearLayout.LayoutParams(px(80),px(40)));TextView unit=text("seconds",17,WHITE,false);unit.setPadding(px(12),0,0,0);auto.addView(unit);
        on.setOnCheckedChangeListener((v,checked)->{config.autoMinimize=checked;secondsInput.setEnabled(checked);});secondsInput.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void afterTextChanged(android.text.Editable s){}public void onTextChanged(CharSequence s,int st,int before,int count){secondsDraft=s.toString();}});
        controls.addView(text("Panel use restarts the inactivity timer.",13,MUTED,false),new LinearLayout.LayoutParams(-1,px(18)));
        LinearLayout previews=card(1);previews.addView(text("Floating panel preview",21,WHITE,true),new LinearLayout.LayoutParams(-1,px(28)));preview=column();preview.setPadding(px(6),px(8),px(6),px(8));preview.setBackground(shape(SURFACE,LINE,false));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,px(142));p.topMargin=px(10);previews.addView(preview,p);TextView title=text("Minimized button preview",17,WHITE,true);LinearLayout.LayoutParams titleSize=new LinearLayout.LayoutParams(-1,px(24));titleSize.topMargin=px(12);previews.addView(title,titleSize);minPreview=row();minPreview.setGravity(Gravity.CENTER);previews.addView(minPreview,new LinearLayout.LayoutParams(-1,px(96)));buildPreview();preview.post(this::buildPreview);
    }
    private void buildPreview(){
        if(preview==null)return;preview.removeAllViews();LinearLayout tools=row();tools.addView(symbol("\ue25d",16,MUTED),new LinearLayout.LayoutParams(px(22),px(22)));TextView min=text("− Minimize",12,MUTED,false);min.setGravity(Gravity.CENTER);tools.addView(min,new LinearLayout.LayoutParams(0,px(22),1));tools.addView(symbol("\ue5cd",16,MUTED),new LinearLayout.LayoutParams(px(22),px(22)));preview.addView(tools);
        int count=0,total=0;for(FloatingConfig.Item item:config.items)if(item.selected){if(count++>0)total+=config.spacing;total+=config.diameter(item.isLive());}
        float available=preview.getWidth()>0?(preview.getWidth()-px(12))/scale:390;float factor=Math.min(.62f,Math.min(76f/config.diameter(true),available/Math.max(1,total)));int largest=px(config.diameter(true)*factor);
        LinearLayout strip=row();strip.setGravity(Gravity.TOP);preview.addView(strip);
        for(FloatingConfig.Item item:config.items)if(item.selected){int diameter=px(config.diameter(item.isLive())*factor);LinearLayout col=column();col.setGravity(Gravity.CENTER_HORIZONTAL);LinearLayout.LayoutParams pos=new LinearLayout.LayoutParams(diameter,-2);if(strip.getChildCount()>0)pos.leftMargin=px(config.spacing*factor);strip.addView(col,pos);SoundIcon circle=icon(item,Math.round(config.diameter(item.isLive())*factor*.45f));circle.setBackground(shape(item.buttonColor(),0,true));LinearLayout.LayoutParams circlePos=new LinearLayout.LayoutParams(diameter,diameter);circlePos.topMargin=(largest-diameter)/2;col.addView(circle,circlePos);TextView label=text(item.name,11,WHITE,false);label.setMaxLines(2);label.setGravity(Gravity.CENTER);LinearLayout.LayoutParams labelPos=new LinearLayout.LayoutParams(diameter,px(30));labelPos.topMargin=(largest-diameter)/2;col.addView(label,labelPos);}
        minPreview.removeAllViews();int d=px(config.minimizedSize*.62f);SoundIcon bubble=icon(config.live(),Math.round(config.minimizedSize*.62f*.45f));bubble.setBackground(shape(config.live().buttonColor(),0,true));minPreview.addView(bubble,new LinearLayout.LayoutParams(d,d));LinearLayout notes=column();LinearLayout.LayoutParams noteSize=new LinearLayout.LayoutParams(-2,-2);noteSize.leftMargin=px(20);minPreview.addView(notes,noteSize);notes.addView(text(config.minimizedSize+"px",21,TEAL,true));notes.addView(text((config.liveOnly()?"Hold to speak":"Tap to reopen")+" · Drag to move",12,MUTED,false));
    }
    private void save(){
        try{int n=Integer.parseInt(secondsDraft);if(n<1||n>3600)throw new NumberFormatException();config.autoSeconds=n;}catch(NumberFormatException e){if(config.autoMinimize){sizeSelected=true;renderTab();secondsInput.setError("Enter 1–3600 seconds");secondsInput.requestFocus();toast("Enter an auto-minimize time from 1 to 3600 seconds.");return;}config.autoSeconds=30;}
        if(config.enabled&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},MICROPHONE);return;}
        if(config.enabled&&!Settings.canDrawOverlays(this)){new AlertDialog.Builder(this).setTitle("Display over other apps").setMessage("Allow Outer Voice to display the floating sound panel above other apps. Turn on Allow display over other apps, then return here.").setNegativeButton("Cancel",null).setPositiveButton("Open Settings",(d,w)->{try{requestingOverlay=true;startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())),OVERLAY);}catch(RuntimeException e){requestingOverlay=false;toast("Open Android Settings > Apps > Special access > Display over other apps > Outer Voice.");}}).show();return;}
        if(!config.save(this)){toast("Could not save settings");return;}saved=true;FloatingConfig.prefs(this).edit().putBoolean("minimized",false).apply();FloatingPanelService.sync(this,true);finish();
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==OVERLAY){requestingOverlay=false;if(Settings.canDrawOverlays(this))save();else toast("Floating panel needs Display over other apps permission. Settings have not been saved.");}}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==MICROPHONE){if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)save();else toast("Microphone permission is required for floating Live Speak.");}}
    private void cancel(){FloatingPanelService.sync(this,false);finish();}
    @Override public void onBackPressed(){cancel();}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);JSONArray array=new JSONArray();try{for(FloatingConfig.Item item:config.items){JSONObject v=new JSONObject();v.put("id",item.id);v.put("selected",item.selected);v.put("color",item.color);v.put("icon",item.icon);v.put("live_color",item.liveColor);array.put(v);}}catch(Exception ignored){}state.putString("draft",array.toString());state.putBoolean("enabled",config.enabled);state.putInt("size",config.size);state.putInt("spacing",config.spacing);state.putInt("enlargement",config.micEnlargement);state.putInt("minimizedSize",config.minimizedSize);state.putBoolean("autoMinimize",config.autoMinimize);state.putString("seconds",secondsDraft);state.putBoolean("sizeTab",sizeSelected);state.putBoolean("requestingOverlay",requestingOverlay);state.putString("editing",editing.id);}
    @Override protected void onDestroy(){if(!saved&&!isChangingConfigurations()&&!requestingOverlay)FloatingPanelService.sync(this,false);super.onDestroy();}
}
