package com.perrinho.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.DisplayMetrics;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.text.SimpleDateFormat;

/**
 * Launcher minimalista estilo Android TV para equipos con muy poca RAM.
 * Sin XML de layouts, sin AndroidX y con iconos cargados bajo demanda.
 */
public class MainActivity extends Activity {

    private static final class AppEntry {
        String label;
        ComponentName cn;
        String key;
    }

    private final Handler handler = new Handler();
    private final ArrayList<AppEntry> apps = new ArrayList<AppEntry>();
    private final LruCache<String, Drawable> icons = new LruCache<String, Drawable>(36);

    private PackageManager pm;
    private float density;
    private GridView grid;
    private AppAdapter adapter;
    private TextView clock;
    private TextView dateView;
    private SimpleDateFormat dayFormat;
    private boolean swallowCenterUp;
    private volatile boolean alive = true;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateClock();
            long now = System.currentTimeMillis();
            handler.postDelayed(this, 60000 - (now % 60000) + 50);
        }
    };

    private final Runnable reloadRunnable = new Runnable() {
        @Override
        public void run() {
            reload();
        }
    };

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            handler.removeCallbacks(reloadRunnable);
            handler.postDelayed(reloadRunnable, 700);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pm = getPackageManager();
        DisplayMetrics dm = getResources().getDisplayMetrics();
        density = dm.density;
        dayFormat = new SimpleDateFormat("EEEE", Locale.getDefault());

        getWindow().setBackgroundDrawable(new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] { 0xFF1C2633, 0xFF0A0D12 }));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setClipChildren(false);
        root.setClipToPadding(false);

        root.addView(buildHeader(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        grid = new GridView(this);
        float widthDp = dm.widthPixels / density;
        int cols = Math.max(3, (int) ((widthDp - 96 + 20) / 190));
        grid.setNumColumns(cols);
        grid.setHorizontalSpacing(dp(20));
        grid.setVerticalSpacing(dp(20));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setPadding(dp(48), dp(16), dp(48), dp(40));
        grid.setClipToPadding(false);
        grid.setClipChildren(false);
        grid.setSelector(new ColorDrawable(Color.TRANSPARENT));
        grid.setCacheColorHint(Color.TRANSPARENT);
        grid.setVerticalScrollBarEnabled(false);
        grid.setVerticalFadingEdgeEnabled(false);
        grid.setOverScrollMode(View.OVER_SCROLL_NEVER);
        adapter = new AppAdapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                launch(position);
            }
        });
        grid.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                showMenu(position);
                return true;
            }
        });
        root.addView(grid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addDataScheme("package");
        registerReceiver(packageReceiver, filter);

        reload();
    }

    @Override
    protected void onStart() {
        super.onStart();
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    @Override
    protected void onStop() {
        handler.removeCallbacks(tick);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        alive = false;
        handler.removeCallbacksAndMessages(null);
        try {
            unregisterReceiver(packageReceiver);
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Al pulsar Inicio, volver arriba y enfocar la cuadrícula
        if (intent != null && Intent.ACTION_MAIN.equals(intent.getAction())
                && intent.hasCategory(Intent.CATEGORY_HOME)) {
            grid.setSelection(0);
            grid.requestFocus();
        }
    }

    @Override
    public void onBackPressed() {
        // Un launcher no debe cerrarse con Atrás
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        boolean center = k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER;
        if (grid != null && grid.hasFocus()) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) {
                // Tecla Menú, o mantener OK pulsado: opciones de la app
                if (k == KeyEvent.KEYCODE_MENU && e.getRepeatCount() == 0) {
                    showMenu(grid.getSelectedItemPosition());
                    return true;
                }
                if (center && e.getRepeatCount() > 0) {
                    if (e.getRepeatCount() == 1) {
                        swallowCenterUp = true;
                        showMenu(grid.getSelectedItemPosition());
                    }
                    return true;
                }
            }
        }
        if (center && e.getAction() == KeyEvent.ACTION_UP && swallowCenterUp) {
            swallowCenterUp = false;
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    // ---------------------------------------------------------------- UI

    private int dp(int v) {
        return (int) (v * density + 0.5f);
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(48), dp(28), dp(48), dp(8));
        header.setClipChildren(false);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);

        clock = new TextView(this);
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 44);
        clock.setTextColor(Color.WHITE);
        clock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        left.addView(clock);

        dateView = new TextView(this);
        dateView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        dateView.setTextColor(0xFF9AA5B1);
        left.addView(dateView);

        header.addView(left, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        header.addView(pill(getString(R.string.wifi), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSettings(Settings.ACTION_WIFI_SETTINGS);
            }
        }), pillParams());
        header.addView(pill(getString(R.string.settings), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSettings(Settings.ACTION_SETTINGS);
            }
        }), pillParams());
        return header;
    }

    private LinearLayout.LayoutParams pillParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(12);
        return lp;
    }

    private TextView pill(String text, View.OnClickListener l) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(20), dp(10), dp(20), dp(10));
        t.setFocusable(true);
        t.setClickable(true);
        t.setOnClickListener(l);

        GradientDrawable on = new GradientDrawable();
        on.setColor(Color.WHITE);
        on.setCornerRadius(dp(22));
        GradientDrawable off = new GradientDrawable();
        off.setColor(0xFF232B36);
        off.setCornerRadius(dp(22));
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[] { android.R.attr.state_focused }, on);
        bg.addState(new int[] { android.R.attr.state_pressed }, on);
        bg.addState(new int[] {}, off);
        t.setBackgroundDrawable(bg);

        t.setTextColor(new ColorStateList(
                new int[][] { { android.R.attr.state_focused }, { android.R.attr.state_pressed }, {} },
                new int[] { 0xFF111418, 0xFF111418, 0xFFDDE3EA }));
        return t;
    }

    private void updateClock() {
        Date d = new Date();
        clock.setText(DateFormat.getTimeFormat(this).format(d));
        String day = dayFormat.format(d);
        if (day.length() > 0) {
            day = day.substring(0, 1).toUpperCase(Locale.getDefault()) + day.substring(1);
        }
        dateView.setText(day + "  ·  " + DateFormat.getMediumDateFormat(this).format(d));
    }

    // ------------------------------------------------------------- Datos

    private void reload() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final ArrayList<AppEntry> list = queryApps();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!alive) {
                            return;
                        }
                        icons.evictAll();
                        apps.clear();
                        apps.addAll(list);
                        adapter.notifyDataSetChanged();
                        if (grid.getSelectedItemPosition() < 0 && !apps.isEmpty()) {
                            grid.requestFocus();
                        }
                    }
                });
            }
        }, "load-apps").start();
    }

    private ArrayList<AppEntry> queryApps() {
        HashSet<String> seen = new HashSet<String>();
        ArrayList<AppEntry> out = new ArrayList<AppEntry>();
        String[] categories = {
                Intent.CATEGORY_LAUNCHER,
                "android.intent.category.LEANBACK_LAUNCHER"
        };
        for (String category : categories) {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(category);
            List<ResolveInfo> found = pm.queryIntentActivities(i, 0);
            if (found == null) {
                continue;
            }
            for (ResolveInfo ri : found) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(getPackageName()) || !seen.add(pkg)) {
                    continue;
                }
                AppEntry e = new AppEntry();
                CharSequence label = ri.loadLabel(pm);
                e.label = label != null ? label.toString() : pkg;
                e.cn = new ComponentName(pkg, ri.activityInfo.name);
                e.key = e.cn.flattenToString();
                out.add(e);
            }
        }
        Collections.sort(out, new Comparator<AppEntry>() {
            @Override
            public int compare(AppEntry a, AppEntry b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });
        return out;
    }

    private Drawable iconFor(AppEntry e) {
        Drawable d = icons.get(e.key);
        if (d == null) {
            try {
                d = pm.getActivityIcon(e.cn);
            } catch (Exception ex) {
                d = pm.getDefaultActivityIcon();
            }
            icons.put(e.key, d);
        }
        return d;
    }

    // ----------------------------------------------------------- Acciones

    private void launch(int position) {
        if (position < 0 || position >= apps.size()) {
            return;
        }
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.setComponent(apps.get(position).cn);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        startSafely(i);
    }

    private void openSettings(String action) {
        Intent i = new Intent(action);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startSafely(i);
    }

    private void startSafely(Intent i) {
        try {
            startActivity(i);
        } catch (Exception ex) {
            Toast.makeText(this, R.string.cant_open, Toast.LENGTH_SHORT).show();
        }
    }

    private void showMenu(final int position) {
        if (position < 0 || position >= apps.size()) {
            return;
        }
        final AppEntry e = apps.get(position);
        CharSequence[] items = {
                getString(R.string.open),
                getString(R.string.app_info),
                getString(R.string.uninstall)
        };
        new AlertDialog.Builder(this)
                .setTitle(e.label)
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String pkg = e.cn.getPackageName();
                        if (which == 0) {
                            launch(position);
                        } else if (which == 1) {
                            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:" + pkg));
                            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startSafely(i);
                        } else {
                            Intent i = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg));
                            startSafely(i);
                        }
                    }
                })
                .show();
    }

    // ----------------------------------------------------------- Adaptador

    private final class AppAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return apps.size();
        }

        @Override
        public Object getItem(int position) {
            return apps.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Tile t = (Tile) convertView;
            if (t == null) {
                t = new Tile(MainActivity.this, density);
            } else {
                t.reset();
            }
            AppEntry e = apps.get(position);
            t.label.setText(e.label);
            t.icon.setImageDrawable(iconFor(e));
            return t;
        }
    }

    /** Tarjeta de una app: icono sobre fondo redondeado y nombre debajo. */
    private static final class Tile extends LinearLayout {
        final ImageView icon;
        final TextView label;

        Tile(Context c, float d) {
            super(c);
            setOrientation(VERTICAL);
            setLayoutParams(new AbsListView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, (int) (132 * d)));

            FrameLayout card = new FrameLayout(c);
            card.setBackgroundDrawable(cardBackground(d));
            addView(card, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, (int) (92 * d)));

            icon = new ImageView(c);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            card.addView(icon, new FrameLayout.LayoutParams(
                    (int) (56 * d), (int) (56 * d), Gravity.CENTER));

            label = new TextView(c);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setGravity(Gravity.CENTER);
            label.setTextColor(new ColorStateList(
                    new int[][] { { android.R.attr.state_selected }, {} },
                    new int[] { Color.WHITE, 0xFF9AA5B1 }));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = (int) (8 * d);
            addView(label, lp);
        }

        private static Drawable cardBackground(float d) {
            GradientDrawable sel = new GradientDrawable();
            sel.setColor(0xFF3B4C63);
            sel.setCornerRadius(8 * d);
            sel.setStroke((int) Math.max(2, 2 * d), Color.WHITE);
            GradientDrawable normal = new GradientDrawable();
            normal.setColor(0xFF232B36);
            normal.setCornerRadius(8 * d);
            StateListDrawable s = new StateListDrawable();
            s.addState(new int[] { android.R.attr.state_selected }, sel);
            s.addState(new int[] {}, normal);
            return s;
        }

        @Override
        public void setSelected(boolean selected) {
            super.setSelected(selected);
            float scale = selected ? 1.08f : 1f;
            animate().cancel();
            animate().scaleX(scale).scaleY(scale).setDuration(110).start();
        }

        /** Se llama al reciclar la vista: sin animación y sin selección. */
        void reset() {
            animate().cancel();
            setScaleX(1f);
            setScaleY(1f);
            super.setSelected(false);
        }
    }
}
