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
        client.onMessage(message);
    }
    
    @Override
    public void onClose(int code, String reason, boolean remote) {
        client.onClose(code);
    }
    
    @Override
    public void onError(Exception ex) {
        client.onError(ex);
    }
}
