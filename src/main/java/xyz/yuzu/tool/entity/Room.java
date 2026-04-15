package xyz.yuzu.tool.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Room implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String roomid;
    private String uid;
    private String content;
    private String time;
    private String statue;
    private String uname;
}
