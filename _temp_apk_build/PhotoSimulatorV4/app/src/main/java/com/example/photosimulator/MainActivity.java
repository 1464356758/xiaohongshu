package com.example.photosimulator;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int PICK_IMAGE = 4101;

    private ImageView preview;
    private TextView status;
    private Uri selectedUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
    }

    private View buildUi() {
        int pad = dp(20);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(250, 250, 250));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("手机照片模拟器 V4");
        title.setTextSize(26);
        title.setTextColor(Color.rgb(20,20,20));
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title);

        TextView desc = new TextView(this);
        desc.setText("导入图片后，保持画面内容不变，重新导出为标准手机照片式 JPEG 文件。\n默认参考设备：Xiaomi 17T。元数据为模拟数据，不代表真实相机拍摄。");
        desc.setTextSize(14);
        desc.setTextColor(Color.rgb(90,90,90));
        desc.setPadding(0, dp(8), 0, dp(16));
        root.addView(desc);

        preview = new ImageView(this);
        preview.setBackgroundColor(Color.rgb(235,235,235));
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(preview, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(340)));

        Button choose = new Button(this);
        choose.setText("① 选择图片");
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(56));
        bp.topMargin = dp(18);
        root.addView(choose, bp);

        Button export = new Button(this);
        export.setText("② 一键导出手机照片式文件");
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(56));
        ep.topMargin = dp(10);
        root.addView(export, ep);

        status = new TextView(this);
        status.setText("等待选择图片");
        status.setTextSize(14);
        status.setTextColor(Color.rgb(80,80,80));
        status.setGravity(Gravity.CENTER_HORIZONTAL);
        status.setPadding(0, dp(18), 0, dp(24));
        root.addView(status);

        choose.setOnClickListener(v -> chooseImage());
        export.setOnClickListener(v -> exportImage());

        scroll.addView(root);
        return scroll;
    }

    private void chooseImage() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i, PICK_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            selectedUri = data.getData();
            try {
                if (selectedUri != null) {
                    getContentResolver().takePersistableUriPermission(
                            selectedUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                }
            } catch (Exception ignored) {}

            preview.setImageURI(selectedUri);
            status.setText("图片已载入。点击下面按钮即可导出。");
        }
    }

    private void exportImage() {
        if (selectedUri == null) {
            Toast.makeText(this, "请先选择图片", Toast.LENGTH_SHORT).show();
            return;
        }

        status.setText("正在导出…");

        new Thread(() -> {
            try {
                String sourceType = getContentResolver().getType(selectedUri);
                boolean sourceIsJpeg = "image/jpeg".equalsIgnoreCase(sourceType)
                        || "image/jpg".equalsIgnoreCase(sourceType);

                String stamp = new SimpleDateFormat(
                        "yyyyMMdd_HHmmss", Locale.US).format(new Date());

                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME,
                        "Xiaomi17T_SimulatedCamera_" + stamp + ".jpg");
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                values.put(MediaStore.Images.Media.RELATIVE_PATH,
                        "Pictures/PhonePhotoSimulator");
                values.put(MediaStore.Images.Media.IS_PENDING, 1);

                Uri outUri = getContentResolver().insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (outUri == null) throw new Exception("无法创建输出文件");

                if (sourceIsJpeg) {
                    copyExactly(selectedUri, outUri);
                } else {
                    encodeAsHighQualityJpeg(selectedUri, outUri);
                }

                writeSimulatedCameraExif(outUri);

                values.clear();
                values.put(MediaStore.Images.Media.IS_PENDING, 0);
                getContentResolver().update(outUri, values, null, null);

                runOnUiThread(() -> {
                    status.setText(
                            "完成 ✅\n已保存到 Pictures/PhonePhotoSimulator\n" +
                            "JPEG输入：尽量保留原始像素编码，只改文件元数据。\n" +
                            "其他格式：高质量转换为JPEG。");
                    Toast.makeText(this, "导出成功", Toast.LENGTH_LONG).show();
                });

            } catch (Exception e) {
                runOnUiThread(() -> status.setText("导出失败：" + e.getMessage()));
            }
        }).start();
    }

    private void copyExactly(Uri inUri, Uri outUri) throws Exception {
        try (InputStream in = getContentResolver().openInputStream(inUri);
             OutputStream out = getContentResolver().openOutputStream(outUri, "w")) {
            if (in == null || out == null) throw new Exception("无法读取或写入文件");
            byte[] buf = new byte[1024 * 64];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.flush();
        }
    }

    private void encodeAsHighQualityJpeg(Uri inUri, Uri outUri) throws Exception {
        Bitmap bitmap;
        try (InputStream in = getContentResolver().openInputStream(inUri)) {
            bitmap = BitmapFactory.decodeStream(in);
        }
        if (bitmap == null) throw new Exception("无法解码图片");

        try (OutputStream out = getContentResolver().openOutputStream(outUri, "w")) {
            if (out == null) throw new Exception("无法写入图片");
            boolean ok = bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
            if (!ok) throw new Exception("JPEG编码失败");
            out.flush();
        } finally {
            bitmap.recycle();
        }
    }

    private void writeSimulatedCameraExif(Uri uri) {
        ParcelFileDescriptor pfd = null;
        try {
            pfd = getContentResolver().openFileDescriptor(uri, "rw");
            if (pfd == null) return;

            FileDescriptor fd = pfd.getFileDescriptor();
            ExifInterface exif = new ExifInterface(fd);

            String exifTime = new SimpleDateFormat(
                    "yyyy:MM:dd HH:mm:ss", Locale.US).format(new Date());

            exif.setAttribute("Make", "Xiaomi");
            exif.setAttribute("Model", "Xiaomi 17T");
            exif.setAttribute("Software", "Camera");
            exif.setAttribute("DateTime", exifTime);
            exif.setAttribute("DateTimeOriginal", exifTime);
            exif.setAttribute("DateTimeDigitized", exifTime);
            exif.setAttribute("Orientation", "1");
            exif.setAttribute("ImageDescription",
                    "Simulated mobile-camera metadata; not an original camera capture.");

            exif.saveAttributes();
        } catch (Exception ignored) {
        } finally {
            try {
                if (pfd != null) pfd.close();
            } catch (Exception ignored) {}
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
