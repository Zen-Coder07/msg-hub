package com.msghub.server;

import java.io.PrintWriter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class OnlineUserManager {
    private final Map<String, PrintWriter> onlineUsers=new ConcurrentHashMap<>();
//    在线用户管理：昵称->输出流 的映射
//    synchronized加锁，保证多线程并发安全

    //添加用户，校验昵称是否存在
    //nick昵称 out该用户对应输出流
    public synchronized boolean addUser(String nick,PrintWriter out){
        if(onlineUsers.containsKey(nick)){
            return false;
        }
        onlineUsers.put(nick, out);
        return true;
    }
    //移除下线用户
    public synchronized void removeUser(String nick){
        onlineUsers.remove(nick);
    }
    //广播给用户
    //message为待广播的消息，excludeNick不该收到信息广播的用户
    public synchronized void broadcast(String message,String excludeNick){
        //循环遍历，拿【用户名，输出流】
        for(Map.Entry<String,PrintWriter> entry: onlineUsers.entrySet()){
            String name=entry.getKey();
            if(name.equals(excludeNick)){
                continue;
            }
            PrintWriter writer=entry.getValue();
            writer.println(message);
            writer.flush();;
        }
    }
}
