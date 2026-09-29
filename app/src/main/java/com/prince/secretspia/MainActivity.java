package com.prince.secretspia;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    static final String SITE="https://divinegamingblogspot-dot.github.io/secret/";
    static final int PICK_MEDIA=71, REQ_MEDIA=72;
    final ArrayList<Uri> selected=new ArrayList<>();
    WebView web;
    LinearLayout root, tray, thumbs;
    TextView status, selectedCount;
    Button addButton, placeButton, syncButton, githubButton;
    boolean pageReady=false, placing=false, uploading=false;
    int armedIndex=-1;
    Uri pendingUploadUri=null;
    String pendingUploadSlot="";
    Handler main=new Handler(Looper.getMainLooper());

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        stopBackgroundSync();
        buildUi();
        setupWeb();
        web.loadUrl(SITE);
        handleIncomingIntent(getIntent());
    }

    void buildUi(){
        root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(9,7,11));

        web=new WebView(this);
        root.addView(web,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout panel=new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14),dp(10),dp(14),dp(10));
        panel.setBackgroundColor(Color.rgb(16,12,18));

        LinearLayout top=new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=label("PIA",16,true);
        top.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        addButton=button("＋ ADD IMAGE");
        placeButton=button("PLACE IMAGE");
        syncButton=button("HARD SYNC");
        syncButton.setEnabled(true);
        githubButton=button("GITHUB");
        top.addView(addButton);
        top.addView(space(6));
        top.addView(placeButton);
        top.addView(space(6));
        top.addView(syncButton);
        top.addView(space(6));
        top.addView(githubButton);
        panel.addView(top);

        selectedCount=label("No media selected · tap ADD IMAGE to choose",11,false);
        selectedCount.setPadding(0,dp(8),0,dp(4));
        panel.addView(selectedCount);

        HorizontalScrollView hsv=new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        thumbs=new LinearLayout(this);
        thumbs.setOrientation(LinearLayout.HORIZONTAL);
        hsv.addView(thumbs);
        panel.addView(hsv,new LinearLayout.LayoutParams(-1,dp(72)));

        status=label("Loading Secrets…",10,false);
        status.setPadding(0,dp(5),0,0);
        panel.addView(status);

        root.addView(panel,new LinearLayout.LayoutParams(-1,dp(142)));
        setContentView(root);

        addButton.setOnClickListener(v->{ pendingUploadSlot=""; pick(null); });
        placeButton.setOnClickListener(v->togglePlaceMode());
        syncButton.setOnClickListener(v->toggleSync());
        githubButton.setOnClickListener(v->connectGitHub());
    }

    void setupWeb(){
        WebSettings s=web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(true);
        web.setBackgroundColor(Color.rgb(9,7,11));
        web.addJavascriptInterface(new Bridge(),"PiaAndroid");
        web.setWebViewClient(new WebViewClient(){
            @Override public void onPageFinished(WebView v,String u){
                pageReady=true;
                main.postDelayed(()->injectTargets(),350);
                main.postDelayed(()->injectTargets(),1200);
            }
        });
    }

    void injectTargets(){
        if(!pageReady||web==null)return;
        String js="javascript:(function(){"+
            "window.__piaPlace="+((placing||!selected.isEmpty())?"true":"false")+";"+
            "var old=document.getElementById('pia-target-style');"+
            "if(!old){var st=document.createElement('style');st.id='pia-target-style';st.textContent="+q(
                ".pia-target{outline:2px dashed rgba(224,119,166,.95)!important;outline-offset:-7px!important;cursor:pointer!important;position:relative!important;}"+
                ".pia-target:after{content:'TAP TO PLACE';position:absolute;left:50%;top:50%;transform:translate(-50%,-50%);z-index:9999;background:rgba(12,8,14,.9);color:#fff;border:1px solid rgba(255,255,255,.38);border-radius:999px;padding:7px 11px;font:700 9px Arial,sans-serif;letter-spacing:.8px;pointer-events:none;opacity:.96}"+
                ".pia-target.pia-active{outline:3px solid #fff!important;box-shadow:0 0 0 5px rgba(224,119,166,.35),0 0 30px rgba(224,119,166,.35)!important}"+
                ".pia-target.pia-has{outline-color:rgba(255,255,255,.55)!important}"
            )+";document.head.appendChild(st);}"+
            "var es=document.querySelectorAll('.gallery .photo');"+
            "for(var i=0;i<es.length;i++){(function(el,ix){"+
            "el.classList.add('pia-target');"+
            "if(window.__piaPlace)el.classList.add('pia-active');else el.classList.remove('pia-active');"+
            "if(el.__piaBound)return;el.__piaBound=true;"+
            "el.addEventListener('click',function(e){e.preventDefault();e.stopPropagation();"+
            "var slot=el.getAttribute('data-slot')||('gallery-'+ix);"+
            "if(window.PiaAndroid)PiaAndroid.target(slot);"+
            "},true);"+
            "})(es[i],i);}"+
            "if(window.PiaAndroid)PiaAndroid.ready();"+
            "})()";
        web.evaluateJavascript(js,null);
    }

    void togglePlaceMode(){
        placing=!placing;
        placeButton.setText(placing?"CANCEL":"PLACE IMAGE");
        placeButton.setEnabled(!uploading);
        status.setText(placing?"Targets highlighted · tap a block":"Place mode off");
        injectTargets();
    }

    void target(String slot){
        if(uploading)return;
        if(slot==null||slot.isEmpty()){setStatus("This block has no upload slot.");return;}
        placing=true;
        placeButton.setText("CANCEL");
        if(armedIndex>=0&&armedIndex<selected.size()){
            uploadOne(selected.get(armedIndex),slot);
            return;
        }
        pendingUploadSlot=slot;
        pick(slot);
    }

    void stopBackgroundSync(){
        try{
            Intent s=new Intent(this,MediaSyncService.class);
            s.setAction(MediaSyncService.ACTION_STOP);
            startService(s);
        }catch(Exception ignored){}
    }

    void toggleSync(){
        if(uploading){setStatus("An image upload is already running.");return;}
        if(MediaSyncService.isRunning){
            setStatus("Hard Sync is already running…");
            return;
        }
        if(!hasAnyMediaAccessForSync()){
            requestMedia();
            return;
        }
        connectGitHubAndStartSync();
    }

    boolean hasAnyMediaAccessForSync(){
        if(Build.VERSION.SDK_INT>=33){
            return checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES)==PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO)==PackageManager.PERMISSION_GRANTED;
        }
        return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED;
    }

    void connectGitHubAndStartSync(){
        GitHubAuth.ensureAccess(this,(tok,err)->{
            if(err!=null){setStatus("GitHub connection needed: "+err.getMessage());return;}
            new Thread(()->{
                try{
                    GitHubAuth.validateRepository(MediaSyncService.DEFAULT_REPO,tok);
                    runOnUiThread(()->{
                        Intent s=new Intent(this,MediaSyncService.class);
                        s.setAction(MediaSyncService.ACTION_START);
                        startForegroundService(s);
                        syncButton.setText("HARD SYNC");
                        setStatus("Hard Sync started · one-time scan only. No background auto-sync.");
                    });
                }catch(Exception e){setStatus("GitHub connection failed: "+e.getMessage());}
            }).start();
        });
    }

    void pick(String slot){
        if(slot!=null)pendingUploadSlot=slot;
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/*","video/*"});
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        try{startActivityForResult(i,PICK_MEDIA);}catch(Exception e){setStatus("Could not open media picker: "+e.getMessage());}
    }

    @Override protected void onActivityResult(int q,int c,Intent d){
        super.onActivityResult(q,c,d);
        if(q!=PICK_MEDIA||c!=RESULT_OK||d==null)return;
        selected.clear();thumbs.removeAllViews();armedIndex=-1;
        if(d.getClipData()!=null){
            for(int i=0;i<d.getClipData().getItemCount();i++)addUri(d.getClipData().getItemAt(i).getUri());
        }else if(d.getData()!=null)addUri(d.getData());
        refreshSelectionUi();
        String slot=pendingUploadSlot;
        pendingUploadSlot="";
        if(slot!=null&&!slot.isEmpty()&&!selected.isEmpty()){
            uploadOne(selected.get(0),slot);
        }else if(!selected.isEmpty()){
            placing=true;
            placeButton.setText("CANCEL");
            setStatus("Image ready ✓ · all image blocks are highlighted. Tap any block to place it.");
            injectTargets();
        }
    }

    void addUri(Uri u){
        try{getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);}catch(Exception ignored){}
        selected.add(u);
    }

    void refreshSelectionUi(){
        thumbs.removeAllViews();
        for(int i=0;i<selected.size();i++)addThumb(i,selected.get(i));
        selectedCount.setText(selected.isEmpty()?"No media selected":selected.size()+" media selected · tap a thumbnail to arm it");
    }

    void addThumb(int id,Uri u){
        FrameLayout box=new FrameLayout(this);
        ImageView im=new ImageView(this);
        im.setScaleType(ImageView.ScaleType.CENTER_CROP);
        box.addView(im,new FrameLayout.LayoutParams(dp(60),dp(60)));
        TextView n=label(String.valueOf(id+1),9,true);
        n.setGravity(Gravity.CENTER);
        n.setTextColor(Color.WHITE);
        n.setBackgroundColor(0xaa000000);
        FrameLayout.LayoutParams np=new FrameLayout.LayoutParams(dp(22),dp(22),Gravity.BOTTOM|Gravity.END);
        box.addView(n,np);
        box.setPadding(dp(2),dp(2),dp(2),dp(2));
        box.setOnClickListener(v->arm(id,box));
        thumbs.addView(box,new LinearLayout.LayoutParams(dp(66),dp(66)));
        new Thread(()->{
            try{
                Bitmap b=loadThumb(u);
                runOnUiThread(()->{im.setImageBitmap(b);if(id==0&&armedIndex<0)arm(id,box);});
            }catch(Exception e){runOnUiThread(()->setStatus("Preview skipped for item "+(id+1)+": "+e.getMessage()));}
        }).start();
    }

    void arm(int id,View box){
        if(id<0||id>=selected.size())return;
        armedIndex=id;
        for(int i=0;i<thumbs.getChildCount();i++)thumbs.getChildAt(i).setBackgroundColor(Color.TRANSPARENT);
        box.setBackgroundColor(Color.rgb(190,55,112));
        placing=true;
        placeButton.setText("CANCEL");
        setStatus("Image "+(id+1)+" armed ✓ · all image blocks are highlighted. Tap any block to place it.");
        injectTargets();
    }

    Bitmap loadThumb(Uri u)throws Exception{
        if(Build.VERSION.SDK_INT>=29)return getContentResolver().loadThumbnail(u,new android.util.Size(160,160),null);
        try(InputStream in=getContentResolver().openInputStream(u)){
            if(in==null)throw new IOException("Cannot open media");
            BitmapFactory.Options o=new BitmapFactory.Options();o.inSampleSize=4;
            Bitmap b=BitmapFactory.decodeStream(in,null,o);
            if(b==null)throw new IOException("Unsupported media");
            return b;
        }
    }

    void uploadOne(Uri u,String slot){
        if(u==null||slot==null||slot.isEmpty())return;
        if(uploading)return;
        String rp=MediaSyncService.DEFAULT_REPO;
        pendingUploadUri=u;
        pendingUploadSlot=slot;
        uploading=true;
        addButton.setEnabled(false);placeButton.setEnabled(false);
        setStatus("Uploading to "+slot+"…");
        GitHubAuth.ensureAccess(this,(tok,authErr)->{
            if(authErr!=null){
                runOnUiThread(()->{
                    uploading=false;addButton.setEnabled(true);placeButton.setEnabled(true);
                    setStatus("Connect GitHub first: "+authErr.getMessage());
                    pendingUploadUri=u;pendingUploadSlot=slot;
                    connectGitHub();
                });
                return;
            }
            new Thread(()->{
            try{
                ArrayList<Uri> one=new ArrayList<>();one.add(u);
                String url=MediaSyncService.uploadSelected(this,tok,rp,one,slot);
                runOnUiThread(()->{
                    uploading=false;addButton.setEnabled(true);placeButton.setEnabled(true);
                    armedIndex=-1;
                    setStatus("Added to "+slot+" ✓");
                    injectMediaIntoPage(slot,url);
                    placing=false;placeButton.setText("PLACE IMAGE");
                    injectTargets();
                });
            }catch(Exception e){
                runOnUiThread(()->{
                    uploading=false;addButton.setEnabled(true);placeButton.setEnabled(true);
                    setStatus("Upload failed: "+friendlyError(e));
                });
            }
            }).start();
        });
    }

    void injectMediaIntoPage(String slot,String url){
        if(url==null)return;
        String js="javascript:(function(){var e=document.querySelector('.gallery .photo[data-slot='+"+q(slot)+"]');"+
            "if(e){e.style.backgroundImage='linear-gradient(180deg,rgba(0,0,0,.05),rgba(0,0,0,.72)),url(\\'"+url.replace("\\","\\\\").replace("'","%27")+"\\')';e.style.backgroundSize='cover';e.style.backgroundPosition='center';e.classList.add('pia-has');}})()";
        web.evaluateJavascript(js,null);
    }

    String friendlyError(Exception e){
        String x=e.getMessage()==null?"Unknown error":e.getMessage();
        if(x.contains("401"))return "GitHub authorization expired. Tap GITHUB and reconnect.";
        if(x.contains("403"))return "GitHub denied write access to the Secrets repository.";
        if(x.contains("404"))return "The Secrets repository could not be accessed.";
        if(x.contains("25 MB"))return "That file is over the 25 MB GitHub upload limit.";
        return x;
    }

    void connectGitHub(){
        setStatus("Connecting to GitHub…");
        GitHubAuth.ensureAccess(this,(tok,err)->{
            if(err!=null){setStatus("GitHub connection failed: "+err.getMessage());return;}
            new Thread(()->{
                try{
                    GitHubAuth.validateRepository(MediaSyncService.DEFAULT_REPO,tok);
                    runOnUiThread(()->{
                        setStatus("GitHub connected ✓ · Secrets uploads are ready.");
                        Toast.makeText(this,"GitHub connected",Toast.LENGTH_SHORT).show();
                        if(pendingUploadUri!=null&&!pendingUploadSlot.isEmpty()){
                            Uri u=pendingUploadUri;String s=pendingUploadSlot;
                            pendingUploadUri=null;pendingUploadSlot="";
                            uploadOne(u,s);
                        }
                    });
                }catch(Exception e){setStatus("GitHub connected, but repository access failed: "+e.getMessage());}
            }).start();
        });
    }

    void showConnection(){
        connectGitHub();
    }

    void handleIncomingIntent(Intent in){
        if(in==null)return;
        String a=in.getAction();
        if(Intent.ACTION_SEND.equals(a)&&in.getParcelableExtra(Intent.EXTRA_STREAM)!=null){
            Uri u=in.getParcelableExtra(Intent.EXTRA_STREAM);
            if(u!=null){selected.add(u);refreshSelectionUi();}
        }else if(Intent.ACTION_SEND_MULTIPLE.equals(a)&&in.getParcelableArrayListExtra(Intent.EXTRA_STREAM)!=null){
            ArrayList<Uri> list=in.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if(list!=null){selected.addAll(list);refreshSelectionUi();}
        }
    }

    boolean fullAccess(){
        if(Build.VERSION.SDK_INT>=33)return checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES)==PackageManager.PERMISSION_GRANTED&&checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO)==PackageManager.PERMISSION_GRANTED;
        return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED;
    }

    void requestMedia(){
        if(Build.VERSION.SDK_INT>=34)requestPermissions(new String[]{Manifest.permission.READ_MEDIA_IMAGES,Manifest.permission.READ_MEDIA_VIDEO,Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED},REQ_MEDIA);
        else if(Build.VERSION.SDK_INT>=33)requestPermissions(new String[]{Manifest.permission.READ_MEDIA_IMAGES,Manifest.permission.READ_MEDIA_VIDEO},REQ_MEDIA);
        else requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},REQ_MEDIA);
    }

    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){
        super.onRequestPermissionsResult(r,p,g);
        if(r==REQ_MEDIA){
            if(fullAccess()){setStatus("Full media access granted ✓");}
            else setStatus("Partial/no access. Android Settings can change photo/video access.");
        }
    }

    void setStatus(String s){runOnUiThread(()->status.setText(s));}

    @Override protected void onResume(){
        super.onResume();
        if(pageReady){main.postDelayed(()->injectTargets(),250);main.postDelayed(()->injectTargets(),900);}
    }

    static String q(String s){
        if(s==null)return "null";
        return "'"+s.replace("\\","\\\\").replace("'","\\'").replace("\n"," ")+"'";
    }
    Button button(String s){
        Button b=new Button(this);b.setText(s);b.setTextSize(10);b.setTextColor(Color.WHITE);b.setAllCaps(false);
        b.setMinHeight(0);b.setMinimumHeight(0);b.setPadding(dp(9),0,dp(9),0);return b;
    }
    TextView label(String s,int size,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(Color.WHITE);t.setTypeface(null,bold?1:0);return t;}
    View space(int w){Space s=new Space(this);s.setLayoutParams(new LinearLayout.LayoutParams(dp(w),1));return s;}
    int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}

    public class Bridge{
        @JavascriptInterface public void ready(){runOnUiThread(()->{if(status!=null&&!uploading)status.setText((placing||!selected.isEmpty())?"Image blocks highlighted · tap a block":"Ready · tap ADD IMAGE to choose media");});}
        @JavascriptInterface public void target(String slot){runOnUiThread(()->MainActivity.this.target(slot));}
    }
}
