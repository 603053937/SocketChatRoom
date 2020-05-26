package bio_chatroom.server;

import javax.swing.tree.FixedHeightLayoutCache;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatServer {

    private int DEFAULT_PORT = 8888;
    private final String QUIT = "quit";

    // 线程池
    private ExecutorService executorService;
    private ServerSocket serverSocket;
    // 保存客户端端口与向该客户端发送消息的writer
    // 存储在线用户
    private Map<Integer, Writer> connectedClients;

    // 构造函数，初始化connectedClients
    public ChatServer() {
        // 选择固定数量的线程池，防止大流量客户请求
        executorService = Executors.newFixedThreadPool(18);
        connectedClients = new HashMap<>();
    }

    // 客户端连接服务器
    // 可能存在多个线程connectedClients.put(port, writer);
    // 所以使用synchronized确保线程安全
    public synchronized void addClient(Socket socket) throws IOException {
        if (socket != null) {
            int port = socket.getPort();
            BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream())
            );
            connectedClients.put(port, writer);
            System.out.println("客户端[" + port + "]已连接到服务器");
        }
    }

    // 客户端与服务器断开连接
    // 可能存在多个线程connectedClients.remove(port);
    public synchronized void removeClient(Socket socket) throws IOException {
        if (socket != null) {
            int port = socket.getPort();
            if (connectedClients.containsKey(port)) {
                // 关闭最外层的writer,相当于关闭socket
                connectedClients.get(port).close();
            }
            // 从map中移除该键值对
            connectedClients.remove(port);
            System.out.println("客户端[" + port + "]已断开连接");
        }
    }

    // 转发消息给聊天室内其他用户
    // 遍历线程，同样会不安全
    public synchronized void forwardMessage(Socket socket, String fwdMsg) throws IOException {
        // 遍历目前在线客户
        for (Integer id : connectedClients.keySet()) {
            // 找出发送者以外的其余客户
            if (!id.equals(socket.getPort())) {
                Writer writer = connectedClients.get(id);
                writer.write(fwdMsg);
                writer.flush();
            }
        }
    }

    // 退出聊天室标记位
    public boolean readyToQuit(String msg) {
        return QUIT.equals(msg);
    }

    // 关闭服务器资源
    // serverSocket != null;
    // serverSocket.close(); 线程不安全
    public synchronized void close() {
        if (serverSocket != null) {
            try {
                serverSocket.close();
                System.out.println("关闭serverSocket");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // 启动服务器
    public void start() {

        try {
            // 绑定监听端口
            serverSocket = new ServerSocket(DEFAULT_PORT);
            System.out.println("启动服务器，监听端口：" + DEFAULT_PORT + "...");

            while (true) {
                // 等待客户端连接
                // accept() 阻塞式等待客户端连接
                Socket socket = serverSocket.accept();
                // 创建ChatHandler线程
                // ChatHandler实现Runnable接口 传入run()
                executorService.execute(new ChatHandler(this,socket));
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            close();
        }
    }

    public static void main(String[] args) {
        ChatServer server = new ChatServer();
        server.start();
    }

}