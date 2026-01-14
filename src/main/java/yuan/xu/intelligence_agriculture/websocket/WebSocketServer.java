package yuan.xu.intelligence_agriculture.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * WebSocket 服务端类
 * 监听 /ws/sensor_date 路径，用于将实时传感器数据推送到已连接的前端页面
 */
@ServerEndpoint("/ws/sensor_date")
@Component
@Slf4j
public class WebSocketServer {

    // 用来存放每个客户端对应的 WebSocketServer 对象
    private static CopyOnWriteArraySet<WebSocketServer> webSocketSet = new CopyOnWriteArraySet<>();
    // 与某个客户端的连接会话，需要通过它来给客户端发送数据
    private Session session;

    /**
     * 连接建立成功调用的方法
     */
    @OnOpen
    public void onOpen(Session session) {
        this.session = session;
        webSocketSet.add(this); // 加入 set 中
        log.info("WebSocket 连接建立: {}, 当前在线人数为: {}", session.getId(), webSocketSet.size());
    }

    /**
     * 连接关闭调用的方法
     */
    @OnClose
    public void onClose() {
        webSocketSet.remove(this); // 从 set 中删除
        log.info("WebSocket 连接关闭: {}, 当前在线人数为: {}", session.getId(), webSocketSet.size());
    }

    /**
     * 发生错误时调用
     */
    @OnError
    public void onError(Session session, Throwable error) {
        log.error("WebSocket 发生错误: {}, 原因: {}", session.getId(), error.getMessage());
        error.printStackTrace();
    }

    /**
     * 实现服务器主动推送消息
     */
    public void sendMessage(String message) {
        try {
            // getBasicRemote() 是同步发送，getAsyncRemote() 是异步发送
            this.session.getBasicRemote().sendText(message);
        } catch (IOException e) {
            log.error("WebSocket 发送消息失败: {}", e.getMessage());
        }
    }

    /**
     * 群发自定义消息（静态方法，方便在 Service 层调用）
     * @param message 发送的消息内容（JSON 字符串）
     */
    public static void sendInfo(String message) {
        log.debug("推送实时消息到所有客户端: {}", message);
        for (WebSocketServer item : webSocketSet) {
            try {
                item.sendMessage(message);
            } catch (Exception e) {
                // 如果某个客户端推送失败，跳过，不影响其他客户端
                continue;
            }
        }
    }
}
