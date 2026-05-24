package com.controlmoblie.ui.main;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.controlmoblie.databinding.ActivityMainBinding;
import com.controlmoblie.databinding.ItemPermissionBinding;
import com.controlmoblie.ocr.ScreenCaptureManager;
import com.controlmoblie.ocr.ScreenOcr;
import com.controlmoblie.service.VoiceControlService;
import com.controlmoblie.ui.overlay.OverlayService;
import com.controlmoblie.util.PermissionHelper;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private MainViewModel viewModel;
    private ActivityResultLauncher<Intent> mediaProjectionLauncher;
    private ActivityResultLauncher<String> recordAudioLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this, new MainViewModelFactory(getApplication()))
                .get(MainViewModel.class);

        mediaProjectionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        MediaProjectionManager mgr = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                        if (mgr != null) {
                            android.media.projection.MediaProjection projection = mgr.getMediaProjection(result.getResultCode(), result.getData());
                            android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
                            ScreenCaptureManager.init(projection, metrics.widthPixels, metrics.heightPixels, metrics.densityDpi);
                            ScreenOcr.init(MainActivity.this);
                            viewModel.setOcrReady(true);
                        }
                    }
                });

        recordAudioLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> viewModel.refreshPermissions(MainActivity.this));

        setupPermissionItems();
        setupObservers();
        setupButtons();

        viewModel.refreshPermissions(this);
        viewModel.refreshModelStatus(this);
    }

    private void setupPermissionItems() {
        ItemPermissionBinding overlayBinding = binding.includeOverlay;
        overlayBinding.tvTitle.setText("悬浮窗权限");
        overlayBinding.tvDesc.setText("需要悬浮窗权限以显示控制状态");
        overlayBinding.btnAction.setText("去开启");
        overlayBinding.btnAction.setOnClickListener(v -> {
            startActivity(PermissionHelper.createOverlaySettingsIntent(this));
        });

        ItemPermissionBinding audioBinding = binding.includeAudio;
        audioBinding.tvTitle.setText("录音权限");
        audioBinding.tvDesc.setText("需要录音权限以识别语音指令");
        audioBinding.btnAction.setText("去开启");
        audioBinding.btnAction.setOnClickListener(v -> {
            recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO);
        });

        ItemPermissionBinding accessibilityBinding = binding.includeAccessibility;
        accessibilityBinding.tvTitle.setText("无障碍服务");
        accessibilityBinding.tvDesc.setText("需要无障碍服务以执行屏幕操作");
        accessibilityBinding.btnAction.setText("去开启");
        accessibilityBinding.btnAction.setOnClickListener(v -> {
            startActivity(PermissionHelper.createAccessibilitySettingsIntent());
        });
    }

    private void setupObservers() {
        viewModel.getHasOverlay().observe(this, hasOverlay -> {
            binding.includeOverlay.btnAction.setVisibility(hasOverlay ? View.GONE : View.VISIBLE);
        });

        viewModel.getHasAudio().observe(this, hasAudio -> {
            binding.includeAudio.btnAction.setVisibility(hasAudio ? View.GONE : View.VISIBLE);
        });

        viewModel.getHasAccessibility().observe(this, hasAccessibility -> {
            binding.includeAccessibility.btnAction.setVisibility(hasAccessibility ? View.GONE : View.VISIBLE);
        });

        viewModel.getAsrModelReady().observe(this, ready -> {
            binding.btnDownloadAsr.setVisibility(ready ? View.GONE : View.VISIBLE);
            binding.tvAsrReady.setVisibility(ready ? View.VISIBLE : View.GONE);
        });

        viewModel.getTtsModelReady().observe(this, ready -> {
            binding.btnDownloadTts.setVisibility(ready ? View.GONE : View.VISIBLE);
            binding.tvTtsReady.setVisibility(ready ? View.VISIBLE : View.GONE);
        });

        viewModel.getOcrReady().observe(this, ready -> {
            binding.btnInitOcr.setVisibility(ready ? View.GONE : View.VISIBLE);
            binding.tvOcrReady.setVisibility(ready ? View.VISIBLE : View.GONE);
        });

        viewModel.getAsrProgress().observe(this, progress -> {
            boolean inProgress = progress != null && progress.isInProgress();
            binding.pbAsrProgress.setVisibility(inProgress ? View.VISIBLE : View.GONE);
            binding.tvAsrProgress.setVisibility(inProgress ? View.VISIBLE : View.GONE);
            if (inProgress) {
                binding.pbAsrProgress.setProgress((int) (progress.getProgress() * 100));
                binding.tvAsrProgress.setText(String.format("下载中 %.0f%%", progress.getProgress() * 100));
            }
        });

        viewModel.getTtsProgress().observe(this, progress -> {
            boolean inProgress = progress != null && progress.isInProgress();
            binding.pbTtsProgress.setVisibility(inProgress ? View.VISIBLE : View.GONE);
            binding.tvTtsProgress.setVisibility(inProgress ? View.VISIBLE : View.GONE);
            if (inProgress) {
                binding.pbTtsProgress.setProgress((int) (progress.getProgress() * 100));
                binding.tvTtsProgress.setText(String.format("下载中 %.0f%%", progress.getProgress() * 100));
            }
        });

        viewModel.getVoiceState().observe(this, vs -> {
            // UI updates for voice state if needed
        });

        viewModel.getRecognizedText().observe(this, text -> {
            // UI updates for recognized text if needed
        });

        viewModel.getLastResult().observe(this, result -> {
            // UI updates for last result if needed
        });
    }

    private void setupButtons() {
        binding.btnDownloadAsr.setOnClickListener(v -> viewModel.downloadAsrModel(this));
        binding.btnDownloadTts.setOnClickListener(v -> viewModel.downloadTtsModel(this));

        binding.btnInitOcr.setOnClickListener(v -> {
            startService(new Intent(this, VoiceControlService.class));
            MediaProjectionManager mgr = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            if (mgr != null) {
                mediaProjectionLauncher.launch(mgr.createScreenCaptureIntent());
            }
        });

        binding.btnStartVoiceControl.setOnClickListener(v -> {
            startService(new Intent(this, VoiceControlService.class));
            startService(new Intent(this, OverlayService.class));
            viewModel.bindService(this);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        viewModel.refreshPermissions(this);
        viewModel.refreshModelStatus(this);
    }

    @Override
    protected void onDestroy() {
        if (viewModel != null) {
            viewModel.unbindService(this);
        }
        binding = null;
        super.onDestroy();
    }
}
