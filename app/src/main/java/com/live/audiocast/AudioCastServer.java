package com.live.audiocast;

import android.util.Log;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AudioCastServer extends WebSocketServer {
    private final String TAG = "AudioCast";

    private Map<String, WebSocket> clients = new ConcurrentHashMap<>();

    public AudioCastServer(int port)
    {
        super(new InetSocketAddress(port));
    }

    public AudioCastServer(InetSocketAddress address)
    {
        super(address);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        String uniqueID = UUID.randomUUID().toString();
        clients.put(uniqueID, conn);

        conn.send("Welcome to the server!");


        broadcast("broadcast message"); //This method sends a message to all clients connected
        Log.d(TAG, "onOpen");
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        Log.d(TAG, "onClose");
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        Log.d(TAG, message);
    }

    @Override
    public void onMessage(WebSocket conn, ByteBuffer message) {
        broadcast(message.array());
    }

    @Override
    public void onError(WebSocket conn, Exception ex)
    {
        ex.printStackTrace();
        if (conn != null) {
            // some errors like port binding failed may not be assignable to a specific websocket
        }
    }

    @Override
    public void onStart()
    {
        Log.d(TAG, "onStart");

        setConnectionLostTimeout(0);
        setConnectionLostTimeout(100);
    }
}
