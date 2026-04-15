package xyz.yuzu.tool.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HostServer implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String host;
    private Integer port;
    private Integer wss_port;
    private Integer ws_port;
}
