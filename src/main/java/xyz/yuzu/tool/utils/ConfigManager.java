package xyz.yuzu.tool.utils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import xyz.yuzu.tool.http.BilibiliApi;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class ConfigManager {

    private static final String CONFIG_FILE = "config.json";
    private static JSONObject config = new JSONObject();

    static {
        loadConfig();
    }

    public static void loadConfig() {
        File file = new File(CONFIG_FILE);
        if (file.exists() && file.isFile()) {
            try {
                String content = new String(Files.readAllBytes(Paths.get(CONFIG_FILE)), StandardCharsets.UTF_8);
                if (!content.isEmpty()) {
                    config = JSON.parseObject(content);
                    if (config.containsKey("cookie") && config.getString("cookie") != null) {
                        BilibiliApi.setCookie(config.getString("cookie"));
                    }
                }
            } catch (IOException e) {
                System.err.println("加载配置文件失败: " + e.getMessage());
            }
        }
    }

    public static synchronized void saveConfig() {
        try {
            String jsonString = JSON.toJSONString(config, SerializerFeature.PrettyFormat);
            Files.write(Paths.get(CONFIG_FILE), jsonString.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            System.err.println("保存配置文件失败: " + e.getMessage());
        }
    }

    public static String getCookie() {
        return config.getString("cookie");
    }

    public static void setCookie(String cookie) {
        config.put("cookie", cookie);
        saveConfig();
        // Update BilibiliApi cookie when config changes
        if (cookie != null && !cookie.isEmpty()) {
            BilibiliApi.setCookie(cookie);
        }
    }

    public static String getLastRoomId() {
        return config.getString("lastRoomId");
    }

    public static void setLastRoomId(String roomId) {
        config.put("lastRoomId", roomId);
        saveConfig();
    }

    public static JSONObject getUiSettings() {
        JSONObject uiSettings = config.getJSONObject("uiSettings");
        if (uiSettings == null) {
            uiSettings = new JSONObject();
            uiSettings.put("bgColor", "#666666");
            uiSettings.put("itemColor", "#555555");
            uiSettings.put("fontColor", "#ffffff");
            uiSettings.put("scrollInterval", 5);
            uiSettings.put("scrollDwellTime", 1);
            uiSettings.put("scrollSpeedDown", 50);
            uiSettings.put("scrollSpeedUp", 100);
        } else {
            if (!uiSettings.containsKey("scrollInterval")) uiSettings.put("scrollInterval", 5);
            if (!uiSettings.containsKey("scrollDwellTime")) uiSettings.put("scrollDwellTime", 1);
            if (!uiSettings.containsKey("scrollSpeedDown")) uiSettings.put("scrollSpeedDown", 50);
            if (!uiSettings.containsKey("scrollSpeedUp")) uiSettings.put("scrollSpeedUp", 100);
        }
        return uiSettings;
    }

    public static void setUiSettings(String bgColor, String itemColor, String fontColor, 
                                     int scrollInterval, int scrollDwellTime, 
                                     int scrollSpeedDown, int scrollSpeedUp) {
        JSONObject uiSettings = new JSONObject();
        uiSettings.put("bgColor", bgColor);
        uiSettings.put("itemColor", itemColor);
        uiSettings.put("fontColor", fontColor);
        uiSettings.put("scrollInterval", scrollInterval);
        uiSettings.put("scrollDwellTime", scrollDwellTime);
        uiSettings.put("scrollSpeedDown", scrollSpeedDown);
        uiSettings.put("scrollSpeedUp", scrollSpeedUp);
        config.put("uiSettings", uiSettings);
        saveConfig();
    }
}
