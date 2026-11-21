package com.msghub.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

public class ChatClient {
    private static Socket socket;
    private static PrintWriter pw;
    private static BufferedReader br;
    private static ReceiveMsgThread receiver;

    public static void main(String []args){
        Scanner sc=new Scanner(System.in);
        String ip ="127.0.0.1";
        int port=8888;
        System.out.print("请输入服务端Ip：(默认127.0.0.1)");
        String inputIp=sc.nextLine().trim();
        if(!inputIp.isBlank()){
            ip=inputIp;
        }

        System.out.print("请输入端口：(默认8888)");
        String inputPort=sc.nextLine().trim();
        if(!inputPort.isBlank()){
            try{
                port=Integer.parseInt(inputPort);
                if(port<1||port>65535){
                    System.out.println("端口范围应为 1-65535，已使用默认端口 "+8888);
                    port=8888;
                }
            }catch (NumberFormatException e){
                System.out.println("端口格式不正确，已使用默认端口 "+8888);
                port=8888;
            }
        }
        try{
            socket=new Socket(ip,port);
            pw=new PrintWriter(socket.getOutputStream(),true);
            br=new BufferedReader(new InputStreamReader(socket.getInputStream()));
            System.out.println("＜（＾－＾）＞ 连接服务器成功！");

            //昵称注册环节：注册成功才跳出循环
            boolean registered=false;
            while(true){
                System.out.print("请输入您的昵称：");
                String nick=sc.nextLine().trim();
                if(nick.isBlank()){
                    System.out.println(" 昵称不能为空！");
                    continue;
                }

                String[]banWords={"【","】","系","统"};
                boolean invalid=false;
                for (String ban:banWords){
                    if(nick.contains(ban)){
                        invalid=true;
                    }
                    break;
                }
                if(invalid){
                    System.out.println("存在违规字符：\"【\",\"】\",\"系\",\"统\"");
                    continue;
                }
                pw.println("NICK:"+nick);
                String resp=br.readLine();
                if(resp==null){
                    System.out.println("× 与服务端的连接已断开");
                    break;
                }
                if("OK".equals(resp)){
                    System.out.println("恭喜！注册成功！");
                    registered=true;
                    break;
                }
                else if("ERROR_NICK".equals(resp)){
                    System.out.println("昵称重复，换一个吧");
                }
            }

            if(registered){
                //启动子线程
                receiver=new ReceiveMsgThread(br);
                new Thread(receiver).start();

                while (true){
                    String sendText=sc.nextLine().trim();
                    if(sendText.isBlank()){
                        System.out.println("不能发送空消息！");
                        continue;
                    }
                    if("/quit".equals(sendText)){
                        receiver.stop();
                        System.out.println("ヾ(•ω•`)o 你已退出聊天室");
                        break;
                    }
                    pw.println(sendText);
                }
            }
        }catch (IOException e){
            System.out.println("× 连接失败："+e.getMessage());
        }finally {
            closeQuietly();
            sc.close();
        }
    }

    //关闭网络资源：关闭Socket会同时中断接收线程阻塞中的readLine
    private static void closeQuietly(){
        try{
            if(pw!=null){
                pw.close();
            }
        }catch (Exception ignored){
        }
        try{
            if(socket!=null&&!socket.isClosed()){
                socket.close();
            }
        }catch (IOException ignored){
        }
    }
}
