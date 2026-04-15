package xyz.yuzu.tool.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoomInit {
    private Long room_id;
    private Integer short_id;
    private Long uid;
    private Short need_p2p;
    private Boolean is_hidden;
    private Boolean is_portrait;
    private Short live_status;
    private Short hidden_till;
    private Short lock_till;
    private Boolean encrypted;
    private Boolean pwd_verified;
    private Long live_time;
    private Short room_shield;
    private Short is_sp;
    private Short special_type;
}
