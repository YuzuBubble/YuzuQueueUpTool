package xyz.yuzu.tool.entity;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.annotation.JSONType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JSONType(orders = {"uid", "roomid", "protover", "platform", "buvid", "type", "key"})
public class FristSecurityData implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private Long uid = 0L;
    private Long roomid;
    private Integer protover = 3;
    private String platform = "web";
    private String buvid = "";
    private Integer type = 2;
    private String key;
    
    public FristSecurityData(Long roomid, String key) {
        this.roomid = roomid;
        this.key = key;
    }
    
    public FristSecurityData(Long uid, Long roomid, String key) {
        this.uid = uid;
        this.roomid = roomid;
        this.key = key;
    }
    
    public String toJson() {
        return JSON.toJSONString(this);
    }
}
