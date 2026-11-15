package com.zrinfile.app.ui;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.VideoView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.zrinfile.app.R;
import com.zrinfile.app.fs.FileItem;
import com.zrinfile.app.fs.FileUtils;
import com.zrinfile.app.media.MediaMeta;
import com.zrinfile.app.util.UiUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

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
    private ImageView coverView;
    private ScrollView lyricsScroll;
    private TextView lyricsView;
    private final List<MediaMeta.LyricLine> lyricLines = new ArrayList<>();
    private int currentLyric = -1;
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
        coverView = findViewById(R.id.audio_cover);
        lyricsScroll = findViewById(R.id.audio_lyrics_scroll);
        lyricsView = findViewById(R.id.audio_lyrics);
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
            loadExtras();
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

    private void loadExtras() {
        final File audioFile = file;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Bitmap cover = MediaMeta.loadCover(audioFile);
                final List<MediaMeta.LyricLine> lines = MediaMeta.loadLyrics(audioFile);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) {
                            if (cover != null) cover.recycle();
                            return;
                        }
                        if (cover != null) {
                            coverView.setImageBitmap(cover);
                            coverView.setVisibility(View.VISIBLE);
                            findViewById(R.id.audio_icon).setVisibility(View.GONE);
                        }
                        if (!lines.isEmpty()) {
                            lyricLines.clear();
                            lyricLines.addAll(lines);
                            lyricsScroll.setVisibility(View.VISIBLE);
                            applyLyricIndex(0);
                        }
                    }
                });
            }
        }, "zrinfile-media-meta").start();
    }

    private void updateLyrics(int position) {
        if (lyricLines.isEmpty()) return;
        int index = -1;
        for (int i = 0; i < lyricLines.size(); i++) {
            if (lyricLines.get(i).time <= position) index = i;
            else break;
        }
        if (index >= 0 && index != currentLyric) applyLyricIndex(index);
    }

    private void applyLyricIndex(int index) {
        if (index < 0 || index >= lyricLines.size()) return;
        currentLyric = index;
        SpannableStringBuilder sb = new SpannableStringBuilder();
        for (int i = 0; i < lyricLines.size(); i++) {
            int start = sb.length();
            sb.append(lyricLines.get(i).text);
            int end = sb.length();
            sb.setSpan(new ForegroundColorSpan(i == index ? 0xFF8FB4E8 : 0xFF7E838B),
                    start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.append("\n");
        }
        lyricsView.setText(sb);
        final int target = index;
        lyricsView.post(new Runnable() {
            @Override
            public void run() {
                int offset = lyricsView.getLineHeight() * target - lyricsScroll.getHeight() / 3;
                lyricsScroll.scrollTo(0, Math.max(0, offset));
            }
        });
    }

    private void tick() {
        if (!prepared) return;
        if (!userSeeking) {
            try {
                int position = video.getCurrentPosition();
                seek.setProgress(position);
                posText.setText(FileUtils.formatTimeShort(position));
                updateLyrics(position);
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
