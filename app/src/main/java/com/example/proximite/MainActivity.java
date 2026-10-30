package com.example.proximite;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String SERVICE_ID = "com.example.proximite";
    private static final String CHANNEL_ID = "alertes";
    private final String myName = "u" + (int) (Math.random() * 100000);
    private final List<String> endpoints = new ArrayList<>();
    private ConnectionsClient client;
    private TextView status;
    private EditText input;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        status = findViewById(R.id.status);
        input = findViewById(R.id.input);
        Button send = findViewById(R.id.send);
        client = Nearby.getConnectionsClient(this);

        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "Alertes", NotificationManager.IMPORTANCE_HIGH);
        getSystemService(NotificationManager.class).createNotificationChannel(ch);

        send.setOnClickListener(v -> sendAlert());
        ActivityCompat.requestPermissions(this, permissions(), 1);
    }

    private String[] permissions() {
        List<String> p = new ArrayList<>();
        p.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (Build.VERSION.SDK_INT >= 31) {
            p.add(Manifest.permission.BLUETOOTH_ADVERTISE);
            p.add(Manifest.permission.BLUETOOTH_CONNECT);
            p.add(Manifest.permission.BLUETOOTH_SCAN);
        }
        if (Build.VERSION.SDK_INT >= 33) {
            p.add(Manifest.permission.NEARBY_WIFI_DEVICES);
            p.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        return p.toArray(new String[0]);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        startNearby();
    }

    private void startNearby() {
        AdvertisingOptions a = new AdvertisingOptions.Builder()
                .setStrategy(Strategy.P2P_CLUSTER).build();
        client.startAdvertising(myName, SERVICE_ID, lifecycle, a)
                .addOnFailureListener(e -> status.setText("Erreur : " + e.getMessage()));
        DiscoveryOptions d = new DiscoveryOptions.Builder()
                .setStrategy(Strategy.P2P_CLUSTER).build();
        client.startDiscovery(SERVICE_ID, discovery, d)
                .addOnFailureListener(e -> status.setText("Erreur : " + e.getMessage()));
        status.setText("Recherche d'appareils proches...");
    }

    private void sendAlert() {
        String msg = input.getText().toString().trim();
        if (msg.isEmpty()) msg = "Alerte !";
        if (endpoints.isEmpty()) {
            status.setText("Aucun appareil proche pour l'instant.");
            return;
        }
        client.sendPayload(new ArrayList<>(endpoints),
                Payload.fromBytes(msg.getBytes(StandardCharsets.UTF_8)));
        status.setText("Message envoyé à " + endpoints.size() + " appareil(s).");
    }

    private void showNotification(String text) {
        Notification n = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Message à proximité")
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build();
        try {
            NotificationManagerCompat.from(this)
                    .notify((int) System.currentTimeMillis(), n);
        } catch (SecurityException ignored) { }
    }

    private void updateStatus() {
        status.setText("Appareils connectés : " + endpoints.size());
    }

    private final PayloadCallback payloads = new PayloadCallback() {
        @Override
        public void onPayloadReceived(String id, Payload p) {
            if (p.getType() == Payload.Type.BYTES) {
                showNotification(new String(p.asBytes(), StandardCharsets.UTF_8));
            }
        }
        @Override
        public void onPayloadTransferUpdate(String id, PayloadTransferUpdate u) { }
    };

    private final ConnectionLifecycleCallback lifecycle = new ConnectionLifecycleCallback() {
        @Override
        public void onConnectionInitiated(String id, ConnectionInfo info) {
            client.acceptConnection(id, payloads);
        }
        @Override
        public void onConnectionResult(String id, ConnectionResolution r) {
            if (r.getStatus().isSuccess()) {
                endpoints.add(id);
                updateStatus();
            }
        }
        @Override
        public void onDisconnected(String id) {
            endpoints.remove(id);
            updateStatus();
        }
    };

    private final EndpointDiscoveryCallback discovery = new EndpointDiscoveryCallback() {
        @Override
        public void onEndpointFound(String id, DiscoveredEndpointInfo info) {
            if (myName.compareTo(info.getEndpointName()) > 0) {
                client.requestConnection(myName, id, lifecycle);
            }
        }
        @Override
        public void onEndpointLost(String id) { }
    };
}
