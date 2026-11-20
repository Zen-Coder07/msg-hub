package com.msghub.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

//聊天室服务器主类，监听8888端口，，每接给一个用户就单开一个独立线程
public class ChatServer {
    public static final int PORT=8888;
    private static final OnlineUserManager userManager=new OnlineUserManager();

    public static void main(String[] args) {
        try(ServerSocket serverSocket=new ServerSocket(PORT)){
            ServerLogger.logRun("＜（＾－＾）＞服务器启动成功，检测端口"+PORT);
            System.out.println("聊天室启动完成，端口"+PORT+"，等待客户端接入。。。");

            while (true){
                Socket clientSocket=serverSocket.accept();
                new Thread(new ClientHandler(clientSocket,userManager)).start();
            }
        }catch (IOException e){
            ServerLogger.logRun("×服务器启动失败，"+e.getMessage());
            System.out.println("服务器启动失败！");
        }
    }
}
