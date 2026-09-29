package com.prince.secretspia;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.*;
import android.util.Base64;
import android.view.*;
import android.webkit.*;
import java.io.*;
import java.util.*;

public class MainActivity extends Activity {
    static final String SITE="https://divinegamingblogspot-dot.github.io/secret/";
    static final int PICK_MEDIA=71, REQ_MEDIA=72;
    final ArrayList<Uri> selected=new ArrayList<>();
    WebView web;
    boolean pageReady=false; Uri pendingUploadUri=null; String pendingUploadSlot="";

    @Override public void onCreate(Bundle b){ super.onCreate(b); web=new WebView(this); setContentView(web); setup(); web.loadUrl(SITE); }
    void setup(){
        WebSettings s=web.getSettings(); s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setAllowFileAccess(false); s.setAllowContentAccess(true);
        web.setBackgroundColor(0xff09070b); web.addJavascriptInterface(new Bridge(),"PiaAndroid");
        web.setWebViewClient(new WebViewClient(){@Override public void onPageFinished(WebView v,String u){pageReady=true;inject();}});
    }
    void inject(){
        String js="javascript:(function(){if(document.getElementById('pia-admin'))return;window.__piaArmed=-1;"+
        "var s=document.createElement('style');s.textContent='#pia-admin{position:fixed;z-index:999999;left:12px;right:12px;bottom:12px;background:rgba(9,7,11,.95);border:1px solid rgba(255,255,255,.16);border-radius:22px;padding:12px;color:white;font-family:DM Sans,sans-serif;box-shadow:0 16px 55px rgba(0,0,0,.55);backdrop-filter:blur(18px)}#pia-head{display:flex;gap:8px;align-items:center}#pia-title{flex:1;font-size:15px;font-weight:700}#pia-sub{display:block;font-size:9px;opacity:.55;font-weight:400;margin-top:2px}.pb{background:#211823;border:1px solid rgba(255,255,255,.15);color:#fff;border-radius:13px;padding:9px 12px;font:600 11px DM Sans}.pon{background:#b92d68!important}#pia-tray{display:flex;gap:8px;overflow-x:auto;padding:9px 1px 1px;min-height:58px}.pt{width:56px;height:56px;object-fit:cover;border-radius:13px;border:1px solid rgba(255,255,255,.18);flex:none;cursor:grab}#pia-status{font-size:10px;opacity:.65;padding-top:4px}.pd{outline:2px dashed rgba(214,107,154,.95)!important;outline-offset:-7px;cursor:pointer!important;position:relative!important}.po{outline-color:#fff!important;box-shadow:0 0 0 3px rgba(214,107,154,.22)!important}.pd:after{content:\"ADD IMAGE\";position:absolute;top:8px;right:8px;z-index:9999;background:rgba(9,7,11,.88);color:#fff;border:1px solid rgba(255,255,255,.35);border-radius:999px;padding:5px 8px;font:700 9px DM Sans,sans-serif;pointer-events:none}';document.head.appendChild(s);"+
        "var b=document.createElement('div');b.id='pia-admin';b.innerHTML='<div id=\"pia-head\"><div id=\"pia-title\">Pia<span id=\"pia-sub\">HER UNIVERSE · ADD A MOMENT</span></div><button class=\"pb\" id=\"pp\">＋ Gallery</button><button class=\"pb\" id=\"ps\">Sync</button></div><div id=\"pia-tray\"></div><div id=\"pia-status\">Choose media, then drag it onto any gallery block.</div>';document.body.appendChild(b);"+
        "var pp=document.getElementById('pp'),ps=document.getElementById('ps');pp.onclick=function(){PiaAndroid.pick()};ps.onclick=function(){var on=!ps.classList.contains('pon');ps.classList.toggle('pon',on);ps.textContent=on?'Sync ON':'Sync';PiaAndroid.direct(on)};"+
        "window.__add=function(id,data,name){var i=document.createElement('img');i.className='pt';i.src=data;i.draggable=true;i.dataset.id=id;i.title=name||'media';i.ondragstart=function(e){e.dataTransfer.setData('text/pia-id',id)};i.onclick=function(e){e.preventDefault();e.stopPropagation();document.querySelectorAll('.pt').forEach(function(x){x.classList.remove('armed')});i.classList.add('armed');window.__piaArmed=Number(id);document.getElementById('pia-status').textContent='Media selected · tap any highlighted ADD IMAGE block.'};document.getElementById('pia-tray').appendChild(i);document.getElementById('pia-status').textContent=document.getElementById('pia-tray').children.length+' selected · tap a thumbnail, then tap a block.'};"+
        "var galleryTargets=document.querySelectorAll('.gallery .photo');galleryTargets.forEach(function(el){el.classList.add('pd');var ix=Array.prototype.indexOf.call(document.querySelectorAll('.gallery .photo'),el),names=['favourite-frame','that-outfit','latest-mood','that-face','too-gorgeous','her-day','the-detail','the-laugh','memory','just-pia','mirror-moment','outfit-check','eyes','hair','unfiltered','date-night','travel','random-click','little-things','favourite-memory'],slot=el.dataset.slot||names[ix];el.addEventListener('dragover',function(e){e.preventDefault();el.classList.add('po')});el.addEventListener('dragleave',function(){el.classList.remove('po')});el.addEventListener('drop',function(e){e.preventDefault();el.classList.remove('po');var id=e.dataTransfer.getData('text/pia-id');if(id&&slot)PiaAndroid.upload(id,slot)});el.addEventListener('click',function(e){e.preventDefault();e.stopPropagation();if(window.__piaArmed>=0&&slot)PiaAndroid.upload(String(window.__piaArmed),slot);else if(slot)PiaAndroid.pickForSlot(slot)});el.addEventListener('touchend',function(e){if(window.__piaTouchMoved)return;e.preventDefault();if(slot)PiaAndroid.pickForSlot(slot)})});PiaAndroid.ready()})()";
        web.evaluateJavascript(js,null);
    }
    public class Bridge{
        @JavascriptInterface public void ready(){setStatus("Ready · choose media from Gallery.");}
        @JavascriptInterface public void pick(){runOnUiThread(()->MainActivity.this.pick(null));}
        @JavascriptInterface public void pickForSlot(String slot){runOnUiThread(()->{if(!selected.isEmpty()){uploadOne(selected.get(0),slot);}else MainActivity.this.pick(slot);});}
        @JavascriptInterface public void direct(boolean on){runOnUiThread(()->{if(on)startDirect();else stopDirect();});}
        @JavascriptInterface public void upload(String id,String slot){try{int i=Integer.parseInt(id);if(i>=0&&i<selected.size())runOnUiThread(()->uploadOne(selected.get(i),slot));}catch(Exception ignored){}}
    }
    void pick(String slot){getSharedPreferences("cfg",0).edit().putString("pendingSlot",slot==null?"":slot).apply();Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/*","video/*"});i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,PICK_MEDIA);}
    @Override protected void onActivityResult(int q,int c,Intent d){super.onActivityResult(q,c,d);if(q!=PICK_MEDIA||c!=RESULT_OK||d==null)return;selected.clear();if(d.getClipData()!=null)for(int i=0;i<d.getClipData().getItemCount();i++)addUri(d.getClipData().getItemAt(i).getUri());else if(d.getData()!=null)addUri(d.getData());for(int i=0;i<selected.size();i++)addThumb(i,selected.get(i));String slot=getSharedPreferences("cfg",0).getString("pendingSlot","");if(!slot.isEmpty()&&!selected.isEmpty())uploadOne(selected.get(0),slot);}
    void addUri(Uri u){try{getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);}catch(Exception ignored){}selected.add(u);}
    void addThumb(int id,Uri u){new Thread(()->{try{String data=thumb(u),name=MediaSyncService.displayName(this,u);String call="javascript:window.__add("+id+","+quote(data)+","+quote(name)+")";runOnUiThread(()->web.evaluateJavascript(call,null));}catch(Exception e){setStatus("Preview skipped: "+e.getMessage());}}).start();}
    String thumb(Uri u)throws Exception{Bitmap b;if(Build.VERSION.SDK_INT>=29)b=getContentResolver().loadThumbnail(u,new android.util.Size(180,180),null);else{try(InputStream in=getContentResolver().openInputStream(u)){if(in==null)throw new IOException("Cannot open media");BitmapFactory.Options o=new BitmapFactory.Options();o.inSampleSize=4;b=BitmapFactory.decodeStream(in,null,o);}}if(b==null)throw new IOException("Unsupported media");ByteArrayOutputStream o=new ByteArrayOutputStream();b.compress(Bitmap.CompressFormat.JPEG,76,o);b.recycle();return "data:image/jpeg;base64,"+Base64.encodeToString(o.toByteArray(),Base64.NO_WRAP);}
    static String quote(String s){if(s==null)return "null";return "\""+s.replace("\\","\\\\").replace("\"","\\\"").replace("\n"," ")+"\"";}
    void uploadOne(Uri u,String slot){String tok=getSharedPreferences("cfg",0).getString("token",""),rp=getSharedPreferences("cfg",0).getString("repo",MediaSyncService.DEFAULT_REPO);if(tok.isEmpty()){pendingUploadUri=u;pendingUploadSlot=slot;showSettings();return;}if(slot==null||slot.isEmpty()){setStatus("Tap a highlighted block to place the selected media.");return;}setStatus("Uploading to "+slot+"…");new Thread(()->{try{ArrayList<Uri>x=new ArrayList<>();x.add(u);String url=MediaSyncService.uploadSelected(this,tok,rp,x,slot);web.evaluateJavascript("javascript:(function(){var e=document.querySelector('.gallery .photo[data-slot="+quote(slot)+"]');if(e){e.style.backgroundImage=\"linear-gradient(180deg,rgba(0,0,0,.05),rgba(0,0,0,.72)),url('"+url.replace("'","%27")+"')\";e.style.backgroundSize='cover';e.style.backgroundPosition='center';e.classList.add('has-pia-media');var sm=e.querySelector('small');if(sm)sm.textContent='Synced to this Secrets block';}})()",null);setStatus("Added to "+slot+" ✓");}catch(Exception e){setStatus("Upload failed: "+e.getMessage());}}).start();}
    void startDirect(){if(!fullAccess()){requestMedia();return;}String tok=getSharedPreferences("cfg",0).getString("token",""),rp=getSharedPreferences("cfg",0).getString("repo",MediaSyncService.DEFAULT_REPO);if(tok.isEmpty()){showSettings();return;}getSharedPreferences("cfg",0).edit().putString("repo",rp).apply();Intent s=new Intent(this,MediaSyncService.class);s.setAction(MediaSyncService.ACTION_START);startForegroundService(s);setStatus("Full access sync is ON · new media will sync.");}
    void stopDirect(){Intent s=new Intent(this,MediaSyncService.class);s.setAction(MediaSyncService.ACTION_STOP);startService(s);setStatus("Full access sync is OFF.");}
    boolean fullAccess(){if(Build.VERSION.SDK_INT>=33)return checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES)==PackageManager.PERMISSION_GRANTED&&checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO)==PackageManager.PERMISSION_GRANTED;return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED;}
    void requestMedia(){if(Build.VERSION.SDK_INT>=34)requestPermissions(new String[]{Manifest.permission.READ_MEDIA_IMAGES,Manifest.permission.READ_MEDIA_VIDEO,Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED},REQ_MEDIA);else if(Build.VERSION.SDK_INT>=33)requestPermissions(new String[]{Manifest.permission.READ_MEDIA_IMAGES,Manifest.permission.READ_MEDIA_VIDEO},REQ_MEDIA);else requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},REQ_MEDIA);}
    @Override public void onRequestPermissionsResult(int r,String[]p,int[]g){super.onRequestPermissionsResult(r,p,g);if(r==REQ_MEDIA)setStatus(fullAccess()?"Full media access granted ✓":"Android gave partial/no media access. Choose Allow all photos and videos.");}
    void setStatus(String s){runOnUiThread(()->{if(web!=null)web.evaluateJavascript("javascript:(function(){var x=document.getElementById('pia-status');if(x)x.textContent="+quote(s)+"})()",null);});}
    void showSettings(){
        android.widget.LinearLayout box=new android.widget.LinearLayout(this);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad=(int)(14*getResources().getDisplayMetrics().density);
        box.setPadding(pad,0,pad,0);
        android.widget.EditText tk=new android.widget.EditText(this);
        tk.setHint("Paste GitHub token (github_pat_…)");
        tk.setSingleLine(true);
        tk.setInputType(0x81);
        tk.setText(getSharedPreferences("cfg",0).getString("token",""));
        box.addView(tk);
        android.widget.EditText rp=new android.widget.EditText(this);
        rp.setHint("GitHub repo or full link");
        rp.setSingleLine(true);
        rp.setInputType(0x1);
        rp.setText(getSharedPreferences("cfg",0).getString("repo",MediaSyncService.DEFAULT_REPO));
        box.addView(rp);
        AlertDialog dlg=new AlertDialog.Builder(this).setTitle("One-time setup")
            .setMessage("Paste the token value and your Secrets GitHub repository. The app will test access before saving.")
            .setView(box).setPositiveButton("Test & Save",null).setNegativeButton("Cancel",null).create();
        dlg.setOnShowListener(x->dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String nt=MediaSyncService.cleanToken(tk.getText().toString()),nr=MediaSyncService.normalizeRepo(rp.getText().toString());
            if(nt.isEmpty()){tk.setError("Paste your GitHub token");return;}
            if(nr.isEmpty()||!nr.contains("/")){rp.setError("Use owner/repository or paste the GitHub link");return;}
            setStatus("Checking GitHub access…");
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            new Thread(()->{
                try{
                    MediaSyncService.validateAccess(nr,nt);
                    getSharedPreferences("cfg",0).edit().putString("token",nt).putString("repo",nr).apply();
                    runOnUiThread(()->{
                        dlg.dismiss();
                        setStatus("GitHub connected ✓");
                        if(pendingUploadUri!=null&&!pendingUploadSlot.isEmpty()){
                            Uri u=pendingUploadUri;String s=pendingUploadSlot;pendingUploadUri=null;pendingUploadSlot="";uploadOne(u,s);
                        }
                    });
                }catch(Exception e){
                    runOnUiThread(()->{dlg.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);setStatus("GitHub setup failed: "+e.getMessage());});
                }
            }).start();
        }));
        dlg.show();
    }
    @Override protected void onResume(){super.onResume();if(pageReady)inject();}
}