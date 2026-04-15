package xyz.yuzu.tool.utils;

import org.apache.commons.compress.compressors.brotli.BrotliCompressorInputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.zip.InflaterOutputStream;

public class ByteUtils {
    
    public static byte[] byteMerger(byte[] byte1, byte[] byte2) {
        byte[] result = new byte[byte1.length + byte2.length];
        System.arraycopy(byte1, 0, result, 0, byte1.length);
        System.arraycopy(byte2, 0, result, byte1.length, byte2.length);
        return result;
    }
    
    public static byte[] decodeValue(ByteBuffer buffer) {
        int len = buffer.limit() - buffer.position();
        byte[] bytes = new byte[len];
        buffer.get(bytes);
        return bytes;
    }
    
    public static byte[] subBytes(byte[] bytes, int begin, int count) {
        byte[] result = new byte[count];
        System.arraycopy(bytes, begin, result, 0, count);
        return result;
    }
    
    public static byte[] BytesTozlibInflate(byte[] bs) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (InflaterOutputStream zos = new InflaterOutputStream(bos)) {
            zos.write(bs);
            return bos.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
    
    public static byte[] BytesToBrotliInflate(byte[] bs) {
        try (BrotliCompressorInputStream brotli = new BrotliCompressorInputStream(new ByteArrayInputStream(bs))) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            int readByte;
            while ((readByte = brotli.read()) != -1) {
                bos.write(readByte);
            }
            return bos.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
    
    public static long bytesToLong(byte[] bs) {
        if (bs.length == 4) {
            return ((bs[0] & 0xffL) << 24) | 
                   ((bs[1] & 0xffL) << 16) | 
                   ((bs[2] & 0xffL) << 8) | 
                   (bs[3] & 0xffL);
        } else if (bs.length == 8) {
            return ((bs[0] & 0xffL) << 56) | 
                   ((bs[1] & 0xffL) << 48) | 
                   ((bs[2] & 0xffL) << 40) | 
                   ((bs[3] & 0xffL) << 32) |
                   ((bs[4] & 0xffL) << 24) | 
                   ((bs[5] & 0xffL) << 16) | 
                   ((bs[6] & 0xffL) << 8) | 
                   (bs[7] & 0xffL);
        }
        return 0;
    }
}
