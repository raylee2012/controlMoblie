package com.controlmoblie.ui.overlay;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;

import com.controlmoblie.databinding.OverlayControlBinding;
import com.controlmoblie.service.VoiceControlService;
import com.controlmoblie.service.binder.VoiceControlBinder;

public class OverlayService extends Service {

    private WindowManager windowManager;
    private View overlayView;
    private OverlayControlBinding binding;
    private OverlayPresenter presenter;
    private VoiceControlBinder binder;
    private boolean bound = false;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            binder = (VoiceControlBinder) service;
            presenter = new OverlayPresenter(binding.tvStatus);
            presenter.attach(OverlayService.this, binder);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            binder = null;
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        binding = OverlayControlBinding.inflate(LayoutInflater.from(this));
        overlayView = binding.getRoot();

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;

        if (windowManager != null) {
            windowManager.addView(overlayView, params);
        }

        Intent intent = new Intent(this, VoiceControlService.class);
        bindService(intent, connection, BIND_AUTO_CREATE);
        bound = true;
    }

    @Override
    public void onDestroy() {
        if (presenter != null) {
            presenter.detach();
            presenter = null;
        }
        if (bound) {
            unbindService(connection);
            bound = false;
        }
        if (overlayView != null && windowManager != null) {
            windowManager.removeView(overlayView);
            overlayView = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
