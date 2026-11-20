package com.msghub.server;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;

//服务端日志工具：运行日志（连接、上下线、异常）与
public class ServerLogger {
    private static final String RUN_LOG_PATH="logs/server-run.log";
    private static final String MSG_LOG_PATH="logs/chat-message.log";
    static{
        File dir=new File("logs");
        if(!dir.exists()){
            dir.mkdirs();
        }
    }
    private static void logToFile(String path,String content){
        try(PrintWriter pw=new PrintWriter(new FileWriter(path,true))){
            String time=LocalDateTime.now().toString();
            pw.printf("[%s] %s%n",time,content);
        }catch (IOException ignored){
            //不影响服务器运行
        }
    }
    public static void logRun(String content){
        logToFile(RUN_LOG_PATH,content);
    }
    public static void logChat(String content){
        logToFile(MSG_LOG_PATH,content);
    }
}
