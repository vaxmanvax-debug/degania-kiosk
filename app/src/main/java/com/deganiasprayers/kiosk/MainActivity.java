package com.deganiasprayers.kiosk;

import android.app.*;
import android.app.admin.DevicePolicyManager;
import android.content.*;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.*;
import android.text.InputType;
import android.util.Base64;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class MainActivity extends Activity {
  private static final String HOME = "https://degania-sprayers.com/";
  private WebView browser;
  private FrameLayout frame;
  private LinearLayout errorView;
  private ProgressBar progress;
  private android.content.SharedPreferences settings;
  private DevicePolicyManager dpm;
  private ComponentName receiver;
  private boolean dialogOpen = false;
  private boolean admin = false;
  private int tapCount = 0, badPins = 0;
  private long firstTap = 0, previousTap = 0, blockedUntil = 0;
  private float downX, downY;
  private final Handler handler = new Handler(Looper.getMainLooper());

  @Override public void onCreate(Bundle saved) {
    super.onCreate(saved);
    settings = getSharedPreferences("degania_admin_pin", MODE_PRIVATE);
    dpm = (DevicePolicyManager)getSystemService(DEVICE_POLICY_SERVICE);
    receiver = new ComponentName(this, KioskAdminReceiver.class);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    hideSystemBars();
    buildScreen();
    if (!settings.contains("pin_hash")) handler.post(this::setFirstPin);
  }

  private void buildScreen() {
    frame = new FrameLayout(this);
    frame.setBackgroundColor(0xFFFFFFFF);
    browser = new WebView(this);
    frame.addView(browser, new FrameLayout.LayoutParams(-1, -1));
    progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
    progress.setMax(100);
    FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(-1, dp(3), Gravity.TOP);
    frame.addView(progress, p);
    progress.setVisibility(View.GONE);
    errorView = new LinearLayout(this);
    errorView.setBackgroundColor(0xFFFFFFFF);
    errorView.setOrientation(LinearLayout.VERTICAL);
    errorView.setGravity(Gravity.CENTER);
    errorView.setPadding(dp(25),dp(20),dp(25),dp(20));
    TextView msg = new TextView(this);
    msg.setText("Не удалось открыть Degania Sprayers.\nПроверь Wi-Fi или подключение к интернету.");
    msg.setTextSize(18);
    msg.setTextColor(0xFF303030);
    msg.setGravity(Gravity.CENTER);
    errorView.addView(msg);
    Button retry = new Button(this);
    retry.setText("ПОВТОРИТЬ");
    retry.setOnClickListener(v -> {errorView.setVisibility(View.GONE);browser.loadUrl(HOME);});
    errorView.addView(retry);
    frame.addView(errorView, new FrameLayout.LayoutParams(-1,-1));
    errorView.setVisibility(View.GONE);
    setContentView(frame);

    WebSettings w = browser.getSettings();
    w.setJavaScriptEnabled(true);
    w.setDomStorageEnabled(true);
    w.setAllowFileAccess(false);
    w.setAllowContentAccess(false);
    w.setJavaScriptCanOpenWindowsAutomatically(false);
    w.setSupportMultipleWindows(false);
    w.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
    w.setBuiltInZoomControls(false);
    w.setDisplayZoomControls(false);
    w.setSafeBrowsingEnabled(true);
    browser.setOverScrollMode(View.OVER_SCROLL_NEVER);
    browser.setWebChromeClient(new WebChromeClient() {
      @Override public void onProgressChanged(WebView view, int n) {
        progress.setProgress(n);
        progress.setVisibility(n < 100 ? View.VISIBLE : View.GONE);
      }
    });
    browser.setWebViewClient(new WebViewClient() {
      @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) {
        if (!request.isForMainFrame()) return false;
        if (allowed(request.getUrl())) return false;
        Toast.makeText(MainActivity.this, "Доступ разрешён только к сайту Degania", Toast.LENGTH_SHORT).show();
        return true;
      }
      @Override public void onPageStarted(WebView v, String url, Bitmap favicon) {
        if (!allowed(Uri.parse(url))) {
          v.stopLoading();
          handler.post(() -> v.loadUrl(HOME));
        }
      }
      @Override public void onPageFinished(WebView v, String url) {
        if (allowed(Uri.parse(url))) progress.setVisibility(View.GONE);
      }
      @Override public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError e) {
        if (req.isForMainFrame()) {
          progress.setVisibility(View.GONE);
          errorView.setVisibility(View.VISIBLE);
        }
      }
      @Override public void onReceivedHttpError(WebView v, WebResourceRequest req, WebResourceResponse e) {
        if (req.isForMainFrame() && e.getStatusCode() >= 500) errorView.setVisibility(View.VISIBLE);
      }
    });
    browser.setDownloadListener((url, agent, disposition, mime, size) ->
      Toast.makeText(this,"Файлы PDF и загрузки пока не поддерживаются",Toast.LENGTH_LONG).show());
    browser.loadUrl(HOME);
  }

  private int dp(int units) {return Math.round(units*getResources().getDisplayMetrics().density);}

  private boolean allowed(Uri url) {
    if(url==null) return false;
    String host = url.getHost();
    return "https".equalsIgnoreCase(url.getScheme())
      && host != null
      && (host.equalsIgnoreCase("degania-sprayers.com") || host.equalsIgnoreCase("www.degania-sprayers.com"))
      && url.getPort() == -1;
  }

  private void hideSystemBars() {
    getWindow().getDecorView().setSystemUiVisibility(
      View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
      View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
      View.SYSTEM_UI_FLAG_FULLSCREEN |
      View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
      View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
      View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
  }

  @Override public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    if(hasFocus && !admin)hideSystemBars();
  }
  @Override public void onResume() {
    super.onResume();
    if(!admin && settings.contains("pin_hash")) handler.postDelayed(this::enableOwnerLockIfAvailable,300);
  }
  private void enableOwnerLockIfAvailable() {
    if(admin || !dpm.isDeviceOwnerApp(getPackageName())) return;
    try {
      dpm.setLockTaskPackages(receiver,new String[]{getPackageName()});
      if(Build.VERSION.SDK_INT>=28) dpm.setLockTaskFeatures(receiver,DevicePolicyManager.LOCK_TASK_FEATURE_NONE);
      if(dpm.isLockTaskPermitted(getPackageName())) startLockTask();
    } catch(Exception ignored) {}
  }

  @Override public boolean dispatchTouchEvent(android.view.MotionEvent e) {
    if(e.getActionMasked() == MotionEvent.ACTION_DOWN) {
      downX=e.getX();downY=e.getY();
    }
    if(e.getActionMasked() == MotionEvent.ACTION_UP && !dialogOpen && !admin && settings.contains("pin_hash")) {
      float dx=e.getX()-downX,dy=e.getY()-downY;
      if(dx*dx+dy*dy < dp(28)*dp(28)) {
        long time = SystemClock.uptimeMillis();
        if(time - previousTap <= 480 && time - firstTap <= 2500) tapCount++;
        else {tapCount=1;firstTap=time;}
        previousTap=time;
        if(tapCount>=5) {
          tapCount=0;
          requestPin();
          return true;
        }
      } else tapCount=0;
    }
    return super.dispatchTouchEvent(e);
  }

  private EditText pinEntry() {
    EditText e = new EditText(this);
    e.setSingleLine(true);
    e.setHint("PIN: 4–8 цифр");
    e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);
    return e;
  }

  private void setFirstPin() {
    if(dialogOpen) return;
    dialogOpen=true;
    EditText e=pinEntry();
    AlertDialog dlg=new AlertDialog.Builder(this).setTitle("Degania Kiosk — установить PIN")
      .setMessage("Создай PIN для входа в режим администратора.")
      .setView(e).setPositiveButton("Далее",null).setCancelable(false).create();
    dlg.setOnShowListener(x->dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
      String pin=e.getText().toString();
      if(!pin.matches("[0-9]{4,8}")){e.setError("Введите 4–8 цифр");return;}
      dlg.dismiss();dialogOpen=false;
      confirmPin(pin);
    }));
    dlg.show();
  }

  private void confirmPin(String pin) {
    dialogOpen=true;
    EditText e=pinEntry();
    AlertDialog dlg=new AlertDialog.Builder(this).setTitle("Повтори PIN")
      .setView(e).setPositiveButton("Сохранить",null).setCancelable(false).create();
    dlg.setOnShowListener(x->dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
      if(!pin.equals(e.getText().toString())) {e.setError("PIN не совпадает");return;}
      try {
        byte[] salt=new byte[16];new SecureRandom().nextBytes(salt);
        String encodedSalt=Base64.encodeToString(salt,Base64.NO_WRAP);
        String hash=Base64.encodeToString(hashPin(pin,salt),Base64.NO_WRAP);
        if(!settings.edit().putString("pin_salt",encodedSalt).putString("pin_hash",hash).commit())
          throw new IllegalStateException("Save failed");
        dlg.dismiss();dialogOpen=false;admin=false;hideSystemBars();
        enableOwnerLockIfAvailable();
      } catch(Exception ex) {e.setError("Ошибка сохранения PIN");}
    }));
    dlg.show();
  }

  private byte[] hashPin(String pin,byte[] salt) throws Exception {
    PBEKeySpec spec=new PBEKeySpec(pin.toCharArray(),salt,120000,256);
    try {return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();}
    finally {spec.clearPassword();}
  }

  private boolean matchesPin(String pin) {
    if(SystemClock.elapsedRealtime() < blockedUntil) return false;
    try {
      byte[] salt=Base64.decode(settings.getString("pin_salt",""),Base64.NO_WRAP);
      byte[] expected=Base64.decode(settings.getString("pin_hash",""),Base64.NO_WRAP);
      if(MessageDigest.isEqual(hashPin(pin,salt),expected)) {badPins=0;return true;}
    }catch(Exception ignored){}
    if(++badPins>=5){badPins=0;blockedUntil=SystemClock.elapsedRealtime()+30000;}
    return false;
  }

  private void requestPin() {
    dialogOpen=true;
    EditText e=pinEntry();
    AlertDialog dlg=new AlertDialog.Builder(this).setTitle("Degania Kiosk — администратор")
      .setView(e).setNegativeButton("Отмена",(d,which)->dialogOpen=false)
      .setPositiveButton("Открыть",null).create();
    dlg.setOnCancelListener(d->dialogOpen=false);
    dlg.setOnShowListener(x->dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
      if(SystemClock.elapsedRealtime() < blockedUntil) {e.setError("Подожди 30 секунд");return;}
      if(!matchesPin(e.getText().toString())) {e.setText("");e.setError("Неверный PIN");return;}
      dlg.dismiss();dialogOpen=false;admin=true;
      try {stopLockTask();}catch(Exception ignored){}
      adminMenu();
    }));
    dlg.show();
  }

  private void adminMenu() {
    new AlertDialog.Builder(this).setTitle("Degania Kiosk — меню")
      .setItems(new String[]{"Вернуться на сайт","На главную","Поменять PIN","Выйти из приложения"},(dialog,index)->{
        if(index==3) {finishAndRemoveTask();return;}
        if(index==2) {admin=false;setFirstPin();return;}
        if(index==1) browser.loadUrl(HOME);
        admin=false;hideSystemBars();enableOwnerLockIfAvailable();
      }).setCancelable(false).show();
  }

  @Override public void onBackPressed() {
    if(admin) {super.onBackPressed();return;}
    if(browser.canGoBack()) browser.goBack();
    else browser.loadUrl(HOME);
  }
}
