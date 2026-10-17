package com.fileviewer.fixture;

import android.app.Instrumentation;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebResourceRequest;
import android.net.Uri;
import java.util.Map;
import java.util.Collections;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.RandomAccessFile;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Arrays;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

public class DeviceSecurity extends Instrumentation {
    private final StringBuilder report = new StringBuilder();
    @Override public void onCreate(Bundle args) { start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            xml();
            web();
            sqlite();
            largeDatabase();
            result.putString("result", report.toString());
            finish(0, result);
        } catch (Throwable error) {
            result.putString("result", report + "FAIL " + android.util.Log.getStackTraceString(error));
            finish(1, result);
        }
    }
    private Class<?> appClass(String name) throws Exception {
        return getTargetContext().getClassLoader().loadClass("com.fileviewer.app." + name);
    }
    private void check(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
        report.append("PASS ").append(label).append('\n');
    }
    private void xml() throws Exception {
        Class<?> safe = appClass("viewer.SafeXml");
        Object instance = safe.getField("INSTANCE").get(null);
        String[] docs = {
            "<root>ordinary XML</root>",
            "<!DOCTYPE root [<!ENTITY secret SYSTEM 'file:///proc/version'>]><root>&secret;</root>",
            "<!DOCTYPE root SYSTEM 'https://example.invalid/external.dtd'><root/>",
            "<!DOCTYPE root [<!ENTITY a 'expanded'>]><root>&a;</root>"
        };
        for (int i = 0; i < docs.length; i++) {
            XMLReader reader = (XMLReader) safe.getMethod("xmlReader").invoke(instance);
            StringBuilder text = new StringBuilder();
            reader.setContentHandler(new DefaultHandler() {
                @Override public void characters(char[] c, int at, int n) { text.append(c, at, n); }
            });
            boolean refused = false;
            try { reader.parse(new InputSource(new ByteArrayInputStream(docs[i].getBytes(StandardCharsets.UTF_8)))); }
            catch (org.xml.sax.SAXException expected) { refused = true; }
            check(i == 0 ? !refused && text.toString().equals("ordinary XML") : refused && text.length() == 0,
                "Android SAX DOCTYPE case " + i + " " + reader.getClass().getName());
        }
    }
    private void web() throws Exception {
        final Throwable[] failure = new Throwable[1];
        runOnMainSync(() -> {
            WebView web = null;
            try {
                Class<?> safe = appClass("viewer.SafeWebView");
                Class<?> function = getTargetContext().getClassLoader().loadClass("kotlin.jvm.functions.Function1");
                web = (WebView) safe.getMethod("create", Context.class, function).invoke(safe.getField("INSTANCE").get(null), getTargetContext(), null);
                check(!web.getSettings().getJavaScriptEnabled(), "WebView scripts disabled");
                check(!web.getSettings().getAllowFileAccess(), "WebView file access disabled");
                check(!web.getSettings().getAllowContentAccess(), "WebView content access disabled");
                check(web.getSettings().getBlockNetworkLoads(), "WebView network disabled");
                check(!web.getSettings().getDomStorageEnabled(), "WebView DOM storage disabled");
                check(getTargetContext().checkSelfPermission("android.permission.INTERNET") == android.content.pm.PackageManager.PERMISSION_DENIED, "App has no INTERNET permission");
                for (String url : new String[]{"https://example.invalid/image.png", "file:///proc/version", "content://com.fileviewer.fixture.files/phase2-secret.txt"}) {
                    check(web.getWebViewClient().shouldInterceptRequest(web, request(url)).getStatusCode() == 404, "WebView blocks " + url);
                }
            } catch (Throwable error) { failure[0] = error; }
            finally { if (web != null) web.destroy(); }
        });
        if (failure[0] != null) throw new Exception(failure[0]);
    }
    private WebResourceRequest request(String url) {
        return new WebResourceRequest() {
            public Uri getUrl() { return Uri.parse(url); }
            public boolean isForMainFrame() { return false; }
            public boolean isRedirect() { return false; }
            public boolean hasGesture() { return false; }
            public String getMethod() { return "GET"; }
            public Map<String, String> getRequestHeaders() { return Collections.emptyMap(); }
        };
    }
    private void sqlite() throws Exception {
        Context context = getTargetContext();
        File folder = new File(context.getFilesDir(), "phase2-security");
        folder.mkdirs();
        File original = new File(folder, "wal.db");
        SQLiteDatabase writer = SQLiteDatabase.openOrCreateDatabase(original, null);
        writer.enableWriteAheadLogging();
        writer.execSQL("CREATE TABLE IF NOT EXISTS evidence (value TEXT)");
        writer.execSQL("INSERT INTO evidence VALUES ('uncheckpointed WAL row')");
        String[] suffixes = {"", "-wal", "-shm", "-journal"};
        byte[][] before = new byte[suffixes.length][];
        for (int i = 0; i < suffixes.length; i++) before[i] = bytes(new File(original + suffixes[i]));
        Object session = null;
        try {
            Class<?> type = appClass("viewers.data.sqlite.SqliteSession");
            Object companion = type.getField("Companion").get(null);
            session = companion.getClass().getMethod("open", Context.class, File.class).invoke(companion, context, original);
            java.lang.reflect.Field connection = type.getDeclaredField("db");
            connection.setAccessible(true);
            check(((SQLiteDatabase) connection.get(session)).isReadOnly(), "SQLite connection is read-only");
            Object count = type.getMethod("count", String.class, android.os.CancellationSignal.class).invoke(session, "evidence", null);
            check(((Long) count) > 0, "Private SQLite copy sees WAL rows");
            type.getMethod("close").invoke(session);
            session = null;
            for (int i = 0; i < suffixes.length; i++) check(Arrays.equals(before[i], bytes(new File(original + suffixes[i]))), "SQLite original unchanged " + suffixes[i]);
        } finally {
            if (session != null) session.getClass().getMethod("close").invoke(session);
            writer.close();
            for (String suffix : suffixes) new File(original + suffix).delete();
            folder.delete();
        }
    }
    private void largeDatabase() throws Exception {
        Context context = getTargetContext();
        File original = new File(context.getFilesDir(), "phase2-oversized.db");
        try {
            try (RandomAccessFile file = new RandomAccessFile(original, "rw")) {
                byte[] header = new byte[100];
                header[18] = 2;
                header[19] = 2;
                file.write(header);
                file.setLength((2L << 30) + 1);
            }
            Class<?> type = appClass("viewers.data.sqlite.SqliteSession");
            Object companion = type.getField("Companion").get(null);
            boolean refused = false;
            try { companion.getClass().getMethod("open", Context.class, File.class).invoke(companion, context, original); }
            catch (InvocationTargetException expected) {
                refused = expected.getCause().getClass().getSimpleName().equals("LimitExceededException");
            }
            check(refused, "Oversized WAL database refuses copy instead of opening original");
            check(!new File(original + "-shm").exists(), "Oversized original has no created SHM");
        } finally { original.delete(); }
    }
    private byte[] bytes(File file) throws Exception {
        if (!file.exists()) return null;
        return MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file.toPath()));
    }
}
