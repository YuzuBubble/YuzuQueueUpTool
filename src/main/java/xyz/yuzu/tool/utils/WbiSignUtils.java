package xyz.yuzu.tool.utils;

import java.security.MessageDigest;
import java.util.Map;
import java.util.TreeMap;

public class WbiSignUtils {
    
    private static final int[] MIXIN_KEY_ENC_TAB = {
            46, 47, 18, 2, 53, 8, 23, 32, 15, 50,
            10, 31, 58, 3, 45, 35, 27, 43, 5, 49,
            33, 9, 42, 19, 29, 28, 14, 39, 12, 38,
            41, 13, 37, 48, 7, 16, 24, 55, 40, 61,
            26, 17, 0, 1, 60, 51, 30, 4, 22, 25,
            54, 21, 56, 59, 6, 63, 57, 62, 11, 36,
            20, 34, 44, 52
    };
    
    public static String getMixinKey(String orig) {
        StringBuilder temp = new StringBuilder();
        for (int n : MIXIN_KEY_ENC_TAB) {
            if (n < orig.length()) {
                temp.append(orig.charAt(n));
            }
        }
        return temp.toString().substring(0, Math.min(32, temp.length()));
    }
    
    public static String getWbiSign(Map<String, String> params, String imgKey, String subKey) {
        String key = imgKey + subKey;
        String mixinKey = getMixinKey(key);
        
        Map<String, String> sortedParams = new TreeMap<>(params);
        StringBuilder query = new StringBuilder();
        for (Map.Entry<String, String> entry : sortedParams.entrySet()) {
            if (query.length() > 0) {
                query.append("&");
            }
            query.append(entry.getKey()).append("=").append(entry.getValue());
        }
        
        return md5(query + mixinKey);
    }
    
    public static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
    
    public static String extractKeyFromUrl(String url) {
        int lastSlash = url.lastIndexOf('/');
        int lastDot = url.lastIndexOf('.');
        if (lastSlash >= 0 && lastDot > lastSlash) {
            return url.substring(lastSlash + 1, lastDot);
        }
        return "";
    }
}
