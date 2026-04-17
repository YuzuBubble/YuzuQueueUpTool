package xyz.yuzu.tool.client;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import xyz.yuzu.tool.entity.*;
import xyz.yuzu.tool.http.BilibiliApi;
import xyz.yuzu.tool.listener.DanmuListener;
import xyz.yuzu.tool.utils.ByteUtils;
import xyz.yuzu.tool.utils.HexUtils;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class DanmuClient {
    
    private static final int PACKAGE_HEAD_LENGTH = 16;
    private static final int AUTH_PACKAGE_VERSION = 1;
    private static final int FIRST_PACKAGE_TYPE = 7;
    private static final int PACKAGE_OTHER = 1;
    private static final String HEART_BEAT = "0000001f0010000100000002000000015b6f626a656374204f626a6563745d";
    
    private DanmuWebSocket webSocket;
    private DanmuListener danmuListener;
    private final CopyOnWriteArrayList<String> messageQueue = new CopyOnWriteArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread parseThread;
    private Thread heartBeatThread;
    
    private volatile boolean hasAuthResponded = false;
    private volatile boolean isAuthSuccess = false;
    private volatile boolean isManualDisconnect = false;
    private volatile boolean hasSuccessfullyConnected = false;
    private final java.util.concurrent.atomic.AtomicBoolean isReconnecting = new java.util.concurrent.atomic.AtomicBoolean(false);
    
    private long roomId;
    private long realRoomId;
    private long anchorUid;
    private String anchorName;
    
    // 缓存认证信息用于快速重连
    private List<HostServer> cachedHostList;
    private byte[] cachedAuthBytes;
    private long cacheTime = 0;
    
    public void setDanmuListener(DanmuListener listener) {
        this.danmuListener = listener;
    }
    
    public void connect(long roomId) throws Exception {
        this.isManualDisconnect = false;
        this.hasSuccessfullyConnected = false;
        this.roomId = roomId;
        
        RoomInit roomInit = BilibiliApi.getRoomInit(roomId);
        if (roomInit == null || roomInit.getRoom_id() == null) {
            throw new Exception("无法获取房间信息");
        }
        
        this.realRoomId = roomInit.getRoom_id();
        this.anchorUid = roomInit.getUid();
        
        this.anchorName = BilibiliApi.getAnchorName(realRoomId);
        
        UserNav userNav = BilibiliApi.getUserNav();
        if (userNav == null || userNav.getWbiImg() == null) {
            throw new Exception("无法获取Wbi签名密钥");
        }
        
        Long userUid = userNav.getMid();
        
        Conf conf = BilibiliApi.getDanmuConfWithSign(realRoomId, userNav);
        if (conf == null) {
            throw new Exception("无法获取弹幕服务器配置");
        }
        
        List<HostServer> hostList = conf.getHost_list();
        if (hostList == null || hostList.isEmpty()) {
            throw new Exception("没有可用的弹幕服务器节点");
        }
        
        FristSecurityData authData;
        if (userUid != null && userUid > 0) {
            authData = new FristSecurityData(userUid, realRoomId, conf.getToken());
        } else {
            authData = new FristSecurityData(realRoomId, conf.getToken());
            authData.setBuvid(UUID.randomUUID().toString() + "infoc");
        }
        byte[] authBytes = buildAuthPackage(authData);
        byte[] heartBeatBytes = HexUtils.fromHexString(HEART_BEAT);
        
        // 保存到缓存用于快速重连
        this.cachedHostList = hostList;
        this.cachedAuthBytes = authBytes;
        this.cacheTime = System.currentTimeMillis();
        
        Exception lastException = null;
        boolean authSuccess = false;
        
        // 尝试打乱节点顺序，然后依次尝试连接，最多尝试3个不同的节点
        java.util.Collections.shuffle(hostList);
        int retryCount = Math.min(3, hostList.size());
        
        for (int i = 0; i < retryCount; i++) {
            HostServer host = hostList.get(i);
            String wsUrl = String.format("wss://%s:%d/sub", host.getHost(), host.getWss_port() != null ? host.getWss_port() : 443);
            try {
                if (webSocket != null) {
                    webSocket.close();
                }
                
                // 重置鉴权状态
                hasAuthResponded = false;
                isAuthSuccess = false;
                
                webSocket = new DanmuWebSocket(wsUrl, this);
                if (webSocket.connectBlocking(10, java.util.concurrent.TimeUnit.SECONDS)) {
                    webSocket.send(authBytes);
                    webSocket.send(heartBeatBytes);
                    
                    // 等待最多3秒钟，看是否收到服务器发回的鉴权成功响应 (Type = 8)
                    long startTime = System.currentTimeMillis();
                    while (System.currentTimeMillis() - startTime < 3000) {
                        if (hasAuthResponded) {
                            break;
                        }
                        Thread.sleep(100);
                    }
                    
                    if (hasAuthResponded && isAuthSuccess) {
                        authSuccess = true;
                        System.out.println("成功连接并鉴权到弹幕节点: " + host.getHost());
                        break; // 鉴权成功，跳出循环
                    } else {
                        System.out.println("弹幕节点鉴权超时或失败: " + host.getHost());
                        webSocket.close(); // 关闭假连接，尝试下一个节点
                        lastException = new Exception("节点鉴权超时或失败: " + host.getHost());
                    }
                } else {
                    lastException = new Exception("连接弹幕服务器节点超时: " + host.getHost());
                }
            } catch (Exception e) {
                lastException = e;
            }
        }
        
        if (!authSuccess) {
            if (lastException != null) {
                throw new Exception("弹幕服务器连接及鉴权失败: " + lastException.getMessage(), lastException);
            } else {
                throw new Exception("弹幕服务器连接及鉴权失败");
            }
        }
        
        this.hasSuccessfullyConnected = true;
        
        startParseThread();
        startHeartBeatThread();
        running.set(true);
        
        if (danmuListener != null) {
            danmuListener.onConnect(anchorName, realRoomId);
        }
    }
    
    public void disconnect() {
        this.isManualDisconnect = true;
        running.set(false);
        if (parseThread != null) {
            parseThread.interrupt();
        }
        if (heartBeatThread != null) {
            heartBeatThread.interrupt();
        }
        if (webSocket != null) {
            webSocket.close();
        }
    }
    
    private byte[] buildAuthPackage(FristSecurityData data) {
        byte[] jsonBytes = data.toJson().getBytes();
        byte[] header = PackageHeader.pack(
            jsonBytes.length + PACKAGE_HEAD_LENGTH,
            PACKAGE_HEAD_LENGTH,
            AUTH_PACKAGE_VERSION,
            FIRST_PACKAGE_TYPE,
            PACKAGE_OTHER
        );
        return ByteUtils.byteMerger(header, jsonBytes);
    }
    
    private void startParseThread() {
        if (parseThread != null) {
            parseThread.interrupt();
        }
        parseThread = new Thread(() -> {
            while (running.get()) {
                try {
                    synchronized (messageQueue) {
                        while (messageQueue.isEmpty() && running.get()) {
                            messageQueue.wait(1000);
                        }
                    }
                    
                    while (!messageQueue.isEmpty()) {
                        String message = messageQueue.remove(0);
                        parseMessage(message);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    // ignore
                }
            }
        });
        parseThread.start();
    }
    
    private void startHeartBeatThread() {
        if (heartBeatThread != null) {
            heartBeatThread.interrupt();
        }
        heartBeatThread = new Thread(() -> {
            while (running.get()) {
                try {
                    Thread.sleep(20000); // Reduce heartbeat interval to 20 seconds
                    if (webSocket != null && webSocket.isOpen() && running.get()) {
                        webSocket.send(HexUtils.fromHexString(HEART_BEAT));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    // ignore
                }
            }
        });
        heartBeatThread.start();
    }
    
    void onMessage(DanmuWebSocket source, ByteBuffer message) {
        if (this.webSocket != source) return; // Ignore messages from old websockets
        try {
            handleMessage(message);
        } catch (Exception e) {
            if (danmuListener != null) {
                danmuListener.onError("解析消息失败: " + e.getMessage());
            }
        }
    }
    
    private void handleMessage(ByteBuffer message) throws Exception {
        byte[] bytes = ByteUtils.decodeValue(message);
        
        if (bytes.length == 0) return;
        
        PackageHeader header = PackageHeader.unpack(bytes);
        
        if (header.getType() == 8) {
            hasAuthResponded = true;
            isAuthSuccess = true;
            System.out.println("收到弹幕服务器鉴权成功响应");
        } else if (header.getType() == 3) {
            // 人气值包
            return;
        }
        
        byte[] data = ByteUtils.subBytes(bytes, header.getHeadLength(), header.getLength() - header.getHeadLength());
        
        switch (header.getVersion()) {
            case 0:
                handleJsonData(data);
                break;
            case 1:
                break;
            case 2:
                byte[] zlibData = ByteUtils.BytesTozlibInflate(data);
                if (zlibData != null) {
                    handleMultiPackage(zlibData);
                }
                break;
            case 3:
                byte[] brotliData = ByteUtils.BytesToBrotliInflate(data);
                if (brotliData != null) {
                    handleMultiPackage(brotliData);
                }
                break;
        }
    }
    
    private void handleMultiPackage(byte[] data) throws Exception {
        int offset = 0;
        while (offset < data.length) {
            byte[] subData = ByteUtils.subBytes(data, offset, data.length - offset);
            PackageHeader header = PackageHeader.unpack(subData);
            byte[] payload = ByteUtils.subBytes(subData, header.getHeadLength(), header.getLength() - header.getHeadLength());
            
            if (header.getVersion() == 0) {
                handleJsonData(payload);
            }
            
            offset += header.getLength();
        }
    }
    
    private void handleJsonData(byte[] data) {
        try {
            String json = new String(data, "UTF-8");
            synchronized (messageQueue) {
                messageQueue.add(json);
                messageQueue.notify();
            }
        } catch (Exception e) {
            // ignore
        }
    }
    
    private void parseMessage(String message) {
        try {
            JSONObject json = JSONObject.parseObject(message);
            String cmd = json.getString("cmd");
            
            if (cmd == null) return;
            
            if (cmd.startsWith("DANMU_MSG")) {
                parseDanmu(json);
            } else if (cmd.equals("SEND_GIFT")) {
                parseGift(json);
            } else if (cmd.equals("INTERACT_WORD")) {
                parseEnter(json);
            }
        } catch (Exception e) {
            // ignore
        }
    }
    
    private void parseDanmu(JSONObject json) {
        try {
            JSONArray info = json.getJSONArray("info");
            if (info == null || info.size() < 3) return;
            
            String message = info.getString(1);
            JSONArray userInfo = info.getJSONArray(2);
            if (userInfo == null) return;
            
            long uid = userInfo.getLongValue(0);
            String username = userInfo.getString(1);
            
            String medal = null;
            JSONArray medalInfo = info.getJSONArray(3);
            if (medalInfo != null && medalInfo.size() >= 2) {
                String medalName = medalInfo.getString(1);
                int medalLevel = medalInfo.getIntValue(0);
                if (medalName != null && !medalName.isEmpty()) {
                    medal = medalName + medalLevel;
                }
            }
            
            if (medal != null) {
                System.out.println("[" + medal + "] " + username + ": " + message);
            } else {
                System.out.println(username + ": " + message);
            }
            
            if (danmuListener != null) {
                danmuListener.onDanmu(uid, username, message, medal);
            }
        } catch (Exception e) {
            // ignore
        }
    }
    
    private void parseGift(JSONObject json) {
        try {
            JSONObject data = json.getJSONObject("data");
            if (data == null) return;
            
            String username = data.getString("uname");
            String giftName = data.getString("giftName");
            int num = data.getIntValue("num");
            
            if (danmuListener != null) {
                danmuListener.onGift(username, giftName, num);
            }
        } catch (Exception e) {
            // ignore
        }
    }
    
    private void parseEnter(JSONObject json) {
        try {
            JSONObject data = json.getJSONObject("data");
            if (data == null) return;
            
            String username = data.getString("uname");
            
            if (danmuListener != null) {
                danmuListener.onEnter(username);
            }
        } catch (Exception e) {
            // ignore
        }
    }
    
    void onClose(DanmuWebSocket source, int code) {
        if (this.webSocket != source) {
            System.out.println(String.format("[%tT] 忽略旧的弹幕连接断开事件，代码: %d", System.currentTimeMillis(), code));
            return;
        }
        System.out.println(String.format("[%tT] 弹幕连接断开，代码: %d", System.currentTimeMillis(), code));
        running.set(false);
        if (parseThread != null) {
            parseThread.interrupt();
            parseThread = null;
        }
        if (heartBeatThread != null) {
            heartBeatThread.interrupt();
            heartBeatThread = null;
        }
        
        if (!isManualDisconnect && hasSuccessfullyConnected) {
            if (isReconnecting.compareAndSet(false, true)) {
                System.out.println("非主动断开，准备重连...");
                reconnect();
            } else {
                System.out.println("已经在重连中，忽略本次 onClose");
            }
        } else {
            if (!isReconnecting.get()) {
                if (danmuListener != null) {
                    danmuListener.onDisconnect();
                }
            }
        }
    }
    
    private void reconnect() {
        new Thread(() -> {
            int retryCount = 0;
            long reconnectStartTime = System.currentTimeMillis();
            System.out.println(String.format("[%tT] 触发断线重连机制，准备发起重连流程...", reconnectStartTime));
            while (!isManualDisconnect) {
                try {
                    // Start with a very short wait to minimize data loss, then increase on subsequent failures
                    long waitTime = retryCount == 0 ? 500 : 3000;
                    Thread.sleep(waitTime);
                    if (isManualDisconnect) break;
                    
                    long beforeConnectTime = System.currentTimeMillis();
                    System.out.println(String.format("[%tT] 正在尝试重新连接 (第 %d 次), 距离触发已过 %d ms...", 
                                        beforeConnectTime, (retryCount + 1), (beforeConnectTime - reconnectStartTime)));
                    
                    // 如果缓存有效（例如2小时内），使用快速重连跳过HTTP请求
                    if (cachedHostList != null && cachedAuthBytes != null && (System.currentTimeMillis() - cacheTime < 2 * 60 * 60 * 1000)) {
                        System.out.println("使用缓存凭据进行快速重连...");
                        fastReconnect();
                    } else {
                        System.out.println("缓存无效或过期，重新获取凭据...");
                        connect(this.roomId);
                    }
                    
                    long afterConnectTime = System.currentTimeMillis();
                    System.out.println(String.format("[%tT] 重连成功！本次重连耗时 %d ms. 距离断线总计耗时 %d ms.", 
                                        afterConnectTime, (afterConnectTime - beforeConnectTime), (afterConnectTime - reconnectStartTime)));
                                        
                    isReconnecting.set(false);
                    return;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    retryCount++;
                    long failTime = System.currentTimeMillis();
                    System.out.println(String.format("[%tT] 重连失败: %s. 距离断线总计耗时 %d ms.", 
                                        failTime, e.getMessage(), (failTime - reconnectStartTime)));
                    
                    // 如果快速重连失败，清空缓存，下一次重试将强制完全连接
                    this.cachedHostList = null;
                                        
                    if (retryCount >= 10) {
                        System.out.println("重试次数过多，放弃重连。");
                        isReconnecting.set(false);
                        this.hasSuccessfullyConnected = false;
                        if (danmuListener != null) {
                            danmuListener.onError("重连失败次数过多，已断开喵~");
                            danmuListener.onDisconnect();
                        }
                        break;
                    }
                }
            }
            isReconnecting.set(false);
        }).start();
    }
    
    private void fastReconnect() throws Exception {
        byte[] heartBeatBytes = HexUtils.fromHexString(HEART_BEAT);
        Exception lastException = null;
        boolean authSuccess = false;
        
        java.util.Collections.shuffle(this.cachedHostList);
        int limit = Math.min(3, this.cachedHostList.size());
        
        for (int i = 0; i < limit; i++) {
            HostServer host = this.cachedHostList.get(i);
            String wsUrl = String.format("wss://%s:%d/sub", host.getHost(), host.getWss_port() != null ? host.getWss_port() : 443);
            try {
                if (webSocket != null) {
                    webSocket.close();
                }
                hasAuthResponded = false;
                isAuthSuccess = false;
                
                webSocket = new DanmuWebSocket(wsUrl, this);
                if (webSocket.connectBlocking(10, java.util.concurrent.TimeUnit.SECONDS)) {
                    webSocket.send(this.cachedAuthBytes);
                    webSocket.send(heartBeatBytes);
                    
                    long startTime = System.currentTimeMillis();
                    while (System.currentTimeMillis() - startTime < 3000) {
                        if (hasAuthResponded) {
                            break;
                        }
                        Thread.sleep(100);
                    }
                    
                    if (hasAuthResponded && isAuthSuccess) {
                        authSuccess = true;
                        System.out.println("快速重连：成功连接并鉴权到弹幕节点: " + host.getHost());
                        break;
                    } else {
                        System.out.println("快速重连：弹幕节点鉴权超时或失败: " + host.getHost());
                        webSocket.close();
                        lastException = new Exception("节点鉴权超时或失败: " + host.getHost());
                    }
                } else {
                    lastException = new Exception("连接弹幕服务器节点超时: " + host.getHost());
                }
            } catch (Exception e) {
                lastException = e;
            }
        }
        
        if (!authSuccess) {
            if (lastException != null) {
                throw new Exception("快速重连失败: " + lastException.getMessage(), lastException);
            } else {
                throw new Exception("快速重连失败");
            }
        }
        
        running.set(true);
        startParseThread();
        startHeartBeatThread();
    }
    
    void onError(DanmuWebSocket source, Exception ex) {
        if (this.webSocket != source) return;
        System.out.println("弹幕连接发生错误: " + ex.getMessage());
        if (danmuListener != null) {
            danmuListener.onError(ex.getMessage());
        }
    }
}
