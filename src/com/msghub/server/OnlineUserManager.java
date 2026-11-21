package com.msghub.server;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
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
    public void broadcast(String message,String excludeNick){
        //先在锁内复制一份接收者快照，避免持锁进行网络I/O
        List<PrintWriter> targets;
        synchronized (this){
            targets=new ArrayList<>(onlineUsers.size());
            //循环遍历，拿【用户名，输出流】
            for(Map.Entry<String,PrintWriter> entry: onlineUsers.entrySet()){
                if(entry.getKey().equals(excludeNick)){
                    continue;
                }
                targets.add(entry.getValue());
            }
        }
        //在锁外发送，单个客户端卡顿不会阻塞其他用户的消息投递
        for(PrintWriter writer: targets){
            try {
                writer.println(message);
                writer.flush();
            }catch (Exception e){
                ServerLogger.logRun("发送信息失败，客户端断开连接："+e.getMessage());
                synchronized (this){
                    String removeNick=null;
                    for(Map.Entry<String,PrintWriter>en:onlineUsers.entrySet()){
                        if(en.getValue()==writer){
                            removeNick=en.getKey();
                            break;
                        }
                    }
                    if(removeNick!=null){
                        onlineUsers.remove(removeNick);
                        ServerLogger.logRun("自动清除下线用户");
                    }
                }
                writer.close();
            }
        }
    }
}
