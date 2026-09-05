package com.bitchat.mesh;

import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ChatActivity extends AppCompatActivity implements ConnectionManager.MessageCallback {

    private ListView lvMessages;
    private EditText etMessage;
    private Button btnSend;
    private TextView tvChatTitle;

    private MessageAdapter messageAdapter;
    private List<Message> messageList;
    private ConnectionManager connectionManager;
    private String localDeviceId;
    private String localDeviceName;
    private Gson gson;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        localDeviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        localDeviceName = android.os.Build.MODEL;
        gson = new Gson();

        lvMessages = findViewById(R.id.lvMessages);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
        tvChatTitle = findViewById(R.id.tvChatTitle);

        messageList = new ArrayList<>();
        messageAdapter = new MessageAdapter(this, messageList);
        lvMessages.setAdapter(messageAdapter);

        // Get instance of ConnectionManager from MainActivity or recreate
        // For simplicity in this demo, we'll initialize a new one and share state
        // In a real app, this should be in a Service
        connectionManager = MainActivity.getConnectionManager();
        if (connectionManager != null) {
            connectionManager.setMessageCallback(this);
            updateTitle();
        } else {
            Toast.makeText(this, "Connection manager not initialized", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        btnSend.setOnClickListener(v -> sendMessage());
    }

    private void updateTitle() {
        int count = connectionManager.getConnectedEndpoints().size();
        tvChatTitle.setText("Mesh Chat (" + count + " connected)");
    }

    private void sendMessage() {
        String content = etMessage.getText().toString().trim();
        if (TextUtils.isEmpty(content)) return;

        String msgId = UUID.randomUUID().toString();
        Message msg = new Message(msgId, localDeviceId, localDeviceName, content, System.currentTimeMillis());
        msg.setMine(true);
        
        // Add to local UI
        addMessageToUI(msg);
        etMessage.setText("");

        // Prepare for sending
        Message networkMsg = new Message(msgId, localDeviceId, localDeviceName, content, msg.getTimestamp());
        String jsonMsg = gson.toJson(networkMsg);
        
        // Encrypt
        String encryptedJson = CryptoUtils.encrypt(jsonMsg);
        if (encryptedJson != null) {
            connectionManager.broadcastMessage(encryptedJson, msgId);
        } else {
            Toast.makeText(this, "Encryption failed", Toast.LENGTH_SHORT).show();
        }
    }

    private void addMessageToUI(Message msg) {
        runOnUiThread(() -> {
            messageList.add(msg);
            messageAdapter.notifyDataSetChanged();
            lvMessages.smoothScrollToPosition(messageList.size() - 1);
        });
    }

    @Override
    public void onMessageReceived(String encryptedJson) {
        // Decrypt
        String jsonMsg = CryptoUtils.decrypt(encryptedJson);
        if (jsonMsg == null) {
            runOnUiThread(() -> Toast.makeText(this, "Received unreadable message", Toast.LENGTH_SHORT).show());
            return;
        }

        try {
            Message msg = gson.fromJson(jsonMsg, Message.class);
            
            // Check if we've seen this message to prevent loops
            if (connectionManager.hasSeenMessage(msg.getId())) {
                return;
            }
            
            // Mark as seen
            connectionManager.markMessageAsSeen(msg.getId());
            
            // Increment hop count
            msg.incrementHop();
            
            // Show in UI
            msg.setMine(false);
            addMessageToUI(msg);
            
            // Relay to other nodes in mesh (Multi-hop)
            // Re-encrypt the message with updated hop count
            String updatedJson = gson.toJson(msg);
            String reEncrypted = CryptoUtils.encrypt(updatedJson);
            if (reEncrypted != null) {
                // In a real implementation, we'd know which endpoint sent this and exclude it
                // For simplicity here, we relay to all except sender
                connectionManager.relayMessage(reEncrypted, msg.getId(), null); 
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        if (connectionManager != null) {
            connectionManager.setMessageCallback(this);
            updateTitle();
        }
    }
}
