package com.perrinho.launcher;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.drawable.GradientDrawable;
import android.media.ExifInterface;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import java.io.File;
import java.util.Locale;

/** Reproductor sencillo para video, música y fotos con el control remoto. */
public class PlayerActivity extends Activity {
    static final String EXTRA_PATHS = "paths";
    static final String EXTRA_INDEX = "index";
    static final String EXTRA_KIND = "kind";

    private final Handler handler = new Handler();

    private String[] paths;
    private int index;
    private int kind;

    private VideoView video;
    private ImageView image;
    private LinearLayout audioBox;
    private TextView audioTitle;
    private LinearLayout overlay;
    private TextView title;
    private TextView timeView;
    private ProgressBar bar;

    private MediaPlayer audio;
    private boolean audioReady;
    private Bitmap bitmap;
    private int loadToken;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            handler.postDelayed(this, 500);
        }
    };

    private final Runnable hide = new Runnable() {
        @Override
        public void run() {
            if (kind == Storage.IMAGE || (kind == Storage.VIDEO && isPlaying())) {
                overlay.setVisibility(View.GONE);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.density = getResources().getDisplayMetrics().density;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        paths = getIntent().getStringArrayExtra(EXTRA_PATHS);
        index = getIntent().getIntExtra(EXTRA_INDEX, 0);
        kind = getIntent().getIntExtra(EXTRA_KIND, Storage.VIDEO);
        if (paths == null || paths.length == 0) {
            finish();
            return;
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        video = new VideoView(this);
        root.addView(video, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));

        image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(image, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        audioBox = new LinearLayout(this);
        audioBox.setOrientation(LinearLayout.VERTICAL);
        audioBox.setGravity(Gravity.CENTER);
        audioBox.setBackgroundDrawable(new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM, new int[] { 0xFF1C2633, 0xFF0A0D12 }));
        ImageView art = new ImageView(this);
        art.setImageDrawable(new GlyphDrawable(GlyphDrawable.MUSIC));
        audioBox.addView(art, new LinearLayout.LayoutParams(Ui.dp(160), Ui.dp(160)));
        audioTitle = Ui.text(this, "", 24, Color.WHITE);
        audioTitle.setGravity(Gravity.CENTER);
        audioTitle.setPadding(Ui.dp(48), Ui.dp(24), Ui.dp(48), 0);
        audioBox.addView(audioTitle);
        root.addView(audioBox, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setPadding(Ui.dp(48), Ui.dp(40), Ui.dp(48), Ui.dp(28));
        overlay.setBackgroundDrawable(new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM, new int[] { 0x00000000, 0xDD000000 }));
        title = Ui.text(this, "", 20, Color.WHITE);
        title.setSingleLine(true);
        overlay.addView(title);
        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(8));
        bp.topMargin = Ui.dp(12);
        overlay.addView(bar, bp);
        timeView = Ui.text(this, "", 14, Ui.MUTED);
        timeView.setPadding(0, Ui.dp(6), 0, 0);
        overlay.addView(timeView);
        TextView hint = Ui.text(this,
                getString(kind == Storage.IMAGE ? R.string.hint_image : R.string.hint_media),
                13, Ui.MUTED);
        hint.setPadding(0, Ui.dp(4), 0, 0);
        overlay.addView(hint);
        root.addView(overlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM));

        setContentView(root);
        play(index);
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(tick);
        pauseMedia();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        loadToken++;
        releaseCurrent();
        super.onDestroy();
    }

    // ---------------------------------------------------------- Reproducción

    private void play(int i) {
        index = (i + paths.length) % paths.length;
        releaseCurrent();
        String path = paths[index];
        String name = new File(path).getName();
        title.setText(name);
        audioTitle.setText(name);

        video.setVisibility(kind == Storage.VIDEO ? View.VISIBLE : View.GONE);
        image.setVisibility(kind == Storage.IMAGE ? View.VISIBLE : View.GONE);
        audioBox.setVisibility(kind == Storage.AUDIO ? View.VISIBLE : View.GONE);
        int mediaVis = kind == Storage.IMAGE ? View.GONE : View.VISIBLE;
        bar.setVisibility(mediaVis);
        timeView.setVisibility(mediaVis);

        if (kind == Storage.VIDEO) {
            video.setOnPreparedListener(mp -> video.start());
            video.setOnCompletionListener(mp -> advance());
            video.setOnErrorListener((mp, what, extra) -> {
                fail();
                return true;
            });
            video.setVideoPath(path);
        } else if (kind == Storage.AUDIO) {
            try {
                audio = new MediaPlayer();
                audio.setDataSource(path);
                audio.setOnPreparedListener(mp -> {
                    audioReady = true;
                    mp.start();
                });
                audio.setOnCompletionListener(mp -> advance());
                audio.setOnErrorListener((mp, what, extra) -> {
                    fail();
                    return true;
                });
                audio.prepareAsync();
            } catch (Exception e) {
                fail();
            }
        } else {
            loadImage(path);
        }
        showOverlay();
    }

    private void releaseCurrent() {
        if (video != null) {
            video.stopPlayback();
        }
        if (audio != null) {
            try {
                audio.release();
            } catch (Exception ignored) {
            }
            audio = null;
        }
        audioReady = false;
        if (image != null) {
            image.setImageDrawable(null);
        }
        if (bitmap != null) {
            bitmap.recycle();
            bitmap = null;
        }
    }

    private void advance() {
        if (index + 1 < paths.length) {
            play(index + 1);
        } else {
            finish();
        }
    }

    private void fail() {
        Toast.makeText(this, R.string.cant_play, Toast.LENGTH_LONG).show();
        finish();
    }

    private boolean isPlaying() {
        try {
            if (kind == Storage.VIDEO) {
                return video.isPlaying();
            }
            if (kind == Storage.AUDIO && audio != null && audioReady) {
                return audio.isPlaying();
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private int position() {
        try {
            if (kind == Storage.VIDEO) {
                return video.getCurrentPosition();
            }
            if (kind == Storage.AUDIO && audio != null && audioReady) {
                return audio.getCurrentPosition();
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    private int duration() {
        try {
            if (kind == Storage.VIDEO) {
                return video.getDuration();
            }
            if (kind == Storage.AUDIO && audio != null && audioReady) {
                return audio.getDuration();
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    private void pauseMedia() {
        try {
            if (kind == Storage.VIDEO) {
                video.pause();
            } else if (kind == Storage.AUDIO && audio != null && audioReady) {
                audio.pause();
            }
        } catch (Exception ignored) {
        }
    }

    private void resumeMedia() {
        try {
            if (kind == Storage.VIDEO) {
                video.start();
            } else if (kind == Storage.AUDIO && audio != null && audioReady) {
                audio.start();
            }
        } catch (Exception ignored) {
        }
    }

    private void seekBy(int ms) {
        int d = duration();
        if (d <= 0) {
            return;
        }
        int t = Math.max(0, Math.min(d - 500, position() + ms));
        try {
            if (kind == Storage.VIDEO) {
                video.seekTo(t);
            } else if (audio != null) {
                audio.seekTo(t);
            }
        } catch (Exception ignored) {
        }
    }

    private void updateProgress() {
        if (kind == Storage.IMAGE) {
            return;
        }
        int d = duration();
        int p = position();
        bar.setProgress(d > 0 ? (int) (1000L * p / d) : 0);
        String state = getString(isPlaying() ? R.string.playing : R.string.paused);
        timeView.setText(state + "   " + fmt(p) + " / " + fmt(d));
    }

    private static String fmt(int ms) {
        int s = Math.max(0, ms) / 1000;
        int h = s / 3600;
        int m = (s % 3600) / 60;
        s = s % 60;
        return h > 0
                ? String.format(Locale.US, "%d:%02d:%02d", h, m, s)
                : String.format(Locale.US, "%d:%02d", m, s);
    }

    private void showOverlay() {
        overlay.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hide);
        handler.postDelayed(hide, 4000);
    }

    // ------------------------------------------------------------- Imágenes

    private void loadImage(final String path) {
        final int token = ++loadToken;
        new Thread(() -> {
            final Bitmap b = decode(path);
            runOnUiThread(() -> {
                if (token != loadToken || isFinishing()) {
                    if (b != null) {
                        b.recycle();
                    }
                    return;
                }
                if (b == null) {
                    fail();
                    return;
                }
                bitmap = b;
                image.setImageBitmap(b);
            });
        }, "load-image").start();
    }

    private static Bitmap decode(String path) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, o);
            int max = Math.max(o.outWidth, o.outHeight);
            int sample = 1;
            while (max / sample > 1920) {
                sample *= 2;
            }
            o.inJustDecodeBounds = false;
            o.inSampleSize = sample;
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap b = BitmapFactory.decodeFile(path, o);
            if (b == null) {
                return null;
            }
            int degrees = 0;
            try {
                int ori = new ExifInterface(path).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                if (ori == ExifInterface.ORIENTATION_ROTATE_90) {
                    degrees = 90;
                } else if (ori == ExifInterface.ORIENTATION_ROTATE_180) {
                    degrees = 180;
                } else if (ori == ExifInterface.ORIENTATION_ROTATE_270) {
                    degrees = 270;
                }
            } catch (Exception ignored) {
            }
            if (degrees != 0) {
                Matrix m = new Matrix();
                m.postRotate(degrees);
                Bitmap r = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
                if (r != b) {
                    b.recycle();
                }
                b = r;
            }
            return b;
        } catch (OutOfMemoryError e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- Teclas

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_SPACE:
                if (kind != Storage.IMAGE && event.getRepeatCount() == 0) {
                    if (isPlaying()) {
                        pauseMedia();
                    } else {
                        resumeMedia();
                    }
                }
                updateProgress();
                showOverlay();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY:
                resumeMedia();
                showOverlay();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                pauseMedia();
                showOverlay();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (kind == Storage.IMAGE) {
                    play(index + 1);
                } else {
                    seekBy(10000);
                    showOverlay();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (kind == Storage.IMAGE) {
                    play(index - 1);
                } else {
                    seekBy(-10000);
                    showOverlay();
                }
                return true;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                seekBy(30000);
                showOverlay();
                return true;
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                seekBy(-30000);
                showOverlay();
                return true;
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                play(index + 1);
                return true;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                play(index - 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                showOverlay();
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }
}
