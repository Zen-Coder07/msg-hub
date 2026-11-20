package com.msghub.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ClientHandler implements Runnable{
    private final Socket socket;
    private final OnlineUserManager userManager;
    private BufferedReader br;
    private PrintWriter pw;
    private String nickName=null;

    public ClientHandler(Socket socket,OnlineUserManager userManager){
        this.socket=socket;
        this.userManager=userManager;
    }
    @Override
    public void run(){
        try {
            br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            pw=new PrintWriter(socket.getOutputStream(),true);

            //昵称注册环节
            while(true){
                String line =br.readLine();
                if(line==null){
                    return;
                }
                if(line.startsWith("NICK:")){
                    String nick=line.substring(5).trim();
                    if(nick.isBlank()){
                        pw.println("ERROR_NICK");
                        continue;
                    }
                    boolean ok=userManager.addUser(nick,pw);
                    if(ok){
                        nickName=nick;
                        pw.println("OK");
                        String welcome="【系统】欢迎 "+nick+" 加入聊天室！ ";
                        userManager.broadcast(welcome,null);
                        ServerLogger.logRun(welcome);
                        break;
                    }
                    else {
                        pw.println("ERROR_NICK");
                    }
                }
            }
            //聊天消息循环
            String msg;
            while ((msg=br.readLine())!=null){
                if(msg.isBlank()){
                    continue;
                }
                String broadcastTest="【"+nickName+"】"+msg;
                userManager.broadcast(broadcastTest,nickName);
                ServerLogger.logChat(broadcastTest);
            }
        } catch (IOException e) {
            ServerLogger.logRun("用户["+nickName+"]连接异常："+e.getMessage());
        }finally {
            cleanUp();
        }
    }
    private void cleanUp(){
        try {
            if (br != null) {
                br.close();
            }
            if (pw!=null) {
                pw.close();
            }
            if(socket!=null){
                socket.close();
            }
        }catch (IOException ignored){

        }
        ServerLogger.logRun("连接资源释放成功");
    }
}
