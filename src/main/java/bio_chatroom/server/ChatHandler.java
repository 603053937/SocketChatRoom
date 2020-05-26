package bio_chatroom.server;

import java.io.*;
import java.net.Socket;

public class ChatHandler implements Runnable {

    private ChatServer chatServer;
    private Socket socket;

    public ChatHandler(ChatServer server, Socket socket) {
        this.chatServer = server;
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            // 存储新上线用户
            chatServer.addClient(socket);

            // 读取用户发送的消息
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream())
            );

            String msg = null;
            // readLine()方法在进行读取一行时，只有遇到回车(\r)或者换行符(\n)才会返回读取结果
            // 当realLine()读取到的内容为空时，并不会返回 null，而是会一直阻塞
            // 只有当读取的输入流发生错误或者被关闭时，readLine()方法才会返回null
            while ((msg = reader.readLine()) != null) {
                String fwdMsg = "客户端[" + socket.getPort() + "]: " + msg + "\n";
                System.out.print(fwdMsg);
                // 将消息转发给聊天室里在线的其他用户
                chatServer.forwardMessage(socket, fwdMsg);
                // 检查用户是否准备退出
                if (chatServer.readyToQuit(msg)) {
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                // 用户退出后，将id从在线列表map中移除
                chatServer.removeClient(socket);
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }
}