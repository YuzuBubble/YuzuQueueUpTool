package xyz.yuzu.tool.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserNav {
    
    private Long mid;
    private WbiImg wbiImg;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WbiImg {
        private String imgUrl;
        private String subUrl;
    }
}
