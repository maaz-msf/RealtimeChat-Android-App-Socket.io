package com.msf.youtubechatapp;

import static com.msf.youtubechatapp.UserListActivity.PRODUCTION_URL;
import static com.msf.youtubechatapp.UserListActivity.deviceId;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.msf.youtubechatapp.Adapters.ChatAdapter;
import com.msf.youtubechatapp.models.Message;
import com.squareup.picasso.Picasso;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import io.socket.client.IO;
import io.socket.client.Socket;

public class ChatActivity extends AppCompatActivity {

    Socket mSocket;
    EditText messageInput;
    Button sendButton;
    RecyclerView chatRecyclerView;
    TextView userNameTextView;
    TextView userStatusTextView;
    TextView userTypingTextView;
    ImageView profileImageView;
    boolean isAlreadyRegistered;
    String profileImageUrl;
    String recipientId;
    List<Message> messageList = new ArrayList<>();
    ChatAdapter chatAdapter;
    Handler typingHandler = new Handler();
    static final int TYPING_TIMER_LENGTH = 1000;
    boolean isTyping = false;


    private static final String TAG = "ChatActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        messageInput = findViewById(R.id.messageInput);
        sendButton = findViewById(R.id.sendButton);
        chatRecyclerView = findViewById(R.id.chatRecyclerView);
        userNameTextView = findViewById(R.id.userNameTextView);
        userStatusTextView = findViewById(R.id.userStatusTextView);
        userTypingTextView = findViewById(R.id.userTypingTextView);
        profileImageView = findViewById(R.id.profileImageView);
        recipientId = getIntent().getStringExtra("recipientId");

        chatRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        chatAdapter = new ChatAdapter(messageList, deviceId);
        chatRecyclerView.setAdapter(chatAdapter);

        try {
            mSocket = IO.socket(PRODUCTION_URL);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            return;
        }
        mSocket.connect();

        checkRegisterStatus();

        loadRecipientInfo(recipientId);

        monitorTypingStatus();

        sendButton.setOnClickListener(view -> {
            if (messageInput.getText().toString().isEmpty()) {
                Toast.makeText(ChatActivity.this, "Enter message", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                sendMessage(messageInput.getText().toString());
                messageInput.setText("");
            } catch (JSONException e) {
                e.printStackTrace();
            }
        });

        try {
            loadMessages();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    Runnable onTypingTimeout = new Runnable() {
        @Override
        public void run() {
            if (isTyping) {
                isTyping = false;
                if (recipientId != null) {
                    mSocket.emit("stop_typing", recipientId);
                }
            }
        }
    };


    private void checkRegisterStatus() {
        mSocket.emit("check_registration", deviceId);
        mSocket.on("registration_status", args -> runOnUiThread(() -> {
            JSONObject data = (JSONObject) args[0];
            try {
                isAlreadyRegistered = data.getBoolean("isRegistered");
                if (isAlreadyRegistered) {
                    String userName = data.getString("userName");
                    profileImageUrl = data.getString("profileImageUrl");
                    mSocket.emit("register", deviceId, userName, profileImageUrl);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }));

    }

    private void loadRecipientInfo(String recipientId) {
        mSocket.emit("get_user_info", recipientId);
        mSocket.on("user_info", args -> runOnUiThread(() -> {
            JSONObject data = (JSONObject) args[0];
            try {
                String userName = data.getString("username");
                String status = data.getString("status");
                profileImageUrl = data.getString("profile_image_url");
                userNameTextView.setText(userName);
                Picasso.get().load(profileImageUrl).into(profileImageView);
                userStatusTextView.setText(status.equals("online") ? "online" : "offline");
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }));
    }

    private void monitorTypingStatus() {
        messageInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if (!isTyping) {
                    mSocket.emit("typing", recipientId);
                    isTyping = true;
                }
                typingHandler.removeCallbacks(onTypingTimeout);
                typingHandler.postDelayed(onTypingTimeout, TYPING_TIMER_LENGTH);
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });


        mSocket.on("typing", args -> runOnUiThread(() -> {
            String typingUser = (String) args[0];
            if (typingUser.equals(recipientId)) {
                userTypingTextView.setVisibility(View.VISIBLE);
                userStatusTextView.setVisibility(View.GONE);
            }
        }));

        mSocket.on("stop_typing", args -> runOnUiThread(() -> {
            String typingUser = (String) args[0];
            if (typingUser.equals(recipientId)) {
                userTypingTextView.setVisibility(View.GONE);
                userStatusTextView.setVisibility(View.VISIBLE);
                loadRecipientStatus(recipientId);
            }
        }));


        mSocket.on("user_status_update", args -> runOnUiThread(() -> {
            JSONObject data = (JSONObject) args[0];
            try {
                String userId = data.getString("device_id");
                String status = data.getString("status");
                if (userId.equals(recipientId)) {
                    userStatusTextView.setText(status.equals("online") ? "online" : "offline");
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }

        }));
    }

    public void loadRecipientStatus(String recipientId) {
        mSocket.emit("get_user_status", recipientId);
        mSocket.on("user_status", args -> runOnUiThread(() -> {
            JSONObject data = (JSONObject) args[0];
            try {
                String status = data.getString("status");
                userStatusTextView.setText(status.equals("online") ? "online" : "offline");
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }));
    }


    @SuppressLint("NotifyDataSetChanged")
    public void sendMessage(String message) throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("recipient", recipientId);
        jsonObject.put("message", message);
        mSocket.emit("private_message", jsonObject);

        messageList.add(new Message(deviceId, message));
        chatAdapter.notifyDataSetChanged();
        mSocket.emit("stop_typing", recipientId);
    }

    @SuppressLint("NotifyDataSetChanged")
    public void loadMessages() throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("sender", deviceId);
        jsonObject.put("recipient", recipientId);
        mSocket.emit("load_messages", jsonObject);

        mSocket.on("load_messages", args -> runOnUiThread(() -> {
            JSONArray messages = (JSONArray) args[0];
            messageList.clear();
            for (int i = 0; i < messages.length(); i++) {
                try {
                    JSONObject userObject = messages.getJSONObject(i);
                    String sender = userObject.getString("sender_id");
                    String text = userObject.getString("message");
                    messageList.add(new Message(sender, text));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            chatAdapter.notifyDataSetChanged();
        }));

        mSocket.on("private_message", args -> runOnUiThread(() -> {
            JSONObject data = (JSONObject) args[0];
            try {
                String sender = data.getString("sender");
                String message = data.getString("message");
                messageList.add(new Message(sender, message));
                chatAdapter.notifyDataSetChanged();
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }));
    }

}