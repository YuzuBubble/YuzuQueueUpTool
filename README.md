# YuzuTool - B站弹幕自动排队工具

[![Java](https://img.shields.io/badge/Java-17-blue.svg)](https://java.com)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

> 基于 [Bilibili_Danmuji](https://github.com/BanqiJane/Bilibili_Danmuji) 项目的弹幕核心功能，专门为直播间设计的弹幕自动排队系统

## 🎯 项目特色

**核心功能：弹幕自动排队系统**

- 🎪 **智能排队** - 自动为直播间弹幕创建排队列表
- 📊 **实时显示** - Web界面实时展示排队用户列表
- 🔄 **动态更新** - 排队列表实时更新，支持手动调整
- 🎮 **直播集成** - 可直接在直播姬/OBS中显示排队界面

## ✨ 功能亮点

### 弹幕排队核心功能
- **自动排队** - 用户发送弹幕后自动加入排队列表
- **队列管理** - 支持手动调整排队顺序
- **实时同步** - 排队状态实时同步到所有观众
- **简洁界面** - 专为直播设计的简洁排队显示界面

### 基础弹幕功能
- **实时弹幕** - 获取直播间所有弹幕消息
- **礼物监控** - 显示用户送出的礼物信息
- **用户提醒** - 用户进入直播间实时提醒
- **二维码登录** - 安全便捷的B站账号登录

## 🚀 快速开始
```bash
# 克隆项目
git clone https://github.com/YOUR_USERNAME/YuzuTool.git
cd YuzuTool

# 编译运行
./gradlew build
java -jar build/libs/YuzuTool-*.jar
```

### 环境要求
- Java 17
- 网络连接（用于连接B站服务器）

### 一键启动

```bash
# 编译项目
./gradlew build

# 运行程序（默认端口8080）
java -jar build/libs/YuzuTool-0.0.1jar

# 指定端口运行
java -jar build/libs/YuzuTool-0.0.1.jar 9090
```

### 使用步骤

1. **启动程序** - 运行jar包，程序将在后台启动Web服务器
2. **扫码登录** - 浏览器访问 `http://localhost:8080`，扫码登录B站账号
3. **连接直播间** - 输入直播间房间号，点击"连接直播间"
4. **显示排队** - 访问 `http://localhost:8080/queue` 查看排队界面
5. **直播集成** - 在直播姬/OBS中添加浏览器源，URL设置为排队页面地址 `http://localhost:8080/queue`

## 🎮 直播场景应用

### 游戏直播排队
- 游戏连麦排队
- 抽奖活动排队
- 互动问答排队
- 观众互动排队

### 排队界面展示
排队界面设计简洁，适合在直播画面中显示：
- 当前排队用户列表
- 排队序号和用户名
- 实时更新状态
- 支持手动调整顺序

## 📁 项目架构

### 基于 Bilibili_Danmuji
本项目基于成熟的 [Bilibili_Danmuji](https://github.com/BanqiJane/Bilibili_Danmuji) 项目：

- ✅ **稳定可靠** - 基于经过验证的弹幕获取核心
- ✅ **协议兼容** - 使用WebSocket协议连接B站服务器
- ✅ **功能完善** - 继承基础弹幕、礼物、用户管理功能
- ✅ **持续维护** - 基于活跃维护的开源项目

### 技术栈
```
核心架构：
├── 弹幕协议层 - WebSocket连接B站服务器
├── 数据处理层 - 弹幕解析和排队逻辑
├── Web服务层 - Javalin框架提供Web界面
└── 前端展示层 - 简洁的排队界面显示
```

## 🔧 核心功能详解

### 排队系统实现
```java
// 排队逻辑核心代码示例
public class QueueManager {
    private CopyOnWriteArrayList<QueueUser> queueList = new CopyOnWriteArrayList<>();
    
    // 用户发送弹幕时自动加入排队
    public void addToQueue(String username, String message) {
        QueueUser user = new QueueUser(username, message, System.currentTimeMillis());
        queueList.add(user);
        // 触发界面更新
        notifyQueueUpdate();
    }
    
    // 手动调整排队顺序
    public void adjustQueue(int fromIndex, int toIndex) {
        // 实现排队顺序调整逻辑
    }
}
```

### Web界面功能
- **主控制台** (`/`) - 直播间连接和管理
- **排队界面** (`/queue`) - 实时排队列表显示
- **登录页面** (`/login`) - B站账号二维码登录

## 🎯 使用场景示例

### 场景1：原神深渊排队
```
观众发送弹幕："排队"
→ 自动加入排队列表
→ 主播在排队界面看到观众排队
→ 按顺序上号帮打深渊
```

### 场景2：抽奖活动排队
```
观众发送弹幕参与抽奖
→ 系统自动记录参与顺序
→ 显示在直播画面中的排队列表
→ 按排队顺序进行抽奖
```

## 📊 API接口

### 排队相关API
```
GET  /api/queue/list      # 获取当前排队列表
POST /api/queue/adjust    # 调整排队顺序
POST /api/queue/remove    # 移除排队用户
```

### 基础功能API
```
GET  /api/qrcode/url      # 获取登录二维码
POST /api/room/connect    # 连接直播间
GET  /api/danmu/list     # 获取弹幕列表
```

## 🛠️ 开发指南

### 环境配置
```bash
# 克隆项目
git clone <项目地址>

# 导入IDE（推荐IntelliJ IDEA）
# 配置Java SDK 17

# 构建项目
./gradlew build
```

### 自定义开发
- 修改排队逻辑：`WebServer.java` 中的排队管理部分
- 调整界面样式：修改HTML模板中的CSS样式
- 添加新功能：基于现有的Web服务架构扩展

## 🤝 贡献与反馈

欢迎提交Issue和Pull Request来改进排队功能！

### 问题反馈
- 功能建议
- Bug报告
- 使用问题

### 开发贡献
- 排队算法优化
- 界面美化
- 功能扩展

## 📄 许可证

本项目基于MIT许可证开源，继承自Bilibili_Danmuji项目的开源精神。

## ⚠️ 使用须知

- 请遵守B站用户协议和直播规范
- 合理使用排队功能，避免影响直播体验
- 登录信息会保存在本地，注意账号安全
- 本工具仅供个人学习和技术交流使用

## 🔗 相关链接

- [Bilibili_Danmuji 原项目](https://github.com/BanqiJane/Bilibili_Danmuji)
- [B站开放平台](https://open.bilibili.com/)
- [Java官方文档](https://docs.oracle.com/javase/17/docs/)

---

**如果这个弹幕排队工具对您的直播有帮助，请给个Star⭐支持一下！**
*~~用DeepseekV3.1跑的Readme，是不是有点太专业了(雾)~~*