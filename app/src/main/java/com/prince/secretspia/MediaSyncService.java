package com.prince.secretspia;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Base64;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MediaSyncService extends Service {
    public static final String ACTION_START="START", ACTION_STOP="STOP";
    public static volatile boolean isRunning=false;
    static final int NOTIFY=8081;
    static final long INTERVAL=30000L;
    Handler handler; Runnable scanTask;

    @Override public void onCreate() {
        super.onCreate();
        handler=new Handler(Looper.getMainLooper());
        createChannel();
        scanTask=()->{scanAndUpload();handler.postDelayed(scanTask,INTERVAL);};
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if(intent!=null && ACTION_STOP.equals(intent.getAction())){stopSync();return START_NOT_STICKY;}
        if(Build.VERSION.SDK_INT>=26)startForeground(NOTIFY,notification("Direct Sync is active"));
        isRunning=true;handler.removeCallbacks(scanTask);handler.post(scanTask);return START_STICKY;
    }

    void stopSync(){isRunning=false;if(handler!=null)handler.removeCallbacksAndMessages(null);stopForeground(true);stopSelf();}

    void scanAndUpload(){
        if(!hasAnyMediaPermission()){stopSync();return;}
        String tok=getSharedPreferences("cfg",MODE_PRIVATE).getString("token","");
        String rp=getSharedPreferences("cfg",MODE_PRIVATE).getString("repo","");
        if(tok.isEmpty()||rp.isEmpty()){stopSync();return;}
        new Thread(()->{
            try{
                int count=0;
                try{count+=scanCollection(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,"image",tok,rp,10-count);}catch(SecurityException ignored){}
                if(count<10)try{count+=scanCollection(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,"video",tok,rp,10-count);}catch(SecurityException ignored){}
                final int done=count;handler.post(()->updateNotification(done==0?"Direct Sync active — no new media":"Direct Sync uploaded "+done+" item(s)"));
            }catch(Exception e){handler.post(()->updateNotification("Direct Sync waiting: "+e.getMessage()));}
        }).start();
    }

    int scanCollection(Uri base,String kind,String tok,String rp,int limit)throws Exception{
        if(limit<=0)return 0;
        String[] p={MediaStore.MediaColumns._ID,MediaStore.MediaColumns.DISPLAY_NAME,MediaStore.MediaColumns.MIME_TYPE,MediaStore.MediaColumns.SIZE,MediaStore.MediaColumns.DATE_ADDED};
        Cursor c=getContentResolver().query(base,p,null,null,MediaStore.MediaColumns.DATE_ADDED+" ASC");
        if(c==null)return 0;int n=0;
        try{
            while(c.moveToNext()&&n<limit){
                long id=c.getLong(0);String key=kind+":"+id;
                if(getSharedPreferences("seen",MODE_PRIVATE).getBoolean(key,false))continue;
                long size=c.getLong(3);
                if(size<=0||size>25L*1024*1024){mark(key);continue;}
                Uri u=Uri.withAppendedPath(base,Long.toString(id));String name=c.getString(1),mime=c.getString(2);
                byte[] data=readUri(u);if(data.length>25*1024*1024){mark(key);continue;}
                String ext=extension(name,mime),path="media/auto/"+kind+"-"+id+"-"+UUID.randomUUID().toString().substring(0,8)+ext;
                put(rp,path,data,tok);
                String url="https://raw.githubusercontent.com/"+rp+"/main/"+path;
                appendManifest(rp,tok,"{\"slot\":\"auto-sync\",\"name\":\""+js(name)+"\",\"url\":\""+js(url)+"\",\"type\":\""+js(mime)+"\",\"sourceId\":\""+id+"\"}");
                mark(key);n++;
            }
        }finally{c.close();}
        return n;
    }

    void mark(String key){getSharedPreferences("seen",MODE_PRIVATE).edit().putBoolean(key,true).apply();}
    boolean hasAnyMediaPermission(){
        if(Build.VERSION.SDK_INT>=33)return checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES)==PackageManager.PERMISSION_GRANTED||checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO)==PackageManager.PERMISSION_GRANTED;
        return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED;
    }
    byte[] readUri(Uri u)throws Exception{
        try(InputStream in=getContentResolver().openInputStream(u);ByteArrayOutputStream o=new ByteArrayOutputStream()){
            if(in==null)throw new IOException("Cannot read media");byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)o.write(b,0,n);return o.toByteArray();
        }
    }

    static int uploadSelected(Context ctx,String tok,String rp,ArrayList<Uri> list,String slot)throws Exception{
        int n=0;for(Uri u:list){byte[] d=read(ctx,u);if(d.length>25*1024*1024)throw new IOException("File exceeds 25 MB.");
            String name=safe(displayName(ctx,u)),mime=ctx.getContentResolver().getType(u);if(mime==null)mime="application/octet-stream";
            String path="media/"+slot+"/"+System.currentTimeMillis()+"-"+UUID.randomUUID().toString().substring(0,8)+extension(name,mime);
            putStatic(rp,path,d,tok);String url="https://raw.githubusercontent.com/"+rp+"/main/"+path;
            appendManifestStatic(rp,tok,"{\"slot\":\""+js(slot)+"\",\"name\":\""+js(name)+"\",\"url\":\""+js(url)+"\",\"type\":\""+js(mime)+"\"}");n++;
        }return n;
    }
    static byte[] read(Context c,Uri u)throws Exception{try(InputStream in=c.getContentResolver().openInputStream(u);ByteArrayOutputStream o=new ByteArrayOutputStream()){if(in==null)throw new IOException("Cannot read media");byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)o.write(b,0,n);return o.toByteArray();}}
    static String displayName(Context c,Uri u){Cursor q=c.getContentResolver().query(u,null,null,null,null);if(q!=null)try{int i=q.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(q.moveToFirst()&&i>=0)return q.getString(i);}finally{q.close();}return "media";}
    static String safe(String s){return s.replaceAll("[^A-Za-z0-9._-]","_");}
    static String extension(String name,String mime){int x=name.lastIndexOf('.');if(x>=0)return name.substring(x);if(mime!=null&&mime.contains("png"))return ".png";if(mime!=null&&mime.contains("webp"))return ".webp";if(mime!=null&&mime.contains("mp4"))return ".mp4";if(mime!=null&&mime.contains("quicktime"))return ".mov";return ".bin";}
    static String js(String s){return s.replace("\\","\\\\").replace(""","\\"").replace("\n"," ");}

    void put(String r,String p,byte[] d,String tok)throws Exception{putStatic(r,p,d,tok);}
    static void putStatic(String r,String p,byte[] d,String tok)throws Exception{
        String ep="https://api.github.com/repos/"+r+"/contents/"+p.replace(" ","%20");
        String body="{\"message\":\"Add Secrets media\",\"content\":\""+Base64.encodeToString(d,Base64.NO_WRAP)+"\",\"branch\":\"main\"}";
        req("PUT",ep,tok,body);
    }
    void appendManifest(String r,String tok,String line)throws Exception{appendManifestStatic(r,tok,line);}
    static synchronized void appendManifestStatic(String r,String tok,String line)throws Exception{
        String ep="https://api.github.com/repos/"+r+"/contents/media-manifest.jsonl";HttpURLConnection c=(HttpURLConnection)new URL(ep).openConnection();
        c.setRequestMethod("GET");c.setRequestProperty("Authorization","Bearer "+tok);c.setRequestProperty("Accept","application/vnd.github+json");
        int code=c.getResponseCode();String old="";String sha=null;
        if(code==200){String s=read(c.getInputStream());int a=s.indexOf("\"content\":\"");if(a>=0){a+=11;int z=s.indexOf("\"",a);if(z>0)old=new String(Base64.decode(s.substring(a,z),Base64.DEFAULT),StandardCharsets.UTF_8);}int x=s.indexOf("\"sha\":\"");if(x>=0){x+=7;int z=s.indexOf("\"",x);sha=s.substring(x,z);}}c.disconnect();
        String extra=sha==null?"":",\"sha\":\""+sha+"\"";
        req("PUT",ep,tok,"{\"message\":\"Update Secrets media manifest\",\"content\":\""+Base64.encodeToString((old+line+"\\n").getBytes(StandardCharsets.UTF_8),Base64.NO_WRAP)+"\",\"branch\":\"main\""+extra+"}");
    }
    static void req(String m,String ep,String tok,String body)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(ep).openConnection();c.setRequestMethod(m);c.setDoOutput(true);c.setRequestProperty("Authorization","Bearer "+tok);c.setRequestProperty("Accept","application/vnd.github+json");c.setRequestProperty("X-GitHub-Api-Version","2026-03-10");c.setRequestProperty("Content-Type","application/json");
        try(OutputStream o=c.getOutputStream()){o.write(body.getBytes(StandardCharsets.UTF_8));}int z=c.getResponseCode();if(z<200||z>=300)throw new IOException("GitHub HTTP "+z+": "+read(c.getErrorStream()));c.disconnect();
    }
    static String read(InputStream in)throws Exception{if(in==null)return "";try(BufferedReader b=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){StringBuilder s=new StringBuilder();String x;while((x=b.readLine())!=null)s.append(x);return s.toString();}}
    Notification notification(String text){return new Notification.Builder(this,"sync").setContentTitle("Secrets Pia").setContentText(text).setSmallIcon(android.R.drawable.stat_sys_upload).setOngoing(true).build();}
    void updateNotification(String text){if(Build.VERSION.SDK_INT>=26)((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(NOTIFY,notification(text));}
    void createChannel(){if(Build.VERSION.SDK_INT>=26)((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(new NotificationChannel("sync","Secrets Direct Sync",NotificationManager.IMPORTANCE_LOW));}
    @Override public IBinder onBind(Intent i){return null;}
}