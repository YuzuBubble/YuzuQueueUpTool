package xyz.yuzu.tool.web;

import com.alibaba.fastjson.JSONObject;
import io.javalin.Javalin;
import xyz.yuzu.tool.client.DanmuClient;
import xyz.yuzu.tool.entity.Qrcode;
import xyz.yuzu.tool.http.BilibiliApi;
import xyz.yuzu.tool.listener.DanmuListener;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import xyz.yuzu.tool.utils.ConfigManager;

public class WebServer {
    
    private Javalin app;
    private int port;
    private DanmuClient danmuClient;
    private String qrcodeKey;
    private String qrcodeUrl;
    private volatile boolean isLoggedIn = false;
    private volatile boolean isConnecting = false;
    private volatile String anchorName = "";
    private volatile long realRoomId = 0;
    private final CopyOnWriteArrayList<Map<String, Object>> danmuList = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Map<String, Object>> queueList = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<String, String> sessions = new ConcurrentHashMap<>();
    
    public WebServer(int port) {
        this.port = port;
    }
    
    public void start() {
        app = Javalin.create(config -> {
        });
        
        // 初始化一下配置，看看有没有保存过的cookie
        String cookie = ConfigManager.getCookie();
        if (cookie != null && !cookie.isEmpty()) {
            isLoggedIn = true;
            BilibiliApi.setCookie(cookie);
        }

        app.get("/", ctx -> {
            if (!isLoggedIn) {
                ctx.redirect("/login");
            } else {
                ctx.html(getIndexHtml());
            }
        });
        
        app.get("/queue", ctx -> {
            if (!isLoggedIn) {
                ctx.redirect("/login");
            } else {
                ctx.html(getQueueHtml());
            }
        });
        
        app.get("/login", ctx -> {
            ctx.html(getLoginHtml());
        });
        
        app.get("/api/qrcode/url", ctx -> {
            if (isLoggedIn) {
                ctx.json(success("已登录"));
                return;
            }
            Qrcode qrcode = BilibiliApi.generateQrcode();
            if (qrcode == null) {
                ctx.json(error("生成二维码失败喵"));
                return;
            }
            qrcodeKey = qrcode.getQrcode_key();
            qrcodeUrl = qrcode.getUrl();
            ctx.json(success(qrcodeUrl));
        });
        
        app.get("/api/qrcode/image", ctx -> {
            if (qrcodeUrl == null) {
                ctx.status(404);
                return;
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            xyz.yuzu.tool.utils.QrcodeUtils.writeQrcodeImage(qrcodeUrl, baos, 200, 200);
            ctx.contentType("image/png");
            ctx.result(baos.toByteArray());
        });
        
        app.get("/api/qrcode/check", ctx -> {
            if (isLoggedIn) {
                ctx.json(success(Map.of("status", "logged_in")));
                return;
            }
            if (qrcodeKey == null) {
                ctx.json(error("请先获取二维码"));
                return;
            }
            int code = BilibiliApi.qrcodePoll(qrcodeKey);
            Map<String, Object> result = new HashMap<>();
            result.put("code", code);
            
            switch (code) {
                case 0:
                    isLoggedIn = true;
                    // 扫码登录成功后本来需要拿cookie存下来
                    // 注意：目前的扫码接口内部直接搞定了，如果要记住登录状态，
                    // 以后可能还得从别的地方把cookie弄出来存好。
                    result.put("status", "success");
                    break;
                case 86038:
                    result.put("status", "expired");
                    break;
                case 86090:
                    result.put("status", "scanned");
                    break;
                case 86101:
                    result.put("status", "waiting");
                    break;
                default:
                    result.put("status", "unknown");
                    break;
            }
            ctx.json(success(result));
        });
        
        app.post("/api/cookie/login", ctx -> {
            String cookieParam = ctx.formParam("cookie");
            if (cookieParam == null || cookieParam.isEmpty()) {
                ctx.json(error("Cookie不能为空"));
                return;
            }
            ConfigManager.setCookie(cookieParam);
            isLoggedIn = true;
            ctx.json(success("登录成功喵"));
        });
        
        app.get("/api/config/lastRoom", ctx -> {
            String roomId = ConfigManager.getLastRoomId();
            if (roomId != null) {
                ctx.json(success(Map.of("roomId", roomId)));
            } else {
                ctx.json(success(Map.of("roomId", "")));
            }
        });

        app.get("/api/config/ui", ctx -> {
            ctx.json(success(ConfigManager.getUiSettings()));
        });

        app.post("/api/config/ui", ctx -> {
            String bgColor = ctx.formParam("bgColor");
            String itemColor = ctx.formParam("itemColor");
            String fontColor = ctx.formParam("fontColor");
            String scrollIntervalStr = ctx.formParam("scrollInterval");
            String scrollDwellTimeStr = ctx.formParam("scrollDwellTime");
            String scrollSpeedDownStr = ctx.formParam("scrollSpeedDown");
            String scrollSpeedUpStr = ctx.formParam("scrollSpeedUp");
            
            if (bgColor != null && itemColor != null && fontColor != null) {
                int scrollInterval = scrollIntervalStr != null ? Integer.parseInt(scrollIntervalStr) : 5;
                int scrollDwellTime = scrollDwellTimeStr != null ? Integer.parseInt(scrollDwellTimeStr) : 1;
                int scrollSpeedDown = scrollSpeedDownStr != null ? Integer.parseInt(scrollSpeedDownStr) : 50;
                int scrollSpeedUp = scrollSpeedUpStr != null ? Integer.parseInt(scrollSpeedUpStr) : 100;
                
                ConfigManager.setUiSettings(bgColor, itemColor, fontColor, scrollInterval, scrollDwellTime, scrollSpeedDown, scrollSpeedUp);
                ctx.json(success("设置已保存喵"));
            } else {
                ctx.json(error("参数不完整喵"));
            }
        });

        app.post("/api/connect", ctx -> {
            if (!isLoggedIn) {
                ctx.json(error("请先登录喵"));
                return;
            }
            String roomIdStr = ctx.formParam("roomId");
            if (roomIdStr == null || roomIdStr.isEmpty()) {
                ctx.json(error("房间号不能为空喵"));
                return;
            }
            
            long roomId;
            try {
                roomId = Long.parseLong(roomIdStr);
            } catch (NumberFormatException e) {
                ctx.json(error("房间号格式错误喵"));
                return;
            }
            
            ConfigManager.setLastRoomId(roomIdStr);
            
            if (danmuClient != null) {
                danmuClient.disconnect();
            }
            
            danmuList.clear();
            isConnecting = true;
            
            new Thread(() -> {
                danmuClient = new DanmuClient();
                danmuClient.setDanmuListener(new DanmuListener() {
                    @Override
                    public void onDanmu(long uid, String username, String message, String medal) {
                        Map<String, Object> danmu = new HashMap<>();
                        danmu.put("type", "danmu");
                        danmu.put("username", username);
                        danmu.put("message", message);
                        danmu.put("medal", medal);
                        danmu.put("time", System.currentTimeMillis());
                        danmuList.add(danmu);
                        if (danmuList.size() > 100) {
                            danmuList.remove(0);
                        }
                        
                        if ("排队".equals(message.trim())) {
                            boolean exists = queueList.stream().anyMatch(q -> uid == (long) q.get("uid"));
                            if (!exists) {
                                String avatar = BilibiliApi.getUserAvatar(uid);
                                if (avatar == null) {
                                    avatar = "https://i0.hdslb.com/bfs/face/member/noface.jpg";
                                }
                                Map<String, Object> queueItem = new HashMap<>();
                                queueItem.put("uid", uid);
                                queueItem.put("username", username);
                                queueItem.put("avatar", avatar);
                                queueItem.put("time", System.currentTimeMillis());
                                queueList.add(queueItem);
                            }
                        }
                    }
                    
                    @Override
                    public void onGift(String username, String giftName, int num) {
                        Map<String, Object> gift = new HashMap<>();
                        gift.put("type", "gift");
                        gift.put("username", username);
                        gift.put("giftName", giftName);
                        gift.put("num", num);
                        gift.put("time", System.currentTimeMillis());
                        danmuList.add(gift);
                    }
                    
                    @Override
                    public void onEnter(String username) {
                        Map<String, Object> enter = new HashMap<>();
                        enter.put("type", "enter");
                        enter.put("username", username);
                        enter.put("time", System.currentTimeMillis());
                        danmuList.add(enter);
                    }
                    
                    @Override
                    public void onConnect(String name, long roomId) {
                        anchorName = name;
                        realRoomId = roomId;
                        isConnecting = false;
                    }
                    
                    @Override
                    public void onDisconnect() {
                        isConnecting = false;
                    }
                    
                    @Override
                    public void onError(String error) {
                        isConnecting = false;
                    }
                });
                
                try {
                    danmuClient.connect(roomId);
                } catch (Exception e) {
                    isConnecting = false;
                    // 如果有监听器，将错误传回
                    anchorName = "";
                    realRoomId = 0;
                    e.printStackTrace();
                }
            }).start();
            
            ctx.json(success("正在连接喵..."));
        });
        
        app.get("/api/status", ctx -> {
            Map<String, Object> status = new HashMap<>();
            status.put("isLoggedIn", isLoggedIn);
            status.put("isConnecting", isConnecting);
            status.put("anchorName", anchorName);
            status.put("realRoomId", realRoomId);
            ctx.json(success(status));
        });
        
        app.get("/api/danmu", ctx -> {
            ctx.json(success(danmuList));
        });
        
        app.get("/api/danmu/latest", ctx -> {
            String sinceStr = ctx.queryParam("since");
            long since = sinceStr != null ? Long.parseLong(sinceStr) : 0;
            var result = danmuList.stream()
                .filter(d -> (long) d.get("time") > since)
                .toList();
            ctx.json(success(result));
        });
        
        app.post("/api/disconnect", ctx -> {
            if (danmuClient != null) {
                danmuClient.disconnect();
                danmuClient = null;
            }
            anchorName = "";
            realRoomId = 0;
            ctx.json(success("已断开连接喵"));
        });
        
        app.get("/api/queue", ctx -> {
            ctx.json(success(queueList));
        });
        
        app.post("/api/queue/skip", ctx -> {
            String uidStr = ctx.formParam("uid");
            if (uidStr != null) {
                long uid = Long.parseLong(uidStr);
                Map<String, Object> target = null;
                for (Map<String, Object> item : queueList) {
                    if ((long) item.get("uid") == uid) {
                        target = item;
                        break;
                    }
                }
                if (target != null) {
                    queueList.remove(target);
                    queueList.add(target);
                }
            }
            ctx.json(success("已过号喵"));
        });
        
        app.post("/api/queue/remove", ctx -> {
            String uidStr = ctx.formParam("uid");
            if (uidStr != null) {
                long uid = Long.parseLong(uidStr);
                queueList.removeIf(item -> (long) item.get("uid") == uid);
            }
            ctx.json(success("已移除喵"));
        });
        
        app.post("/api/queue/reorder", ctx -> {
            String uidStr = ctx.formParam("uid");
            String newIndexStr = ctx.formParam("newIndex");
            if (uidStr != null && newIndexStr != null) {
                long uid = Long.parseLong(uidStr);
                int newIndex = Integer.parseInt(newIndexStr);
                
                Map<String, Object> target = null;
                for (Map<String, Object> item : queueList) {
                    if ((long) item.get("uid") == uid) {
                        target = item;
                        break;
                    }
                }
                if (target != null) {
                    queueList.remove(target);
                    if (newIndex >= queueList.size()) {
                        queueList.add(target);
                    } else if (newIndex < 0) {
                        queueList.add(0, target);
                    } else {
                        queueList.add(newIndex, target);
                    }
                }
            }
            ctx.json(success("已重排喵"));
        });
        
        app.get("/api/logout", ctx -> {
            isLoggedIn = false;
            ConfigManager.setCookie(""); // 把保存的cookie清空
            BilibiliApi.setCookie(null);
            if (danmuClient != null) {
                danmuClient.disconnect();
                danmuClient = null;
            }
            anchorName = "";
            realRoomId = 0;
            ctx.redirect("/login");
        });

        app.post("/api/shutdown", ctx -> {
            ctx.json(success("正在关闭服务器..."));
            new Thread(() -> {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                Runtime.getRuntime().halt(0); // 强行关掉程序
            }).start();
        });
        
        app.start(port);
        System.out.println("Web服务器已启动: http://localhost:" + port);
    }
    
    public void stop() {
        if (app != null) {
            app.stop();
        }
    }
    
    private Map<String, Object> success(Object data) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 0);
        result.put("data", data);
        return result;
    }
    
    private Map<String, Object> error(String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", -1);
        result.put("message", message);
        return result;
    }
    
    private String getLoginHtml() {
        return """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>YuzuTool - 登录</title>
                <style>
                    :root {
                        --primary: #4F46E5;
                        --primary-hover: #4338CA;
                        --bg-gradient-start: #E0E7FF;
                        --bg-gradient-end: #F3E8FF;
                        --card-bg: rgba(255, 255, 255, 0.85);
                        --text-main: #1F2937;
                        --text-muted: #6B7280;
                    }
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    body { 
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; 
                        background: linear-gradient(135deg, var(--bg-gradient-start) 0%, var(--bg-gradient-end) 100%); 
                        min-height: 100vh; 
                        display: flex; justify-content: center; align-items: center; 
                        color: var(--text-main);
                    }
                    .container { 
                        background: var(--card-bg); 
                        backdrop-filter: blur(20px);
                        -webkit-backdrop-filter: blur(20px);
                        border: 1px solid rgba(255, 255, 255, 0.3);
                        border-radius: 24px; 
                        padding: 48px 40px; 
                        box-shadow: 0 25px 50px -12px rgba(0,0,0,0.1), 0 0 15px rgba(255,255,255,0.5) inset; 
                        max-width: 420px; width: 90%; 
                        transition: transform 0.3s ease, box-shadow 0.3s ease;
                    }
                    .container:hover {
                        transform: translateY(-5px);
                        box-shadow: 0 30px 60px -12px rgba(0,0,0,0.15), 0 0 15px rgba(255,255,255,0.6) inset;
                    }
                    h1 { 
                        text-align: center; 
                        font-size: 28px;
                        font-weight: 800;
                        background: linear-gradient(135deg, var(--primary) 0%, #9333EA 100%);
                        -webkit-background-clip: text;
                        -webkit-text-fill-color: transparent;
                        margin-bottom: 32px; 
                        letter-spacing: -0.5px;
                    }
                    .tabs { display: flex; margin-bottom: 28px; background: rgba(255, 255, 255, 0.5); border-radius: 12px; padding: 4px; }
                    .tab { 
                        flex: 1; padding: 10px; text-align: center; cursor: pointer; 
                        border-radius: 8px; font-weight: 600; font-size: 14px;
                        color: var(--text-muted);
                        transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1); 
                    }
                    .tab.active { 
                        background: white; 
                        color: var(--primary); 
                        box-shadow: 0 4px 6px -1px rgba(0,0,0,0.05); 
                    }
                    .tab-content { display: none; animation: fadeIn 0.4s ease-out; }
                    .tab-content.active { display: block; }
                    @keyframes fadeIn {
                        from { opacity: 0; transform: translateY(10px); }
                        to { opacity: 1; transform: translateY(0); }
                    }
                    
                    .qrcode-wrapper { text-align: center; display: flex; flex-direction: column; align-items: center; }
                    .qrcode-placeholder { 
                        width: 220px; height: 220px; 
                        background: rgba(255,255,255,0.5);
                        border: 2px dashed rgba(79, 70, 229, 0.3); 
                        border-radius: 16px; 
                        display: flex; justify-content: center; align-items: center; 
                        cursor: pointer; transition: all 0.3s; 
                    }
                    .qrcode-placeholder:hover { 
                        border-color: var(--primary); 
                        background: rgba(255,255,255,0.8); 
                        transform: scale(1.02);
                    }
                    .qrcode-placeholder span { color: var(--primary); font-weight: 600; font-size: 15px; }
                    .qrcode-img { 
                        width: 220px; height: 220px; 
                        border: none; border-radius: 16px; 
                        box-shadow: 0 10px 25px -5px rgba(0,0,0,0.1);
                        padding: 10px; background: white;
                    }
                    .qrcode-tip { color: var(--text-muted); font-size: 14px; margin-top: 16px; font-weight: 500; }
                    
                    .status { margin-top: 20px; padding: 12px 16px; border-radius: 12px; text-align: center; font-weight: 600; font-size: 14px; width: 100%; transition: all 0.3s;}
                    .status.waiting { background: #F3F4F6; color: #4B5563; }
                    .status.scanned { background: #DBEAFE; color: #1D4ED8; }
                    .status.success { background: #D1FAE5; color: #047857; }
                    .status.error { background: #FEE2E2; color: #B91C1C; }
                    
                    .form-group { margin-bottom: 20px; }
                    .form-group label { display: block; margin-bottom: 8px; color: var(--text-main); font-weight: 600; font-size: 14px;}
                    .form-group textarea { 
                        width: 100%; padding: 16px; 
                        background: rgba(255,255,255,0.6);
                        border: 1px solid rgba(0,0,0,0.1); 
                        border-radius: 12px; resize: vertical; min-height: 120px; 
                        font-family: monospace; font-size: 13px;
                        transition: all 0.3s;
                    }
                    .form-group textarea:focus {
                        outline: none; border-color: var(--primary);
                        background: white; box-shadow: 0 0 0 3px rgba(79, 70, 229, 0.2);
                    }
                    .btn { 
                        width: 100%; padding: 14px; 
                        background: linear-gradient(135deg, var(--primary) 0%, #6366F1 100%); 
                        color: white; border: none; border-radius: 12px; 
                        font-size: 16px; font-weight: 700; cursor: pointer; 
                        transition: all 0.3s; 
                        box-shadow: 0 4px 14px rgba(79, 70, 229, 0.4);
                    }
                    .btn:hover { transform: translateY(-2px); box-shadow: 0 6px 20px rgba(79, 70, 229, 0.5); }
                    .btn:active { transform: translateY(0); box-shadow: 0 2px 8px rgba(79, 70, 229, 0.4); }
                    .btn:disabled { background: #D1D5DB; cursor: not-allowed; transform: none; box-shadow: none; color: #9CA3AF;}
                </style>
            </head>
            <body>
                <div class="container">
                    <h1>YuzuTool</h1>
                    <div class="tabs">
                        <div class="tab active" onclick="switchTab('qrcode')">扫码登录</div>
                        <div class="tab" onclick="switchTab('cookie')">Cookie登录</div>
                    </div>
                    <div id="qrcode-tab" class="tab-content active">
                        <div class="qrcode-wrapper">
                            <div id="qrcode-placeholder" class="qrcode-placeholder" onclick="getQrcode()">
                                <span>点击获取二维码喵</span>
                            </div>
                            <img id="qrcode-img" class="qrcode-img" src="" alt="二维码" style="display:none;">
                            <div class="qrcode-tip">请使用哔哩哔哩 APP 扫码</div>
                            <div id="status" class="status" style="display:none;"></div>
                        </div>
                    </div>
                    <div id="cookie-tab" class="tab-content">
                        <div class="form-group">
                            <label>输入 Bilibili Cookie</label>
                            <textarea id="cookie-input" placeholder="SESSDATA=...; bili_jct=...; DedeUserID=..."></textarea>
                        </div>
                        <button class="btn" onclick="cookieLogin()">登录</button>
                    </div>
                </div>
                <script>
                    let checkInterval = null;
                    function switchTab(tab) {
                        document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
                        document.querySelectorAll('.tab-content').forEach(t => t.classList.remove('active'));
                        document.querySelector('.tab:' + (tab === 'qrcode' ? 'first' : 'last') + '-child').classList.add('active');
                        document.getElementById(tab + '-tab').classList.add('active');
                        if (checkInterval) { clearInterval(checkInterval); checkInterval = null; }
                    }
                    async function getQrcode() {
                        const placeholder = document.getElementById('qrcode-placeholder');
                        const img = document.getElementById('qrcode-img');
                        const status = document.getElementById('status');
                        try {
                            const res = await fetch('/api/qrcode/url');
                            const data = await res.json();
                            if (data.code === 0) {
                                placeholder.style.display = 'none';
                                img.style.display = 'block';
                                img.src = '/api/qrcode/image?t=' + Date.now();
                                status.style.display = 'block';
                                status.className = 'status waiting';
                                status.textContent = '请使用B站APP扫码';
                                if (checkInterval) clearInterval(checkInterval);
                                checkInterval = setInterval(checkLogin, 2000);
                            } else {
                                alert(data.message || '获取二维码失败喵');
                            }
                        } catch (e) {
                            alert('请求失败喵: ' + e.message);
                        }
                    }
                    async function checkLogin() {
                        try {
                            const res = await fetch('/api/qrcode/check');
                            const data = await res.json();
                            const status = document.getElementById('status');
                            if (data.code === 0) {
                                const result = data.data;
                                switch (result.status) {
                                    case 'success':
                                        status.className = 'status success';
                                        status.textContent = '登录成功喵！';
                                        clearInterval(checkInterval);
                                        setTimeout(() => location.href = '/', 1000);
                                        break;
                                    case 'scanned':
                                        status.className = 'status scanned';
                                        status.textContent = '已扫码，请在手机确认喵...';
                                        break;
                                    case 'expired':
                                        status.className = 'status error';
                                        status.textContent = '二维码已过期，点击刷新喵';
                                        clearInterval(checkInterval);
                                        break;
                                }
                            }
                        } catch (e) {}
                    }
                    async function cookieLogin() {
                        const cookie = document.getElementById('cookie-input').value;
                        if (!cookie) { alert('请输入Cookie'); return; }
                        const btn = document.querySelector('#cookie-tab .btn');
                        btn.disabled = true; btn.textContent = '正在摇尾巴让B站放松警惕喵~';
                        try {
                            const res = await fetch('/api/cookie/login', {
                                method: 'POST',
                                headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                                body: 'cookie=' + encodeURIComponent(cookie)
                            });
                            const data = await res.json();
                            if (data.code === 0) {
                                location.href = '/';
                            } else {
                                alert(data.message || '登录失败喵');
                                btn.disabled = false; btn.textContent = '登录';
                            }
                        } catch (e) {
                            alert('请求失败喵: ' + e.message);
                            btn.disabled = false; btn.textContent = '登录';
                        }
                    }
                </script>
            </body>
            </html>
            """;
    }
    
    private String getQueueHtml() {
        return """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>排队列表</title>
                <style>
                    :root {
                        --bg-color: #transparent;
                        --item-color: rgba(255, 255, 255, 0.9);
                        --font-color: #1F2937;
                    }
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    body { 
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; 
                        background: var(--bg-color); 
                        padding: 16px; transition: background-color 0.3s ease; 
                        color: var(--font-color);
                        overflow-x: hidden;
                    }
                    /* 如果选了透明背景，这里强制让页面变透明，方便在直播软件里用 */
                    body[style*="transparent"] { background: transparent !important; }
                    
                    h1 { 
                        text-align: center; color: var(--font-color); margin-bottom: 24px; 
                        font-size: 20px; font-weight: 800; letter-spacing: 1px;
                        text-shadow: 0 2px 4px rgba(0,0,0,0.1);
                        transition: color 0.3s ease; 
                    }
                    .queue-list { display: flex; flex-direction: column; gap: 12px; position: relative; }
                    
                    .queue-item { 
                        background: var(--item-color); 
                        border-radius: 16px; padding: 12px; 
                        display: flex; align-items: center; 
                        box-shadow: 0 4px 15px rgba(0,0,0,0.05), 0 1px 3px rgba(0,0,0,0.1); 
                        backdrop-filter: blur(10px); -webkit-backdrop-filter: blur(10px);
                        border: 1px solid rgba(255,255,255,0.2);
                        cursor: grab; 
                        transition: transform 0.3s cubic-bezier(0.25, 0.8, 0.25, 1), box-shadow 0.3s ease, background-color 0.3s ease; 
                        transform-origin: center center; 
                    }
                    .queue-item:hover { box-shadow: 0 8px 20px rgba(0,0,0,0.1); transform: translateY(-2px); }
                    .queue-item:active { cursor: grabbing; transform: scale(0.98); box-shadow: 0 2px 8px rgba(0,0,0,0.1); }
                    .queue-item.dragging { opacity: 0.7; transform: scale(1.02); box-shadow: 0 15px 30px rgba(0,0,0,0.15); z-index: 100; border-color: #4F46E5; }
                    
                    body::-webkit-scrollbar { display: none; }
                    body { -ms-overflow-style: none; scrollbar-width: none; }
                    
                    /* 列表进场和退场时的动画效果 */
                    .item-enter { animation: slideIn 0.5s cubic-bezier(0.34, 1.56, 0.64, 1) forwards; }
                    .item-leave { animation: slideOut 0.4s cubic-bezier(0.25, 0.8, 0.25, 1) forwards; }
                    
                    @keyframes slideIn {
                        from { opacity: 0; transform: translateX(-30px) scale(0.9); }
                        to { opacity: 1; transform: translateX(0) scale(1); }
                    }
                    @keyframes slideOut {
                        from { opacity: 1; transform: scale(1) translateX(0); }
                        to { opacity: 0; transform: scale(0.9) translateX(30px); }
                    }

                    .serial { 
                        width: 32px; height: 32px; 
                        display: flex; align-items: center; justify-content: center;
                        background: rgba(0,0,0,0.05); border-radius: 50%;
                        font-weight: 800; font-size: 14px;
                        color: var(--font-color); text-align: center; transition: color 0.3s ease; 
                    }
                    .queue-item:first-child .serial { 
                        background: linear-gradient(135deg, #F59E0B 0%, #D97706 100%); 
                        color: white; box-shadow: 0 2px 8px rgba(245, 158, 11, 0.4);
                    }
                    .queue-item:nth-child(2) .serial { 
                        background: linear-gradient(135deg, #9CA3AF 0%, #6B7280 100%); 
                        color: white; box-shadow: 0 2px 8px rgba(156, 163, 175, 0.4);
                    }
                    .queue-item:nth-child(3) .serial { 
                        background: linear-gradient(135deg, #D97706 0%, #B45309 100%); 
                        color: white; box-shadow: 0 2px 8px rgba(217, 119, 6, 0.4);
                    }
                    
                    .avatar { 
                        width: 44px; height: 44px; border-radius: 50%; object-fit: cover; 
                        margin: 0 12px; pointer-events: none;
                        border: 2px solid white; box-shadow: 0 2px 6px rgba(0,0,0,0.1);
                    }
                    .user-info { 
                        flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; 
                        color: var(--font-color); font-weight: 700; font-size: 15px;
                        pointer-events: none; transition: color 0.3s ease; 
                        text-shadow: 0 1px 2px rgba(255,255,255,0.5);
                    }
                    
                    .actions { display: flex; gap: 6px; opacity: 0; transition: opacity 0.3s; }
                    .queue-item:hover .actions { opacity: 1; }
                    /* 在手机等没有鼠标悬停的设备上，一直显示操作按钮 */
                    @media (hover: none) { .actions { opacity: 1; } }
                    
                    .btn { 
                        padding: 6px 10px; border: none; cursor: pointer; 
                        font-size: 12px; font-weight: 700; border-radius: 8px;
                        background: rgba(255,255,255,0.5); backdrop-filter: blur(4px);
                        transition: all 0.2s;
                    }
                    .btn:hover { transform: translateY(-1px); box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
                    .btn:active { transform: translateY(1px); box-shadow: none; }
                    
                    .btn-skip { color: #F59E0B; background: rgba(245, 158, 11, 0.1); }
                    .btn-skip:hover { background: rgba(245, 158, 11, 0.2); }
                    
                    .btn-remove { color: #EF4444; background: rgba(239, 68, 68, 0.1); }
                    .btn-remove:hover { background: rgba(239, 68, 68, 0.2); }
                    
                    .empty { 
                        text-align: center; color: var(--font-color); padding: 40px 0; 
                        font-weight: 600; opacity: 0.6; transition: color 0.3s ease; 
                        background: var(--item-color); border-radius: 16px;
                        border: 2px dashed rgba(0,0,0,0.1);
                    }
                </style>
            </head>
            <body>
                <h1>排队列表</h1>
                <div class="queue-list" id="queue-list">
                    <div class="empty">正在用尾巴当螺旋桨飞速加载喵~</div>
                </div>
                <script>
                    let isDragging = false;
                    let dragSrcEl = null;
                    let currentList = [];
                    
                    // 自动滚动的各种参数
                    let autoScrollConfig = { interval: 5000, dwellTime: 1000, speedDown: 50, speedUp: 100 };
                    let scrollTimer = null;
                    let isAutoScrolling = false;

                    async function loadUiSettings() {
                        try {
                            const res = await fetch('/api/config/ui');
                            const data = await res.json();
                            if (data.code === 0) {
                                const root = document.documentElement;
                                root.style.setProperty('--bg-color', data.data.bgColor);
                                root.style.setProperty('--item-color', data.data.itemColor);
                                root.style.setProperty('--font-color', data.data.fontColor);
                                
                                autoScrollConfig.interval = (data.data.scrollInterval || 5) * 1000;
                                autoScrollConfig.dwellTime = (data.data.scrollDwellTime || 1) * 1000;
                                autoScrollConfig.speedDown = data.data.scrollSpeedDown || 50;
                                autoScrollConfig.speedUp = data.data.scrollSpeedUp || 100;
                                
                                setupAutoScroll();
                            }
                        } catch (e) {
                            console.error('加载UI设置失败喵', e);
                        }
                    }

                    function setupAutoScroll() {
                        if (scrollTimer) clearTimeout(scrollTimer);
                        
                        // 看看页面是不是太长了，长到出现了滚动空间
                        const maxScrollTop = document.documentElement.scrollHeight - window.innerHeight;
                        if (maxScrollTop > 0 && !isAutoScrolling) {
                            scrollTimer = setTimeout(performScroll, autoScrollConfig.interval);
                        }
                    }
                    
                    function performScroll() {
                        if (isDragging) {
                            scrollTimer = setTimeout(performScroll, autoScrollConfig.interval);
                            return;
                        }
                        
                        const maxScrollTop = document.documentElement.scrollHeight - window.innerHeight;
                        if (maxScrollTop <= 0) {
                            setupAutoScroll();
                            return;
                        }
                        
                        isAutoScrolling = true;
                        
                        let currentPos = window.scrollY;
                        const scrollDownInterval = setInterval(() => {
                            if (isDragging) {
                                clearInterval(scrollDownInterval);
                                isAutoScrolling = false;
                                setupAutoScroll();
                                return;
                            }
                            
                            currentPos += (autoScrollConfig.speedDown / 10); // 每次往下滚一丁点距离
                            window.scrollTo(0, currentPos);
                            
                            if (currentPos >= maxScrollTop) {
                                clearInterval(scrollDownInterval);
                                window.scrollTo(0, maxScrollTop);
                                
                                setTimeout(() => {
                                    if (isDragging) {
                                        isAutoScrolling = false;
                                        setupAutoScroll();
                                        return;
                                    }
                                    
                                    let upPos = window.scrollY;
                                    const scrollUpInterval = setInterval(() => {
                                        if (isDragging) {
                                            clearInterval(scrollUpInterval);
                                            isAutoScrolling = false;
                                            setupAutoScroll();
                                            return;
                                        }
                                        
                                        upPos -= (autoScrollConfig.speedUp / 10);
                                        window.scrollTo(0, upPos);
                                        
                                        if (upPos <= 0) {
                                            clearInterval(scrollUpInterval);
                                            window.scrollTo(0, 0);
                                            isAutoScrolling = false;
                                            setupAutoScroll(); 
                                        }
                                    }, 10);
                                }, autoScrollConfig.dwellTime);
                            }
                        }, 10);
                    }
                    
                    window.addEventListener('wheel', () => {
                        if (isAutoScrolling) return; 
                        if (scrollTimer) {
                            clearTimeout(scrollTimer);
                            scrollTimer = setTimeout(performScroll, autoScrollConfig.interval);
                        }
                    });

                    async function fetchQueue() {
                        if (isDragging) return;
                        try {
                            const res = await fetch('/api/queue');
                            const data = await res.json();
                            if (data.code === 0) {
                                renderQueue(data.data);
                            }
                        } catch (e) {
                            console.error('获取排队列表失败喵', e);
                        }
                    }
                    
                    function renderQueue(newList) {
                        const container = document.getElementById('queue-list');
                        const wasScrollable = document.documentElement.scrollHeight > window.innerHeight;
                        
                        if (!newList || newList.length === 0) {
                            if (currentList.length > 0 || container.innerHTML.includes('加载中')) {
                                container.innerHTML = '<div class="empty">暂无排队人员</div>';
                                currentList = [];
                            }
                            return;
                        }
                        
                        if (container.querySelector('.empty')) {
                            container.innerHTML = '';
                        }

                        newList.forEach((item, index) => {
                            let el = document.querySelector(`.queue-item[data-uid="${item.uid}"]`);
                            
                            if (!el) {
                                // 发现新来排队的，给他做个牌子
                                el = document.createElement('div');
                                el.className = 'queue-item item-enter';
                                el.draggable = true;
                                el.dataset.uid = item.uid;
                                el.dataset.index = index;
                                el.innerHTML = `
                                    <div class="serial">${index + 1}</div>
                                    <img class="avatar" src="${item.avatar}" alt="头像" referrerpolicy="no-referrer">
                                    <div class="user-info" title="${item.username}">${item.username}</div>
                                    <div class="actions">
                                        <button class="btn btn-skip" onclick="skipUser(${item.uid})">过号</button>
                                        <button class="btn btn-remove" onclick="removeUser(${item.uid})">移除</button>
                                    </div>
                                `;
                                
                                el.addEventListener('dragstart', handleDragStart);
                                el.addEventListener('dragover', handleDragOver);
                                el.addEventListener('drop', handleDrop);
                                el.addEventListener('dragend', handleDragEnd);
                                
                                if (index >= container.children.length) {
                                    container.appendChild(el);
                                } else {
                                    container.insertBefore(el, container.children[index]);
                                }
                                
                                setTimeout(() => el.classList.remove('item-enter'), 400);
                            } else {
                                el.dataset.index = index;
                                const serialEl = el.querySelector('.serial');
                                if (serialEl.textContent !== String(index + 1)) {
                                    serialEl.textContent = index + 1;
                                }
                                
                                if (Array.from(container.children).indexOf(el) !== index) {
                                    if (index >= container.children.length) {
                                        container.appendChild(el);
                                    } else {
                                        container.insertBefore(el, container.children[index]);
                                    }
                                }
                            }
                        });

                        const newUids = newList.map(i => String(i.uid));
                        Array.from(container.children).forEach(el => {
                            if (el.classList.contains('queue-item')) {
                                const uid = el.dataset.uid;
                                if (!newUids.includes(uid)) {
                                    el.classList.add('item-leave');
                                    setTimeout(() => {
                                        if (el.parentNode) el.parentNode.removeChild(el);
                                        if (container.children.length === 0) {
                                            container.innerHTML = '<div class="empty">暂无排队人员</div>';
                                        }
                                    }, 300);
                                }
                            }
                        });

                        currentList = newList;
                        
                        setTimeout(() => {
                            const isScrollable = document.documentElement.scrollHeight > window.innerHeight;
                            if (isScrollable && !wasScrollable && !isAutoScrolling) {
                                setupAutoScroll();
                            }
                        }, 100);
                    }

                    function handleDragStart(e) {
                        isDragging = true;
                        dragSrcEl = this;
                        e.dataTransfer.effectAllowed = 'move';
                        e.dataTransfer.setData('text/plain', this.dataset.uid);
                        setTimeout(() => this.classList.add('dragging'), 0);
                    }

                    function handleDragOver(e) {
                        if (e.preventDefault) {
                            e.preventDefault();
                        }
                        e.dataTransfer.dropEffect = 'move';
                        
                        const target = e.target.closest('.queue-item');
                        if (target && target !== dragSrcEl) {
                            const rect = target.getBoundingClientRect();
                            const offset = e.clientY - rect.top;
                            const container = target.parentNode;
                            
                            if (offset > rect.height / 2) {
                                if (target.nextSibling) {
                                    container.insertBefore(dragSrcEl, target.nextSibling);
                                } else {
                                    container.appendChild(dragSrcEl);
                                }
                            } else {
                                container.insertBefore(dragSrcEl, target);
                            }
                            
                            Array.from(container.children).forEach((el, idx) => {
                                if (el.classList.contains('queue-item')) {
                                    el.querySelector('.serial').textContent = idx + 1;
                                    el.dataset.index = idx;
                                }
                            });
                        }
                        
                        return false;
                    }

                    async function handleDrop(e) {
                        if (e.stopPropagation) {
                            e.stopPropagation();
                        }
                        return false;
                    }

                    async function handleDragEnd(e) {
                        this.classList.remove('dragging');
                        isDragging = false;
                        
                        const container = this.parentNode;
                        const newIndex = Array.from(container.children).indexOf(this);
                        const uid = this.dataset.uid;
                        
                        // 如果真的换位置了，才去告诉服务器
                        const originalIndex = currentList.findIndex(item => String(item.uid) === String(uid));
                        if (originalIndex !== newIndex && originalIndex !== -1) {
                            try {
                                await fetch('/api/queue/reorder', {
                                    method: 'POST',
                                    headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                                    body: 'uid=' + uid + '&newIndex=' + newIndex
                                });
                                fetchQueue();
                            } catch (e) {
                                console.error('重排失败喵', e);
                            }
                        }
                    }
                    
                    async function skipUser(uid) {
                        try {
                            await fetch('/api/queue/skip', {
                                method: 'POST',
                                headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                                body: 'uid=' + uid
                            });
                            fetchQueue();
                        } catch (e) {
                            alert('操作失败喵');
                        }
                    }
                    
                    async function removeUser(uid) {
                        try {
                            const el = document.querySelector(`.queue-item[data-uid="${uid}"]`);
                            if (el) el.classList.add('item-leave');
                            await fetch('/api/queue/remove', {
                                method: 'POST',
                                headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                                body: 'uid=' + uid
                            });
                            fetchQueue();
                        } catch (e) {
                            alert('操作失败喵');
                        }
                    }
                    
                    loadUiSettings();
                    fetchQueue();
                    setInterval(fetchQueue, 2000);
                    setInterval(loadUiSettings, 5000);
                </script>
            </body>
            </html>
            """;
    }

    private String getIndexHtml() {
        return """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>YuzuTool - 指挥中心！</title>
                <style>
                    :root {
                        --primary: #4F46E5;
                        --primary-hover: #4338CA;
                        --bg-gradient-start: #EEF2FF;
                        --bg-gradient-end: #E0E7FF;
                        --card-bg: rgba(255, 255, 255, 0.9);
                        --text-main: #1F2937;
                        --text-muted: #6B7280;
                    }
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    body { 
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; 
                        background: linear-gradient(135deg, var(--bg-gradient-start) 0%, var(--bg-gradient-end) 100%); 
                        min-height: 100vh; color: var(--text-main);
                    }
                    .header { 
                        background: linear-gradient(135deg, var(--primary) 0%, #6366F1 100%); 
                        color: white; padding: 24px 40px; 
                        box-shadow: 0 4px 20px rgba(79, 70, 229, 0.2);
                        display: flex; justify-content: space-between; align-items: center;
                    }
                    .header h1 { font-size: 28px; font-weight: 800; letter-spacing: -0.5px; }
                    .header .status { 
                        font-size: 14px; font-weight: 600; 
                        background: rgba(255,255,255,0.2); padding: 6px 16px; border-radius: 20px; 
                        backdrop-filter: blur(10px);
                    }
                    .container { max-width: 900px; margin: 40px auto; padding: 0 20px; display: flex; flex-direction: column; gap: 24px; }
                    
                    .card { 
                        background: var(--card-bg); 
                        backdrop-filter: blur(20px); -webkit-backdrop-filter: blur(20px);
                        border: 1px solid rgba(255, 255, 255, 0.5);
                        border-radius: 20px; padding: 28px; 
                        box-shadow: 0 10px 30px -10px rgba(0,0,0,0.05), 0 0 15px rgba(255,255,255,0.7) inset; 
                        transition: transform 0.3s ease, box-shadow 0.3s ease;
                    }
                    .card:hover { box-shadow: 0 15px 35px -10px rgba(0,0,0,0.1), 0 0 15px rgba(255,255,255,0.8) inset; }
                    .card h2 { font-size: 20px; margin-bottom: 20px; font-weight: 700; color: #111827; display: flex; align-items: center;}
                    
                    .form-row { display: flex; gap: 12px; }
                    .form-row input[type="text"] { 
                        flex: 1; padding: 14px 16px; 
                        background: rgba(255,255,255,0.8);
                        border: 1px solid rgba(0,0,0,0.1); 
                        border-radius: 12px; font-size: 15px; transition: all 0.3s;
                    }
                    .form-row input[type="text"]:focus { outline: none; border-color: var(--primary); box-shadow: 0 0 0 3px rgba(79, 70, 229, 0.15); background: white; }
                    
                    .btn { 
                        padding: 12px 24px; 
                        background: linear-gradient(135deg, var(--primary) 0%, #6366F1 100%); 
                        color: white; border: none; border-radius: 12px; 
                        cursor: pointer; font-size: 15px; font-weight: 600; 
                        transition: all 0.3s; box-shadow: 0 4px 12px rgba(79, 70, 229, 0.3);
                        display: inline-flex; align-items: center; justify-content: center;
                    }
                    .btn:hover { transform: translateY(-2px); box-shadow: 0 6px 16px rgba(79, 70, 229, 0.4); }
                    .btn:active { transform: translateY(0); box-shadow: 0 2px 8px rgba(79, 70, 229, 0.3); }
                    .btn:disabled { background: #D1D5DB; cursor: not-allowed; transform: none; box-shadow: none; color: #9CA3AF; }
                    
                    .btn-outline { 
                        background: transparent; color: var(--primary); 
                        border: 2px solid var(--primary); box-shadow: none; 
                    }
                    .btn-outline:hover { background: rgba(79, 70, 229, 0.05); box-shadow: none; }
                    
                    .btn-danger { 
                        background: linear-gradient(135deg, #EF4444 0%, #DC2626 100%); 
                        box-shadow: 0 4px 12px rgba(239, 68, 68, 0.3); 
                    }
                    .btn-danger:hover { box-shadow: 0 6px 16px rgba(239, 68, 68, 0.4); }
                    
                    .danmu-list { 
                        height: 400px; overflow-y: auto; 
                        background: rgba(249, 250, 251, 0.5); 
                        border-radius: 12px; border: 1px solid rgba(0,0,0,0.05);
                        padding: 12px;
                    }
                    .danmu-list::-webkit-scrollbar { width: 6px; }
                    .danmu-list::-webkit-scrollbar-track { background: transparent; }
                    .danmu-list::-webkit-scrollbar-thumb { background: rgba(0,0,0,0.1); border-radius: 10px; }
                    .danmu-list::-webkit-scrollbar-thumb:hover { background: rgba(0,0,0,0.2); }
                    
                    .danmu-item { 
                        padding: 12px 16px; margin-bottom: 8px;
                        background: white; border-radius: 10px;
                        box-shadow: 0 2px 4px rgba(0,0,0,0.02);
                        font-size: 14px; transition: transform 0.2s;
                        animation: slideDown 0.3s ease-out forwards;
                    }
                    @keyframes slideDown {
                        from { opacity: 0; transform: translateY(-10px); }
                        to { opacity: 1; transform: translateY(0); }
                    }
                    .danmu-item .username { color: var(--primary); font-weight: 700; margin-right: 6px; }
                    .danmu-item .medal { 
                        background: linear-gradient(135deg, #F59E0B 0%, #D97706 100%); 
                        color: white; padding: 2px 8px; border-radius: 12px; 
                        font-size: 12px; font-weight: 600; margin-right: 8px; 
                        box-shadow: 0 2px 4px rgba(245, 158, 11, 0.2);
                    }
                    .danmu-item .message { color: #374151; }
                    .danmu-item.gift { 
                        background: linear-gradient(to right, #FFFBEB, white); 
                        border-left: 3px solid #F59E0B; 
                    }
                    .danmu-item.gift .gift-icon { color: #F59E0B; margin-right: 6px;}
                    .danmu-item.enter { 
                        background: linear-gradient(to right, #EFF6FF, white);
                        border-left: 3px solid #3B82F6; color: #1D4ED8; font-weight: 500; 
                    }
                    .empty { text-align: center; color: var(--text-muted); padding: 60px 0; font-weight: 500; }
                    
                    .settings-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 16px; margin-bottom: 24px; }
                    .setting-item { display: flex; flex-direction: column; gap: 8px; }
                    .setting-item label { font-weight: 600; font-size: 14px; color: var(--text-main); }
                    .setting-item select { 
                        padding: 12px; border-radius: 10px; border: 1px solid rgba(0,0,0,0.1); 
                        background: rgba(255,255,255,0.8); font-size: 14px; 
                        cursor: pointer; transition: all 0.3s; appearance: none;
                        background-image: url("data:image/svg+xml;charset=UTF-8,%3csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'%3e%3cpolyline points='6 9 12 15 18 9'%3e%3c/polyline%3e%3c/svg%3e");
                        background-repeat: no-repeat; background-position: right 12px center; background-size: 16px;
                    }
                    .setting-item select:focus { outline: none; border-color: var(--primary); box-shadow: 0 0 0 3px rgba(79, 70, 229, 0.15); }
                    .color-picker-wrapper { display: flex; align-items: center; gap: 10px; }
                    input[type="color"] { 
                        -webkit-appearance: none; border: none; width: 36px; height: 36px; 
                        border-radius: 8px; cursor: pointer; padding: 0; background: none;
                    }
                    input[type="color"]::-webkit-color-swatch-wrapper { padding: 0; }
                    input[type="color"]::-webkit-color-swatch { border: 2px solid rgba(0,0,0,0.1); border-radius: 8px; }
                    
                    .actions-card { display: flex; justify-content: space-between; align-items: center; background: transparent; border: none; box-shadow: none; padding: 0;}
                    .actions-card .btn { width: auto; padding: 10px 20px;}
                </style>
            </head>
            <body>
                <div class="header">
                    <h1>YuzuTool</h1>
                    <div class="status" id="status">还没连接到直播间喵~</div>
                </div>
                <div class="container">
                    <div class="card">
                        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;">
                            <h2><svg style="width:24px;height:24px;margin-right:8px;color:var(--primary);" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z"></path></svg> 连接直播间</h2>
                            <button class="btn btn-outline" id="queue-btn" onclick="openQueue()" style="display:none; padding: 8px 16px; font-size: 13px;">独立排队窗口</button>
                        </div>
                        <div class="form-row">
                            <input type="text" id="room-id" placeholder="输入 Bilibili 房间号">
                            <button class="btn" id="connect-btn" onclick="connect()">连接弹幕</button>
                            <button class="btn btn-danger" id="disconnect-btn" onclick="disconnect()" style="display:none;">断开连接</button>
                        </div>
                    </div>
                    
                    <div class="card">
                        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px;">
                            <h2><svg style="width:24px;height:24px;margin-right:8px;color:var(--primary);" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z"></path></svg> 实时弹幕</h2>
                            <span id="danmu-count" style="font-size:13px; font-weight:600; color:var(--primary); background:rgba(79, 70, 229, 0.1); padding: 4px 12px; border-radius: 12px;">0 条</span>
                        </div>
                        <div class="danmu-list" id="danmu-list">
                            <div class="empty">暂无弹幕数据喵</div>
                        </div>
                    </div>
                    
                    <div class="card">
                        <h2><svg style="width:24px;height:24px;margin-right:8px;color:var(--primary);" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z"></path><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"></path></svg> 排队列表外观设置</h2>
                        <div class="settings-grid">
                            <div class="setting-item">
                                <label>页面背景色</label>
                                <div class="color-picker-wrapper">
                                    <select id="bg-color-preset" onchange="toggleCustomColor('bg')">
                                        <option value="transparent">透明 (OBS/直播姬捕获推荐)</option>
                                        <option value="#F8FAFC">极简灰 (Slate 50)</option>
                                        <option value="#EFF6FF">清透蓝 (Blue 50)</option>
                                        <option value="#FDF2F8">樱花粉 (Pink 50)</option>
                                        <option value="#ECFDF5">森系绿 (Emerald 50)</option>
                                        <option value="#ffffff">纯白 (White)</option>
                                        <option value="custom">自定义颜色...</option>
                                    </select>
                                    <input type="color" id="bg-color-custom" value="#666666" style="display: none;">
                                </div>
                            </div>
                            
                            <div class="setting-item">
                                <label>卡片背景色</label>
                                <div class="color-picker-wrapper">
                                    <select id="item-color-preset" onchange="toggleCustomColor('item')">
                                        <option value="#ffffff">纯净白 (White)</option>
                                        <option value="#F1F5F9">高级灰 (Slate 100)</option>
                                        <option value="#DBEAFE">梦幻蓝 (Blue 100)</option>
                                        <option value="#FCE7F3">元气粉 (Pink 100)</option>
                                        <option value="#FEF3C7">温暖黄 (Amber 100)</option>
                                        <option value="custom">自定义颜色...</option>
                                    </select>
                                    <input type="color" id="item-color-custom" value="#555555" style="display: none;">
                                </div>
                            </div>
                            
                            <div class="setting-item">
                                <label>文字颜色</label>
                                <div class="color-picker-wrapper">
                                    <select id="font-color-preset" onchange="toggleCustomColor('font')">
                                        <option value="#0F172A">深邃黑 (Slate 900)</option>
                                        <option value="#334155">优雅灰 (Slate 700)</option>
                                        <option value="#1E3A8A">静谧蓝 (Blue 900)</option>
                                        <option value="#ffffff">纯洁白 (White)</option>
                                        <option value="custom">自定义颜色...</option>
                                    </select>
                                    <input type="color" id="font-color-custom" value="#ffffff" style="display: none;">
                                </div>
                            </div>
                            
                            <div class="setting-item" style="grid-column: 1 / -1; margin-top: 8px;">
                                <label style="border-bottom: 1px solid rgba(0,0,0,0.1); padding-bottom: 8px; margin-bottom: 12px; display: block;">自动滚动设置 (当列表过长时)</label>
                            </div>
                            
                            <div class="setting-item">
                                <label>触发间隔 (秒)</label>
                                <input type="number" id="scroll-interval" value="5" min="1" max="60" style="padding: 12px; border-radius: 10px; border: 1px solid rgba(0,0,0,0.1); background: rgba(255,255,255,0.8);">
                            </div>
                            
                            <div class="setting-item">
                                <label>底部停留 (秒)</label>
                                <input type="number" id="scroll-dwell" value="1" min="0" max="10" style="padding: 12px; border-radius: 10px; border: 1px solid rgba(0,0,0,0.1); background: rgba(255,255,255,0.8);">
                            </div>
                            
                            <div class="setting-item">
                                <label>向下滚动速度</label>
                                <input type="number" id="scroll-speed-down" value="50" min="10" max="500" style="padding: 12px; border-radius: 10px; border: 1px solid rgba(0,0,0,0.1); background: rgba(255,255,255,0.8);">
                            </div>
                            
                            <div class="setting-item">
                                <label>向上回滚速度</label>
                                <input type="number" id="scroll-speed-up" value="100" min="10" max="1000" style="padding: 12px; border-radius: 10px; border: 1px solid rgba(0,0,0,0.1); background: rgba(255,255,255,0.8);">
                            </div>
                        </div>
                        <button class="btn" style="width:100%" onclick="saveUiSettings()">应用配置</button>
                    </div>
                    
                    <div class="actions-card">
                        <button class="btn btn-outline" onclick="location.href='/api/logout'">切换账号退出</button>
                        <button class="btn btn-danger" onclick="shutdownServer()">
                            <svg style="width:18px;height:18px;margin-right:6px;" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1"></path></svg>
                            停止服务并退出工具
                        </button>
                    </div>
                </div>
                <script>
                    async function shutdownServer() {
                        if (confirm('真的要关闭服务器吗~，关闭了我就消失了哦~')) {
                            try {
                                await fetch('/api/shutdown', { method: 'POST' });
                                alert('服务器已关闭，现在可以安全退出该页面了喵');
                                // 如果浏览器允许的话，顺手把这个网页也关了
                                window.close();
                            } catch (e) {
                                alert('服务器可能已关闭');
                            }
                        }
                    }
                    let lastTime = 0;
                    let isConnected = false;
                    let pollInterval = null;
                    let statusInterval = null;
                    async function connect() {
                        const roomId = document.getElementById('room-id').value;
                        if (!roomId) { alert('请长官给我入侵目标喵！'); return; }
                        const btn = document.getElementById('connect-btn');
                        btn.disabled = true;
                        btn.textContent = '正在努力入侵直播间喵~';
                        try {
                            const res = await fetch('/api/connect', {
                                method: 'POST',
                                headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                                body: 'roomId=' + roomId
                            });
                            const data = await res.json();
                            if (data.code === 0) {
                                checkStatus();
                                statusInterval = setInterval(() => {
                                    checkStatus();
                                    const statusEl = document.getElementById('status');
                                    if (statusEl && statusEl.textContent.startsWith('已连接')) {
                                        clearInterval(statusInterval);
                                    }
                                }, 500);
                                pollInterval = setInterval(pollDanmu, 1000);
                            } else {
                                alert(data.message || '连接失败喵');
                                btn.disabled = false;
                                btn.textContent = '连接';
                            }
                        } catch (e) {
                            alert('请求失败喵: ' + e.message);
                            btn.disabled = false;
                            btn.textContent = '连接';
                        }
                    }
                    async function disconnect() {
                        try {
                            await fetch('/api/disconnect', { method: 'POST' });
                            checkStatus();
                            if (pollInterval) { clearInterval(pollInterval); pollInterval = null; }
                            if (statusInterval) { clearInterval(statusInterval); statusInterval = null; }
                        } catch (e) {}
                    }
                    function openQueue() {
                        window.open('/queue', 'QueueWindow', 'width=400,height=600,menubar=no,toolbar=no,location=no,status=no');
                    }
                    async function checkStatus() {
                        try {
                            const res = await fetch('/api/status');
                            const data = await res.json();
                            if (data.code === 0) {
                                const status = data.data;
                                const statusEl = document.getElementById('status');
                                const connectBtn = document.getElementById('connect-btn');
                                const disconnectBtn = document.getElementById('disconnect-btn');
                                const queueBtn = document.getElementById('queue-btn');
                                if (status.anchorName || status.realRoomId > 0) {
                                    statusEl.textContent = '已经连接到' + (status.anchorName || '直播间') + '的直播间了喵~ ' + ' (房间号是 ' + status.realRoomId + ' + '喵')';
                                    connectBtn.style.display = 'none';
                                    disconnectBtn.style.display = 'inline-block';
                                    queueBtn.style.display = 'inline-block';
                                    isConnected = true;
                                    
                                    // 重新加载页面时，如果已连接但没启动定时器，则启动
                                    if (!pollInterval) {
                                        pollInterval = setInterval(pollDanmu, 1000);
                                        // 立即拉取一次
                                        pollDanmu();
                                    }
                                } else if (status.isConnecting) {
                                    statusEl.textContent = '正在努力入侵直播间喵~';
                                } else {
                                    statusEl.textContent = '还没连接到直播间喵~';
                                    connectBtn.style.display = 'inline-block';
                                    connectBtn.disabled = false;
                                    connectBtn.textContent = '连接';
                                    disconnectBtn.style.display = 'none';
                                    queueBtn.style.display = 'none';
                                    isConnected = false;
                                    
                                    if (pollInterval) {
                                        clearInterval(pollInterval);
                                        pollInterval = null;
                                    }
                                }
                            }
                        } catch (e) {}
                    }
                    async function pollDanmu() {
                        if (!isConnected) return;
                        try {
                            const res = await fetch('/api/danmu/latest?since=' + lastTime);
                            const data = await res.json();
                            if (data.code === 0 && data.data.length > 0) {
                                const list = document.getElementById('danmu-list');
                                const empty = list.querySelector('.empty');
                                if (empty) empty.remove();
                                data.data.forEach(item => {
                                    const el = document.createElement('div');
                                    el.className = 'danmu-item';
                                    if (item.type === 'danmu') {
                                        let medal = item.medal ? '<span class="medal">' + item.medal + '</span>' : '';
                                        el.innerHTML = medal + '<span class="username">' + item.username + ':</span> <span class="message">' + item.message + '</span>';
                                    } else if (item.type === 'gift') {
                                        el.className += ' gift';
                                        el.innerHTML = '<span class="gift-icon">🎁</span> <span class="username">' + item.username + '</span> 送出 ' + item.giftName + ' x' + item.num;
                                    } else if (item.type === 'enter') {
                                        el.className += ' enter';
                                        el.innerHTML = item.username + ' 进入直播间了喵';
                                    }
                                    list.insertBefore(el, list.firstChild);
                                    lastTime = Math.max(lastTime, item.time);
                                });
                                while (list.children.length > 100) {
                                    list.removeChild(list.lastChild);
                                }
                                document.getElementById('danmu-count').textContent = '(共 ' + list.children.length + ' 条弹~)';
                            }
                        } catch (e) {}
                    }
                    async function loadLastRoom() {
                        try {
                            const res = await fetch('/api/config/lastRoom');
                            const data = await res.json();
                            if (data.code === 0 && data.data.roomId) {
                                document.getElementById('room-id').value = data.data.roomId;
                            }
                        } catch (e) {}
                    }

                    function toggleCustomColor(type) {
                        const preset = document.getElementById(type + '-color-preset').value;
                        const custom = document.getElementById(type + '-color-custom');
                        if (preset === 'custom') {
                            custom.style.display = 'inline-block';
                        } else {
                            custom.style.display = 'none';
                        }
                    }

                    function setColorSelect(type, value) {
                        const presetSelect = document.getElementById(type + '-color-preset');
                        const customInput = document.getElementById(type + '-color-custom');
                        
                        let found = false;
                        for (let i = 0; i < presetSelect.options.length; i++) {
                            if (presetSelect.options[i].value === value) {
                                presetSelect.selectedIndex = i;
                                found = true;
                                break;
                            }
                        }
                        
                        if (!found) {
                            presetSelect.value = 'custom';
                            customInput.value = value;
                        }
                        toggleCustomColor(type);
                    }

                    async function loadUiSettings() {
                        try {
                            const res = await fetch('/api/config/ui');
                            const data = await res.json();
                            if (data.code === 0) {
                                setColorSelect('bg', data.data.bgColor);
                                setColorSelect('item', data.data.itemColor);
                                setColorSelect('font', data.data.fontColor);
                                
                                document.getElementById('scroll-interval').value = data.data.scrollInterval || 5;
                                document.getElementById('scroll-dwell').value = data.data.scrollDwellTime || 1;
                                document.getElementById('scroll-speed-down').value = data.data.scrollSpeedDown || 50;
                                document.getElementById('scroll-speed-up').value = data.data.scrollSpeedUp || 100;
                            }
                        } catch (e) {}
                    }

                    async function saveUiSettings() {
                        const bgPreset = document.getElementById('bg-color-preset').value;
                        const bgColor = bgPreset === 'custom' ? document.getElementById('bg-color-custom').value : bgPreset;
                        
                        const itemPreset = document.getElementById('item-color-preset').value;
                        const itemColor = itemPreset === 'custom' ? document.getElementById('item-color-custom').value : itemPreset;
                        
                        const fontPreset = document.getElementById('font-color-preset').value;
                        const fontColor = fontPreset === 'custom' ? document.getElementById('font-color-custom').value : fontPreset;
                        
                        const scrollInterval = document.getElementById('scroll-interval').value;
                        const scrollDwell = document.getElementById('scroll-dwell').value;
                        const scrollSpeedDown = document.getElementById('scroll-speed-down').value;
                        const scrollSpeedUp = document.getElementById('scroll-speed-up').value;
                        
                        try {
                            const res = await fetch('/api/config/ui', {
                                method: 'POST',
                                headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                                body: `bgColor=${encodeURIComponent(bgColor)}&itemColor=${encodeURIComponent(itemColor)}&fontColor=${encodeURIComponent(fontColor)}&scrollInterval=${scrollInterval}&scrollDwellTime=${scrollDwell}&scrollSpeedDown=${scrollSpeedDown}&scrollSpeedUp=${scrollSpeedUp}`
                            });
                            const data = await res.json();
                            if (data.code === 0) {
                                alert('嗯嗯！已经记到小本本上了喵~');
                            } else {
                                alert(data.message || '可恶！我的小本本不见了，保存失败了喵~');
                            }
                        } catch (e) {
                            alert('请求失败喵: ' + e.message);
                        }
                    }
                    
                    checkStatus();
                    loadLastRoom();
                    loadUiSettings();
                </script>
            </body>
            </html>
            """;
    }
}
