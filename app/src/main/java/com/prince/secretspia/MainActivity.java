package com.prince.secretspia;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    static final String DEFAULT_REPO = "divinegamingblogspot-dot/secret";
    static final int REQ_MEDIA = 41;
    static final int PICK_MEDIA = 7;

    EditText token, repo;
    Spinner slot;
    Switch auto;
    TextView status, selectionInfo;
    LinearLayout root;
    LinearLayout mediaStrip, blockList;
    ArrayList<Uri> selected = new ArrayList<>();

    final String[] SLOTS = {
        "favourite-frame","that-outfit","latest-mood","that-face","too-gorgeous",
        "her-day","the-detail","the-laugh","memory","just-pia",
        "mirror-moment","outfit-check","eyes","hair","unfiltered",
        "date-night","travel","random-click","little-things","favourite-memory"
    };

    final String[] SLOT_LABELS = {
        "01 · Featured","02 · That outfit","03 · Latest mood","04 · That face","05 · Too gorgeous",
        "06 · Her day","07 · The detail","08 · The laugh","09 · Memory","10 · Just Pia",
        "11 · Mirror moment","12 · Outfit check","13 · Those eyes","14 · Hair day","15 · Unfiltered",
        "16 · Date night","17 · Adventure / travel","18 · Random click","19 · Little things","20 · Favourite memory"
    };

    final String[] LABELS = SLOT_LABELS;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        token=new EditText(this); repo=new EditText(this);
        token.setText(getSharedPreferences("cfg",0).getString("token",""));
        repo.setText(getSharedPreferences("cfg",0).getString("repo",DEFAULT_REPO));
        buildUi();
        handleIncomingIntent(getIntent());
    }

    void buildUi() {
        ScrollView scroll=new ScrollView(this);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(22,24,22,40);
        root.setBackgroundColor(Color.rgb(9,7,11));

        LinearLayout top=new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand=t("Pia",28,Color.WHITE);
        top.addView(brand,new LinearLayout.LayoutParams(0,70,1));
        Button gear=new Button(this); gear.setText("⚙"); gear.setOnClickListener(v->showSettings());
        top.addView(gear);
        root.addView(top);

        TextView kicker=t("HER UNIVERSE · MEDIA",11,Color.rgb(220,170,205)); root.addView(kicker);
        TextView title=t("Add a moment.\\nGive it its own spotlight.",29,Color.WHITE);
        title.setTypeface(null,1); title.setPadding(0,8,0,8); root.addView(title);
        root.addView(t("Choose from Gallery, then drag a thumbnail onto any block.",15,Color.LTGRAY));

        LinearLayout sync=new LinearLayout(this); sync.setGravity(Gravity.CENTER_VERTICAL); sync.setPadding(16,16,12,16);
        sync.setBackgroundColor(Color.rgb(28,20,29));
        LinearLayout st=new LinearLayout(this); st.setOrientation(LinearLayout.VERTICAL);
        st.addView(t("Full access sync",17,Color.WHITE));
        st.addView(t("Automatically sync new photos & videos you allow.",12,Color.LTGRAY));
        sync.addView(st,new LinearLayout.LayoutParams(0,-2,1));
        direct=new Switch(this); direct.setChecked(MediaSyncService.isRunning);
        direct.setOnCheckedChangeListener((v,on)->{if(on)enableDirect();else stopDirect();});
        sync.addView(direct);
        root.addView(sync);

        Button allow=new Button(this); allow.setText("Allow full media access"); allow.setAllCaps(false);
        allow.setOnClickListener(v->{if(!hasMediaPermission())requestMediaPermission();else toast("Full media access is already allowed.");});
        root.addView(allow);

        TextView gt=t("YOUR GALLERY",12,Color.rgb(225,170,205)); gt.setPadding(0,22,0,8); root.addView(gt);
        Button pick=new Button(this); pick.setText("＋ Choose photos / videos"); pick.setAllCaps(false); pick.setOnClickListener(v->pick()); root.addView(pick);

        HorizontalScrollView hs=new HorizontalScrollView(this);
        mediaStrip=new LinearLayout(this); mediaStrip.setPadding(0,10,0,6); hs.addView(mediaStrip); root.addView(hs);

        TextView bt=t("DRAG INTO A BLOCK",12,Color.rgb(225,170,205)); bt.setPadding(0,22,0,8); root.addView(bt);
        blockList=new LinearLayout(this); blockList.setOrientation(LinearLayout.VERTICAL); root.addView(blockList);
        for(int i=0;i<SLOTS.length;i++) addSimpleBlock(i);

        status=t("Nothing selected yet.",13,Color.LTGRAY); status.setPadding(0,18,0,0); root.addView(status);
        scroll.addView(root); setContentView(scroll);
    }

    EditText f(String h, boolean pw) {
        EditText e = new EditText(this);
        e.setHint(h);
        if (pw) e.setInputType(0x81);
        return e;
    }

    void showDropHelp() {
        new AlertDialog.Builder(this)
            .setTitle("Add media quickly")
            .setMessage("On supported devices, drag photos/videos from a file manager onto this screen. You can also use Gallery/Files → Share → Secrets Pia.\n\nAfter importing, choose the Secrets block and tap Upload selected to this block.")
            .setPositiveButton("OK", null)
            .show();
    }

    void pick() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*","video/*"});
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i, PICK_MEDIA);
    }

    void handleIncomingIntent(Intent d) {
        if (d == null) return;
        String action = d.getAction();
        if (Intent.ACTION_SEND.equals(action)) {
            Uri u = d.getParcelableExtra(Intent.EXTRA_STREAM);
            if (u != null) selected.add(u);
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            ArrayList<Uri> us = d.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (us != null) selected.addAll(us);
        }
        if (!selected.isEmpty()) refreshSimpleGallery();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
    }

    @Override protected void onActivityResult(int q, int c, Intent d) {
        super.onActivityResult(q,c,d);
        if (q != PICK_MEDIA || c != RESULT_OK || d == null) return;
        selected.clear();
        if (d.getClipData() != null) {
            for (int i=0;i<d.getClipData().getItemCount();i++) selected.add(d.getClipData().getItemAt(i).getUri());
        } else if (d.getData() != null) selected.add(d.getData());
        refreshSelection();
    }

    void refreshSelection() {
        selectionInfo.setText(selected.size()+" item(s) ready for \"" + SLOT_LABELS[slot.getSelectedItemPosition()] + "\".");
    }

    void enableAutoSync() {
        String tok = token.getText().toString().trim();
        String rp = repo.getText().toString().trim();
        if (tok.isEmpty() || rp.isEmpty()) {
            auto.setChecked(false);
            status.setText("Enter the GitHub token and repository first.");
            return;
        }
        if (!hasMediaPermission()) {
            requestMediaPermission();
            auto.setChecked(false);
            status.setText("Allow the requested photos/videos access, then enable Direct Sync again.");
            return;
        }
        saveConfig(tok, rp);
        startDirectSync();
        status.setText("Direct Sync is ON. New authorized device media can be synced while the visible sync service is active.");
    }

    void startDirectSync() {
        String tok = token.getText().toString().trim();
        String rp = repo.getText().toString().trim();
        if (tok.isEmpty() || rp.isEmpty()) {
            status.setText("Enter the GitHub token and repository first.");
            return;
        }
        saveConfig(tok, rp);
        Intent s = new Intent(this, MediaSyncService.class);
        s.setAction(MediaSyncService.ACTION_START);
        startForegroundService(s);
    }

    void saveConfig(String tok, String rp) {
        getSharedPreferences("cfg",MODE_PRIVATE).edit()
            .putString("token", tok).putString("repo", rp).apply();
    }

    void stopAutoSync() {
        Intent s = new Intent(this, MediaSyncService.class);
        s.setAction(MediaSyncService.ACTION_STOP);
        startService(s);
        status.setText("Direct Sync is off.");
    }

    boolean hasMediaPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            return checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
                    || checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED;
        }
        return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    void requestMediaPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO}, REQ_MEDIA);
        } else {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_MEDIA);
        }
    }

    void syncSelected() {
        String tok=token.getText().toString().trim(), rp=repo.getText().toString().trim();
        if(tok.isEmpty()||rp.isEmpty()||selected.isEmpty()){
            status.setText("Enter token/repository and select media.");
            return;
        }
        saveConfig(tok,rp);
        status.setText("Uploading "+selected.size()+" item(s) to \"" + SLOT_LABELS[slot.getSelectedItemPosition()] + "\"...");
        final ArrayList<Uri> batch = new ArrayList<>(selected);
        final String chosen = SLOTS[slot.getSelectedItemPosition()];
        new Thread(() -> {
            try {
                int n = MediaSyncService.uploadSelected(this, tok, rp, batch, chosen);
                runOnUiThread(() -> status.setText("Uploaded "+n+" item(s) to "+SLOT_LABELS[slot.getSelectedItemPosition()]+". Prince's synced-media feed remains separate and unchanged."));
            } catch(Exception e) {
                runOnUiThread(() -> status.setText("Upload failed: "+e.getMessage()));
            }
        }).start();
    }

    TextView t(String s,float z,int color){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(color);return v;}

    void addSimpleBlock(final int index){
        LinearLayout card=new LinearLayout(this); card.setGravity(Gravity.CENTER_VERTICAL); card.setPadding(16,14,10,14);
        card.setBackgroundColor(Color.rgb(24,18,25));
        LinearLayout copy=new LinearLayout(this); copy.setOrientation(LinearLayout.VERTICAL);
        TextView name=t(String.format("%02d  %s",index+1,LABELS[index]),16,Color.WHITE); name.setTypeface(null,1);
        copy.addView(name); copy.addView(t("Drop photo / video here",12,Color.GRAY));
        card.addView(copy,new LinearLayout.LayoutParams(0,74,1));
        TextView plus=t("＋",28,Color.rgb(220,70,130)); plus.setGravity(Gravity.CENTER); card.addView(plus,new LinearLayout.LayoutParams(60,74));
        View.OnDragListener dl=(v,e)->{
            if(e.getAction()==DragEvent.ACTION_DRAG_STARTED)return true;
            if(e.getAction()==DragEvent.ACTION_DROP){
                ClipData cd=e.getClipData();
                if(cd!=null)for(int j=0;j<cd.getItemCount();j++){Uri u=cd.getItemAt(j).getUri();if(u!=null)uploadOneSimple(u,SLOTS[index],LABELS[index]);}
                return true;
            }
            return true;
        };
        card.setOnDragListener(dl); plus.setOnDragListener(dl);
        card.setOnClickListener(v->{if(selected.isEmpty())pick();else for(Uri u:selected)uploadOneSimple(u,SLOTS[index],LABELS[index]);});
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,0,0,10);blockList.addView(card,p);
    }

    void refreshSimpleGallery(){
        if(mediaStrip==null)return; mediaStrip.removeAllViews();
        for(Uri u:selected){
            ImageView iv=new ImageView(this); iv.setImageURI(u); iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setLayoutParams(new LinearLayout.LayoutParams(130,130)); iv.setPadding(2,2,12,2);
            iv.setOnLongClickListener(v->{ClipData cd=ClipData.newUri(getContentResolver(),"media",u);v.startDragAndDrop(cd,new View.DragShadowBuilder(v),null,View.DRAG_FLAG_GLOBAL|View.DRAG_FLAG_GLOBAL_URI_READ);return true;});
            mediaStrip.addView(iv);
        }
        status.setText(selected.size()+" selected · long-press a thumbnail and drag it to a block.");
    }

    void uploadOneSimple(Uri u,String slotId,String label){
        String tok=getSharedPreferences("cfg",0).getString("token",""),rp=getSharedPreferences("cfg",0).getString("repo",DEFAULT_REPO);
        if(tok.isEmpty()){showSettings();return;}
        status.setText("Adding to "+label+"…");
        new Thread(()->{try{
            ArrayList<Uri> one=new ArrayList<>(); one.add(u);
            MediaSyncService.uploadSelected(this,tok,rp,one,slotId);
            runOnUiThread(()->status.setText("Added to "+label+" ✓"));
        }catch(Exception e){runOnUiThread(()->status.setText("Upload failed: "+e.getMessage()));}}).start();
    }

    void enableDirect(){
        String tok=getSharedPreferences("cfg",0).getString("token",""),rp=getSharedPreferences("cfg",0).getString("repo",DEFAULT_REPO);
        if(tok.isEmpty()){direct.setChecked(false);showSettings();return;}
        if(!hasMediaPermission()){direct.setChecked(false);requestMediaPermission();return;}
        Intent s=new Intent(this,MediaSyncService.class);s.setAction(MediaSyncService.ACTION_START);startForegroundService(s);
        status.setText("Full access sync is ON.");
    }

    void stopDirect(){
        Intent s=new Intent(this,MediaSyncService.class);s.setAction(MediaSyncService.ACTION_STOP);startService(s);
        status.setText("Full access sync is off.");
    }

    void showSettings(){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(20,4,20,4);
        EditText tk=new EditText(this);tk.setHint("GitHub token");tk.setInputType(0x81);tk.setText(getSharedPreferences("cfg",0).getString("token",""));box.addView(tk);
        EditText rp=new EditText(this);rp.setHint("Website repo");rp.setText(getSharedPreferences("cfg",0).getString("repo",DEFAULT_REPO));box.addView(rp);
        new AlertDialog.Builder(this).setTitle("One-time setup").setMessage("Only needed once so Pia can upload media to the website.").setView(box)
          .setPositiveButton("Save",(d,w)->getSharedPreferences("cfg",0).edit().putString("token",tk.getText().toString().trim()).putString("repo",rp.getText().toString().trim()).apply())
          .setNegativeButton("Cancel",null).show();
    }

    void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}

    @Override protected void onResume() {
        super.onResume();
        if (auto != null && MediaSyncService.isRunning) {
            auto.setChecked(true);
            status.setText("Direct Sync is ON.");
        }
    }
}
