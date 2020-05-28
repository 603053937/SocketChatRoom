package aio_chatroom.server;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatServer {

    private static final String LOCALHOST = "localhost";
    private static final int DEFAULT_PORT = 8888;
    private static final String QUIT = "quit";
    private static final int BUFFER = 1024;
    private static final int THREADPOOL_SIZE = 20;

    private AsynchronousChannelGroup channelGroup;
    private AsynchronousServerSocketChannel serverChannel;
    // 存储ClientHandler , 可由此获得ClientChannel
    private List<ClientHandler> connectedClients;
    private Charset charset = StandardCharsets.UTF_8;
    private int port;

    public ChatServer() {
        this(DEFAULT_PORT);
    }

    public ChatServer(int port) {
        this.port = port;
        this.connectedClients = new ArrayList<>();
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

    private void start() {
        try {
            // 创建自动义ChannelGroup的线程池
            ExecutorService executorService = Executors.newFixedThreadPool(THREADPOOL_SIZE);
            channelGroup = AsynchronousChannelGroup.withThreadPool(executorService);
            // 指定ChannelGroup
            serverChannel = AsynchronousServerSocketChannel.open(channelGroup);
            serverChannel.bind(new InetSocketAddress(LOCALHOST, port));
            System.out.println("启动服务器，监听端口：" + port);

            // 持续监听连接请求
            while (true) {
                serverChannel.accept(null, new AcceptHandler());
                // 阻塞式等待输入数据
                // 目的：节约系统资源，减少accept() 的调用
                //      更多的使用AcceptHandler()中的accept()
                System.in.read();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            close(serverChannel);
        }
    }

    public static void main(String[] args) {
        ChatServer server = new ChatServer(7777);
        server.start();
    }

    // <AsynchronousSocketChannel, Object>
    // 返回类型AsynchronousSocketChannel,attachment对象类型Object
    private class AcceptHandler implements CompletionHandler<AsynchronousSocketChannel, Object> {

        // accept()执行成功后的rollback()
        @Override
        public void completed(AsynchronousSocketChannel clientChannel, Object attachment) {
            // 服务器继续监听连接请求
            if (serverChannel.isOpen()) {
                serverChannel.accept(null, this);
            }
            // 检测客户端是否有效
            if (clientChannel != null && clientChannel.isOpen()) {
                ClientHandler handler = new ClientHandler(clientChannel);
                ByteBuffer buffer = ByteBuffer.allocate(BUFFER);
                // 将新用户添加到在线用户列表
                addClient(handler);
                // 异步read(), 将客户端channel中的数据写入buffer
                // attachment 设置为buffer
                //  why： read()返回值为Integer,
                //       想要知道具体数据类型，只能依靠attachment
                clientChannel.read(buffer, buffer, handler);
            }
        }
        // 执行失败后的rollback()
        @Override
        public void failed(Throwable exc, Object attachment) {
            System.out.println("连接失败：" + exc);
        }
    }

    // 添加在线用户
    private synchronized void addClient(ClientHandler handler) {
        connectedClients.add(handler);
        System.out.println(getClientName(handler.clientChannel) + "已连接到服务器");
    }

    // 移除下线用户
    private synchronized void removeClient(ClientHandler handler) {
        connectedClients.remove(handler);
        System.out.println(getClientName(handler.clientChannel) + "已断开连接");
        close(handler.clientChannel);
    }


    private class ClientHandler implements CompletionHandler<Integer, Object> {
        private AsynchronousSocketChannel clientChannel;

        public ClientHandler(AsynchronousSocketChannel channel) {
            this.clientChannel = channel;
        }

        @Override
        public void completed(Integer result, Object attachment) {
            ByteBuffer buffer = (ByteBuffer) attachment;
            // 只有read()会传入buffer作为attachment
            // 所以判断buffer是否为空，可以判断是read()还是write()
            if (buffer != null) {
                // read()
                if (result <= 0) {
                    // 客户端异常
                    // 将客户移除出在线客户列表
                    removeClient(this);
                } else {
                    // 读写转换
                    buffer.flip();
                    // receive解码
                    String fwdMsg = receive(buffer);
                    System.out.println(getClientName(clientChannel) + ":" + fwdMsg);
                    forwardMessage(clientChannel, fwdMsg);
                    buffer.clear();

                    // 检查用户是否退出
                    if (readyToQuit(fwdMsg)) {
                        removeClient(this);
                    } else {
                        // 如果不退出，继续等待写入
                        clientChannel.read(buffer, buffer, this);
                    }
                }
            }

        }

        @Override
        public void failed(Throwable exc, Object attachment) {
            System.out.println("读写失败：" + exc);
        }
    }

    private synchronized void forwardMessage(AsynchronousSocketChannel clientChannel, String fwdMsg) {
        for (ClientHandler handler : connectedClients) {
            if (!clientChannel.equals(handler.clientChannel)) {
                try {
                    // 将信息编码
                    ByteBuffer buffer = charset.encode(getClientName(clientChannel) + ":" + fwdMsg);
                    // 将信息从buffer写入channel
                    handler.clientChannel.write(buffer, null, handler);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private String getClientName(AsynchronousSocketChannel clientChannel) {
        int clientPort = -1;
        try {
            // 获取IP Socket地址
            InetSocketAddress address = (InetSocketAddress) clientChannel.getRemoteAddress();
            // 获取port
            clientPort = address.getPort();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "客户端[" + clientPort + "]";
    }

    private String receive(ByteBuffer buffer) {
        // 解码为UTF-8
        CharBuffer charBuffer = charset.decode(buffer);
        return String.valueOf(charBuffer);
    }

}
