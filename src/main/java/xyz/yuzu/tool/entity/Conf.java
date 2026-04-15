package xyz.yuzu.tool.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Conf implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private Short business_id;
    private String group;
    private List<HostServer> host_list;
    private Short max_delay;
    private Short refresh_rate;
    private Short refresh_row_factor;
    private String token;
}
