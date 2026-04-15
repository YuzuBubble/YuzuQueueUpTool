package xyz.yuzu.tool.http;

import com.alibaba.fastjson.JSONObject;
import okhttp3.*;
import xyz.yuzu.tool.entity.Conf;
import xyz.yuzu.tool.entity.Qrcode;
import xyz.yuzu.tool.entity.Room;
import xyz.yuzu.tool.entity.RoomInit;
import xyz.yuzu.tool.entity.UserNav;
import xyz.yuzu.tool.utils.WbiSignUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class BilibiliApi {
    
    private static final OkHttpClient client;
    
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.138 Safari/537.36";
    
    private static String userCookie = null;
    
    static {
        client = new OkHttpClient.Builder()
                .readTimeout(15, TimeUnit.SECONDS)
                .connectTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build();
    }
    
    public static void setCookie(String cookie) {
        userCookie = cookie;
    }
    
    public static String getCookie() {
        return userCookie;
    }
    
    public static Qrcode generateQrcode() {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", USER_AGENT);
        headers.put("Referer", "https://www.bilibili.com/");
        
        String response = httpGet("https://passport.bilibili.com/x/passport-login/web/qrcode/generate?source=main-fe-header", headers, null);
        
        if (response == null) return null;
        
        JSONObject json = JSONObject.parseObject(response);
        
        if (json.getShort("code") != 0) return null;
        
        JSONObject data = json.getJSONObject("data");
        Qrcode qrcode = new Qrcode();
        qrcode.setUrl(data.getString("url"));
        qrcode.setQrcode_key(data.getString("qrcode_key"));
        
        return qrcode;
    }
    
    public static int qrcodePoll(String qrcodeKey) {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", USER_AGENT);
        headers.put("Referer", "https://www.bilibili.com/");
        
        Map<String, String> params = new HashMap<>();
        params.put("qrcode_key", qrcodeKey);
        params.put("source", "main-fe-header");
        
        Response response = httpGetWithResponse("https://passport.bilibili.com/x/passport-login/web/qrcode/poll", headers, params);
        
        if (response == null) return -1;
        
        try {
            String body = response.body().string();
            JSONObject json = JSONObject.parseObject(body);
            JSONObject data = json.getJSONObject("data");
            if (data == null) return -1;
            
            int code = data.getIntValue("code");
            
            if (code == 0) {
                Headers headers2 = response.headers();
                List<String> cookies = headers2.values("Set-Cookie");
                Set<String> cookieSet = new HashSet<>();
                for (String string : cookies) {
                    int idx = string.indexOf(";");
                    if (idx > 0) {
                        cookieSet.add(string.substring(0, idx));
                    }
                }
                StringBuilder stringBuilder = new StringBuilder(100);
                Iterator<String> iterable = cookieSet.iterator();
                while (iterable.hasNext()) {
                    stringBuilder.append(iterable.next());
                    if (iterable.hasNext()) {
                        stringBuilder.append(";");
                    }
                }
                userCookie = stringBuilder.toString();
                xyz.yuzu.tool.utils.ConfigManager.setCookie(userCookie);
            }
            
            return code;
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }
    
    public static RoomInit getRoomInit(long roomId) {
        String url = "https://api.live.bilibili.com/room/v1/Room/room_init?id=" + roomId;
        
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", USER_AGENT);
        
        String response = httpGet(url, headers, null);
        
        if (response == null) return null;
        
        JSONObject json = JSONObject.parseObject(response);
        if (json.getShort("code") != 0) return null;
        
        return json.getObject("data", RoomInit.class);
    }
    
    public static String getAnchorName(long roomId) {
        String url = "https://api.live.bilibili.com/xlive/web-room/v1/index/getInfoByRoom?room_id=" + roomId;
        
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", USER_AGENT);
        headers.put("Referer", "https://live.bilibili.com/" + roomId);
        if (userCookie != null) {
            headers.put("Cookie", userCookie);
        }
        
        String response = httpGet(url, headers, null);
        
        if (response == null) return null;
        
        JSONObject json = JSONObject.parseObject(response);
        if (json.getShort("code") != 0) return null;
        
        JSONObject data = json.getJSONObject("data");
        if (data == null) return null;
        
        JSONObject anchorInfo = data.getJSONObject("anchor_info");
        if (anchorInfo == null) return null;
        
        JSONObject baseInfo = anchorInfo.getJSONObject("base_info");
        if (baseInfo == null) return null;
        
        return baseInfo.getString("uname");
    }
    
    public static Room getRoomData(long roomId) {
        String url = "https://api.live.bilibili.com/room_ex/v1/RoomNews/get?roomid=" + roomId;
        
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", USER_AGENT);
        headers.put("Referer", "https://live.bilibili.com/" + roomId);
        
        String response = httpGet(url, headers, null);
        
        if (response == null) return null;
        
        JSONObject json = JSONObject.parseObject(response);
        if (json.getShort("code") != 0) return null;
        
        return json.getObject("data", Room.class);
    }
    
    public static String getUserAvatar(long uid) {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", USER_AGENT);
        
        String response = httpGet("https://api.bilibili.com/x/web-interface/card?mid=" + uid, headers, null);
        if (response == null) return null;
        
        try {
            JSONObject json = JSONObject.parseObject(response);
            if (json.getShort("code") == 0) {
                JSONObject data = json.getJSONObject("data");
                if (data != null) {
                    JSONObject card = data.getJSONObject("card");
                    if (card != null) {
                        return card.getString("face");
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
    
    public static UserNav getUserNav() {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", USER_AGENT);
        if (userCookie != null) {
            headers.put("Cookie", userCookie);
        }
        
        String response = httpGet("https://api.bilibili.com/x/web-interface/nav", headers, null);
        
        if (response == null) return null;
        
        JSONObject json = JSONObject.parseObject(response);
        
        if (json.getShort("code") != 0) return null;
        
        JSONObject data = json.getJSONObject("data");
        if (data == null) return null;
        
        JSONObject wbiImg = data.getJSONObject("wbi_img");
        if (wbiImg == null) return null;
        
        UserNav userNav = new UserNav();
        userNav.setMid(data.getLong("mid"));
        
        UserNav.WbiImg img = new UserNav.WbiImg();
        img.setImgUrl(wbiImg.getString("img_url"));
        img.setSubUrl(wbiImg.getString("sub_url"));
        userNav.setWbiImg(img);
        
        return userNav;
    }
    
    public static Conf getDanmuConfWithSign(long roomId, UserNav userNav) {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", USER_AGENT);
        headers.put("Referer", "https://live.bilibili.com/" + roomId);
        if (userCookie != null) {
            headers.put("Cookie", userCookie);
        }
        
        long timestamp = System.currentTimeMillis() / 1000;
        
        Map<String, String> params = new HashMap<>();
        params.put("id", String.valueOf(roomId));
        params.put("type", "0");
        params.put("wts", String.valueOf(timestamp));
        params.put("web_location", "444.8");
        
        String imgKey = WbiSignUtils.extractKeyFromUrl(userNav.getWbiImg().getImgUrl());
        String subKey = WbiSignUtils.extractKeyFromUrl(userNav.getWbiImg().getSubUrl());
        
        String wbiSign = WbiSignUtils.getWbiSign(params, imgKey, subKey);
        params.put("w_rid", wbiSign);
        
        String response = httpGet("https://api.live.bilibili.com/xlive/web-room/v1/index/getDanmuInfo", headers, params);
        
        if (response == null) return null;
        
        JSONObject json = JSONObject.parseObject(response);
        
        if (json.getShort("code") != 0) return null;
        
        return json.getObject("data", Conf.class);
    }
    
    private static String httpGet(String url, Map<String, String> headers, Map<String, String> params) {
        if (params != null && !params.isEmpty()) {
            StringBuilder sb = new StringBuilder("?");
            for (Map.Entry<String, String> entry : params.entrySet()) {
                sb.append(entry.getKey()).append("=").append(entry.getValue()).append("&");
            }
            sb.deleteCharAt(sb.length() - 1);
            url = url + sb.toString();
        }
        
        Request.Builder builder = new Request.Builder().url(url).get();
        
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                builder.addHeader(entry.getKey(), entry.getValue());
            }
        }
        
        Request request = builder.build();
        
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                return response.body().string();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        return null;
    }
    
    private static Response httpGetWithResponse(String url, Map<String, String> headers, Map<String, String> params) {
        if (params != null && !params.isEmpty()) {
            StringBuilder sb = new StringBuilder("?");
            for (Map.Entry<String, String> entry : params.entrySet()) {
                sb.append(entry.getKey()).append("=").append(entry.getValue()).append("&");
            }
            sb.deleteCharAt(sb.length() - 1);
            url = url + sb.toString();
        }
        
        Request.Builder builder = new Request.Builder().url(url).get();
        
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                builder.addHeader(entry.getKey(), entry.getValue());
            }
        }
        
        Request request = builder.build();
        
        try {
            return client.newCall(request).execute();
        } catch (IOException e) {
            return null;
        }
    }
}
