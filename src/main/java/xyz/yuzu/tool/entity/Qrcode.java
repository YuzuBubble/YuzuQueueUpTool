package xyz.yuzu.tool.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Qrcode {
    private String url;
    private String qrcode_key;
}
