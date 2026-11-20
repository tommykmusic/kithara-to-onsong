package com.tapediatispolis.kitharaimport;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONObject;
import org.json.JSONTokener;

public final class ImportActivity extends Activity {
    private static final String ONSONG = "com.onsongapp.app";
    private static final String SEARCH = "https://kithara.to/fi";
    private static final Pattern SHARED_URL = Pattern.compile("https://[^\\s<>]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern SONG_ID = Pattern.compile("[A-Za-z0-9_-]{1,512}");
    private static final int FOREST = Color.rgb(23, 60, 55);
    private static final int PAPER = Color.rgb(246, 244, 234);
    private static final int MIST = Color.rgb(224, 230, 218);
    private WebView browser;
    private TextView status;
    private Button addButton;
    private String extractor;
    private boolean pageFailed;
    private int navigation;
    private String readySong;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        createLayout();
        try (InputStream asset = getAssets().open("extract-chart.js")) {
            ByteArrayOutputStream source = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int length;
            while ((length = asset.read(buffer)) != -1) source.write(buffer, 0, length);
            extractor = new String(source.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            status.setText("The chart reader could not load. Reinstall Kithara to OnSong.");
            return;
        }
        pruneExpiredCharts();
        if (state == null || browser.restoreState(state) == null) openIntent(getIntent());
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        openIntent(intent);
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        browser.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    private void createLayout() {
        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(PAPER);
        screen.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                Insets safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            } else {
                view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(12), dp(4), dp(12), dp(4));
        TextView title = new TextView(this);
        title.setText("Kithara to OnSong");
        title.setTextSize(19);
        title.setTextColor(FOREST);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        Button search = new Button(this, null, android.R.attr.borderlessButtonStyle);
        search.setAllCaps(false);
        search.setText("Search");
        search.setTextColor(FOREST);
        search.setOnClickListener(view -> browser.loadUrl(SEARCH));
        toolbar.addView(search);
        screen.addView(toolbar);

        browser = new WebView(this);
        browser.getSettings().setJavaScriptEnabled(true);
        browser.getSettings().setDomStorageEnabled(true);
        browser.getSettings().setAllowFileAccess(false);
        browser.getSettings().setAllowContentAccess(false);
        browser.getSettings().setJavaScriptCanOpenWindowsAutomatically(false);
        browser.getSettings().setSupportMultipleWindows(false);
        browser.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return request.isForMainFrame() && !isKithara(request.getUrl());
            }

            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
                navigation++;
                pageFailed = false;
                readySong = null;
                addButton.setEnabled(false);
                status.setText("Loading Kithara…");
            }

            @Override public void onPageFinished(WebView view, String url) {
                inspectPage(navigation, 4);
            }

            @Override public void doUpdateVisitedHistory(WebView view, String url, boolean reload) {
                int current = ++navigation;
                readySong = null;
                addButton.setEnabled(false);
                view.postDelayed(() -> inspectPage(current, 6), 200);
            }

            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (!request.isForMainFrame()) return;
                pageFailed = true;
                addButton.setEnabled(false);
                status.setText("Kithara could not load. Check your connection, then tap Search.");
            }
        });
        screen.addView(browser, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setPadding(dp(12), dp(7), dp(12), dp(7));
        status = new TextView(this);
        status.setTextColor(FOREST);
        status.setTextSize(14);
        status.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);
        footer.addView(status);
        addButton = new Button(this);
        addButton.setAllCaps(false);
        addButton.setText("Add to OnSong");
        addButton.setBackgroundTintList(new ColorStateList(
                new int[][] {{android.R.attr.state_enabled}, {}}, new int[] {FOREST, MIST}));
        addButton.setTextColor(new ColorStateList(
                new int[][] {{android.R.attr.state_enabled}, {}}, new int[] {Color.WHITE, FOREST}));
        addButton.setEnabled(false);
        addButton.setOnClickListener(view -> addChart());
        footer.addView(addButton, new LinearLayout.LayoutParams(-1, -2));
        screen.addView(footer);
        setContentView(screen);
        screen.requestApplyInsets();
    }

    private void inspectPage(int current, int retries) {
        if (isDestroyed() || current != navigation || pageFailed) return;
        browser.evaluateJavascript("[location.href, !!document.querySelector('#text > .te, #text > .ch')]", raw -> {
            if (isDestroyed() || current != navigation || pageFailed) return;
            try {
                org.json.JSONArray snapshot = new org.json.JSONArray(raw);
                String address = snapshot.getString(0);
                boolean hasChart = snapshot.getBoolean(1);
                if (songId(Uri.parse(address)) == null) {
                    status.setText("Open a song to add its chart.");
                } else if (hasChart) {
                    readySong = address;
                    addButton.setEnabled(extractor != null);
                    status.setText("Ready to add this arrangement.");
                } else if (retries > 0) {
                    browser.postDelayed(() -> inspectPage(current, retries - 1), 350);
                } else {
                    status.setText("This page does not show a Kithara chart yet.");
                }
            } catch (org.json.JSONException ignored) {
                // An earlier document can be replaced before its script executes.
            }
        });
    }

    private void openIntent(Intent intent) {
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            CharSequence shared = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
            if (shared != null && shared.length() <= 8192) {
                Matcher urls = SHARED_URL.matcher(shared);
                while (urls.find()) {
                    Uri candidate = Uri.parse(urls.group());
                    if (songId(candidate) != null) {
                        browser.loadUrl(candidate.toString());
                        return;
                    }
                }
            }
        }
        browser.loadUrl(SEARCH);
    }

    private void addChart() {
        String currentUrl = readySong;
        if (currentUrl == null || songId(Uri.parse(currentUrl)) == null) return;
        int currentNavigation = navigation;
        addButton.setEnabled(false);
        status.setText("Preparing the chord chart…");
        browser.evaluateJavascript(extractor, raw -> {
            if (isFinishing() || isDestroyed() || currentNavigation != navigation) return;
            addButton.setEnabled(true);
            try {
                JSONObject chart = (JSONObject) new JSONTokener(raw).nextValue();
                if (chart.has("error")) {
                    status.setText(chart.getString("error"));
                    return;
                }
                String id = songId(Uri.parse(chart.getString("sourceUrl")));
                String content = chart.getString("chordpro");
                if (!songId(Uri.parse(currentUrl)).equals(id) || content.length() > 256_000) {
                    status.setText("This chart could not be prepared. Reopen the song and try again.");
                    return;
                }
                int endOfTitle = content.indexOf('\n') + 1;
                String fileText = content.substring(0, endOfTitle) + "{book: Kithara Inbox}\n"
                        + content.substring(endOfTitle);
                openInOnSong(chart.getString("title"), fileText);
            } catch (ActivityNotFoundException error) {
                status.setText("Install or update OnSong to open this chord chart.");
            } catch (IOException | org.json.JSONException | ClassCastException | NullPointerException error) {
                status.setText("This chart could not be prepared. Reopen the song and try again.");
            }
        });
    }

    private void openInOnSong(String title, String content) throws IOException {
        String exportId = UUID.randomUUID().toString();
        File directory = new File(new File(getCacheDir(), ChartProvider.DIRECTORY), exportId);
        if (!directory.mkdirs()) throw new IOException("Chart directory could not be created.");
        String name = title.replaceAll("[\\p{Cc}\\\\/:*?\"<>|]", " ").trim();
        if (name.isEmpty()) name = "Kithara chart";
        int limit = Math.min(42, name.codePointCount(0, name.length()));
        // Kithara can rotate a URL without changing the arrangement.
        String arrangement = content.replaceFirst("(?m)^# Source: [^\\r\\n]+\\n", "");
        String hash = UUID.nameUUIDFromBytes(arrangement.getBytes(StandardCharsets.UTF_8)).toString();
        String filename = name.substring(0, name.offsetByCodePoints(0, limit)).trim() + " - " + hash + ".chordpro";
        Files.write(new File(directory, filename).toPath(), content.getBytes(StandardCharsets.UTF_8));
        Uri uri = new Uri.Builder().scheme("content").authority(ChartProvider.AUTHORITY)
                .appendPath(exportId).appendPath(filename).build();
        Intent open = new Intent(Intent.ACTION_VIEW)
                .setPackage(ONSONG)
                .setDataAndType(uri, ChartProvider.MIME_TYPE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        open.setClipData(ClipData.newRawUri("ChordPro chart", uri));
        startActivity(open);
        status.setText("Chart sent to OnSong.");
    }

    private void pruneExpiredCharts() {
        File[] exports = new File(getCacheDir(), ChartProvider.DIRECTORY).listFiles();
        if (exports == null) return;
        long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1);
        for (File directory : exports) {
            if (!directory.getName().matches("[0-9a-f-]{36}") || directory.lastModified() >= cutoff) continue;
            File[] charts = directory.listFiles();
            if (charts == null) continue;
            for (File chart : charts) chart.delete();
            directory.delete();
        }
    }

    private static boolean isKithara(Uri uri) {
        return "https".equalsIgnoreCase(uri.getScheme()) && uri.getUserInfo() == null
                && (uri.getPort() == -1 || uri.getPort() == 443)
                && ("kithara.to".equalsIgnoreCase(uri.getHost()) || "www.kithara.to".equalsIgnoreCase(uri.getHost()));
    }

    private static String songId(Uri uri) {
        if (!isKithara(uri)) return null;
        List<String> path = uri.getPathSegments();
        if (path.size() < 2 || !"stixoi".equals(path.get(0))) return null;
        String id = path.get(1);
        return SONG_ID.matcher(id).matches() ? id : null;
    }

    private int dp(int value) { return Math.round(getResources().getDisplayMetrics().density * value); }

    @Override public void onBackPressed() {
        if (browser.canGoBack()) browser.goBack();
        else super.onBackPressed();
    }
}
