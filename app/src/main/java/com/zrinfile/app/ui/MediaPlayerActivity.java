package com.zrinfile.app.ui;

import android.graphics.Color;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.VideoView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.zrinfile.app.R;
import com.zrinfile.app.fs.FileItem;
import com.zrinfile.app.fs.FileUtils;
import com.zrinfile.app.util.UiUtils;

import java.io.File;

public class MediaPlayerActivity extends AppCompatActivity {

    private Toolbar toolbar;
    private VideoView video;
    private LinearLayout audioPanel;
    private TextView audioLabel;
    private ProgressBar progress;
    private SeekBar seek;
    private ImageButton playButton;
    private TextView posText;
    private TextView durText;
    private File file;
    private boolean audio;
    private boolean prepared;
    private boolean userSeeking;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_media);
        toolbar = findViewById(R.id.toolbar);
        video = findViewById(R.id.video_view);
        audioPanel = findViewById(R.id.audio_panel);
        audioLabel = findViewById(R.id.audio_label);
        progress = findViewById(R.id.media_progress);
        seek = findViewById(R.id.media_seek);
        playButton = findViewById(R.id.media_play);
        posText = findViewById(R.id.media_pos);
        durText = findViewById(R.id.media_dur);
        toolbar.setTitleTextColor(Color.WHITE);
        toolbar.setSubtitleTextColor(0xFF8B9098);
        toolbar.setNavigationIcon(UiUtils.tint(this, R.drawable.ic_arrow_back, 0xFFE6E7EA));
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        String path = getIntent().getStringExtra("path");
        if (path == null) {
            finish();
            return;
        }
        file = new File(path);
        audio = FileItem.fromFile(file).type() == FileItem.TYPE_AUDIO;
        toolbar.setTitle(file.getName());
        toolbar.setSubtitle(FileUtils.formatSize(file.length()) + (audio ? " · 音频" : " · 视频"));
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        audioLabel.setText(file.getName());
        if (audio) {
            audioPanel.setVisibility(View.VISIBLE);
        }
        playButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                togglePlay();
            }
        });
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int value, boolean fromUser) {
                if (fromUser && prepared) {
                    posText.setText(FileUtils.formatTimeShort(value));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                userSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                userSeeking = false;
                if (prepared) video.seekTo(seekBar.getProgress());
            }
        });
        video.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                prepared = true;
                progress.setVisibility(View.GONE);
                seek.setMax(mp.getDuration());
                durText.setText(FileUtils.formatTimeShort(mp.getDuration()));
                mp.setOnInfoListener(null);
                video.start();
                playButton.setImageDrawable(UiUtils.tint(MediaPlayerActivity.this, R.drawable.ic_pause, 0xFFE6E7EA));
                tick();
            }
        });
        video.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                playButton.setImageDrawable(UiUtils.tint(MediaPlayerActivity.this, R.drawable.ic_play, 0xFFE6E7EA));
                seek.setProgress(0);
                posText.setText("00:00");
            }
        });
        video.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                progress.setVisibility(View.GONE);
                UiUtils.toast(MediaPlayerActivity.this, "无法播放此文件，可尝试用其他应用打开");
                return true;
            }
        });
        video.setVideoPath(file.getAbsolutePath());
    }

    private void tick() {
        if (!prepared) return;
        if (!userSeeking) {
            try {
                int position = video.getCurrentPosition();
                seek.setProgress(position);
                posText.setText(FileUtils.formatTimeShort(position));
            } catch (Exception ignored) {
            }
        }
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                tick();
            }
        }, 420);
    }

    private void togglePlay() {
        if (!prepared) return;
        if (video.isPlaying()) {
            video.pause();
            playButton.setImageDrawable(UiUtils.tint(this, R.drawable.ic_play, 0xFFE6E7EA));
        } else {
            video.start();
            playButton.setImageDrawable(UiUtils.tint(this, R.drawable.ic_pause, 0xFFE6E7EA));
            tick();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            if (video.isPlaying()) video.pause();
            playButton.setImageDrawable(UiUtils.tint(this, R.drawable.ic_play, 0xFFE6E7EA));
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        try {
            video.stopPlayback();
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }
}
