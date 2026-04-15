package xyz.yuzu.tool.utils;

public class HexUtils {
    
    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();
    
    private static final int[] DEC = {
            00, 01, 02, 03, 04, 05, 06, 07, 8, 9, -1, -1, -1, -1, -1, -1,
            -1, 10, 11, 12, 13, 14, 15, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, 10, 11, 12, 13, 14, 15,
    };
    
    public static byte[] fromHexString(String input) {
        if (input == null) {
            return null;
        }
        
        if ((input.length() & 1) == 1) {
            throw new IllegalArgumentException("Input string must contain an even number of characters");
        }
        
        char[] inputChars = input.toCharArray();
        byte[] result = new byte[input.length() >> 1];
        for (int i = 0; i < result.length; i++) {
            int upperNibble = getDec(inputChars[2 * i]);
            int lowerNibble = getDec(inputChars[2 * i + 1]);
            if (upperNibble < 0 || lowerNibble < 0) {
                throw new IllegalArgumentException("Input string must only contain hex digits");
            }
            result[i] = (byte) ((upperNibble << 4) + lowerNibble);
        }
        return result;
    }
    
    private static int getDec(int index) {
        try {
            return DEC[index - '0'];
        } catch (ArrayIndexOutOfBoundsException ex) {
            return -1;
        }
    }
    
    public static String toHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(HEX_CHARS[(b >> 4) & 0x0F]);
            sb.append(HEX_CHARS[b & 0x0F]);
        }
        return sb.toString();
    }
}
