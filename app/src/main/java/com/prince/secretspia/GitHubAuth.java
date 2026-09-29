package com.prince.secretspia;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

public final class GitHubAuth {
    /*
     * This is a PUBLIC OAuth application client ID, not a password or PAT.
     * Replace it once with the Client ID of the GitHub OAuth App registered
     * for Secrets Pia. The app never asks the user to paste a token.
     */
    public static final String CLIENT_ID="Ov23lie66cGFs0OvAUCF";
    static final String SCOPE="public_repo offline_access";
    static final String PREF="cfg";
    static final String ACCESS="oauth_access";
    static final String REFRESH="oauth_refresh";
    static final String EXPIRES="oauth_expires";

    public interface Callback { void done(String token, Exception error); }

    public static boolean configured(){
        return CLIENT_ID!=null && !CLIENT_ID.startsWith("REPLACE_WITH_") && CLIENT_ID.trim().length()>8;
    }

    public static String cached(Context c){
        String t=c.getSharedPreferences(PREF,0).getString(ACCESS,"");
        long exp=c.getSharedPreferences(PREF,0).getLong(EXPIRES,0);
        if(t.length()>0 && (exp==0 || System.currentTimeMillis()<exp-60000)) return t;
        return "";
    }

    public static void ensureAccess(Activity a, Callback cb){
        if(!configured()){
            cb.done("",new IOException("GitHub connection is not configured yet. The app needs its GitHub OAuth Client ID."));
            return;
        }
        String t=cached(a);
        if(!t.isEmpty()){cb.done(t,null);return;}
        String refresh=a.getSharedPreferences(PREF,0).getString(REFRESH,"");
        if(!refresh.isEmpty()){
            new Thread(()->{
                try{
                    Token tok=refresh(refresh);
                    save(a,tok);
                    cb.done(tok.access,null);
                }catch(Exception e){
                    a.getSharedPreferences(PREF,0).edit().remove(ACCESS).remove(REFRESH).remove(EXPIRES).apply();
                    a.runOnUiThread(()->startDevice(a,cb));
                }
            }).start();
        }else startDevice(a,cb);
    }

    static void startDevice(Activity a, Callback cb){
        new Thread(()->{
            try{
                JSONObject o=postForm("https://github.com/login/device/code",
                    "client_id="+enc(CLIENT_ID)+"&scope="+enc(SCOPE));
                if(o.has("error"))throw new IOException(o.optString("error")+" "+o.optString("error_description"));
                String device=o.optString("device_code");
                String user=o.optString("user_code");
                String uri=o.optString("verification_uri","https://github.com/login/device");
                int interval=o.optInt("interval",5);
                int expires=o.optInt("expires_in",900);
                a.runOnUiThread(()->showDeviceDialog(a,cb,device,user,uri,interval,expires));
            }catch(Exception e){a.runOnUiThread(()->cb.done("",e));}
        }).start();
    }

    static void showDeviceDialog(Activity a, Callback cb,String device,String user,String uri,int interval,int expires){
        LinearLayout box=new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(a,18),0,dp(a,18),0);

        TextView info=new TextView(a);
        info.setText("GitHub will authorize this app. No password or token is entered here.\n\nOpen GitHub, enter the code below, then return to this screen.");
        info.setTextSize(15);
        box.addView(info);

        TextView code=new TextView(a);
        code.setText(user);
        code.setTextSize(30);
        code.setTypeface(null,1);
        code.setGravity(Gravity.CENTER);
        code.setPadding(0,dp(a,18),0,dp(a,18));
        box.addView(code);

        Button copy=new Button(a);
        copy.setText("COPY CODE");
        copy.setOnClickListener(v->{
            ((android.content.ClipboardManager)a.getSystemService(Context.CLIPBOARD_SERVICE))
                .setPrimaryClip(android.content.ClipData.newPlainText("GitHub code",user));
            Toast.makeText(a,"Code copied",Toast.LENGTH_SHORT).show();
        });
        box.addView(copy);

        final AlertDialog[] holder=new AlertDialog[1];
        AlertDialog dlg=new AlertDialog.Builder(a)
            .setTitle("Connect GitHub")
            .setView(box)
            .setPositiveButton("OPEN GITHUB",null)
            .setNegativeButton("CANCEL",null)
            .create();
        holder[0]=dlg;
        dlg.setOnShowListener(v->{
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x->{
                try{a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(uri)));}catch(Exception ignored){}
                dlg.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                poll(a,cb,dlg,device,interval,expires);
            });
        });
        dlg.setOnDismissListener(v->{});
        dlg.show();
    }

    static void poll(Activity a,Callback cb,AlertDialog dlg,String device,int interval,int expires){
        new Thread(()->{
            long end=System.currentTimeMillis()+expires*1000L;
            int wait=Math.max(5,interval);
            while(System.currentTimeMillis()<end){
                try{
                    Thread.sleep(wait*1000L);
                    JSONObject o=postForm("https://github.com/login/oauth/access_token",
                        "client_id="+enc(CLIENT_ID)+
                        "&device_code="+enc(device)+
                        "&grant_type="+enc("urn:ietf:params:oauth:grant-type:device_code"));
                    String access=o.optString("access_token","");
                    if(!access.isEmpty()){
                        Token tok=new Token(access,o.optString("refresh_token",""),o.optLong("expires_in",28800));
                        save(a,tok);
                        a.runOnUiThread(()->{if(dlg.isShowing())dlg.dismiss();cb.done(tok.access,null);});
                        return;
                    }
                    String err=o.optString("error","");
                    if("authorization_pending".equals(err))continue;
                    if("slow_down".equals(err)){wait=Math.max(wait+5,o.optInt("interval",wait+5));continue;}
                    throw new IOException(err.length()>0?err:o.optString("error_description","GitHub authorization failed."));
                }catch(InterruptedException e){Thread.currentThread().interrupt();return;}
                catch(Exception e){
                    a.runOnUiThread(()->{if(dlg.isShowing())dlg.dismiss();cb.done("",e);});
                    return;
                }
            }
            a.runOnUiThread(()->{if(dlg.isShowing())dlg.dismiss();cb.done("",new IOException("GitHub authorization timed out. Tap Connect GitHub to try again."));});
        }).start();
    }

    static Token refresh(String refresh)throws Exception{
        JSONObject o=postForm("https://github.com/login/oauth/access_token",
            "client_id="+enc(CLIENT_ID)+
            "&refresh_token="+enc(refresh)+
            "&grant_type=refresh_token");
        String a=o.optString("access_token","");
        if(a.isEmpty())throw new IOException(o.optString("error_description","GitHub refresh failed."));
        return new Token(a,o.optString("refresh_token",refresh),o.optLong("expires_in",28800));
    }

    static void save(Context c,Token t){
        long exp=t.expires<=0?0:System.currentTimeMillis()+t.expires*1000L;
        c.getSharedPreferences(PREF,0).edit()
            .putString(ACCESS,t.access)
            .putString(REFRESH,t.refresh)
            .putLong(EXPIRES,exp)
            .apply();
    }

    public static void disconnect(Context c){
        c.getSharedPreferences(PREF,0).edit().remove(ACCESS).remove(REFRESH).remove(EXPIRES).apply();
    }

    static JSONObject postForm(String endpoint,String form)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(endpoint).openConnection();
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Accept","application/json");
        c.setRequestProperty("Content-Type","application/x-www-form-urlencoded");
        try(OutputStream out=c.getOutputStream()){out.write(form.getBytes(StandardCharsets.UTF_8));}
        int z=c.getResponseCode();
        String body=read(z>=200&&z<300?c.getInputStream():c.getErrorStream());
        c.disconnect();
        if(body.isEmpty())throw new IOException("GitHub returned HTTP "+z);
        JSONObject o=new JSONObject(body);
        if(z<200||z>=300)throw new IOException(o.optString("error_description",o.optString("message","GitHub HTTP "+z)));
        return o;
    }

    public static void validateRepository(String repo,String token)throws Exception{
        String r=repo==null?"":repo.trim();
        if(!r.contains("/"))throw new IOException("Secrets repository configuration is invalid.");
        HttpURLConnection c=(HttpURLConnection)new URL("https://api.github.com/repos/"+r).openConnection();
        c.setRequestProperty("Authorization","Bearer "+token);
        c.setRequestProperty("Accept","application/vnd.github+json");
        c.setRequestProperty("X-GitHub-Api-Version","2026-03-10");
        int z=c.getResponseCode();
        String body=read(z>=200&&z<300?c.getInputStream():c.getErrorStream());
        c.disconnect();
        if(z<200||z>=300)throw new IOException("GitHub repository access failed (HTTP "+z+").");
        JSONObject o=new JSONObject(body);
        JSONObject p=o.optJSONObject("permissions");
        if(p!=null && !p.optBoolean("push",false))throw new IOException("This GitHub account can read the Secrets repository but cannot upload to it.");
    }

    static String enc(String s)throws Exception{return URLEncoder.encode(s,StandardCharsets.UTF_8.name());}
    static String read(InputStream in)throws Exception{
        if(in==null)return "";
        try(BufferedReader b=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){
            StringBuilder s=new StringBuilder();String x;while((x=b.readLine())!=null)s.append(x);return s.toString();
        }
    }
    static int dp(Context c,int n){return (int)(n*c.getResources().getDisplayMetrics().density+.5f);}
    static final class Token{
        final String access,refresh;final long expires;
        Token(String a,String r,long e){access=a;refresh=r;expires=e;}
    }
}
