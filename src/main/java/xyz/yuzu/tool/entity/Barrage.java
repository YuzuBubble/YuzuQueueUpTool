package xyz.yuzu.tool.entity;

import lombok.Data;

import java.io.Serializable;

@Data
public class Barrage implements Serializable, Cloneable {
    private static final long serialVersionUID = 1L;
    
    private Long uid;
    private String uname;
    private String msg;
    private Short msgType;
    private Long timestamp;
    private Short manager;
    private Short vip;
    private Short svip;
    private Short medalLevel;
    private String medalName;
    private String medalAnchor;
    private Long medalRoom;
    private Short uguard;
    
    public static Barrage of(Long uid, String uname, String msg, Long timestamp,
                             Short medalLevel, String medalName, String medalAnchor,
                             Long medalRoom, Short uguard) {
        Barrage b = new Barrage();
        b.uid = uid;
        b.uname = uname;
        b.msg = msg;
        b.timestamp = timestamp;
        b.medalLevel = medalLevel;
        b.medalName = medalName;
        b.medalAnchor = medalAnchor;
        b.medalRoom = medalRoom;
        b.uguard = uguard;
        return b;
    }
}
