package com.prince.secretspia;

import android.app.Activity;
import android.os.Bundle;
import android.content.*;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import android.util.Base64;

public class MainActivity extends Activity {
    private static final String DEFAULT_REPO = "divinegamingblogspot-dot/secret";
    private EditText token, repo;
    private Spinner slot;
    private TextView status;
    private final ArrayList<Uri> selected = new ArrayList<>();

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32,32,32,32);

        TextView title = new TextView(this);
        title.setText("Secrets Pia");
        title.setTextSize(28); title.setTextColor(Color.rgb(210,40,100));
        root.addView(title);

        TextView info = new TextView(this);
        info.setText("Choose media from Pia's phone, choose a Secrets image block, then sync it.");
        info.setTextSize(16); root.addView(info);

        token = field("GitHub fine-grained token (Contents: write)", true);
        root.addView(token);

        repo = field("Website repository", false);
        repo.setText(DEFAULT_REPO); root.addView(repo);

        slot = new Spinner(this);
        String[] slots = {"favourite-frame","that-outfit","latest-mood","that-face","too-gorgeous"};
        slot.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, slots));
        root.addView(slot);

        Button pick = new Button(this); pick.setText("Choose photos / videos");
        pick.setOnClickListener(v -> chooseMedia()); root.addView(pick);

        Button upload = new Button(this); upload.setText("Sync selected media");
        upload.setOnClickListener(v -> sync()); root.addView(upload);

        status = new TextView(this); status.setText("Nothing selected yet."); status.setPadding(0,24,0,0);
        root.addView(status);
        setContentView(root);
    }

    private EditText field(String hint, boolean password) {
        EditText e = new EditText(this); e.setHint(hint);
        if(password) e.setInputType(0x00000081);
        return e;
    }

    private void chooseMedia() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*","video/*"});
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i, 7);
    }

    @Override protected void onActivityResult(int r, int c, Intent d) {
        super.onActivityResult(r,c,d);
        if(r != 7 || c != RESULT_OK || d == null) return;
        selected.clear();
        if(d.getClipData()!=null) for(int i=0;i<d.getClipData().getItemCount();i++) selected.add(d.getClipData().getItemAt(i).getUri());
        else if(d.getData()!=null) selected.add(d.getData());
        status.setText(selected.size()+" media item(s) selected.");
    }

    private void sync() {
        final String t = token.getText().toString().trim();
        final String r = repo.getText().toString().trim();
        if(t.isEmpty() || r.isEmpty() || selected.isEmpty()) {
            status.setText("Enter the GitHub token/repository and select media first.");
            return;
        }
        status.setText("Syncing...");
        new Thread(() -> {
            int ok=0;
            try {
                String folder=(String)slot.getSelectedItem();
                for(Uri u:selected) {
                    String name=safeName(displayName(u));
                    String ext=name.contains(".")?name.substring(name.lastIndexOf('.')):"";
                    String path="media/"+folder+"/"+System.currentTimeMillis()+"-"+UUID.randomUUID().toString().substring(0,8)+ext;
                    byte[] bytes=readBytes(u);
                    if(bytes.length>25*1024*1024) throw new IOException("File over 25 MB: "+name);
                    putFile(r,path,bytes,t,"Add Secrets media");
                    String url="https://raw.githubusercontent.com/"+r+"/main/"+path;
                    appendManifest(r,t, "{"slot":""+json(folder)+"","name":""+json(name)+"","url":""+json(url)+"","type":""+json(getContentType(u))+"","createdAt":""+new Date()+""}");
                    ok++;
                }
                final int done=ok; runOnUiThread(()->status.setText("Synced "+done+" item(s)."));
            } catch(Exception e) { runOnUiThread(()->status.setText("Sync failed: "+e.getMessage())); }
        }).start();
    }

    private byte[] readBytes(Uri u) throws Exception {
        try(InputStream in=getContentResolver().openInputStream(u); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            if(in==null) throw new IOException("Cannot read selected media");
            byte[] b=new byte[8192]; int n; while((n=in.read(b))!=-1) out.write(b,0,n);
            return out.toByteArray();
        }
    }

    private void putFile(String repoName,String path,byte[] bytes,String token,String message) throws Exception {
        String endpoint="https://api.github.com/repos/"+repoName+"/contents/"+encodePath(path);
        String body="{"message":""+json(message)+"","content":""+Base64.encodeToString(bytes,Base64.NO_WRAP)+"","branch":"main"}";
        request("PUT",endpoint,token,body);
    }

    private void appendManifest(String repoName,String token,String line) throws Exception {
        String endpoint="https://api.github.com/repos/"+repoName+"/contents/media-manifest.jsonl";
        HttpURLConnection c=(HttpURLConnection)new URL(endpoint).openConnection();
        c.setRequestMethod("GET"); c.setRequestProperty("Authorization","Bearer "+token); c.setRequestProperty("Accept","application/vnd.github+json");
        int code=c.getResponseCode(); String old=""; String sha=null;
        if(code==200){ String s=read(c.getInputStream()); int p=s.indexOf("\"content\":\""); if(p>=0){ int a=p+11,b=s.indexOf("\"",a); old=new String(Base64.decode(s.substring(a,b).replace("\\n",""),Base64.DEFAULT),StandardCharsets.UTF_8); } int sp=s.indexOf("\"sha\":\""); if(sp>=0){int a=sp+7,b=s.indexOf("\"",a);sha=s.substring(a,b);} }
        c.disconnect();
        String body="{"message":"Update Secrets media manifest","content":""+Base64.encodeToString((old+line+"\\n").getBytes(StandardCharsets.UTF_8),Base64.NO_WRAP)+"","branch":"main""+(sha!=null?",\"sha\":\""+sha+"\"":"")+"}";
        request("PUT",endpoint,token,body);
    }

    private void request(String method,String endpoint,String token,String body) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(endpoint).openConnection();
        c.setRequestMethod(method); c.setDoOutput(true); c.setRequestProperty("Authorization","Bearer "+token);
        c.setRequestProperty("Accept","application/vnd.github+json"); c.setRequestProperty("X-GitHub-Api-Version","2026-03-10"); c.setRequestProperty("Content-Type","application/json");
        try(OutputStream o=c.getOutputStream()){o.write(body.getBytes(StandardCharsets.UTF_8));}
        int code=c.getResponseCode(); if(code<200||code>=300) throw new IOException("GitHub HTTP "+code+": "+read(c.getErrorStream()));
        c.disconnect();
    }

    private String displayName(Uri u){ Cursor c=getContentResolver().query(u,null,null,null,null); if(c!=null){try{int i=c.getColumnIndex(OpenableColumns.DISPLAY_NAME); if(c.moveToFirst()&&i>=0)return c.getString(i);}finally{c.close();}} return "media"; }
    private String getContentType(Uri u){String t=getContentResolver().getType(u);return t==null?"application/octet-stream":t;}
    private String safeName(String s){return s.replaceAll("[^A-Za-z0-9._-]","_");}
    private String encodePath(String p){return p.replace(" ","%20");}
    private String json(String s){return s.replace("\\","\\\\").replace(""","\\"").replace("\n"," "); }
    private String read(InputStream in)throws Exception{if(in==null)return "";try(BufferedReader b=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){StringBuilder s=new StringBuilder();String x;while((x=b.readLine())!=null)s.append(x);return s.toString();}}
}
