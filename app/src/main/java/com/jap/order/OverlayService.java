package com.jap.order;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

public class OverlayService extends Service {
    public static boolean running = false;
    WindowManager wm;
    View btn;
    Handler main = new Handler(Looper.getMainLooper());
    String lastOrdered = "";

    @Override
    public IBinder onBind(Intent i) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        startForegroundNotif();

        wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        Button b = new Button(this);
        b.setText("주문");
        b.setTextColor(Color.WHITE);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#E87A2A"));
        bg.setCornerRadius(dp(40));
        b.setBackground(bg);
        btn = b;

        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        final WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                dp(72), dp(72), type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = dp(16);
        lp.y = dp(300);

        b.setOnTouchListener(new View.OnTouchListener() {
            int ix, iy;
            float tx, ty;
            boolean moved;
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        ix = lp.x; iy = lp.y; tx = e.getRawX(); ty = e.getRawY(); moved = false;
                        return false;
                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) (e.getRawX() - tx);
                        int dy = (int) (e.getRawY() - ty);
                        if (Math.abs(dx) > 12 || Math.abs(dy) > 12) moved = true;
                        lp.x = ix + dx; lp.y = iy + dy;
                        wm.updateViewLayout(btn, lp);
                        return false;
                    case MotionEvent.ACTION_UP:
                        if (!moved) onTap();
                        return true;
                }
                return false;
            }
        });

        wm.addView(btn, lp);
    }

    void onTap() {
        ClipboardManager cb = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        String text = null;
        try {
            if (cb != null && cb.hasPrimaryClip() && cb.getPrimaryClip().getItemCount() > 0) {
                CharSequence cs = cb.getPrimaryClip().getItemAt(0).getText();
                if (cs != null) text = cs.toString();
            }
        } catch (Exception ex) { /* ignore */ }

        String u = Jap.igUrl(text);
        if (u == null) {
            toast("먼저 인스타 게시물 '링크 복사' 후 버튼을 눌러주세요");
            return;
        }
        if (u.equals(lastOrdered)) {
            toast("방금 주문한 게시물이에요");
            return;
        }
        toast("주문 중...");
        final String url = u;
        new Thread(() -> {
            String r = Jap.order(this, url);
            if (r.startsWith("주문 완료")) lastOrdered = url;
            main.post(() -> toast(r));
        }).start();
    }

    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    void startForegroundNotif() {
        String ch = "jap_overlay";
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            NotificationChannel c = new NotificationChannel(ch, "JAP 떠다니는 버튼", NotificationManager.IMPORTANCE_LOW);
            if (nm != null) nm.createNotificationChannel(c);
        }
        Notification.Builder nb = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, ch)
                : new Notification.Builder(this);
        nb.setContentTitle("JAP 주문기 실행 중")
                .setContentText("화면의 주문 버튼을 사용하세요")
                .setSmallIcon(android.R.drawable.ic_menu_send);
        startForeground(1, nb.build());
    }

    @Override
    public int onStartCommand(Intent i, int f, int id) { return START_STICKY; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        running = false;
        try { if (wm != null && btn != null) wm.removeView(btn); } catch (Exception e) { /* ignore */ }
    }
}
