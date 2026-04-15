package xyz.yuzu.tool.entity;

import lombok.Data;

import java.io.Serializable;

@Data
public class PackageHeader implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private int length;
    private short headLength;
    private short version;
    private int type;
    private int other;
    
    public static byte[] pack(int length, int headLength, int version, int type, int other) {
        byte[] bytes = new byte[headLength];
        
        bytes[0] = (byte) (length >>> 24);
        bytes[1] = (byte) (length >>> 16);
        bytes[2] = (byte) (length >>> 8);
        bytes[3] = (byte) length;
        
        bytes[4] = (byte) (headLength >>> 8);
        bytes[5] = (byte) headLength;
        
        bytes[6] = (byte) (version >>> 8);
        bytes[7] = (byte) version;
        
        bytes[8] = (byte) (type >>> 24);
        bytes[9] = (byte) (type >>> 16);
        bytes[10] = (byte) (type >>> 8);
        bytes[11] = (byte) type;
        
        bytes[12] = (byte) (other >>> 24);
        bytes[13] = (byte) (other >>> 16);
        bytes[14] = (byte) (other >>> 8);
        bytes[15] = (byte) other;
        
        return bytes;
    }
    
    public static PackageHeader unpack(byte[] bytes) {
        PackageHeader header = new PackageHeader();
        
        header.length = ((bytes[0] & 0xFF) << 24) | 
                        ((bytes[1] & 0xFF) << 16) | 
                        ((bytes[2] & 0xFF) << 8) | 
                        (bytes[3] & 0xFF);
        
        header.headLength = (short) (((bytes[4] & 0xFF) << 8) | (bytes[5] & 0xFF));
        
        header.version = (short) (((bytes[6] & 0xFF) << 8) | (bytes[7] & 0xFF));
        
        header.type = ((bytes[8] & 0xFF) << 24) | 
                      ((bytes[9] & 0xFF) << 16) | 
                      ((bytes[10] & 0xFF) << 8) | 
                      (bytes[11] & 0xFF);
        
        header.other = ((bytes[12] & 0xFF) << 24) | 
                       ((bytes[13] & 0xFF) << 16) | 
                       ((bytes[14] & 0xFF) << 8) | 
                       (bytes[15] & 0xFF);
        
        return header;
    }
}
