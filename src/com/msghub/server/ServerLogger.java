package com.msghub.server;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;

//服务端日志工具：运行日志（连接、上下线、异常）与聊天消息日志
public class ServerLogger {
    //日志目录可通过 -Dmsghub.log.dir=... 指定，默认使用工作目录下的 logs/
    private static final File LOG_DIR=new File(System.getProperty("msghub.log.dir","logs"));
    private static final String RUN_LOG_PATH=new File(LOG_DIR,"server-run.log").getPath();
    private static final String MSG_LOG_PATH=new File(LOG_DIR,"chat-message.log").getPath();
    //多个客户端线程会同时写日志，加锁保证每条日志完整写入、互不交错
    private static final Object WRITE_LOCK=new Object();
    static{
        if(!LOG_DIR.exists()){
            LOG_DIR.mkdirs();
        }
    }
    private static void logToFile(String path,String content){
        synchronized (WRITE_LOCK){
            try(PrintWriter pw=new PrintWriter(new FileWriter(path,true))){
                String time=LocalDateTime.now().toString();
                pw.printf("[%s] %s%n",time,content);
            }catch (IOException ignored){
                //不影响服务器运行
            }
        }
    }
    public static void logRun(String content){
        logToFile(RUN_LOG_PATH,content);
    }
    public static void logChat(String content){
        logToFile(MSG_LOG_PATH,content);
    }
}
