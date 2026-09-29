package com.prince.secretspia;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    static final String DEFAULT_REPO = "divinegamingblogspot-dot/secret";
    static final int REQ_MEDIA = 41;
    EditText token, repo;
    Spinner slot;
    Switch auto;
    TextView status;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setPadding(28,28,28,28);

        TextView t = new TextView(this);
        t.setText("Secrets Pia");
        t.setTextSize(28);
        t.setTextColor(Color.rgb(210,40,100));
        r.addView(t);

        TextView i = new TextView(this);
        i.setText("Direct Sync: when you enable it and grant media access, new device photos/videos are synced automatically. A visible notification stays on while sync is active.\n\nImportant: this version uses the configured GitHub repository as storage. If that repository is public, uploaded media is publicly accessible.");
        i.setTextSize(15);
        r.addView(i);

        token = f("GitHub fine-grained token — Contents: write", true);
        r.addView(token);

        repo = f("Website repository", false);
        repo.setText(DEFAULT_REPO);
        r.addView(repo);

        slot = new Spinner(this);
        slot.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"favourite-frame","that-outfit","latest-mood","that-face","too-gorgeous","her-day","the-detail","the-laugh","memory","just-pia"}));
        r.addView(slot);

        Button p = new Button(this);
        p.setText("Choose photos / videos for a Secrets block");
        p.setOnClickListener(v -> pick());
        r.addView(p);

        Button u = new Button(this);
        u.setText("Sync selected media");
        u.setOnClickListener(v -> syncSelected());
        r.addView(u);

        auto = new Switch(this);
        auto.setText("Direct Sync — automatically sync device media");
        auto.setTextSize(16);
        auto.setOnCheckedChangeListener((v, on) -> {
            if (on) enableAutoSync();
            else stopAutoSync();
        });
        r.addView(auto);

        status = new TextView(this);
        status.setText("Direct Sync is off.");
        status.setPadding(0,20,0,0);
        r.addView(status);

        setContentView(r);
    }

    EditText f(String h, boolean pw) {
        EditText e = new EditText(this);
        e.setHint(h);
        if (pw) e.setInputType(0x81);
        return e;
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
            status.setText("Allow the requested photos/videos access, then turn Direct Sync on again.");
            auto.setChecked(false);
            return;
        }
        getSharedPreferences("cfg",MODE_PRIVATE).edit().putString("token", tok).putString("repo", rp).apply();
        Intent s = new Intent(this, MediaSyncService.class);
        s.setAction(MediaSyncService.ACTION_START);
        startForegroundService(s);
        status.setText("Direct Sync is ON. Keep the notification visible to know syncing is active.");
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

    void pick() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*","video/*"});
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i, 7);
    }

    ArrayList<android.net.Uri> selected = new ArrayList<>();

    @Override protected void onActivityResult(int q, int c, Intent d) {
        super.onActivityResult(q,c,d);
        if (q != 7 || c != RESULT_OK || d == null) return;
        selected.clear();
        if (d.getClipData() != null) for (int i=0;i<d.getClipData().getItemCount();i++) selected.add(d.getClipData().getItemAt(i).getUri());
        else if (d.getData() != null) selected.add(d.getData());
        status.setText(selected.size()+" item(s) selected for "+slot.getSelectedItem()+".");
    }

    void syncSelected() {
        String tok=token.getText().toString().trim(), rp=repo.getText().toString().trim();
        if(tok.isEmpty()||rp.isEmpty()||selected.isEmpty()){status.setText("Enter token/repository and select media.");return;}
        status.setText("Syncing selected media...");
        new Thread(() -> {
            try {
                int n = MediaSyncService.uploadSelected(this, tok, rp, selected, (String)slot.getSelectedItem());
                runOnUiThread(() -> status.setText("Synced "+n+" selected item(s)."));
            } catch(Exception e) {
                runOnUiThread(() -> status.setText("Sync failed: "+e.getMessage()));
            }
        }).start();
    }

    @Override protected void onResume() {
        super.onResume();
        if (auto != null && MediaSyncService.isRunning) {
            auto.setChecked(true);
            status.setText("Direct Sync is ON.");
        }
    }
}