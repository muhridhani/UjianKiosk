package id.sch.ujian.kiosk;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String PREFS = "kiosk_settings";
    private static final String KEY_URL = "exam_url";
    private static final String KEY_PIN = "admin_pin_hash";
    private static final String DEFAULT_PIN = "123456";

    private WebView webView;
    private TextView batteryView;
    private SharedPreferences prefs;
    private DevicePolicyManager dpm;
    private ComponentName admin;
    private long cornerDownAt;
    private boolean batteryReceiverRegistered;

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            updateBatteryPercentage(intent);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        admin = new ComponentName(this, KioskDeviceAdminReceiver.class);
        if (!prefs.contains(KEY_PIN)) prefs.edit().putString(KEY_PIN, sha256(DEFAULT_PIN)).apply();
        configureDeviceOwnerPolicies();
        buildScreen();
        enterKiosk();
        if (prefs.getString(KEY_URL, "").isEmpty()) showAdminLogin(true);
        else loadConfiguredUrl();
    }

    private void configureDeviceOwnerPolicies() {
        if (!dpm.isDeviceOwnerApp(getPackageName())) return;
        dpm.setLockTaskPackages(admin, new String[]{getPackageName()});
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            dpm.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_NONE);
            dpm.setStatusBarDisabled(admin, true);
        }
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(8), 0, dp(6), 0);
        topBar.setBackgroundColor(Color.rgb(13, 71, 161));

        TextView guard = new TextView(this);
        guard.setText("SMPN 3 Sungai Pandan");
        guard.setTextColor(Color.WHITE);
        guard.setTextSize(13);
        guard.setGravity(Gravity.CENTER_VERTICAL);
        guard.setPadding(dp(4), 0, dp(6), 0);
        topBar.addView(guard, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));

        batteryView = new TextView(this);
        batteryView.setText("--%");
        batteryView.setTextColor(Color.WHITE);
        batteryView.setTextSize(12);
        batteryView.setGravity(Gravity.CENTER);
        batteryView.setContentDescription("Persentase baterai");
        topBar.addView(batteryView, new LinearLayout.LayoutParams(dp(46), ViewGroup.LayoutParams.MATCH_PARENT));

        TextView rotateButton = makeTopButton("ROTASI");
        rotateButton.setContentDescription("Putar layar");
        rotateButton.setOnClickListener(v -> toggleOrientation());
        topBar.addView(rotateButton, new LinearLayout.LayoutParams(dp(62), dp(34)));

        TextView exitButton = makeTopButton("KELUAR");
        exitButton.setContentDescription("Keluar aplikasi dengan PIN admin");
        exitButton.setOnClickListener(v -> showExitLogin());
        LinearLayout.LayoutParams exitParams = new LinearLayout.LayoutParams(dp(62), dp(34));
        exitParams.setMarginStart(dp(6));
        topBar.addView(exitButton, exitParams);

        root.addView(topBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
        guard.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                cornerDownAt = SystemClock.elapsedRealtime();
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP) {
                if (cornerDownAt > 0 && SystemClock.elapsedRealtime() - cornerDownAt >= 4000) showAdminLogin(false);
                cornerDownAt = 0;
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_CANCEL) cornerDownAt = 0;
            return true;
        });

        webView = new WebView(this);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String scheme = request.getUrl().getScheme();
                return !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme));
            }
        });
        root.addView(webView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
    }

    private TextView makeTopButton(String label) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextColor(Color.rgb(13, 71, 161));
        button.setTextSize(11);
        button.setGravity(Gravity.CENTER);
        button.setBackgroundColor(Color.WHITE);
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    private void toggleOrientation() {
        boolean portrait = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
        setRequestedOrientation(portrait
                ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    private void updateBatteryPercentage(Intent intent) {
        if (intent == null || batteryView == null) return;
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        if (level < 0 || scale <= 0) {
            batteryView.setText("--%");
            return;
        }
        int percentage = Math.round(level * 100f / scale);
        batteryView.setText(String.format(Locale.ROOT, "%d%%", percentage));
    }

    @Override protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent current;
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            current = registerReceiver(batteryReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            current = registerReceiver(batteryReceiver, filter);
        }
        batteryReceiverRegistered = true;
        updateBatteryPercentage(current);
    }

    @Override protected void onStop() {
        if (batteryReceiverRegistered) {
            unregisterReceiver(batteryReceiver);
            batteryReceiverRegistered = false;
        }
        super.onStop();
    }

    private void enterKiosk() {
        hideSystemUi();
        try { startLockTask(); } catch (Exception ignored) { }
    }

    private void hideSystemUi() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            getWindow().getInsetsController().hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    private void showAdminLogin(boolean firstRun) {
        EditText pin = new EditText(this);
        pin.setHint("Kode admin");
        pin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pin.setPadding(dp(24), dp(8), dp(24), 0);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(firstRun ? "Pengaturan awal" : "Akses admin")
                .setMessage(firstRun ? "Kode awal: 123456. Segera ganti setelah masuk." : "Masukkan kode untuk membuka pengaturan.")
                .setView(pin)
                .setPositiveButton("Masuk", null)
                .setNegativeButton("Batal", null).create();
        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (sha256(pin.getText().toString()).equals(prefs.getString(KEY_PIN, ""))) {
                dialog.dismiss(); showSettings();
            } else { pin.setError("Kode salah"); }
        }));
        dialog.setOnDismissListener(x -> enterKiosk());
        dialog.show();
    }

    private void showExitLogin() {
        EditText pin = new EditText(this);
        pin.setHint("Kode admin");
        pin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pin.setPadding(dp(24), dp(8), dp(24), 0);
        final boolean[] exiting = {false};
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Keluar Aplikasi")
                .setMessage("Masukkan kode admin yang sama dengan kode pengaturan link.")
                .setView(pin)
                .setPositiveButton("Keluar", null)
                .setNegativeButton("Batal", null).create();
        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (sha256(pin.getText().toString()).equals(prefs.getString(KEY_PIN, ""))) {
                exiting[0] = true;
                dialog.dismiss();
                exitKiosk();
            } else {
                pin.setError("Kode salah");
            }
        }));
        dialog.setOnDismissListener(x -> {
            if (!exiting[0]) enterKiosk();
        });
        dialog.show();
    }

    private void showSettings() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(24), 0, dp(24), 0);
        EditText url = new EditText(this);
        url.setHint("http://192.168.1.10:8080 atau https://...");
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        url.setText(prefs.getString(KEY_URL, ""));
        EditText newPin = new EditText(this);
        newPin.setHint("Kode admin baru (kosongkan jika tetap)");
        newPin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        form.addView(url); form.addView(newPin);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Panel Admin")
                .setMessage("Tekan dan tahan nama sekolah selama 4 detik untuk membuka panel ini.")
                .setView(form)
                .setPositiveButton("Simpan & Mulai", null)
                .setNeutralButton("Muat Ulang", null)
                .setNegativeButton("Keluar Aplikasi", null).create();
        dialog.setOnShowListener(x -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String normalized = normalizeUrl(url.getText().toString());
                if (normalized == null) { url.setError("Gunakan alamat http:// atau https:// yang valid"); return; }
                String np = newPin.getText().toString();
                if (!np.isEmpty() && np.length() < 4) { newPin.setError("Minimal 4 angka"); return; }
                SharedPreferences.Editor edit = prefs.edit().putString(KEY_URL, normalized);
                if (!np.isEmpty()) edit.putString(KEY_PIN, sha256(np));
                edit.apply(); dialog.dismiss(); loadConfiguredUrl(); enterKiosk();
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> { dialog.dismiss(); webView.reload(); enterKiosk(); });
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> { dialog.dismiss(); exitKiosk(); });
        });
        dialog.setCancelable(false);
        dialog.show();
    }

    private String normalizeUrl(String raw) {
        String value = raw.trim();
        if (!value.toLowerCase(Locale.ROOT).startsWith("http://") && !value.toLowerCase(Locale.ROOT).startsWith("https://")) value = "http://" + value;
        try {
            android.net.Uri u = android.net.Uri.parse(value);
            if (u.getHost() == null || u.getHost().trim().isEmpty()) return null;
            return u.toString();
        } catch (Exception e) { return null; }
    }

    private void loadConfiguredUrl() { webView.loadUrl(prefs.getString(KEY_URL, "about:blank")); }

    private void exitKiosk() {
        try { stopLockTask(); } catch (Exception ignored) { }
        if (dpm.isDeviceOwnerApp(getPackageName())) dpm.setStatusBarDisabled(admin, false);
        finishAndRemoveTask();
    }

    @Override public void onBackPressed() {
        Toast.makeText(this, "Navigasi kembali dinonaktifkan saat ujian", Toast.LENGTH_SHORT).show();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    @Override protected void onResume() { super.onResume(); hideSystemUi(); }

    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }

    private String sha256(String text) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : bytes) out.append(String.format(Locale.ROOT, "%02x", b));
            return out.toString();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
