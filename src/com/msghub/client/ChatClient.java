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
            port= Integer.parseInt(inputPort);
        }
        try{
            socket=new Socket(ip,port);
            pw=new PrintWriter(socket.getOutputStream(),true);
            br=new BufferedReader(new InputStreamReader(socket.getInputStream()));
            System.out.println("＜（＾－＾）＞ 连接服务器成功！");


            while(true){
                System.out.print("请输入您的昵称：");
                String nick=sc.nextLine().trim();
                if(nick.isBlank()){
                    System.out.println(" 昵称不能为空！");
                    continue;
                }
                pw.println("NICK:"+nick);
                String resp=br.readLine();
                if("OK".equals(resp)){
                    System.out.println("恭喜！注册成功！");
                }
                else if("ERROR_NICK".equals(resp)){
                    System.out.println("昵称重复，换一个吧");
                }
            }
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
                    pw.close();
                    socket.close();
                    System.out.println("ヾ(•ω•`)o 你已退出聊天室");
                    break;
                }
                pw.println(sendText);
            }
        }catch (IOException e){
            System.out.println("× 连接失败："+e.getMessage());
        }finally {
            sc.close();
        }
    }
}
