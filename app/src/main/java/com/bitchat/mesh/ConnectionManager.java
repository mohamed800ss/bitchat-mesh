package com.bitchat.mesh;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AdvertisingOptions;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionsClient;
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo;
import com.google.android.gms.nearby.connection.DiscoveryOptions;
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.Strategy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ConnectionManager {

    private static final String TAG = "ConnectionManager";
    private static final String SERVICE_ID = "com.bitchat.mesh.SERVICE_ID";
    // P2P_CLUSTER supports M-to-N topologies (Mesh)
    private static final Strategy STRATEGY = Strategy.P2P_CLUSTER;

    private Context context;
    private ConnectionsClient connectionsClient;
    private String localEndpointName;
    
    private ConnectionCallback connectionCallback;
    private MessageCallback messageCallback;
    
    // Connected endpoints
    private Set<String> connectedEndpoints = new HashSet<>();
    // Discovered endpoints (not yet connected)
    private Map<String, String> discoveredEndpoints = new HashMap<>();
    // Cache of seen message IDs to avoid infinite loops in mesh routing
    private Set<String> seenMessages = new HashSet<>();

    public interface ConnectionCallback {
        void onEndpointDiscovered(String endpointId, String endpointName);
        void onEndpointConnected(String endpointId, String endpointName);
        void onEndpointDisconnected(String endpointId);
        void onError(String message);
    }

    public interface MessageCallback {
        void onMessageReceived(String encryptedJson);
    }

    public ConnectionManager(Context context, String localName) {
        this.context = context;
        this.localEndpointName = localName;
        this.connectionsClient = Nearby.getConnectionsClient(context);
    }

    public void setConnectionCallback(ConnectionCallback callback) {
        this.connectionCallback = callback;
    }

    public void setMessageCallback(MessageCallback callback) {
        this.messageCallback = callback;
    }

    public void startAdvertising() {
        AdvertisingOptions advertisingOptions = new AdvertisingOptions.Builder().setStrategy(STRATEGY).build();
        connectionsClient.startAdvertising(localEndpointName, SERVICE_ID, connectionLifecycleCallback, advertisingOptions)
                .addOnSuccessListener(unusedResult -> Log.d(TAG, "Advertising started"))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Advertising failed: " + e.getMessage());
                    if (connectionCallback != null) connectionCallback.onError("Advertising failed: " + e.getMessage());
                });
    }

    public void startDiscovery() {
        DiscoveryOptions discoveryOptions = new DiscoveryOptions.Builder().setStrategy(STRATEGY).build();
        connectionsClient.startDiscovery(SERVICE_ID, endpointDiscoveryCallback, discoveryOptions)
                .addOnSuccessListener(unusedResult -> Log.d(TAG, "Discovery started"))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Discovery failed: " + e.getMessage());
                    if (connectionCallback != null) connectionCallback.onError("Discovery failed: " + e.getMessage());
                });
    }

    public void stopAll() {
        connectionsClient.stopAdvertising();
        connectionsClient.stopDiscovery();
        connectionsClient.stopAllEndpoints();
        connectedEndpoints.clear();
        discoveredEndpoints.clear();
    }

    public void connectToEndpoint(String endpointId) {
        connectionsClient.requestConnection(localEndpointName, endpointId, connectionLifecycleCallback)
                .addOnSuccessListener(unusedResult -> Log.d(TAG, "Connection requested to " + endpointId))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Connection request failed: " + e.getMessage());
                    if (connectionCallback != null) connectionCallback.onError("Connection request failed");
                });
    }

    public void broadcastMessage(String encryptedJson, String messageId) {
        // Remember we sent this to avoid bouncing back
        seenMessages.add(messageId);
        
        if (connectedEndpoints.isEmpty()) return;
        
        Payload payload = Payload.fromBytes(encryptedJson.getBytes());
        connectionsClient.sendPayload(new ArrayList<>(connectedEndpoints), payload);
    }
    
    public void relayMessage(String encryptedJson, String messageId, String excludeEndpointId) {
        if (seenMessages.contains(messageId)) return;
        seenMessages.add(messageId);
        
        List<String> endpointsToRelay = new ArrayList<>();
        for (String endpoint : connectedEndpoints) {
            if (!endpoint.equals(excludeEndpointId)) {
                endpointsToRelay.add(endpoint);
            }
        }
        
        if (!endpointsToRelay.isEmpty()) {
            Payload payload = Payload.fromBytes(encryptedJson.getBytes());
            connectionsClient.sendPayload(endpointsToRelay, payload);
        }
    }
    
    public boolean hasSeenMessage(String messageId) {
        return seenMessages.contains(messageId);
    }
    
    public void markMessageAsSeen(String messageId) {
        seenMessages.add(messageId);
    }
    
    public List<String> getConnectedEndpoints() {
        return new ArrayList<>(connectedEndpoints);
    }

    private final ConnectionLifecycleCallback connectionLifecycleCallback = new ConnectionLifecycleCallback() {
        @Override
        public void onConnectionInitiated(@NonNull String endpointId, @NonNull ConnectionInfo connectionInfo) {
            Log.d(TAG, "Connection initiated with " + connectionInfo.getEndpointName());
            // Automatically accept connection for seamless mesh
            connectionsClient.acceptConnection(endpointId, payloadCallback);
        }

        @Override
        public void onConnectionResult(@NonNull String endpointId, @NonNull ConnectionResolution result) {
            if (result.getStatus().isSuccess()) {
                Log.d(TAG, "Connected to " + endpointId);
                connectedEndpoints.add(endpointId);
                if (connectionCallback != null) {
                    // Try to get name from discovered list, or use ID
                    String name = discoveredEndpoints.containsKey(endpointId) ? discoveredEndpoints.get(endpointId) : "Device " + endpointId.substring(0, 4);
                    connectionCallback.onEndpointConnected(endpointId, name);
                }
            } else {
                Log.e(TAG, "Connection failed to " + endpointId);
            }
        }

        @Override
        public void onDisconnected(@NonNull String endpointId) {
            Log.d(TAG, "Disconnected from " + endpointId);
            connectedEndpoints.remove(endpointId);
            if (connectionCallback != null) {
                connectionCallback.onEndpointDisconnected(endpointId);
            }
        }
    };

    private final EndpointDiscoveryCallback endpointDiscoveryCallback = new EndpointDiscoveryCallback() {
        @Override
        public void onEndpointFound(@NonNull String endpointId, @NonNull DiscoveredEndpointInfo info) {
            Log.d(TAG, "Endpoint found: " + info.getEndpointName());
            discoveredEndpoints.put(endpointId, info.getEndpointName());
            if (connectionCallback != null) {
                connectionCallback.onEndpointDiscovered(endpointId, info.getEndpointName());
            }
        }

        @Override
        public void onEndpointLost(@NonNull String endpointId) {
            Log.d(TAG, "Endpoint lost: " + endpointId);
            discoveredEndpoints.remove(endpointId);
        }
    };

    private final PayloadCallback payloadCallback = new PayloadCallback() {
        @Override
        public void onPayloadReceived(@NonNull String endpointId, @NonNull Payload payload) {
            if (payload.getType() == Payload.Type.BYTES) {
                byte[] receivedBytes = payload.asBytes();
                if (receivedBytes != null) {
                    String encryptedJson = new String(receivedBytes);
                    if (messageCallback != null) {
                        messageCallback.onMessageReceived(encryptedJson);
                    }
                }
            }
        }

        @Override
        public void onPayloadTransferUpdate(@NonNull String endpointId, @NonNull PayloadTransferUpdate update) {
            // Can be used for progress of file transfers
        }
    };
}
