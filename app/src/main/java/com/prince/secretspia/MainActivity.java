package com.prince.secretspia;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.DragEvent;
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
    ArrayList<Uri> selected = new ArrayList<>();

    final String[] SLOTS = {
        "favourite-frame","that-outfit","latest-mood","that-face","too-gorgeous",
        "her-day","the-detail","the-laugh","memory","just-pia"
    };

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        buildUi();
        handleIncomingIntent(getIntent());
    }

    void buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28,28,28,28);
        root.setBackgroundColor(Color.rgb(255,248,252));

        TextView title = new TextView(this);
        title.setText("Secrets Pia");
        title.setTextSize(28);
        title.setTextColor(Color.rgb(210,40,100));
        root.addView(title);

        TextView info = new TextView(this);
        info.setText("Add media to the Secrets website, assign it to a block, or keep Direct Sync enabled for authorized device media. You can also share photos/videos to this app from Gallery or Files.\n\nStorage note: the configured GitHub repository is the storage target. If it is public, uploaded media is publicly accessible.");
        info.setTextSize(15);
        root.addView(info);

        token = f("GitHub fine-grained token — Contents: write", true);
        root.addView(token);

        repo = f("Website repository", false);
        repo.setText(DEFAULT_REPO);
        root.addView(repo);

        slot = new Spinner(this);
        slot.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, SLOTS));
        root.addView(slot);

        selectionInfo = new TextView(this);
        selectionInfo.setText("No media selected.");
        selectionInfo.setPadding(0,14,0,8);
        root.addView(selectionInfo);

        Button add = new Button(this);
        add.setText("＋ Add photos / videos to website");
        add.setOnClickListener(v -> pick());
        root.addView(add);

        Button shareHelp = new Button(this);
        shareHelp.setText("Share / drag media into Secrets Pia");
        shareHelp.setOnClickListener(v -> showDropHelp());
        root.addView(shareHelp);

        Button sync = new Button(this);
        sync.setText("Upload selected to this block");
        sync.setOnClickListener(v -> syncSelected());
        root.addView(sync);

        Button clear = new Button(this);
        clear.setText("Clear selection");
        clear.setOnClickListener(v -> {
            selected.clear();
            refreshSelection();
        });
        root.addView(clear);

        Button scan = new Button(this);
        scan.setText("Sync new device media now");
        scan.setOnClickListener(v -> {
            if (!hasMediaPermission()) {
                requestMediaPermission();
                return;
            }
            startDirectSync();
            status.setText("Sync requested. New authorized device media will be queued.");
        });
        root.addView(scan);

        auto = new Switch(this);
        auto.setText("Direct Sync — automatically sync new device media");
        auto.setTextSize(16);
        auto.setOnCheckedChangeListener((v, on) -> {
            if (on) enableAutoSync();
            else stopAutoSync();
        });
        root.addView(auto);

        Button site = new Button(this);
        site.setText("Open Secrets website");
        site.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://divinegamingblogspot-dot.github.io/secret/"));
            startActivity(i);
        });
        root.addView(site);

        Button settings = new Button(this);
        settings.setText("Open app media permissions");
        settings.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName()))));
        root.addView(settings);

        status = new TextView(this);
        status.setText("Direct Sync is off.");
        status.setPadding(0,18,0,0);
        root.addView(status);

        root.setOnDragListener((v, event) -> {
            if (event.getAction() == DragEvent.ACTION_DROP) {
                ClipData cd = event.getClipData();
                if (cd != null) {
                    for (int i=0;i<cd.getItemCount();i++) {
                        Uri u = cd.getItemAt(i).getUri();
                        if (u != null && !selected.contains(u)) selected.add(u);
                    }
                    refreshSelection();
                    return true;
                }
            }
            return true;
        });

        setContentView(root);
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
        if (!selected.isEmpty()) refreshSelection();
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
        selectionInfo.setText(selected.size()+" item(s) ready for \"" + slot.getSelectedItem() + "\".");
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
        status.setText("Uploading "+selected.size()+" item(s) to \"" + slot.getSelectedItem() + "\"...");
        final ArrayList<Uri> batch = new ArrayList<>(selected);
        final String chosen = (String)slot.getSelectedItem();
        new Thread(() -> {
            try {
                int n = MediaSyncService.uploadSelected(this, tok, rp, batch, chosen);
                runOnUiThread(() -> status.setText("Uploaded "+n+" item(s) to "+chosen+". Prince's synced-media feed remains separate and unchanged."));
            } catch(Exception e) {
                runOnUiThread(() -> status.setText("Upload failed: "+e.getMessage()));
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
