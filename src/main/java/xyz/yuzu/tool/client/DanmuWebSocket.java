package xyz.yuzu.tool.client;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;

class DanmuWebSocket extends WebSocketClient {
    
    private final DanmuClient client;
    
    public DanmuWebSocket(String url, DanmuClient client) throws URISyntaxException {
        super(new URI(url));
        this.client = client;
    }
    
    @Override
    public void onOpen(ServerHandshake handshakedata) {
    }
    
    @Override
    public void onMessage(String message) {
    }
    
    @Override
    public void onMessage(ByteBuffer message) {
        client.onMessage(this, message);
    }
    
    @Override
    public void onClose(int code, String reason, boolean remote) {
        client.onClose(this, code);
    }
    
    @Override
    public void onError(Exception ex) {
        // Some WebSocket errors don't trigger onClose but mean the connection is dead
        // We'll call onClose explicitly if it's a critical error, but usually
        // the WebSocket library will call onClose after onError anyway.
        client.onError(this, ex);
    }
}
