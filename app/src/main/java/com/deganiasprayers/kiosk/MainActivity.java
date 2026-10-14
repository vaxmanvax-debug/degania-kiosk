package com.deganiasprayers.kiosk;

import android.app.*;
import android.app.admin.DevicePolicyManager;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import java.security.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import android.util.Base64;

public final class MainActivity extends Activity {
 private static final String HOME="https://deganiasprayers.com/";
 private WebView web;
 private boolean dialogOpen=false, admin=false;
 private final long[] taps=new long[5];
 private int count=0, failures=0;
 private long waitUntil=0;
 private android.content.SharedPreferences prefs;
 private DevicePolicyManager policy;
 private android.content.ComponentName receiver;
 @Override public void onCreate(Bundle b) {
  super.onCreate(b);
  prefs=getSharedPreferences("pin",MODE_PRIVATE);
  policy=(DevicePolicyManager)getSystemService(DEVICE_POLICY_SERVICE);
  receiver=new android.content.ComponentName(this,KioskAdminReceiver.class);
  getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  hideBars();
  web=new WebView(this);
  setContentView(web);
  WebSettings s=web.getSettings();
  s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true);
  s.setAllowFileAccess(false); s.setAllowContentAccess(false);
  s.setSupportMultipleWindows(false); s.setJavaScriptCanOpenWindowsAutomatically(false);
  s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
  web.setWebChromeClient(new WebChromeClient());
  web.setWebViewClient(new WebViewClient() {
   @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r) {
    if(!r.isForMainFrame()) return false;
    if(allowed(r.getUrl())) return false;
    Toast.makeText(MainActivity.this,"Разрешён только сайт Degania",Toast.LENGTH_SHORT).show();
    return true;
   }
   @Override public void onPageStarted(WebView v,String url,android.graphics.Bitmap icon) {
    if(!allowed(Uri.parse(url))) { v.stopLoading();v.loadUrl(HOME); }
   }
  });
  web.setDownloadListener((url,ua,cd,mime,len)->Toast.makeText(this,"Загрузка отключена",Toast.LENGTH_SHORT).show());
  web.loadUrl(HOME);
  if(!prefs.contains("hash")) new Handler(Looper.getMainLooper()).post(this::createPin);
 }
 private boolean allowed(Uri u) {
  String h=u.getHost();
  return "https".equalsIgnoreCase(u.getScheme()) && h!=null && (h.equalsIgnoreCase("deganiasprayers.com")||h.equalsIgnoreCase("www.deganiasprayers.com")) && (u.getPort()==-1);
 }
 private void hideBars(){getWindow().getDecorView().setSystemUiVisibility(5894|1024|512);}
 @Override public void onWindowFocusChanged(boolean f){super.onWindowFocusChanged(f);if(f&&!admin)hideBars();}
 @Override public void onResume(){super.onResume();if(!admin&&prefs.contains("hash"))new Handler(Looper.getMainLooper()).postDelayed(this::startStrictLock,300);}
 private void startStrictLock(){
  if(admin||!policy.isDeviceOwnerApp(getPackageName()))return;
  try{policy.setLockTaskPackages(receiver,new String[]{getPackageName()});policy.setLockTaskFeatures(receiver,DevicePolicyManager.LOCK_TASK_FEATURE_NONE);if(policy.isLockTaskPermitted(getPackageName()))startLockTask();}catch(Exception ignored){}
 }
 @Override public boolean dispatchTouchEvent(MotionEvent e){
  if(e.getActionMasked()==MotionEvent.ACTION_UP && e.getPointerCount()==1 && !dialogOpen && prefs.contains("hash")){
   long n=SystemClock.uptimeMillis();
   if(count>0&&n-taps[count-1]>430)count=0;
   taps[count++]=n;
   if(count>=5){count=0;if(n-taps[0]<1750){enterPin();return true;}}
  }
  return super.dispatchTouchEvent(e);
 }
 private EditText edit(){
  EditText x=new EditText(this);x.setSingleLine(true);x.setHint("4–8 цифр");
  x.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);return x;
 }
 private void createPin(){
  if(dialogOpen)return;
  dialogOpen=true;
  EditText a=edit();
  AlertDialog d=new AlertDialog.Builder(this).setTitle("Degania Kiosk — новый PIN").setView(a).setCancelable(false).setPositiveButton("Далее",null).create();
  d.setOnShowListener(z->d.getButton(-1).setOnClickListener(v->{
   String v1=a.getText().toString();if(!v1.matches("[0-9]{4,8}")){a.setError("4–8 цифр");return;}
   d.dismiss();dialogOpen=false;confirm(v1);
  }));d.show();
 }
 private void confirm(String first){
  dialogOpen=true;EditText b=edit();
  AlertDialog d=new AlertDialog.Builder(this).setTitle("Повторить PIN").setView(b).setCancelable(false).setPositiveButton("Сохранить",null).create();
  d.setOnShowListener(z->d.getButton(-1).setOnClickListener(v->{
   if(!first.equals(b.getText().toString())){b.setError("Не совпадает");return;}
   try{
    byte[] salt=new byte[16];new SecureRandom().nextBytes(salt);
    String hash=Base64.encodeToString(derive(first,salt),Base64.NO_WRAP);
    boolean ok=prefs.edit().putString("salt",Base64.encodeToString(salt,Base64.NO_WRAP)).putString("hash",hash).commit();
    if(!ok)throw new Exception("Storage");
    d.dismiss();dialogOpen=false;admin=false;startStrictLock();
   }catch(Exception ex){b.setError("Ошибка сохранения");}
  }));d.show();
 }
 private byte[] derive(String pin,byte[] salt)throws Exception{
  PBEKeySpec spec=new PBEKeySpec(pin.toCharArray(),salt,150000,256);
  try{return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();}
  finally{spec.clearPassword();}
 }
 private boolean check(String input){
  if(SystemClock.elapsedRealtime()<waitUntil)return false;
  try{
   byte[] salt=Base64.decode(prefs.getString("salt",""),Base64.NO_WRAP);
   byte[] actual=derive(input,salt);
   byte[] expected=Base64.decode(prefs.getString("hash",""),Base64.NO_WRAP);
   if(MessageDigest.isEqual(expected,actual)){failures=0;return true;}
  }catch(Exception ignored){}
  failures++;if(failures>=5){failures=0;waitUntil=SystemClock.elapsedRealtime()+30000;}
  return false;
 }
 private void enterPin(){
  dialogOpen=true;EditText b=edit();
  AlertDialog d=new AlertDialog.Builder(this).setTitle("Режим администратора").setView(b).setNegativeButton("Отмена",(x,w)->dialogOpen=false).setPositiveButton("Войти",null).create();
  d.setOnShowListener(z->d.getButton(-1).setOnClickListener(v->{
   if(SystemClock.elapsedRealtime()<waitUntil){b.setError("Повтори через 30 секунд");return;}
   if(!check(b.getText().toString())){b.getText().clear();b.setError("Неверный PIN");return;}
   d.dismiss();dialogOpen=false;admin=true;try{stopLockTask();}catch(Exception ignored){}menu();
  }));d.show();
 }
 private void menu(){
  new AlertDialog.Builder(this).setTitle("Degania Kiosk — администратор")
    .setItems(new String[]{"Вернуться на сайт","Сменить PIN","Выйти из приложения"},(dlg,which)->{
     if(which==0){admin=false;hideBars();startStrictLock();}
     else if(which==1)createPin();
     else {finish();}
    }).setCancelable(false).show();
 }
 @Override public void onBackPressed(){if(web.canGoBack())web.goBack();else web.loadUrl(HOME);}
}
