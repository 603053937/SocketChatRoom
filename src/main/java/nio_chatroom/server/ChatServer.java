package nio_chatroom.server;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Set;

public class ChatServer {

    private static final int DEFAULT_PORT = 8888;
    private static final String QUIT = "quit";
    // Buffer缓冲区大小
    private static final int BUFFER = 1024;
    // 服务器端IO的通道
    private ServerSocketChannel server;
    // 选择器
    private Selector selector;
    // 读写Buffer
    private ByteBuffer rBuffer = ByteBuffer.allocate(BUFFER);
    private ByteBuffer wBuffer = ByteBuffer.allocate(BUFFER);
    private Charset charset = StandardCharsets.UTF_8;
    private int port;

    public ChatServer() {
        this(DEFAULT_PORT);
    }

    public ChatServer(int port) {
        this.port = port;
    }

    private void start() {
        try {
            // 打开一个新的通道
            server = ServerSocketChannel.open();
            // 设置为非阻塞，默认设置为阻塞
            server.configureBlocking(false);
            // 绑定serverSocket到监听端口
            server.socket().bind(new InetSocketAddress(port));
            // 打开一个选择器
            selector = Selector.open();
            // 注册，让selector监听accept()事件
            server.register(selector, SelectionKey.OP_ACCEPT);
            System.out.println("启动服务器， 监听端口：" + port + "...");

            while (true) {
                // 开始监听，阻塞
                selector.select();
                // 返回selector监听到的触发事件的信息
                Set<SelectionKey> selectionKeys = selector.selectedKeys();
                for (SelectionKey key : selectionKeys) {
                    // 处理被触发的事件
                    handles(key);
                }
                selectionKeys.clear();
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // 关闭selector ， 会将其上注册的通道解除注册并关闭
            close(selector);
        }

    }

    private void handles(SelectionKey key) throws IOException {
        // ACCEPT事件 - 和客户端建立了连接
        if (key.isAcceptable()) {
            // 获取SelectionKey所在channel
            ServerSocketChannel server = (ServerSocketChannel) key.channel();
            // 接受客户端连接请求，获取客户端的通道
            SocketChannel client = server.accept();
            client.configureBlocking(false);
            // 注册selector，使其监听read事件
            client.register(selector, SelectionKey.OP_READ);
            System.out.println(getClientName(client) + "已连接");
        }
        // READ事件 - 客户端发送了消息
        else if (key.isReadable()) {
            // 获取SelectionKey所在channel
            SocketChannel client = (SocketChannel) key.channel();
            // 从channel读取数据
            String fwdMsg = receive(client);
            if (fwdMsg.isEmpty()) {
                // 客户端异常
                // cancel() 将key在selector上的注册取消
                // selector不再监听该通道上的read事件
                key.cancel();
                // 取消了key，更新了selector监听任务
                // selector处于阻塞状态，将其唤醒
                // 重新审视监听任务
                selector.wakeup();
            } else {
                System.out.println(getClientName(client) + ":" + fwdMsg);

                // 检查用户是否退出
                if (readyToQuit(fwdMsg)) {
                    key.cancel();
                    selector.wakeup();
                    System.out.println(getClientName(client) + "已断开");
                }

                // 转发消息给其余客户端
                forwardMessage(client, fwdMsg);
            }

        }
    }

    private void forwardMessage(SocketChannel client, String fwdMsg) throws IOException {
        for (SelectionKey key : selector.keys()) {
            Channel connectedClient = key.channel();
            // 跳过ServerSocketChannel
            if (connectedClient instanceof ServerSocketChannel) {
                continue;
            }

            // channel未被关闭且channel不是发送方
            if (key.isValid() && !client.equals(connectedClient)) {
                wBuffer.clear();
                // 写入
                wBuffer.put(charset.encode(getClientName(client) + ":" + fwdMsg));
                // 将缓冲区的读写模式转换
                wBuffer.flip();
                // wBuffer中的数据写入channel
                while (wBuffer.hasRemaining()) {
                    ((SocketChannel) connectedClient).write(wBuffer);
                }
            }
        }
    }

    private String receive(SocketChannel client) throws IOException {
        // 读取消息前，清空buffer信息
        rBuffer.clear();
        // 将channel中的信息读取到rbuffer中
        // read()返回值是本次读取字节数目
        while (client.read(rBuffer) > 0) ;
        // 将缓冲区的读写模式转换
        rBuffer.flip();
        return String.valueOf(charset.decode(rBuffer));
    }

    private String getClientName(SocketChannel client) {
        return "客户端[" + client.socket().getPort() + "]";
    }

    private boolean readyToQuit(String msg) {
        return QUIT.equals(msg);
    }

    private void close(Closeable closable) {
        if (closable != null) {
            try {
                closable.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        ChatServer chatServer = new ChatServer(7777);
        chatServer.start();
    }
}
