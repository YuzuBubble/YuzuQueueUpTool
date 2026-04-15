package xyz.yuzu.tool.entity;

import lombok.Data;

import java.io.Serializable;

@Data
public class Gift implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private Integer giftId;
    private Short giftType;
    private String giftName;
    private Integer num;
    private String uname;
    private Long uid;
    private Long timestamp;
    private Integer price;
    private Short coinType;
    private Long totalCoin;
    
    public static Gift of(Integer giftId, String giftName, Integer num, 
                          String uname, Long uid, Long timestamp, Integer price) {
        Gift g = new Gift();
        g.giftId = giftId;
        g.giftName = giftName;
        g.num = num;
        g.uname = uname;
        g.uid = uid;
        g.timestamp = timestamp;
        g.price = price;
        return g;
    }
}
