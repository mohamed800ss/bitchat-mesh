package com.bitchat.mesh;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements ConnectionManager.ConnectionCallback {

    private static final int REQUEST_CODE_PERMISSIONS = 1;

    private TextView tvStatus;
    private Button btnAdvertise, btnDiscover;
    private ListView lvDevices;
    
    private ArrayAdapter<String> devicesAdapter;
    private List<String> devicesList;
    private Map<String, String> endpointMap; // Name to ID mapping

    private static ConnectionManager connectionManager;
    private boolean isAdvertising = false;
    private boolean isDiscovering = false;

    public static ConnectionManager getConnectionManager() {
        return connectionManager;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tvStatus);
        btnAdvertise = findViewById(R.id.btnAdvertise);
        btnDiscover = findViewById(R.id.btnDiscover);
        lvDevices = findViewById(R.id.lvDevices);

        devicesList = new ArrayList<>();
        endpointMap = new HashMap<>();
        devicesAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, devicesList);
        lvDevices.setAdapter(devicesAdapter);

        String deviceName = Build.MODEL;
        connectionManager = new ConnectionManager(this, deviceName);
        connectionManager.setConnectionCallback(this);

        btnAdvertise.setOnClickListener(v -> {
            if (checkPermissions()) {
                if (isAdvertising) {
                    connectionManager.stopAll();
                    isAdvertising = false;
                    btnAdvertise.setText(R.string.advertise);
                    tvStatus.setText(R.string.status_disconnected);
                } else {
                    connectionManager.startAdvertising();
                    isAdvertising = true;
                    btnAdvertise.setText(R.string.stop);
                    tvStatus.setText("Advertising as " + deviceName);
                }
            }
        });

        btnDiscover.setOnClickListener(v -> {
            if (checkPermissions()) {
                if (isDiscovering) {
                    connectionManager.stopAll();
                    isDiscovering = false;
                    btnDiscover.setText(R.string.discover);
                    tvStatus.setText(R.string.status_disconnected);
                } else {
                    connectionManager.startDiscovery();
                    isDiscovering = true;
                    btnDiscover.setText(R.string.stop);
                    tvStatus.setText("Discovering...");
                }
            }
        });

        lvDevices.setOnItemClickListener((parent, view, position, id) -> {
            String displayName = devicesList.get(position);
            if (displayName.contains("(Connected)")) {
                // Open chat if already connected
                startActivity(new Intent(MainActivity.this, ChatActivity.class));
            } else {
                // Extract name and connect
                String name = displayName.replace(" (Discovered)", "");
                String endpointId = endpointMap.get(name);
                if (endpointId != null) {
                    tvStatus.setText(R.string.status_connecting);
                    connectionManager.connectToEndpoint(endpointId);
                }
            }
        });
        
        // Add a button to jump to chat directly if we have connections
        Button btnChat = new Button(this);
        btnChat.setText("Open Chat");
        btnChat.setOnClickListener(v -> {
            if (connectionManager.getConnectedEndpoints().size() > 0) {
                startActivity(new Intent(MainActivity.this, ChatActivity.class));
            } else {
                Toast.makeText(this, "Connect to at least one device first", Toast.LENGTH_SHORT).show();
            }
        });
        ((android.widget.LinearLayout)findViewById(R.id.lvDevices).getParent()).addView(btnChat, 2);
    }

    private boolean checkPermissions() {
        List<String> requiredPermissions = new ArrayList<>();
        
        requiredPermissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        requiredPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requiredPermissions.add(Manifest.permission.BLUETOOTH_SCAN);
            requiredPermissions.add(Manifest.permission.BLUETOOTH_ADVERTISE);
            requiredPermissions.add(Manifest.permission.BLUETOOTH_CONNECT);
        } else {
            requiredPermissions.add(Manifest.permission.BLUETOOTH);
            requiredPermissions.add(Manifest.permission.BLUETOOTH_ADMIN);
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requiredPermissions.add(Manifest.permission.NEARBY_WIFI_DEVICES);
        }

        List<String> missingPermissions = new ArrayList<>();
        for (String permission : requiredPermissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permission);
            }
        }

        if (!missingPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toArray(new String[0]), REQUEST_CODE_PERMISSIONS);
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (!allGranted) {
                Toast.makeText(this, "Permissions required for Mesh Network", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onEndpointDiscovered(String endpointId, String endpointName) {
        runOnUiThread(() -> {
            String displayName = endpointName + " (Discovered)";
            if (!devicesList.contains(displayName) && !devicesList.contains(endpointName + " (Connected)")) {
                devicesList.add(displayName);
                endpointMap.put(endpointName, endpointId);
                devicesAdapter.notifyDataSetChanged();
            }
        });
    }

    @Override
    public void onEndpointConnected(String endpointId, String endpointName) {
        runOnUiThread(() -> {
            tvStatus.setText(R.string.status_connected);
            
            // Update list
            String discoveredName = endpointName + " (Discovered)";
            String connectedName = endpointName + " (Connected)";
            
            if (devicesList.contains(discoveredName)) {
                devicesList.remove(discoveredName);
            }
            if (!devicesList.contains(connectedName)) {
                devicesList.add(connectedName);
            }
            devicesAdapter.notifyDataSetChanged();
            
            Toast.makeText(this, "Connected to " + endpointName, Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onEndpointDisconnected(String endpointId) {
        runOnUiThread(() -> {
            // Find and remove the disconnected device
            String nameToRemove = null;
            for (Map.Entry<String, String> entry : endpointMap.entrySet()) {
                if (entry.getValue().equals(endpointId)) {
                    nameToRemove = entry.getKey();
                    break;
                }
            }
            
            if (nameToRemove != null) {
                devicesList.remove(nameToRemove + " (Connected)");
                devicesAdapter.notifyDataSetChanged();
                Toast.makeText(this, "Disconnected from " + nameToRemove, Toast.LENGTH_SHORT).show();
            }
            
            if (connectionManager.getConnectedEndpoints().isEmpty()) {
                tvStatus.setText(isAdvertising ? "Advertising..." : (isDiscovering ? "Discovering..." : getString(R.string.status_disconnected)));
            }
        });
    }

    @Override
    public void onError(String message) {
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (connectionManager != null) {
            connectionManager.stopAll();
        }
    }
}
