package com.perrinho.launcher;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;

/** Sección Multimedia: menú lateral propio (dispositivos y filtros) y explorador de archivos. */
final class MediaPage extends LinearLayout {
    static final int ALL = 0;

    private final Activity act;
    private final LinearLayout side;
    private final TextView pathView;
    private final ListView list;
    private final LinearLayout emptyBox;
    private final TextView emptyText;
    private final TextView grantBtn;
    private final RowAdapter adapter = new RowAdapter();

    private final ArrayList<Storage.Vol> vols = new ArrayList<Storage.Vol>();
    private final ArrayList<File> entries = new ArrayList<File>();
    private final ArrayList<TextView> volItems = new ArrayList<TextView>();
    private final ArrayList<TextView> filterItems = new ArrayList<TextView>();

    private File root;
    private File cur;
    private String rootLabel = "";
    private int filter = ALL;
    private String signature = "";

    MediaPage(Activity activity) {
        super(activity);
        act = activity;
        setOrientation(HORIZONTAL);
        setClipChildren(false);

        side = new LinearLayout(act);
        side.setOrientation(VERTICAL);
        side.setPadding(Ui.dp(40), Ui.dp(8), Ui.dp(16), Ui.dp(16));
        addView(side, new LinearLayout.LayoutParams(Ui.dp(300), ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout right = new LinearLayout(act);
        right.setOrientation(VERTICAL);
        right.setClipChildren(false);
        addView(right, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        pathView = Ui.text(act, "", 15, Ui.MUTED);
        pathView.setSingleLine(true);
        pathView.setEllipsize(TextUtils.TruncateAt.START);
        pathView.setPadding(Ui.dp(8), Ui.dp(14), Ui.dp(48), Ui.dp(10));
        right.addView(pathView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout frame = new FrameLayout(act);
        frame.setClipChildren(false);
        right.addView(frame, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        list = new ListView(act);
        list.setDivider(null);
        list.setDividerHeight(Ui.dp(8));
        list.setSelector(new ColorDrawable(Color.TRANSPARENT));
        list.setCacheColorHint(Color.TRANSPARENT);
        list.setClipToPadding(false);
        list.setPadding(Ui.dp(6), Ui.dp(6), Ui.dp(48), Ui.dp(40));
        list.setVerticalScrollBarEnabled(false);
        list.setOverScrollMode(View.OVER_SCROLL_NEVER);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> openEntry(entries.get(position)));
        frame.addView(list, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        emptyBox = new LinearLayout(act);
        emptyBox.setOrientation(VERTICAL);
        emptyBox.setGravity(Gravity.CENTER);
        emptyText = Ui.text(act, "", 18, Ui.MUTED);
        emptyText.setGravity(Gravity.CENTER);
        emptyBox.addView(emptyText);
        grantBtn = Ui.pill(act, act.getString(R.string.grant), v -> requestPermission());
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gp.topMargin = Ui.dp(18);
        emptyBox.addView(grantBtn, gp);
        frame.addView(emptyBox, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    // ------------------------------------------------------------ Estado

    /** Vuelve a leer las memorias conectadas; se llama al mostrar la sección o al conectar un USB. */
    void refresh() {
        boolean ok = Storage.hasPermission(act);
        vols.clear();
        if (ok) {
            vols.addAll(Storage.volumes(act));
        }
        StringBuilder sb = new StringBuilder(ok ? "1" : "0");
        for (Storage.Vol v : vols) {
            sb.append('|').append(v.dir.getPath());
        }
        if (!sb.toString().equals(signature) || volItems.isEmpty() && filterItems.isEmpty()) {
            signature = sb.toString();
            buildSidebar();
        }

        if (!ok) {
            entries.clear();
            adapter.notifyDataSetChanged();
            pathView.setText("");
            showEmpty(act.getString(R.string.need_permission), true);
            return;
        }
        if (root != null && !stillMounted(root)) {
            root = null;
            cur = null;
        }
        if (root == null) {
            if (vols.isEmpty()) {
                entries.clear();
                adapter.notifyDataSetChanged();
                pathView.setText("");
                showEmpty(act.getString(R.string.connect_usb), false);
            } else {
                openRoot(vols.get(0));
            }
        } else {
            load(cur);
        }
        updateActive();
    }

    private boolean stillMounted(File dir) {
        for (Storage.Vol v : vols) {
            if (v.dir.equals(dir)) {
                return true;
            }
        }
        return false;
    }

    void setFilter(int f) {
        filter = f;
        updateActive();
        if (cur != null) {
            load(cur);
        }
    }

    /** Sube una carpeta si se puede. Devuelve false si ya está en la raíz. */
    boolean back() {
        if (cur != null && root != null && !cur.equals(root)) {
            File parent = cur.getParentFile();
            if (parent != null) {
                load(parent);
                return true;
            }
        }
        return false;
    }

    void focusContent() {
        if (list.getVisibility() == VISIBLE && entries.size() > 0) {
            list.requestFocus();
        } else if (grantBtn.getVisibility() == VISIBLE) {
            grantBtn.requestFocus();
        } else if (!volItems.isEmpty()) {
            volItems.get(0).requestFocus();
        } else if (!filterItems.isEmpty()) {
            filterItems.get(0).requestFocus();
        }
    }

    // --------------------------------------------------------- Menú lateral

    private TextView sideLabel(int res) {
        TextView t = Ui.text(act, act.getString(res).toUpperCase(), 13, Ui.MUTED);
        t.setPadding(Ui.dp(16), Ui.dp(18), Ui.dp(16), Ui.dp(6));
        return t;
    }

    private LinearLayout.LayoutParams itemParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(4);
        return lp;
    }

    private void buildSidebar() {
        side.removeAllViews();
        volItems.clear();
        filterItems.clear();

        TextView title = Ui.text(act, act.getString(R.string.tab_media), 26, Color.WHITE);
        title.setPadding(Ui.dp(16), Ui.dp(6), Ui.dp(16), Ui.dp(4));
        side.addView(title);

        side.addView(sideLabel(R.string.devices));
        for (int i = 0; i < vols.size(); i++) {
            final Storage.Vol v = vols.get(i);
            TextView t = Ui.sideItem(act, v.label, v.usb ? GlyphDrawable.USB : GlyphDrawable.DISK);
            t.setOnClickListener(x -> openRoot(v));
            volItems.add(t);
            side.addView(t, itemParams());
        }

        side.addView(sideLabel(R.string.show));
        int[] names = { R.string.all, R.string.videos, R.string.music, R.string.photos };
        int[] glyphs = { GlyphDrawable.FOLDER, GlyphDrawable.VIDEO, GlyphDrawable.MUSIC, GlyphDrawable.IMAGE };
        for (int j = 0; j < names.length; j++) {
            final int f = j;
            TextView t = Ui.sideItem(act, act.getString(names[j]), glyphs[j]);
            t.setOnClickListener(x -> setFilter(f));
            filterItems.add(t);
            side.addView(t, itemParams());
        }
        updateActive();
    }

    private void updateActive() {
        for (int i = 0; i < volItems.size() && i < vols.size(); i++) {
            volItems.get(i).setActivated(vols.get(i).dir.equals(root));
        }
        for (int j = 0; j < filterItems.size(); j++) {
            filterItems.get(j).setActivated(j == filter);
        }
    }

    // -------------------------------------------------------- Explorador

    private void openRoot(Storage.Vol v) {
        root = v.dir;
        rootLabel = v.label;
        load(v.dir);
        updateActive();
    }

    private void load(File dir) {
        cur = dir;
        entries.clear();
        File[] fs = dir.listFiles();
        if (fs != null) {
            ArrayList<File> dirs = new ArrayList<File>();
            ArrayList<File> files = new ArrayList<File>();
            for (File f : fs) {
                if (f.getName().startsWith(".")) {
                    continue;
                }
                if (f.isDirectory()) {
                    dirs.add(f);
                } else {
                    int k = Storage.kind(f);
                    if (k != Storage.NONE && (filter == ALL || k == filter)) {
                        files.add(f);
                    }
                }
            }
            Comparator<File> byName = (a, b) -> a.getName().compareToIgnoreCase(b.getName());
            Collections.sort(dirs, byName);
            Collections.sort(files, byName);
            entries.addAll(dirs);
            entries.addAll(files);
        }
        String rel = dir.getPath().substring(Math.min(root.getPath().length(), dir.getPath().length()));
        pathView.setText(rootLabel + rel);
        adapter.notifyDataSetChanged();
        list.setSelection(0);
        if (entries.isEmpty()) {
            showEmpty(act.getString(R.string.empty_folder), false);
        } else {
            emptyBox.setVisibility(GONE);
            list.setVisibility(VISIBLE);
        }
    }

    private void showEmpty(String msg, boolean needsPermission) {
        emptyText.setText(msg);
        grantBtn.setVisibility(needsPermission ? VISIBLE : GONE);
        emptyBox.setVisibility(VISIBLE);
        list.setVisibility(GONE);
    }

    private void openEntry(File f) {
        if (f.isDirectory()) {
            load(f);
            return;
        }
        int kind = Storage.kind(f);
        ArrayList<String> paths = new ArrayList<String>();
        int index = 0;
        for (File e : entries) {
            if (!e.isDirectory() && Storage.kind(e) == kind) {
                if (e.equals(f)) {
                    index = paths.size();
                }
                paths.add(e.getPath());
            }
        }
        Intent i = new Intent(act, PlayerActivity.class);
        i.putExtra(PlayerActivity.EXTRA_PATHS, paths.toArray(new String[0]));
        i.putExtra(PlayerActivity.EXTRA_INDEX, index);
        i.putExtra(PlayerActivity.EXTRA_KIND, kind);
        act.startActivity(i);
    }

    private void requestPermission() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + act.getPackageName()));
                act.startActivity(i);
            } catch (Exception e) {
                try {
                    act.startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                } catch (Exception ignored) {
                }
            }
        } else if (Build.VERSION.SDK_INT >= 23) {
            act.requestPermissions(
                    new String[] { android.Manifest.permission.READ_EXTERNAL_STORAGE }, 7);
        }
    }

    // ---------------------------------------------------------- Adaptador

    private final class RowAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return entries.size();
        }

        @Override
        public Object getItem(int position) {
            return entries.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Row row = convertView instanceof Row ? (Row) convertView : new Row();
            File f = entries.get(position);
            row.name.setText(f.getName());
            int glyph;
            String sub;
            if (f.isDirectory()) {
                glyph = GlyphDrawable.FOLDER;
                sub = act.getString(R.string.folder);
            } else {
                int k = Storage.kind(f);
                glyph = k == Storage.VIDEO ? GlyphDrawable.VIDEO
                        : k == Storage.AUDIO ? GlyphDrawable.MUSIC : GlyphDrawable.IMAGE;
                sub = Formatter.formatShortFileSize(act, f.length());
            }
            row.sub.setText(sub);
            row.icon.setImageDrawable(new GlyphDrawable(glyph));
            return row;
        }
    }

    private final class Row extends LinearLayout {
        final ImageView icon;
        final TextView name;
        final TextView sub;

        Row() {
            super(act);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(Ui.dp(18), Ui.dp(8), Ui.dp(18), Ui.dp(8));
            setBackgroundDrawable(Ui.focusCard(14, Ui.CARD, Ui.CARD_FOCUS));
            setLayoutParams(new AbsListView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(64)));

            icon = new ImageView(act);
            addView(icon, new LinearLayout.LayoutParams(Ui.dp(36), Ui.dp(36)));

            LinearLayout col = new LinearLayout(act);
            col.setOrientation(VERTICAL);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            cp.leftMargin = Ui.dp(16);
            addView(col, cp);

            name = new TextView(act);
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            name.setTextColor(Color.WHITE);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            col.addView(name);

            sub = new TextView(act);
            sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            sub.setTextColor(Ui.MUTED);
            sub.setSingleLine(true);
            col.addView(sub);
        }
    }
}
