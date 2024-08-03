package com.msf.youtubechatapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.msf.youtubechatapp.Adapters.UserAdapter;
import com.msf.youtubechatapp.models.User;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.socket.client.IO;
import io.socket.client.Socket;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class UserListActivity extends AppCompatActivity implements UserAdapter.OnUserClickListener {


    public static final int PICK_IMAGE_REQUEST = 1;
    public static final int STORAGE_PERMISSION_CODE = 101;

    Socket mSocket;
    RecyclerView recyclerView;
    Button selectImageButton, registerButton;
    EditText nameEdt;
    ImageView imageView;

    List<User> usersList = new ArrayList<>();
    Map<String, Integer> unreadMessagesCount = new HashMap<>();
    UserAdapter userAdapter;
    static String deviceId;
    String profileImageUrl;

    static String deviceIP = "192.168.1.128";
    static String PORT = ":3000";
    static String PRODUCTION_URL = "https://realtimechat-api-nodejs-socket-io.onrender.com/";
    boolean isAlreadyRegistered;
    private static final String TAG = "UserListActivity";

    @SuppressLint({"HardwareIds", "NotifyDataSetChanged"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recyclerView = findViewById(R.id.recyclerView);
        nameEdt = findViewById(R.id.nameedt);
        imageView = findViewById(R.id.profile_image);
        selectImageButton = findViewById(R.id.selectImageButton);
        registerButton = findViewById(R.id.registerButton);

        deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        userAdapter = new UserAdapter(usersList, unreadMessagesCount, this);
        recyclerView.setAdapter(userAdapter);

        try {
            mSocket = IO.socket(PRODUCTION_URL);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            return;
        }
        mSocket.connect();
        checkRegisterStatus();

        registerButton.setOnClickListener(view -> {
            if (profileImageUrl == null) {
                Toast.makeText(UserListActivity.this, "Please select profile Picture", Toast.LENGTH_SHORT).show();
                return;
            }
            if (nameEdt.getText().toString().isEmpty()) {
                Toast.makeText(UserListActivity.this, "please enter name ", Toast.LENGTH_SHORT).show();
                return;
            }
            mSocket.emit("register", deviceId, nameEdt.getText().toString(), profileImageUrl);
            mSocket.emit("load_users");

            selectImageButton.setVisibility(View.GONE);
            registerButton.setVisibility(View.GONE);
            nameEdt.setVisibility(View.GONE);
            imageView.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        });

        selectImageButton.setOnClickListener(view -> {
            if (checkPermission()) {
                openFileChooser();
            }
        });

        mSocket.on("users", args -> runOnUiThread(() -> {
            JSONArray userArray = (JSONArray) args[0];
            usersList.clear();
            for (int i = 0; i < userArray.length(); i++) {
                try {
                    JSONObject userObject = userArray.getJSONObject(i);
                    String userId = userObject.getString("device_id");
                    String status = userObject.getString("status");
                    String username = userObject.getString("username");
                    String profileImageUrl = userObject.getString("profile_image_url");
                    if (!userId.equals(deviceId)) {
                        usersList.add(new User(userId, username, status, profileImageUrl));
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            userAdapter.notifyDataSetChanged();
        }));

        monitorPendingMessagesCounts();
    }


    public void openFileChooser() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }


    private void checkRegisterStatus() {
        mSocket.emit("check_registration", deviceId);
        mSocket.on("registration_status", args -> runOnUiThread(() -> {
            JSONObject data = (JSONObject) args[0];
            try {
                isAlreadyRegistered = data.getBoolean("isRegistered");
                Log.e(TAG, "Reg value : " + isAlreadyRegistered);
                if (!isAlreadyRegistered) {
                    selectImageButton.setVisibility(View.VISIBLE);
                    registerButton.setVisibility(View.VISIBLE);
                    nameEdt.setVisibility(View.VISIBLE);
                    imageView.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                } else {
                    selectImageButton.setVisibility(View.GONE);
                    registerButton.setVisibility(View.GONE);
                    nameEdt.setVisibility(View.GONE);
                    imageView.setVisibility(View.GONE);
                    String userName = data.getString("userName");
                    profileImageUrl = data.getString("profileImageUrl");
                    mSocket.emit("register", deviceId, userName, profileImageUrl);
                    recyclerView.setVisibility(View.VISIBLE);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }));

    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri imageUri = data.getData();
            String imagePath = getPathFromUri(imageUri);
            uploadImage(imagePath);
        }
    }

    private String getPathFromUri(Uri uri) {
        String[] projection = {MediaStore.Images.Media.DATA};
        Cursor cursor = getContentResolver().query(uri, projection, null, null, null);
        if (cursor != null) {
            cursor.moveToFirst();
            int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            String path = cursor.getString(columnIndex);
            cursor.close();
            return path;
        }
        return null;
    }

    private void uploadImage(String imagePath) {
        OkHttpClient client = new OkHttpClient();
        File file = new File(imagePath);
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("profile_image", file.getName(), RequestBody.create(MediaType.parse("image/*"), file))
                .build();

        Request request = new Request.Builder()
                .url(PRODUCTION_URL + "upload_profile_image")
                .post(requestBody)
                .build();


        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> {
                    Toast.makeText(UserListActivity.this, "Image upload failed", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                runOnUiThread(() -> {
                    Toast.makeText(UserListActivity.this, "Image uploaded successfully", Toast.LENGTH_SHORT).show();
                });
                assert response.body() != null;
                String responseData = response.body().string();
                JSONObject jsonObject;
                try {
                    jsonObject = new JSONObject(responseData);
                    profileImageUrl = jsonObject.getString("imageUrl");
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private boolean checkPermission() {
        if (ContextCompat.checkSelfPermission(UserListActivity.this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(UserListActivity.this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, UserListActivity.STORAGE_PERMISSION_CODE);
            return false;
        } else {
            return true;
        }
    }


    @SuppressLint("NotifyDataSetChanged")
    public void monitorPendingMessagesCounts() {
        mSocket.on("private_message", args -> runOnUiThread(() -> {
            JSONObject data = (JSONObject) args[0];
            try {
                String sender = data.getString("sender");
                if (unreadMessagesCount.containsKey(sender)) {
                    unreadMessagesCount.put(sender, unreadMessagesCount.get(sender) + 1);
                } else {
                    unreadMessagesCount.put(sender, 1);
                }
                userAdapter.notifyDataSetChanged();
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }));
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openFileChooser();
            } else {
                Toast.makeText(UserListActivity.this, "Permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mSocket.disconnect();
    }

    @Override
    public void onUserClick(String userId) {
        unreadMessagesCount.put(userId, 0);
        Intent intent = new Intent(UserListActivity.this, ChatActivity.class);
        intent.putExtra("recipientId", userId);
        startActivity(intent);
    }
}