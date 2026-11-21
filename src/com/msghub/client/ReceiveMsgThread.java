package com.msghub.client;

import java.io.BufferedReader;
import java.io.IOException;

public class ReceiveMsgThread implements Runnable{
    private final BufferedReader br;
    private volatile boolean running =true;;

    public ReceiveMsgThread(BufferedReader br){
        this.br=br;
    }

    public void stop(){
        running=false;
    }

    @Override
    public void run(){
        String line;
        try{
            while (running&&(line=br.readLine())!=null){
                System.out.println(line);
            }
        }catch(IOException e){
            if(running){
                System.out.println("\n警告！ 和服务端断开连接了");
            }
        }
    }
}
