package com.perrinho.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
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
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/**
 * Launcher ligero con estilo Google TV: barra de pestañas (Inicio, Aplicaciones, Multimedia),
 * filas horizontales con tarjetas redondeadas y una sección de multimedia con su propio menú.
 * Sin XML de layouts ni librerías externas, para equipos con muy poca RAM.
 */
public class MainActivity extends Activity {

    private static final class AppEntry {
        String label;
        ComponentName cn;
        String key;
    }

    private static final int PAGE_HOME = 0;
    private static final int PAGE_APPS = 1;
    private static final int PAGE_MEDIA = 2;

    private final Handler handler = new Handler();
    private final ArrayList<AppEntry> apps = new ArrayList<AppEntry>();
    private final LruCache<String, Drawable> icons = new LruCache<String, Drawable>(36);
    private final TextView[] tabs = new TextView[3];

    private PackageManager pm;
    private SharedPreferences prefs;
    private FrameLayout pages;
    private ScrollView homeScroll;
    private LinearLayout homeContent;
    private GridView grid;
    private AppAdapter adapter;
    private MediaPage media;
    private TextView clock;
    private TextView dateView;
    private SimpleDateFormat dayFormat;

    private int page = -1;
    private boolean homeDirty = true;
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

    private final Runnable reloadRunnable = this::reload;
    private final Runnable mediaRefresh = new Runnable() {
        @Override
        public void run() {
            if (page == PAGE_MEDIA) {
                media.refresh();
            }
        }
    };

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            handler.removeCallbacks(reloadRunnable);
            handler.postDelayed(reloadRunnable, 700);
        }
    };

    private final BroadcastReceiver storageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            handler.removeCallbacks(mediaRefresh);
            handler.postDelayed(mediaRefresh, 1500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pm = getPackageManager();
        prefs = getSharedPreferences("launcher", MODE_PRIVATE);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        Ui.density = dm.density;
        dayFormat = new SimpleDateFormat("EEEE", Locale.getDefault());

        getWindow().setBackgroundDrawable(new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] { 0xFF1C2633, 0xFF0A0D12 }));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setClipChildren(false);
        root.addView(buildHeader(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        pages = new FrameLayout(this);
        pages.setClipChildren(false);
        root.addView(pages, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        buildHomePage();
        buildAppsPage(dm);
        media = new MediaPage(this);
        pages.addView(media, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);

        IntentFilter pkg = new IntentFilter();
        pkg.addAction(Intent.ACTION_PACKAGE_ADDED);
        pkg.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pkg.addDataScheme("package");
        registerReceiver(packageReceiver, pkg);

        IntentFilter sto = new IntentFilter();
        sto.addAction(Intent.ACTION_MEDIA_MOUNTED);
        sto.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        sto.addAction(Intent.ACTION_MEDIA_REMOVED);
        sto.addAction(Intent.ACTION_MEDIA_EJECT);
        sto.addDataScheme("file");
        registerReceiver(storageReceiver, sto);

        showPage(PAGE_HOME);
        reload();
    }

    @Override
    protected void onStart() {
        super.onStart();
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (page == PAGE_HOME && homeDirty && !apps.isEmpty()) {
            buildHome();
        }
        if (page == PAGE_MEDIA) {
            media.refresh();
        }
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
            unregisterReceiver(storageReceiver);
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (page == PAGE_MEDIA) {
            media.refresh();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Al pulsar Inicio: volver a la pestaña Inicio, arriba del todo
        if (intent != null && Intent.ACTION_MAIN.equals(intent.getAction())
                && intent.hasCategory(Intent.CATEGORY_HOME)) {
            showPage(PAGE_HOME);
            homeScroll.scrollTo(0, 0);
            pages.requestFocus();
        }
    }

    @Override
    public void onBackPressed() {
        if (page == PAGE_MEDIA && media.back()) {
            return;
        }
        if (page != PAGE_HOME) {
            showPage(PAGE_HOME);
            pages.requestFocus();
        }
        // En Inicio un launcher no se cierra con Atrás
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        boolean center = k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER;
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            // Tecla Menú sobre una app: opciones
            if (k == KeyEvent.KEYCODE_MENU && e.getRepeatCount() == 0) {
                AppEntry ae = focusedEntry();
                if (ae != null) {
                    showMenu(ae);
                    return true;
                }
            }
            // Mantener OK sobre la cuadrícula de aplicaciones: opciones
            if (page == PAGE_APPS && grid.hasFocus() && center && e.getRepeatCount() > 0) {
                if (e.getRepeatCount() == 1) {
                    swallowCenterUp = true;
                    AppEntry ae = focusedEntry();
                    if (ae != null) {
                        showMenu(ae);
                    }
                }
                return true;
            }
        }
        if (center && e.getAction() == KeyEvent.ACTION_UP && swallowCenterUp) {
            swallowCenterUp = false;
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    private AppEntry focusedEntry() {
        View f = getCurrentFocus();
        if (f != null && f.getTag() instanceof AppEntry) {
            return (AppEntry) f.getTag();
        }
        if (page == PAGE_APPS && grid.hasFocus()) {
            int pos = grid.getSelectedItemPosition();
            if (pos >= 0 && pos < apps.size()) {
                return apps.get(pos);
            }
        }
        return null;
    }

    // ------------------------------------------------------- Barra superior

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(Ui.dp(40), Ui.dp(22), Ui.dp(48), Ui.dp(6));
        header.setClipChildren(false);

        String[] names = {
                getString(R.string.tab_home), getString(R.string.tab_apps), getString(R.string.tab_media)
        };
        LinearLayout tabBar = new LinearLayout(this);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            TextView t = Ui.tab(this, names[i]);
            t.setOnFocusChangeListener((v, has) -> {
                if (has && page != idx) {
                    showPage(idx);
                }
            });
            t.setOnClickListener(v -> {
                showPage(idx);
                pages.requestFocus();
                if (idx == PAGE_MEDIA) {
                    media.focusContent();
                }
            });
            tabs[i] = t;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = Ui.dp(6);
            tabBar.addView(t, lp);
        }
        header.addView(tabBar, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout time = new LinearLayout(this);
        time.setOrientation(LinearLayout.VERTICAL);
        time.setGravity(Gravity.RIGHT);
        clock = Ui.text(this, "", 26, Color.WHITE);
        clock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        clock.setGravity(Gravity.RIGHT);
        time.addView(clock);
        dateView = Ui.text(this, "", 12, Ui.MUTED);
        dateView.setGravity(Gravity.RIGHT);
        time.addView(dateView);
        header.addView(time, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        header.addView(Ui.pill(this, getString(R.string.wifi),
                v -> openSettings(Settings.ACTION_WIFI_SETTINGS)), pillParams());
        header.addView(Ui.pill(this, getString(R.string.settings),
                v -> openSettings(Settings.ACTION_SETTINGS)), pillParams());
        return header;
    }

    private LinearLayout.LayoutParams pillParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = Ui.dp(14);
        return lp;
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

    private void showPage(int p) {
        if (p == page) {
            return;
        }
        page = p;
        homeScroll.setVisibility(p == PAGE_HOME ? View.VISIBLE : View.GONE);
        grid.setVisibility(p == PAGE_APPS ? View.VISIBLE : View.GONE);
        media.setVisibility(p == PAGE_MEDIA ? View.VISIBLE : View.GONE);
        for (int i = 0; i < tabs.length; i++) {
            tabs[i].setActivated(i == p);
        }
        if (p == PAGE_HOME && homeDirty && !apps.isEmpty()) {
            buildHome();
        }
        if (p == PAGE_MEDIA) {
            media.refresh();
        }
    }

    private void goMedia(int filter) {
        showPage(PAGE_MEDIA);
        media.setFilter(filter);
        media.focusContent();
    }

    // ------------------------------------------------------- Página Inicio

    private void buildHomePage() {
        homeScroll = new ScrollView(this);
        homeScroll.setVerticalScrollBarEnabled(false);
        homeScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        homeScroll.setClipToPadding(false);
        homeScroll.setClipChildren(false);
        homeContent = new LinearLayout(this);
        homeContent.setOrientation(LinearLayout.VERTICAL);
        homeContent.setClipChildren(false);
        homeContent.setPadding(0, 0, 0, Ui.dp(40));
        homeScroll.addView(homeContent, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        pages.addView(homeScroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void buildHome() {
        homeDirty = false;
        boolean hadFocus = homeScroll.hasFocus();
        homeContent.removeAllViews();

        ArrayList<AppEntry> favs = entriesFor(getList("favs"));
        ArrayList<AppEntry> recents = entriesFor(getList("recents"));

        if (!favs.isEmpty()) {
            addAppRow(getString(R.string.favorites), favs);
        } else {
            ArrayList<AppEntry> first = new ArrayList<AppEntry>(
                    apps.subList(0, Math.min(12, apps.size())));
            addAppRow(getString(R.string.tab_apps), first);
        }
        if (!recents.isEmpty()) {
            addAppRow(getString(R.string.recents), recents);
        }
        addMediaRow();

        if (hadFocus || (page == PAGE_HOME && getCurrentFocus() == null)) {
            pages.requestFocus();
        }
    }

    private TextView sectionTitle(String s) {
        TextView t = Ui.text(this, s, 19, Ui.TEXT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(Ui.dp(48), Ui.dp(14), Ui.dp(48), 0);
        return t;
    }

    private LinearLayout newRow() {
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        hs.setClipToPadding(false);
        hs.setClipChildren(false);
        hs.setPadding(Ui.dp(40), Ui.dp(14), Ui.dp(40), Ui.dp(14));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        hs.addView(row, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        homeContent.addView(hs, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private void addRowTile(LinearLayout row, Tile t) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                Ui.dp(184), ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Ui.dp(18);
        row.addView(t, lp);
    }

    private void addAppRow(String title, ArrayList<AppEntry> list) {
        homeContent.addView(sectionTitle(title));
        LinearLayout row = newRow();
        for (final AppEntry e : list) {
            Tile t = new Tile(this, 104, 56, true);
            t.label.setText(e.label);
            t.icon.setImageDrawable(iconFor(e));
            t.setTag(e);
            t.setOnClickListener(v -> launch(e));
            t.setOnLongClickListener(v -> {
                showMenu(e);
                return true;
            });
            addRowTile(row, t);
        }
    }

    private void addMediaRow() {
        homeContent.addView(sectionTitle(getString(R.string.tab_media)));
        LinearLayout row = newRow();
        int[] names = { R.string.storage_card, R.string.videos, R.string.music, R.string.photos };
        int[] glyphs = { GlyphDrawable.USB, GlyphDrawable.VIDEO, GlyphDrawable.MUSIC, GlyphDrawable.IMAGE };
        for (int i = 0; i < names.length; i++) {
            final int filter = i;  // 0 = todo, 1 = videos, 2 = música, 3 = fotos
            Tile t = new Tile(this, 104, 60, true);
            t.label.setText(names[i]);
            t.icon.setImageDrawable(new GlyphDrawable(glyphs[i]));
            t.setOnClickListener(v -> goMedia(filter));
            addRowTile(row, t);
        }
    }

    // -------------------------------------------- Página Aplicaciones (cuadrícula)

    private void buildAppsPage(DisplayMetrics dm) {
        grid = new GridView(this);
        float widthDp = dm.widthPixels / dm.density;
        int cols = Math.max(3, (int) ((widthDp - 96 + 20) / 190));
        grid.setNumColumns(cols);
        grid.setHorizontalSpacing(Ui.dp(20));
        grid.setVerticalSpacing(Ui.dp(20));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setPadding(Ui.dp(48), Ui.dp(16), Ui.dp(48), Ui.dp(40));
        grid.setClipToPadding(false);
        grid.setClipChildren(false);
        grid.setSelector(new ColorDrawable(Color.TRANSPARENT));
        grid.setCacheColorHint(Color.TRANSPARENT);
        grid.setVerticalScrollBarEnabled(false);
        grid.setVerticalFadingEdgeEnabled(false);
        grid.setOverScrollMode(View.OVER_SCROLL_NEVER);
        adapter = new AppAdapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((AdapterView<?> parent, View view, int position, long id) ->
                launch(apps.get(position)));
        grid.setOnItemLongClickListener((AdapterView<?> parent, View view, int position, long id) -> {
            showMenu(apps.get(position));
            return true;
        });
        pages.addView(grid, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    // --------------------------------------------------------------- Datos

    private void reload() {
        new Thread(() -> {
            final ArrayList<AppEntry> list = queryApps();
            runOnUiThread(() -> {
                if (!alive) {
                    return;
                }
                icons.evictAll();
                apps.clear();
                apps.addAll(list);
                adapter.notifyDataSetChanged();
                homeDirty = true;
                if (page == PAGE_HOME) {
                    buildHome();
                }
            });
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
        Collections.sort(out, (a, b) -> a.label.compareToIgnoreCase(b.label));
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

    // Favoritos y recientes se guardan como una lista de componentes separados por saltos de línea

    private ArrayList<String> getList(String name) {
        ArrayList<String> out = new ArrayList<String>();
        String s = prefs.getString(name, "");
        if (s.length() > 0) {
            Collections.addAll(out, s.split("\n"));
        }
        return out;
    }

    private void saveList(String name, ArrayList<String> list) {
        StringBuilder sb = new StringBuilder();
        for (String s : list) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(s);
        }
        prefs.edit().putString(name, sb.toString()).apply();
    }

    private ArrayList<AppEntry> entriesFor(ArrayList<String> keys) {
        ArrayList<AppEntry> out = new ArrayList<AppEntry>();
        for (String key : keys) {
            for (AppEntry e : apps) {
                if (e.key.equals(key)) {
                    out.add(e);
                    break;
                }
            }
        }
        return out;
    }

    private void recordRecent(AppEntry e) {
        ArrayList<String> list = getList("recents");
        list.remove(e.key);
        list.add(0, e.key);
        while (list.size() > 10) {
            list.remove(list.size() - 1);
        }
        saveList("recents", list);
        homeDirty = true;
    }

    private void toggleFavorite(AppEntry e) {
        ArrayList<String> list = getList("favs");
        if (!list.remove(e.key)) {
            list.add(e.key);
        }
        saveList("favs", list);
        homeDirty = true;
        if (page == PAGE_HOME) {
            buildHome();
        }
    }

    // ------------------------------------------------------------- Acciones

    private void launch(AppEntry e) {
        recordRecent(e);
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.setComponent(e.cn);
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

    private void showMenu(final AppEntry e) {
        final boolean fav = getList("favs").contains(e.key);
        CharSequence[] items = {
                getString(R.string.open),
                getString(fav ? R.string.unpin : R.string.pin),
                getString(R.string.app_info),
                getString(R.string.uninstall)
        };
        new AlertDialog.Builder(this)
                .setTitle(e.label)
                .setItems(items, (dialog, which) -> {
                    String pkg = e.cn.getPackageName();
                    if (which == 0) {
                        launch(e);
                    } else if (which == 1) {
                        toggleFavorite(e);
                    } else if (which == 2) {
                        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:" + pkg));
                        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startSafely(i);
                    } else {
                        startSafely(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg)));
                    }
                })
                .show();
    }

    // ------------------------------------------------------------ Adaptador

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
                t = new Tile(MainActivity.this, 92, 56, false);
                t.setLayoutParams(new AbsListView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(134)));
            } else {
                t.reset();
            }
            AppEntry e = apps.get(position);
            t.label.setText(e.label);
            t.icon.setImageDrawable(iconFor(e));
            return t;
        }
    }
}
