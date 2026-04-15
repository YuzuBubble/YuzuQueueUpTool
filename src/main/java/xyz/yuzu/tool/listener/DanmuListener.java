package xyz.yuzu.tool.listener;

public interface DanmuListener {
    
    void onDanmu(long uid, String username, String message, String medal);
    
    void onGift(String username, String giftName, int num);
    
    void onEnter(String username);
    
    void onConnect(String anchorName, long roomId);
    
    void onDisconnect();
    
    void onError(String error);
}
