package com.example.photoviewer;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    ImageView imgView;
    TextView textView;
    String site_url = "http://10.0.2.2:8000";
    String token = "06aa732c414805ecd1ccd35d9edea355e923bf0e";
    JSONObject post_json;
    String imageUrl = null;
    Bitmap bmImg = null;
    CloadImage taskDownload;
    PutPost taskUpload;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        //imgView = (ImageView) findViewById(R.id.imgView);
        textView = (TextView) findViewById(R.id.textView);
    }

    public void onClickDownload(View v) {
        if (taskDownload != null && taskDownload.getStatus() == AsyncTask.Status.RUNNING) {
            taskDownload.cancel(true);
        }
        taskDownload = new CloadImage();
        taskDownload.execute(site_url + "/api_root/Post/");
        Toast.makeText(getApplicationContext(), "Download", Toast.LENGTH_LONG).show();
    }

    public void onClickUpload(View v) {
        if (taskUpload != null && taskUpload.getStatus() == AsyncTask.Status.RUNNING) {
            Toast.makeText(getApplicationContext(), "업로드 중입니다", Toast.LENGTH_SHORT).show();
            return;
        }
        taskUpload = new PutPost();
        taskUpload.execute(site_url + "/api_root/Post/");
        Toast.makeText(getApplicationContext(), "Upload", Toast.LENGTH_LONG).show();
    }

    private class CloadImage extends AsyncTask<String, Integer, List<Bitmap>> {
        @Override
        protected List<Bitmap> doInBackground(String... urls) {
            List<Bitmap> bitmapList = new ArrayList<>();
            try {
                String apiUrl = urls[0];
                URL urlAPI = new URL(apiUrl);
                HttpURLConnection conn = (HttpURLConnection) urlAPI.openConnection();
                conn.setRequestProperty("Authorization", "Token " + token);
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);

                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    InputStream is = conn.getInputStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                    StringBuilder result = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        result.append(line);
                    }
                    is.close();

                    String strJson = result.toString();
                    JSONArray aryJson = new JSONArray(strJson);
                    // 배열 내 모든 이미지 다운로드
                    for (int i = 0; i < aryJson.length(); i++) {
                        post_json = (JSONObject) aryJson.get(i);
                        imageUrl = post_json.getString("image");
                        if (!imageUrl.equals("")) {
                            URL myImageUrl = new URL(imageUrl);
                            conn = (HttpURLConnection) myImageUrl.openConnection();
                            InputStream imgStream = conn.getInputStream();
                            Bitmap imageBitmap = decodeSampledBitmap(readAllBytes(imgStream), 1080);
                            bitmapList.add(imageBitmap); // 이미지 리스트에 추가
                            imgStream.close();
                        }
                    }
                }
            } catch (IOException | JSONException e) {
                e.printStackTrace();
            }
            return bitmapList;
        }

        @Override
        protected void onPostExecute(List<Bitmap> images) {
            if (images.isEmpty()) {
                textView.setText("불러올 이미지가 없습니다.");
            } else {
                textView.setText("이미지 로드 성공!");
                RecyclerView recyclerView = findViewById(R.id.recyclerView);
                ImageAdapter adapter = new ImageAdapter(images);
                recyclerView.setLayoutManager(new LinearLayoutManager(MainActivity.this));
                recyclerView.setAdapter(adapter);
            }
        }
    }

    // Upload (Hard coding): 제목, 본문, 이미지를 코드에 고정해 두고 multipart/form-data로 POST
    private class PutPost extends AsyncTask<String, Void, Integer> {
        @Override
        protected Integer doInBackground(String... urls) {
            String boundary = "----PhotoViewerBoundary" + System.currentTimeMillis();
            Date now = new Date();
            String date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(now);
            String time = new SimpleDateFormat("HH:mm:ss", Locale.US).format(now);

            try {
                URL url = new URL(urls[0]);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(10000);
                conn.setRequestProperty("Authorization", "Token " + token);
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

                DataOutputStream out = new DataOutputStream(conn.getOutputStream());
                writeTextPart(out, boundary, "author", "1");
                writeTextPart(out, boundary, "title", "Android 업로드 테스트 " + time);
                writeTextPart(out, boundary, "text", "PhotoViewer 앱에서 하드코딩으로 업로드한 게시물입니다.");
                writeTextPart(out, boundary, "created_date", date);
                writeTextPart(out, boundary, "published_date", date);
                writeFilePart(out, boundary, "image", "upload_sample.jpg", "image/jpeg",
                        readRawResource(R.raw.upload_sample));
                out.writeBytes("--" + boundary + "--\r\n");
                out.flush();
                out.close();

                int responseCode = conn.getResponseCode();
                conn.disconnect();
                return responseCode;
            } catch (IOException e) {
                e.printStackTrace();
                return -1;
            }
        }

        @Override
        protected void onPostExecute(Integer responseCode) {
            if (responseCode == HttpURLConnection.HTTP_CREATED) {
                textView.setText("업로드 성공! (HTTP 201) 동기화를 눌러 확인하세요.");
                Toast.makeText(getApplicationContext(), "업로드 성공", Toast.LENGTH_LONG).show();
            } else {
                textView.setText("업로드 실패 (응답 코드: " + responseCode + ")");
                Toast.makeText(getApplicationContext(), "업로드 실패", Toast.LENGTH_LONG).show();
            }
        }
    }

    private static void writeTextPart(DataOutputStream out, String boundary, String name, String value)
            throws IOException {
        out.writeBytes("--" + boundary + "\r\n");
        out.writeBytes("Content-Disposition: form-data; name=\"" + name + "\"\r\n");
        out.writeBytes("\r\n");
        out.write(value.getBytes(StandardCharsets.UTF_8)); // 한글 제목·본문은 UTF-8로 전송
        out.writeBytes("\r\n");
    }

    private static void writeFilePart(DataOutputStream out, String boundary, String name,
                                      String fileName, String contentType, byte[] data) throws IOException {
        out.writeBytes("--" + boundary + "\r\n");
        out.writeBytes("Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + fileName + "\"\r\n");
        out.writeBytes("Content-Type: " + contentType + "\r\n");
        out.writeBytes("\r\n");
        out.write(data);
        out.writeBytes("\r\n");
    }

    private byte[] readRawResource(int resId) throws IOException {
        try (InputStream is = getResources().openRawResource(resId)) {
            return readAllBytes(is);
        }
    }

    private static byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int n;
        while ((n = is.read(chunk)) != -1) {
            buffer.write(chunk, 0, n);
        }
        return buffer.toByteArray();
    }

    // 큰 사진(예: 4000x3000)을 원본 크기로 읽으면 한 장에 수십 MB라 메모리가 부족해지므로 화면 폭에 맞게 줄여서 읽음
    private static Bitmap decodeSampledBitmap(byte[] data, int reqWidth) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, options);

        int inSampleSize = 1;
        while (options.outWidth / (inSampleSize * 2) >= reqWidth) {
            inSampleSize *= 2;
        }
        options.inJustDecodeBounds = false;
        options.inSampleSize = inSampleSize;
        return rotateByExif(data, BitmapFactory.decodeByteArray(data, 0, data.length, options));
    }

    // 스마트폰 사진은 회전 정보(EXIF)만 기록해 두는 경우가 많아, BitmapFactory로 읽으면 옆으로 누워 보이므로 바로 세움
    private static Bitmap rotateByExif(byte[] data, Bitmap bitmap) {
        if (bitmap == null) {
            return null;
        }
        int degrees;
        try (InputStream is = new ByteArrayInputStream(data)) {
            ExifInterface exif = new ExifInterface(is);
            switch (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    degrees = 90;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    degrees = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    degrees = 270;
                    break;
                default:
                    return bitmap;
            }
        } catch (IOException e) {
            return bitmap;
        }
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }
}
