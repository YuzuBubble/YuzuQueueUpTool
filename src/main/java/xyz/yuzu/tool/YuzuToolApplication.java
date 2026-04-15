package xyz.yuzu.tool;

import xyz.yuzu.tool.web.WebServer;

public class YuzuToolApplication {
    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("       YuzuTool - B站弹幕获取工具");
        System.out.println("========================================");
        
        int port = 8080;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("端口参数无效，使用默认端口 8080");
            }
        }
        
        WebServer server = new WebServer(port);
        server.start();
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n正在关闭服务器...");
            server.stop();
        }));
    }
}
