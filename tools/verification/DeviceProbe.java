package com.fileviewer.fixture;

import android.app.Instrumentation;
import android.app.Activity;
import android.content.Context;
import android.content.ClipData;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.view.InputDevice;
import android.view.MotionEvent;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;

public class DeviceProbe extends Instrumentation {
    private Bundle args;
    @Override public void onCreate(Bundle arguments) { args = arguments; start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            if ("pinch".equals(args.getString("mode"))) pinch();
            else send(getContext(), args);
            result.putString("result", "PASS");
            finish(0, result);
        } catch (Throwable error) {
            result.putString("result", error.toString());
            finish(1, result);
        }
    }
    private static void send(Context context, Bundle args) {
        String mode = args.getString("mode", "send");
        ArrayList<Uri> uris = new ArrayList<>();
        for (String file : args.getString("files", "sample.kt").split(",")) {
            uris.add(Uri.parse("content://com.fileviewer.fixture.files/" + file));
        }
        Intent intent = new Intent("multiple".equals(mode) ? Intent.ACTION_SEND_MULTIPLE : Intent.ACTION_SEND);
        intent.setClassName("com.fileviewer.app", "com.fileviewer.app.MainActivity");
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if ("new".equals(args.getString("delivery"))) intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (!"clip".equals(mode)) {
            if ("multiple".equals(mode)) intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            else intent.putExtra(Intent.EXTRA_STREAM, uris.get(0));
        }
        ClipData clip = ClipData.newRawUri("fixtures", uris.get(0));
        for (int i = 1; i < uris.size(); i++) clip.addItem(new ClipData.Item(uris.get(i)));
        intent.setClipData(clip);
        context.startActivity(intent);
    }
    public static class Sender extends Activity {
        @Override public void onCreate(Bundle state) {
            super.onCreate(state);
            send(this, getIntent().getExtras());
            finish();
        }
    }
    private void pinch() {
        float cx = Float.parseFloat(args.getString("x", "540"));
        float cy = Float.parseFloat(args.getString("y", "950"));
        float factor = Float.parseFloat(args.getString("factor", "2"));
        long start = android.os.SystemClock.uptimeMillis();
        event(start, start, MotionEvent.ACTION_DOWN, cx, cy, 60, 1);
        event(start, start+20, MotionEvent.ACTION_POINTER_DOWN | (1 << 8), cx, cy, 60, 2);
        for (int i = 1; i <= 20; i++) {
            event(start, start+20+i*20, MotionEvent.ACTION_MOVE, cx, cy, 60*(1+(factor-1)*i/20), 2);
            android.os.SystemClock.sleep(20);
        }
        event(start, start+450, MotionEvent.ACTION_POINTER_UP | (1 << 8), cx, cy, 60*factor, 2);
        event(start, start+470, MotionEvent.ACTION_UP, cx, cy, 60*factor, 1);
    }
    private void event(long start, long now, int action, float x, float y, float radius, int count) {
        MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[count];
        MotionEvent.PointerCoords[] coordinates = new MotionEvent.PointerCoords[count];
        for (int i = 0; i < count; i++) {
            properties[i] = new MotionEvent.PointerProperties();
            properties[i].id = i;
            properties[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
            coordinates[i] = new MotionEvent.PointerCoords();
            coordinates[i].x = x + (i == 0 ? -radius : radius);
            coordinates[i].y = y;
            coordinates[i].pressure = 1;
            coordinates[i].size = 0.1f;
        }
        MotionEvent event = MotionEvent.obtain(start, now, action, count, properties, coordinates, 0, 0, 1, 1, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
        try { getUiAutomation().injectInputEvent(event, true); } finally { event.recycle(); }
    }
    public static class Files extends ContentProvider {
        @Override public boolean onCreate() { return true; }
        private File file(Uri uri) {
            String name = uri.getLastPathSegment();
            if (name == null || name.contains("..") || name.contains("/")) throw new IllegalArgumentException("fixture name");
            return new File(getContext().getFilesDir(), name);
        }
        @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sort) {
            File file = file(uri);
            MatrixCursor cursor = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
            cursor.addRow(new Object[]{file.getName(), file.length()});
            return cursor;
        }
        @Override public String getType(Uri uri) { return "application/octet-stream"; }
        @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
            return ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.MODE_READ_ONLY);
        }
        @Override public AssetFileDescriptor openAssetFile(Uri uri, String mode) throws FileNotFoundException {
            File file = file(uri);
            if (file.getName().equals("window.pdf")) return new AssetFileDescriptor(openFile(uri, mode), 512, file.length()-1024);
            return new AssetFileDescriptor(openFile(uri, mode), 0, file.length());
        }
        @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
        @Override public int delete(Uri uri, String where, String[] args) { throw new UnsupportedOperationException(); }
        @Override public int update(Uri uri, ContentValues values, String where, String[] args) { throw new UnsupportedOperationException(); }
    }
}
