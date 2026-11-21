# MsgHub 控制台多人聊天室

> Java Socket 网络编程阶段项目报告
>
> 关键词：TCP Socket、阻塞 IO、多线程、并发同步、文本协议、日志

## 目录

- [摘要](#摘要)
- [一、项目名称与选题](#一项目名称与选题)
- [二、系统架构与主要类职责](#二系统架构与主要类职责)
- [三、编译与运行指南](#三编译与运行指南)
- [四、遇到的困难与解决体会](#四遇到的困难与解决体会)
- [五、核心知识点梳理](#五核心知识点梳理)
- [六、同类项目的通用公式](#六同类项目的通用公式)
- [七、总结与改进方向](#七总结与改进方向)
- [附录：核心代码索引](#附录核心代码索引)

## 摘要

MsgHub 是一个基于 Java SE 的控制台多人聊天室，采用典型的 C/S（客户端/服务端）结构。服务端监听固定端口，为每个接入的客户端分配一个独立线程；客户端使用一个主线程负责键盘输入与消息发送，另用一个接收线程持续读取服务端消息，从而解决“输入时收不到消息”的问题。系统支持昵称注册与唯一性校验、消息广播、上下线通知、异常断开清理，并将运行日志与聊天消息分别写入文件。

项目不依赖任何第三方框架，完整覆盖了 Socket 编程中最核心的三组知识：**IO 流与协议编解码、多线程任务模型、共享状态的并发同步**。本报告在第 1～4 节说明项目本身，第 5 节梳理知识点，第 6 节给出同类项目的通用实现公式，便于迁移到私聊、文件传输、在线协作等场景。

---

## 一、项目名称与选题

### 1.1 项目名称

**MsgHub —— 基于 Java Socket 的控制台多人聊天室**

本项目对应课程阶段项目中的“控制台多人聊天室”选题。在完成基础功能的前提下，项目重点补充了并发安全、异常断线清理和日志记录等工程化处理。

### 1.2 选题背景与目标

即时通讯是网络编程中最典型、最容易观察运行效果的综合场景：它同时涉及连接建立、数据收发、协议设计、多用户并发、异常断线和资源释放。与单机程序不同，聊天室的正确性不仅取决于单个方法，还取决于多个线程同时对共享在线列表读写时的行为。

本项目希望达到以下目标：

1. 使用原生 `Socket` / `ServerSocket` 完成一个可实际运行的多用户聊天室；
2. 支持多用户同时在线、消息广播和上下线通知；
3. 保证在线用户表在并发访问下不丢失、不重复、不抛异常；
4. 单个客户端异常不影响服务端整体运行；
5. 通过日志记录服务端运行情况与聊天内容，便于排查问题；
6. 以尽量少的代码把 IO、多线程、并发同步三部分知识串成一个完整闭环。

### 1.3 功能需求

| 模块 | 功能要求 |
| --- | --- |
| 服务端 | 绑定端口（默认 `8888`），持续 `accept()` 等待连接 |
| 服务端 | 每个客户端由独立线程处理，互不阻塞 |
| 服务端 | 维护“昵称 → 输出流”的全局在线用户表 |
| 服务端 | 注册时校验昵称唯一性，重复则返回错误并允许重新输入 |
| 服务端 | 普通消息广播给除发送者外的所有在线用户，格式 `【昵称】消息` |
| 服务端 | 上线广播 `【系统】欢迎 昵称 加入聊天室！` |
| 服务端 | 主动退出或异常断开时广播 `【系统】昵称 已退出聊天室。` |
| 服务端 | 单客户端异常不影响整体；离线后清理用户、关闭连接、结束线程 |
| 服务端 | 记录运行日志与聊天消息日志 |
| 客户端 | 支持输入服务端 IP 与端口，并提供默认值 |
| 客户端 | 连接后先注册昵称，重复或为空时可重新输入 |
| 客户端 | 支持文本发送，拦截空消息；`/quit` 主动退出 |
| 客户端 | 独立接收线程持续收消息，输入与接收可同时进行 |
| 客户端 | 退出或断线时给出友好提示，关闭 Socket 并释放资源 |

### 1.4 技术选型

| 选择 | 说明 |
| --- | --- |
| TCP `Socket` | 聊天消息要求可靠、有序，TCP 的字节流语义最合适；UDP 需自行处理丢包与乱序 |
| 阻塞 IO（BIO） | 代码直观，适合课程阶段理解“一个连接一个线程”的模型；高并发场景可替换为 NIO |
| 一连接一线程 | `accept()` 后立即 `new Thread(...).start()`，连接之间互不阻塞 |
| 文本行协议 | 一行一条消息，便于用 `telnet` / `nc` 手工调试 |
| `ConcurrentHashMap` + `synchronized` | 保证在线用户表的线程安全，并在需要时保证复合操作原子性 |
| 原生文件 IO | `FileWriter` 追加写日志，不引入日志框架，突出 IO 流知识 |

### 1.5 项目结构

```text
MsgHub/
├── README.md
├── logs/                          # 运行后生成（已在 .gitignore 中忽略 *.log）
│   ├── server-run.log             # 运行日志：启动、上下线、异常
│   └── chat-message.log           # 聊天消息日志
├── src/
│   └── com/msghub/
│       ├── client/
│       │   ├── ChatClient.java    # 客户端入口：连接、注册、输入发送
│       │   └── ReceiveMsgThread.java  # 客户端接收线程
│       └── server/
│           ├── ChatServer.java    # 服务端入口：accept 循环
│           ├── ClientHandler.java # 单客户端处理线程：注册、消息循环、清理
│           ├── OnlineUserManager.java # 在线用户表与广播
│           └── ServerLogger.java  # 线程安全的文件日志工具
└── out/                           # 编译输出（已在 .gitignore 中忽略）
```

---

## 二、系统架构与主要类职责

### 2.1 总体架构

系统采用“**一个接入线程 + 每连接一个处理线程 + 一份共享在线表**”的结构。

```text
                         ┌──────────────────────────────────┐
                         │           ChatServer             │
                         │   main 线程：accept() 循环        │
                         └────────────────┬─────────────────┘
                                          │ 每接入一个 Socket
              ┌───────────────────────────┼───────────────────────────┐
              ▼                           ▼                           ▼
   ┌────────────────────┐     ┌────────────────────┐     ┌────────────────────┐
   │  ClientHandler #1  │     │  ClientHandler #2  │     │  ClientHandler #N  │
   │  独立线程           │     │  独立线程           │     │  独立线程           │
   │  读 / 写 / 清理     │     │  读 / 写 / 清理     │     │  读 / 写 / 清理     │
   └─────────┬──────────┘     └─────────┬──────────┘     └─────────┬──────────┘
             └───────────────────────────┼───────────────────────────┘
                                         ▼
                         ┌──────────────────────────────────┐
                         │       OnlineUserManager          │
                         │   昵称 → PrintWriter             │
                         │   ConcurrentHashMap + 同步方法    │
                         └────────────────┬─────────────────┘
                                          ▼
                         ┌──────────────────────────────────┐
                         │          ServerLogger            │
                         │  server-run.log / chat-message.log│
                         └──────────────────────────────────┘
```

客户端则采用“**主线程负责输入、接收线程负责输出**”的双线程模型：

```text
键盘输入 ──► Scanner ──► PrintWriter ──► Socket ──► 服务端
                                                      │
控制台输出 ◄── System.out ◄── ReceiveMsgThread ◄── BufferedReader
                                   （独立线程，持续 readLine）
```

### 2.2 主要类职责

| 类 | 所在包 | 核心职责 | 关键成员 / 方法 |
| --- | --- | --- | --- |
| `ChatServer` | `com.msghub.server` | 服务端入口。绑定端口、循环接收连接、为每个连接启动处理线程 | `PORT = 8888`、`OnlineUserManager` 单例、`main` |
| `ClientHandler` | `com.msghub.server` | 单个客户端的会话处理：建流、昵称注册、消息循环、异常与下线清理 | `run()`、`cleanUp()`、`nickName` |
| `OnlineUserManager` | `com.msghub.server` | 维护在线用户表；提供线程安全的注册、移除与广播 | `addUser()`、`removeUser()`、`broadcast()` |
| `ServerLogger` | `com.msghub.server` | 线程安全的日志写入；区分运行日志与聊天日志 | `logRun()`、`logChat()`、`WRITE_LOCK` |
| `ChatClient` | `com.msghub.client` | 客户端入口：连接、注册、启动接收线程、输入发送、退出清理 | `main()`、`closeQuietly()` |
| `ReceiveMsgThread` | `com.msghub.client` | 客户端接收线程：持续读取服务端消息并打印，支持停止标志 | `run()`、`stop()`、`volatile running` |

### 2.3 关键流程

#### （1）服务端启动与接入

1. `ChatServer.main()` 使用 `try-with-resources` 创建 `ServerSocket(8888)`；
2. 进入 `while (true)`，调用阻塞方法 `accept()`；
3. 每返回一个 `Socket`，就构造 `ClientHandler` 并启动新线程；
4. 主线程继续回到 `accept()`，因此接入新用户不会被某个用户的消息处理阻塞。

```java
try (ServerSocket serverSocket = new ServerSocket(PORT)) {
    while (true) {
        Socket clientSocket = serverSocket.accept();
        new Thread(new ClientHandler(clientSocket, userManager)).start();
    }
}
```

#### （2）昵称注册

```text
客户端                          ClientHandler                 OnlineUserManager
  │  NICK:Alice                     │                              │
  │───────────────────────────────► │  addUser("Alice", out)       │
  │                               │─────────────────────────────► │
  │                               │ ◄──────── true / false ────── │
  │ ◄──────── OK ───────────────── │                              │
  │                               │  broadcast(欢迎消息, null)     │
  │                               │─────────────────────────────► │
  │ ◄──────── 欢迎消息 ──────────── │                              │
  │                               │                              │
  │  hello                        │                              │
  │───────────────────────────────► │  broadcast("【Alice】hello",  │
  │                               │            "Alice")          │
  │                               │─────────────────────────────► │ ──► 其他客户端
```

- 注册成功后，服务端先向该客户端回 `OK`，再广播欢迎消息；
- 欢迎广播的排除参数为 `null`，因此**所有在线用户（包括新用户本人）**都会收到欢迎消息；
- 普通消息广播的排除参数为发送者昵称，发送者不会收到自己的消息回显；
- 昵称重复或为空时返回 `ERROR_NICK`，客户端继续提示输入。

#### （3）消息广播

`ClientHandler` 的消息循环：

```java
while ((msg = br.readLine()) != null) {
    if (msg.isBlank()) {
        continue;                       // 空消息 / 纯空白消息直接忽略
    }
    String broadcastText = "【" + nickName + "】" + msg;
    userManager.broadcast(broadcastText, nickName);  // 排除发送者
    ServerLogger.logChat(broadcastText);
}
```

`OnlineUserManager.broadcast()` 的关键是**锁内取快照，锁外做网络 IO**：

```java
List<PrintWriter> targets;
synchronized (this) {
    targets = new ArrayList<>(onlineUsers.size());
    for (Map.Entry<String, PrintWriter> entry : onlineUsers.entrySet()) {
        if (entry.getKey().equals(excludeNick)) {
            continue;
        }
        targets.add(entry.getValue());
    }
}
for (PrintWriter writer : targets) {   // 不在锁内发送
    writer.println(message);
    writer.flush();
}
```

这样即使某个客户端网络卡顿，也不会因为持有管理器的锁而阻塞其他所有广播。

#### （4）退出与异常清理

`ClientHandler.run()` 使用 `finally` 保证无论正常退出、对端关闭还是 IO 异常，都会执行 `cleanUp()`：

1. 若已注册昵称，先从在线表移除；
2. 再广播 `【系统】昵称 已退出聊天室。`；
3. 关闭 `BufferedReader`、`PrintWriter`、`Socket`；
4. 记录资源释放日志。

“先移除、再广播”的顺序是有意为之：保证退出者不会收到自己的退出通知，同时避免后续广播继续向已断开的输出流写数据。

### 2.4 通信协议

协议为**按行分隔的纯文本协议**。发送端使用 `PrintWriter.println()`，行尾是平台默认换行符（Windows 下为 `\r\n`，Linux/macOS 下为 `\n`）；接收端 `BufferedReader.readLine()` 同时兼容 `\r\n`、`\r`、`\n`，因此跨平台不需要额外处理。

| 方向 | 类型 | 格式 | 说明 |
| --- | --- | --- | --- |
| C → S | 昵称提交 | `NICK:<昵称>` | 昵称不能为空；服务端会 `trim()` |
| S → C | 注册成功 | `OK` | 客户端随后启动接收线程 |
| S → C | 昵称重复 / 无效 | `ERROR_NICK` | 客户端重新输入昵称 |
| C → S | 聊天消息 | 任意单行文本 | 空行 / 纯空白行被服务端忽略 |
| S → C | 用户消息广播 | `【<昵称>】<消息>` | 不发送给消息发送者本人 |
| S → C | 系统广播 | `【系统】<内容>` | 上线欢迎、下线通知 |
| C → S | 退出 | 关闭 Socket | 客户端输入 `/quit` 后本地关闭连接，`/quit` 本身不发送给服务端 |

协议特点与限制：

- **一行一条消息**，实现简单，便于抓包和手工测试；
- 不对消息内容做转义，因此消息中不能包含换行符；
- 没有长度前缀，默认单条消息不会过长；工程化场景应增加长度上限，防止超长行占用内存；
- 服务端在昵称注册阶段只识别 `NICK:` 前缀，其他行会被忽略；注册完成后所有行都按聊天消息处理。

### 2.5 共享状态与线程模型

| 共享资源 | 访问者 | 风险 | 保护方式 |
| --- | --- | --- | --- |
| `onlineUsers` 在线表 | 所有 `ClientHandler` 线程 | 并发读写、检查后写入竞态、迭代时被修改 | `ConcurrentHashMap` + `synchronized` 方法 + 广播快照 |
| `ReceiveMsgThread.running` | 客户端主线程写、接收线程读 | 可见性问题 | `volatile` |
| 日志文件 | 所有服务端处理线程 | 多线程同时追加导致内容交错 | `ServerLogger.WRITE_LOCK` |
| 单个客户端的 `br` / `pw` | 该连接自己的处理线程；`pw` 还可能被广播线程写入 | 多线程写同一输出流 | 每连接独占读流；`PrintWriter` 的写方法内部同步 |
| `Socket` | 对应处理线程 | 关闭与读写并发 | `finally` 统一关闭；关闭会中断阻塞读 |

---

## 三、编译与运行指南

### 3.1 环境要求

- JDK **11 及以上**（代码使用了 `String.isBlank()`，该项目在 JDK 21 下编译运行通过）；
- Windows / macOS / Linux 均可；
- 不需要 Maven、Gradle 或任何第三方依赖；
- 客户端与服务端在同一台机器测试时，使用默认 IP `127.0.0.1` 即可。

### 3.2 编译

在项目根目录 `MsgHub/` 下执行。

**Windows CMD：**

```bat
cd /d D:\code\Code\MsgHub
javac -encoding UTF-8 -d out src\com\msghub\server\*.java src\com\msghub\client\*.java
```

**PowerShell：**

```powershell
cd D:\code\Code\MsgHub
javac -encoding UTF-8 -d out src\com\msghub\server\*.java src\com\msghub\client\*.java
```

**Git Bash / macOS / Linux：**

```bash
cd "D:/code/Code/MsgHub"
javac -encoding UTF-8 -d out src/com/msghub/server/*.java src/com/msghub/client/*.java
```

参数说明：

- `-encoding UTF-8`：源码文件为 UTF-8 编码，避免中文注释和字符串编译乱码；
- `-d out`：把 `.class` 文件输出到 `out/` 目录，保持包结构；
- 编译成功后应生成 6 个 class 文件，分别对应 6 个源文件。

> 使用 IntelliJ IDEA 时，将 `src` 标记为 Sources Root，直接运行 `ChatServer` 和 `ChatClient` 的 `main` 方法即可。工作目录默认为项目根目录，因此日志会生成在项目根目录的 `logs/` 下。

### 3.3 运行

**第 1 步：启动服务端**（终端 1）

```bash
java -cp out com.msghub.server.ChatServer
```

看到以下输出即表示启动成功：

```text
＜（＾－＾）＞服务器启动成功，检测端口8888
聊天室启动完成，端口8888，等待客户端接入。。。
```

**第 2 步：启动多个客户端**（终端 2、3、……）

```bash
java -cp out com.msghub.client.ChatClient
```

每个客户端按提示输入：

1. 服务端 IP（直接回车使用 `127.0.0.1`）；
2. 端口（直接回车使用 `8888`）；
3. 昵称（不能为空，不能与在线用户重复）。

进入聊天后：

- 输入任意非空文本并回车即可发送；
- 输入 `/quit` 主动退出；
- 直接关闭终端属于异常断开，服务端同样会清理并广播下线。

**可选：自定义日志目录**

日志目录默认是“当前工作目录下的 `logs/`”，可通过系统属性覆盖：

```bash
java -Dmsghub.log.dir=D:/code/Code/MsgHub/logs -cp out com.msghub.server.ChatServer
```

> 如果从其他目录启动，日志会生成在启动目录下的 `logs/`。用 `-Dmsghub.log.dir` 可以固定位置。

### 3.4 运行效果示例

服务端日志（`logs/server-run.log`）：

```text
[2026-11-21T23:37:23.673227300] ＜（＾－＾）＞服务器启动成功，检测端口8888
[2026-11-21T23:37:26.302301600] 【系统】欢迎 Alice 加入聊天室！
[2026-11-21T23:37:26.303305200] 【系统】欢迎 Bob 加入聊天室！
[2026-11-21T23:37:26.309303500] 【系统】Bob 已退出聊天室。
[2026-11-21T23:37:26.309303500] 用户[Bob]连接资源释放成功
```

聊天日志（`logs/chat-message.log`）：

```text
[2026-11-21T23:37:26.308308200] 【Alice】hello everyone
```

客户端交互示意：

```text
[客户端 A]                               [客户端 B]
恭喜！注册成功！                          恭喜！注册成功！
【系统】欢迎 Alice 加入聊天室！            【系统】欢迎 Bob 加入聊天室！
【系统】欢迎 Bob 加入聊天室！
                                        【Alice】大家好
大家好  ← 发送后自己看不到回显
【Bob】你好                             你好
/quit
ヾ(•ω•`)o 你已退出聊天室                 【系统】Alice 已退出聊天室。
```

### 3.5 日志说明

| 文件 | 内容 | 写入位置 |
| --- | --- | --- |
| `logs/server-run.log` | 启动、用户上线、下线、连接异常、资源释放 | `ServerLogger.logRun()` |
| `logs/chat-message.log` | 被广播的聊天消息 | `ServerLogger.logChat()` |

日志格式为 `[ISO 时间] 内容`，以追加模式写入。项目根目录的 `.gitignore` 已忽略 `*.log`，避免把运行数据提交到仓库。

### 3.6 用 telnet / nc 手工验证协议

不启动图形界面或 Java 客户端，也可以直接验证服务端协议：

```text
telnet 127.0.0.1 8888
NICK:test
OK
【系统】欢迎 test 加入聊天室！
hello
```

此时另一个在线客户端会收到 `【test】hello`。这说明服务端只依赖行协议，客户端实现可以是 Java、telnet、nc 或任何其他语言。Windows 默认可能未启用 telnet 客户端，可在“启用或关闭 Windows 功能”中开启，或使用 `nc 127.0.0.1 8888`。

### 3.7 常见问题

| 现象 | 原因 | 解决方法 |
| --- | --- | --- |
| `Address already in use: bind` | `8888` 端口已被其他进程占用（常见于上次服务端未退出） | `netstat -ano \| findstr :8888` 找到 PID，执行 `taskkill /PID <pid> /F`；或修改 `ChatServer.PORT` 与客户端默认端口 |
| `Connection refused` | 服务端未启动、IP / 端口填错、防火墙拦截 | 先启动服务端；确认 IP 和端口；检查防火墙 |
| 控制台中文乱码 | 终端编码与源码 / 运行编码不一致 | 编译时加 `-encoding UTF-8`；Windows 控制台可先执行 `chcp 65001` |
| 提示“昵称重复” | 该昵称已在线 | 换一个昵称；确认旧客户端已退出 |
| `UnsupportedClassVersionError` | 编译与运行使用的 JDK 版本不一致 | 用同一版本 JDK（11+）编译和运行 |
| 找不到或无法写入日志 | 工作目录不同或目录无权限 | 使用 `-Dmsghub.log.dir=...` 指定可写目录 |
| 客户端输入时收不到消息 | 接收线程未启动或被误改 | 确认 `new Thread(receiver).start()` 已执行，且没有把 `readLine()` 放回主线程 |

### 3.8 功能自测记录

| 测试用例 | 操作 | 预期结果 | 实际结果 |
| --- | --- | --- | --- |
| 双客户端注册 | A、B 先后发送 `NICK:` | 均返回 `OK`；每次注册向当前在线用户广播欢迎（新用户本人也收到） | 通过（协议模拟） |
| 重名注册 | 第三个连接提交已存在的昵称 | 返回 `ERROR_NICK` | 通过（协议模拟） |
| 消息广播 | A 发送 `hello` | B 收到 `【A】hello`，A 本人不回显 | 通过（协议模拟） |
| 主动退出 | B 关闭连接 | A 收到 `【系统】B 已退出聊天室。` | 通过（协议模拟） |
| 异常断开 | 直接关闭客户端 / 重置连接 | 服务端记录异常并清理在线表 | 通过（服务端日志可见 `Connection reset`） |
| 空消息 | 发送空行或纯空白行 | 服务端忽略，不广播 | 代码校验 |
| 非法端口 | 输入 `abc` 或 `70000` | 提示后回退默认端口 `8888` | 代码校验 |

> “协议模拟”指使用原始 TCP 连接按协议发送文本，验证服务端的注册、重名、广播、下线逻辑；客户端交互界面仍需手工启动 `ChatClient` 验证。

---

## 四、遇到的困难与解决体会

### 4.1 多线程同时修改在线用户表

**问题现象：** 多个客户端同时注册、退出或广播时，在线表可能出现数据不一致；若使用普通 `HashMap`，一个线程遍历时另一个线程 `put/remove`，还可能抛出 `ConcurrentModificationException`。另外，“先 `containsKey()` 判断、再 `put()` 写入”是两步操作，两个线程可能同时判断昵称不存在，导致重名。

**分析与解决：**

- 使用 `ConcurrentHashMap` 存储“昵称 → 输出流”，保证单次 `get/put/remove` 的线程安全；
- 把 `addUser()` 和 `removeUser()` 声明为 `synchronized`，让“检查 + 写入”成为一个原子操作；
- 广播时在锁内复制接收者快照，避免边遍历边修改。

**体会：** 线程安全容器只保证**单个操作**安全，不保证**多个操作的组合**安全。昵称唯一性校验属于典型的 check-then-act 复合操作，必须额外加锁或使用 `putIfAbsent()` 这类原子方法。

### 4.2 广播时持锁做网络 IO，导致全员被拖慢

**问题现象：** 最初直接在 `synchronized` 块中遍历在线表并调用 `writer.println()`。如果某个客户端网络卡顿或输出缓冲区满，`println()` 可能阻塞，导致其他所有客户端的广播都被这把锁挡住。

**分析与解决：** 采用“**锁内取数据，锁外做 IO**”：

1. 在锁内把需要接收消息的 `PrintWriter` 复制到一个临时 `List`；
2. 释放锁后再逐个发送；
3. 发送失败时重新加锁，按输出流反查昵称并移除失效用户。

**体会：** 临界区只应该保护共享数据，不应该包含网络、文件等阻塞操作。锁的粒度越细、持锁时间越短，系统的并发度越高。

### 4.3 客户端“能发不能收”

**问题现象：** 客户端如果只用主线程，`Scanner.nextLine()` 等待键盘输入时会阻塞，此时无法同时执行 `br.readLine()` 读取服务端消息；表现为“我能发出去，但收不到别人的消息”。

**分析与解决：** 将客户端拆成两个线程：

- 主线程：读键盘、校验输入、通过 `PrintWriter` 发送；
- `ReceiveMsgThread`：独占 `BufferedReader`，循环 `readLine()` 并打印服务端消息。

两个线程一个负责“写”，一个负责“读”，互不阻塞。

**体会：** 一个阻塞式数据源通常需要一个独立线程持续消费。设计线程模型时，可以先画出“谁在什么地方阻塞”，再决定线程如何拆分。

### 4.4 断线与退出后的资源清理

**问题现象：** 客户端关闭窗口或网络中断后，如果服务端不清理，会出现“幽灵用户”：在线表仍有该昵称，后续广播仍向已断开的输出流写入，处理线程也无法结束。

**分析与解决：**

- 将清理逻辑放入 `finally`，保证正常退出和异常退出都会执行；
- 清理顺序为：**先从在线表移除 → 再广播下线通知 → 关闭流和 Socket**；
- 对 `nickName` 做空判断，未完成注册就断开的连接只关闭资源、不广播；
- `removeUser()` 对不存在的昵称是幂等的，重复清理不会报错；
- 关闭 Socket 会让阻塞中的 `readLine()` 抛出异常或返回 `null`，从而唤醒并结束处理线程。

**体会：** 网络程序的资源释放必须放在 `finally` 中；清理顺序会影响业务语义（先移除才能保证退出者收不到自己的退出广播），也会影响并发安全（避免向已关闭的流继续写）。

### 4.5 多线程写日志导致内容交错

**问题现象：** 多个 `ClientHandler` 线程同时写同一个日志文件，可能出现两条日志互相穿插、时间戳与内容错位的情况。

**分析与解决：** 在 `ServerLogger` 中使用一个静态锁对象 `WRITE_LOCK`，把“打开文件 → 写入 → 关闭”整体串行化；使用 `FileWriter(path, true)` 追加写入，保留历史日志；使用 `try-with-resources` 自动关闭流；日志写入失败时静默忽略，避免日志问题影响聊天服务本身。

**体会：** 日志文件也是共享资源，同样需要并发保护。每次写都打开、关闭文件实现简单，但有一定开销；生产环境可以换成 `java.util.logging`、Logback 等成熟框架。

### 4.6 输入校验：客户端体验与服务端正确性

**问题现象：** 空昵称、空消息、非法端口、重复昵称都可能让流程进入异常状态。

**分析与解决：**

- 客户端：`trim()` 后使用 `isBlank()` 拦截空昵称和空消息；端口用 `Integer.parseInt()` 并校验 `1～65535`，非法时回退默认值；
- 服务端：不信任客户端，独立校验昵称是否为空、是否重复，返回 `ERROR_NICK`；
- 消息处理阶段：服务端再次忽略空行 / 纯空白行，防止非标准客户端绕过客户端校验。

**体会：** 客户端校验主要提升体验，服务端校验才决定系统正确性。凡是来自网络的数据都必须视为不可信输入。

### 4.7 `PrintWriter` 的异常语义：写失败不一定抛异常

**问题现象：** 原本希望在广播时通过 `try-catch` 捕获客户端断开导致的写失败，并立即清理该用户。但实际测试发现，`PrintWriter.println()/flush()` 在底层 IO 失败时**不会抛出 `IOException`**，而是把错误记录在内部标志中，需要调用 `checkError()` 才能发现。

**分析与解决：** 当前系统仍然能够正确清理用户，因为每个连接都有自己的处理线程：客户端断开后，该线程的 `readLine()` 会返回 `null` 或抛出 `IOException`，随后在 `finally` 中完成清理。广播路径中的 `catch` 更多是兜底。若需要“发送失败立即剔除”，应使用 `writer.checkError()` 判断，或改用会抛出 `IOException` 的 `BufferedWriter` / `OutputStreamWriter`。

**体会：** 使用一个 API 前要理解它的异常策略。“写失败”不等于“抛异常”。`PrintWriter` 为了简化调用，把 IOException 转换成了错误标志，这在聊天、日志等场景很方便，但也容易让人误判错误处理已经生效。

### 4.8 端口占用与测试进程清理

**问题现象：** 反复启动、停止服务端进行测试时，容易出现 `Address already in use: bind`，或者旧进程仍在监听 `8888`。

**分析与解决：**

- 启动前用 `netstat -ano | findstr :8888` 检查端口；
- 找到占用进程后用 `taskkill /PID <pid> /F` 结束；
- 测试脚本使用超时或显式关闭连接，避免残留客户端；
- 也可以在开发阶段临时修改 `ChatServer.PORT` 和客户端默认端口。

**体会：** 网络程序的“进程是否退出”和“端口是否释放”是两个问题，测试前先确认端口状态，可以节省大量排查时间。

---

## 五、核心知识点梳理

### 5.1 IO 流

#### 5.1.1 IO 流的分类

| 维度 | 类型 | 代表类 | 本项目中的使用 |
| --- | --- | --- | --- |
| 数据单位 | 字节流 | `InputStream` / `OutputStream` | `socket.getInputStream()` / `getOutputStream()` |
| 数据单位 | 字符流 | `Reader` / `Writer` | `InputStreamReader`、`BufferedReader`、`PrintWriter` |
| 方向 | 输入流 | `InputStream` / `Reader` | 读取客户端发来的行 |
| 方向 | 输出流 | `OutputStream` / `Writer` | 向客户端发送消息 |
| 角色 | 节点流 | `FileInputStream`、`Socket` 流 | 直接连接数据源/目的地 |
| 角色 | 处理流 | `BufferedReader`、`PrintWriter`、`InputStreamReader` | 在节点流之上增加缓冲、字符转换、格式化能力 |

#### 5.1.2 Socket 上的流式装饰链

网络传输的底层单位是字节，而聊天协议按“行”处理文本，因此需要使用“**字节流 → 字符流 → 缓冲/格式化**”的装饰链：

```text
输入：
Socket.getInputStream()                 // 字节输入流
        │
        ▼
InputStreamReader                       // 字节 → 字符（桥接）
        │
        ▼
BufferedReader                          // 缓冲 + readLine()
        │
        ▼
String line = br.readLine();

输出：
Socket.getOutputStream()                // 字节输出流
        │
        ▼
PrintWriter                             // 字符输出 + println() + 自动 flush
        │
        ▼
pw.println(message);
```

对应代码：

```java
br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
pw = new PrintWriter(socket.getOutputStream(), true);  // true = println 时自动 flush
```

设计要点：

- **`InputStreamReader` 是字节流与字符流之间的桥梁**，负责按字符集解码；
- **`BufferedReader` 提供缓冲区和 `readLine()`**，减少系统调用次数，按行读取；
- **`PrintWriter` 提供 `println()` 等便捷方法**，构造参数 `autoFlush = true` 时，每次 `println()` 后自动刷新；
- 装饰器模式：每一层只增加一种能力，关闭最外层流会级联关闭底层流。

#### 5.1.3 关键方法语义

| 方法 | 语义 | 注意事项 |
| --- | --- | --- |
| `InputStream.read()` | 读取一个字节；返回 `-1` 表示流结束 | 返回 `int`，需要强转 `byte` |
| `Reader.read()` | 读取一个字符；返回 `-1` 表示结束 | 字符流按字符集解码 |
| `BufferedReader.readLine()` | 读取一行，去掉行结束符；**阻塞**直到有数据、换行、EOF 或异常 | 返回 `null` 表示对端正常关闭；断线时可能抛 `IOException` |
| `PrintWriter.println()` | 写入内容并换行 | 底层失败时不一定抛异常，可用 `checkError()` 检查 |
| `flush()` | 强制把缓冲区数据写出 | `autoFlush=true` 时 `println()` 自动调用 |
| `close()` | 关闭流并释放资源；通常级联关闭底层流 | 关闭 Socket 会中断阻塞中的读写 |

#### 5.1.4 阻塞 IO 与线程的关系

`readLine()` 在以下情况之前会一直阻塞：

- 读到一行完整数据；
- 对端关闭连接（返回 `null`）；
- 发生 IO 异常（如连接被重置）；
- 当前线程被中断（本项目的 Socket 读不一定响应中断，实际靠关闭 Socket 唤醒）。

这解释了为什么服务端需要“一连接一线程”，客户端需要独立接收线程：**阻塞调用会占住线程，必须用其他线程继续处理别的输入源**。

#### 5.1.5 字符集问题

当前代码使用：

```java
new InputStreamReader(socket.getInputStream())
new PrintWriter(socket.getOutputStream(), true)
```

没有显式指定字符集，因此两端都依赖 JVM 的默认字符集。两端在同一台机器、同一 JDK 下通常一致；但跨平台或跨 JDK 版本时可能出现乱码。

**工程化建议：两端显式使用 UTF-8。**

```java
new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)
new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)
```

#### 5.1.6 try-with-resources 与文件 IO

- `ChatServer` 对 `ServerSocket` 使用 `try-with-resources`，异常或退出时自动关闭；
- `ServerLogger` 对 `FileWriter` / `PrintWriter` 使用 `try-with-resources`，保证日志文件句柄释放；
- `ClientHandler` 因为需要在 `finally` 中按顺序清理多个可能为 `null` 的资源，使用显式 `close()` 并捕获 `IOException`。

文件追加写日志的核心代码：

```java
try (PrintWriter pw = new PrintWriter(new FileWriter(path, true))) {
    pw.printf("[%s] %s%n", LocalDateTime.now(), content);
} catch (IOException ignored) {
    // 日志失败不影响主业务
}
```

`FileWriter(path, true)` 的第二个参数表示**追加模式**，不会覆盖历史日志。

---

### 5.2 多线程

#### 5.2.1 `Thread` 与 `Runnable`

本项目把任务和线程分离：

```java
public class ClientHandler implements Runnable {
    @Override
    public void run() { ... }
}

new Thread(new ClientHandler(socket, userManager)).start();
```

- `Runnable` 只描述“要做什么”，不绑定线程；
- `Thread` 负责“在哪个线程执行”；
- 同一份 `Runnable` 可以交给普通线程、线程池或虚拟线程执行，扩展性更好；
- Java 单继承，实现 `Runnable` 比继承 `Thread` 更灵活。

`start()` 与 `run()` 的区别：

- `start()`：启动新线程，由 JVM 在新线程中调用 `run()`；
- 直接调用 `run()`：就是普通方法调用，仍在当前线程执行，不会产生并发。

#### 5.2.2 本项目的线程模型

| 线程 | 数量 | 职责 | 主要阻塞点 |
| --- | --- | --- | --- |
| 服务端主线程 | 1 | `accept()` 接入新连接 | `ServerSocket.accept()` |
| 服务端处理线程 | 每个已接入连接 1 个 | 注册、读消息、广播、清理 | `Socket` 的 `readLine()` |
| 客户端主线程 | 1 | 读键盘、发送消息、处理 `/quit` | `Scanner.nextLine()` |
| 客户端接收线程 | 1 | 持续读取并打印服务端消息 | `Socket` 的 `readLine()` |

总线程数约为：`1 + 在线用户数`（服务端）和 `2`（每个客户端）。

#### 5.2.3 线程生命周期与阻塞状态

线程状态包括 `NEW`、`RUNNABLE`、`BLOCKED`、`WAITING`、`TIMED_WAITING`、`TERMINATED`。本项目中大量线程处于阻塞等待状态：

- 服务端主线程阻塞在 `accept()`；
- 处理线程阻塞在 `readLine()`；
- 客户端主线程阻塞在 `nextLine()`；
- 客户端接收线程阻塞在 `readLine()`。

阻塞不是错误，而是阻塞 IO 模型的正常表现。真正的风险是：**线程被永久阻塞且没有退出机制**，例如客户端连接后一直不发送昵称，服务端处理线程就会长期停留在注册循环的 `readLine()` 上。

#### 5.2.4 异常隔离

`ClientHandler.run()` 捕获 `IOException`，只记录日志，不让异常逃逸：

```java
try {
    ...
} catch (IOException e) {
    ServerLogger.logRun("用户[" + nickName + "]连接异常：" + e.getMessage());
} finally {
    cleanUp();
}
```

- 某个客户端处理线程抛出异常，只会结束该线程，不会杀死整个 JVM；
- 但 `main` 线程若抛出未捕获异常，服务端会停止，因此 `ChatServer` 也对 `IOException` 做了捕获；
- 线程中未捕获的异常不会自动通知其他线程，必须通过日志、关闭连接或共享状态来传递。

#### 5.2.5 一线程一连接的代价与演进

**优点：**

- 模型简单，每个连接的代码像单线程程序一样顺序执行；
- 一个连接阻塞不会影响其他连接；
- 便于理解 Socket 读写和会话状态。

**代价：**

- 每个线程都有栈空间和调度开销，连接数大时内存和上下文切换成本高；
- 线程数随在线人数线性增长；
- 大量线程只是阻塞在 `readLine()`，CPU 利用率低。

**演进方向：**

| 方案 | 说明 | 适用场景 |
| --- | --- | --- |
| 线程池 | `Executors.newCachedThreadPool()` 或自定义池 | 连接数可控、短任务；长连接会长期占用线程，需谨慎 |
| 虚拟线程（JDK 21+） | `Executors.newVirtualThreadPerTaskExecutor()` | 大量阻塞式长连接，代码几乎不变 |
| NIO / Selector | 少量线程管理大量连接，事件驱动 | 高并发网关、IM 服务端 |
| Netty 等框架 | 封装 NIO、编解码、心跳、重连 | 生产级网络应用 |

#### 5.2.6 `volatile` 与停止标志

客户端接收线程使用 `volatile` 修饰运行标志：

```java
private volatile boolean running = true;

public void stop() {
    running = false;
}
```

作用：

- 保证主线程对 `running` 的修改对接收线程**可见**；
- 防止 JIT 编译器把循环中的 `running` 缓存到寄存器，导致 `stop()` 后线程仍不退出；
- `volatile` 只保证可见性和有序性，**不保证复合操作的原子性**。

实际退出时，客户端还会关闭 Socket，使阻塞中的 `readLine()` 立即返回或抛异常，避免线程一直卡在 IO 上。

#### 5.2.7 守护线程与 JVM 退出

本项目创建的线程默认都是**非守护线程**。JVM 会在所有非守护线程结束后退出。

- 服务端主线程运行 `accept()` 无限循环，因此服务端进程持续存活；
- 客户端接收线程如果一直阻塞，会阻止 JVM 退出；`finally` 中关闭 Socket 后，接收线程结束，JVM 才能正常退出；
- 若把接收线程设为 `setDaemon(true)`，主线程结束时 JVM 可以直接退出，但守护线程可能在任意时刻被强行终止，不适合需要可靠清理的任务。

#### 5.2.8 控制台输出的线程交错

客户端主线程负责打印提示语，接收线程负责打印服务端消息，两者都写 `System.out`：

- `PrintStream` 的单个 `println` 调用是同步的，不会把一行字符拆坏；
- 但“提示语”和“收到的消息”可能交错出现，例如提示输入时突然插入一条广播；
- 这是控制台 UI 的固有限制，不影响协议正确性。若要严格排版，可以引入统一的输出队列或图形界面。

---

### 5.3 并发同步机制

#### 5.3.1 并发问题的三个根源

| 特性 | 含义 | 本项目中的例子 |
| --- | --- | --- |
| 原子性 | 一个或多个操作不可被其他线程打断 | `containsKey()` + `put()` 必须整体原子 |
| 可见性 | 一个线程的修改能被其他线程及时看到 | `running` 标志需要 `volatile` |
| 有序性 | 指令重排不能破坏程序语义 | `synchronized` / `volatile` 提供 happens-before 保证 |

#### 5.3.2 `synchronized` 的作用

`synchronized` 同时提供两件事：

1. **互斥**：同一时刻只有一个线程能进入同一把锁保护的代码块；
2. **可见性**：释放锁之前的修改，对随后获得同一把锁的线程可见。

本项目中的使用：

```java
public synchronized boolean addUser(String nick, PrintWriter out) {
    if (onlineUsers.containsKey(nick)) {
        return false;          // 检查
    }
    onlineUsers.put(nick, out); // 写入
    return true;               // 检查 + 写入整体原子
}

public synchronized void removeUser(String nick) {
    onlineUsers.remove(nick);
}
```

广播中的同步块：

```java
synchronized (this) {
    targets = new ArrayList<>(onlineUsers.size());
    // 遍历并复制接收者
}
```

注意：

- `synchronized` 锁的是对象监视器，不是代码本身；
- 静态方法的 `synchronized` 锁的是 `Class` 对象；
- 锁可重入，同一线程可以重复进入同一把锁；
- 锁的范围要尽量小，但不能小到破坏业务原子性。

#### 5.3.3 为什么用了 `ConcurrentHashMap` 还要 `synchronized`

这是本项目最重要的并发知识点之一：

- `ConcurrentHashMap` 保证 `get/put/remove` 等**单个操作**线程安全；
- 但 `addUser()` 的业务语义是“**如果不存在才插入**”，这是两个步骤；
- 两个线程可能同时执行 `containsKey()` 都得到 `false`，然后都执行 `put()`，导致后一个覆盖前一个，昵称唯一性被破坏；
- 因此仍需用 `synchronized` 包住“检查 + 插入”，或者改用原子的 `putIfAbsent()`。

```java
// 更简洁的原子写法（可选）
public boolean addUser(String nick, PrintWriter out) {
    return onlineUsers.putIfAbsent(nick, out) == null;
}
```

`ConcurrentHashMap` 的其他特性：

- 不允许 `null` 键和 `null` 值；
- 迭代器是**弱一致**的，遍历过程中其他线程修改不会抛 `ConcurrentModificationException`；
- 适合高并发读写，但复合操作仍需额外同步。

#### 5.3.4 最小临界区：快照广播

如果把网络发送放在锁内，锁的持有时间取决于最慢的客户端。快照模式把临界区缩小为“复制引用”：

```java
List<PrintWriter> targets;
synchronized (this) {
    targets = new ArrayList<>(onlineUsers.size());
    for (Map.Entry<String, PrintWriter> entry : onlineUsers.entrySet()) {
        if (!entry.getKey().equals(excludeNick)) {
            targets.add(entry.getValue());
        }
    }
}
for (PrintWriter writer : targets) {
    writer.println(message);   // 锁外发送
}
```

优点：

- 锁内只做内存操作，速度快；
- 某个客户端网络慢，不会阻塞其他广播；
- 遍历的是快照，不受并发增删影响。

代价：

- 快照期间新加入的用户不会收到本次广播，这是可接受的业务语义；
- 快照会短暂增加内存占用，但 `PrintWriter` 只是引用，开销很小。

#### 5.3.5 失效连接的清理与锁

广播发送失败时，需要在锁内按输出流反查昵称并移除：

```java
synchronized (this) {
    String removeNick = null;
    for (Map.Entry<String, PrintWriter> entry : onlineUsers.entrySet()) {
        if (entry.getValue() == writer) {
            removeNick = entry.getKey();
            break;
        }
    }
    if (removeNick != null) {
        onlineUsers.remove(removeNick);
    }
}
```

这里使用 `==` 比较输出流对象身份，而不是 `equals()`，因为需要找到“同一个输出流实例”对应的昵称。

#### 5.3.6 `volatile`、`synchronized`、原子类的选择

| 机制 | 保证 | 适用场景 | 本项目 |
| --- | --- | --- | --- |
| `volatile` | 可见性、禁止部分重排；不保证原子性 | 一个线程写、多个线程读的状态标志 | `ReceiveMsgThread.running` |
| `synchronized` | 互斥 + 可见性 | 复合操作、临界区 | `addUser`、`removeUser`、广播快照 |
| `ConcurrentHashMap` | 单操作线程安全、弱一致迭代 | 高并发共享映射 | `onlineUsers` |
| `AtomicInteger` 等 | 单变量 CAS 原子操作 | 计数器、序号生成 | 本项目未使用 |
| `ReentrantLock` | 可中断、可超时、可公平 | 需要高级锁特性 | 本项目未使用 |

#### 5.3.7 锁顺序与死锁预防

死锁的典型条件是“多个线程以不同顺序获取多把锁”。本项目的锁使用遵循以下原则：

- 每个方法尽量只持有一把锁；
- `OnlineUserManager` 的锁和 `ServerLogger.WRITE_LOCK` 是两把独立的锁；
- 不在持有管理器锁时调用可能反向获取管理器锁的外部方法；
- 不在锁内执行网络 IO 等长时间阻塞操作。

需要特别注意：`broadcast()` 的异常清理分支在持有管理器锁时调用了日志方法，日志方法会获取 `WRITE_LOCK`。当前代码中不存在“先持有 `WRITE_LOCK` 再获取管理器锁”的路径，因此不会形成循环等待；但更严谨的做法是把日志移到锁外，进一步缩短临界区。

#### 5.3.8 happens-before 与内存模型

Java 内存模型通过 happens-before 规则保证可见性：

- 线程 `start()` happens-before 该线程中的任何操作；
- 一个锁的 `unlock()` happens-before 后续对同一锁的 `lock()`；
- 对 `volatile` 变量的写 happens-before 后续对它的读；
- 线程 `join()` 返回 happens-before 该线程中的所有操作。

这解释了为什么：

- `ClientHandler` 启动后能看到构造方法中传入的 `socket` 和 `userManager`；
- `addUser()` 写入的昵称，其他线程在同一个锁保护下能看到；
- `volatile running = false` 能被接收线程及时观察到。

#### 5.3.9 共享资源与保护方式对照

| 资源 | 是否共享 | 保护方式 | 说明 |
| --- | --- | --- | --- |
| `onlineUsers` | 是 | `ConcurrentHashMap` + `synchronized` | 注册、移除、广播快照 |
| 每个连接的 `br` | 否 | 无锁 | 只由该连接的处理线程读取 |
| 每个连接的 `pw` | 是（广播线程会写） | `PrintWriter` 内部同步 | 单次 `println` 不会拆行 |
| `nickName` | 否 | 无锁 | 处理线程私有；注册后不再改变 |
| `running` | 是 | `volatile` | 主线程写、接收线程读 |
| 日志文件 | 是 | `WRITE_LOCK` | 打开、写入、关闭整体串行 |
| `System.out` | 是 | `PrintStream` 内部同步 | 行级安全，但输出顺序可能交错 |

---

## 六、同类项目的通用公式

### 6.1 一句话公式

> **服务端 = accept 循环 + 每连接一个处理线程 + 一份加锁的共享状态 + 一套行协议 + 快照广播 + finally 清理 + 统一日志**
>
> **客户端 = 连接 + 注册握手 + 接收线程 + 输入循环 + finally 关闭**

可以进一步拆成四个层次：

```text
① 传输层：Socket / ServerSocket，负责“连得上”
② 协议层：按行文本协议，负责“聊得通”
③ 并发层：线程模型 + 共享状态同步，负责“撑得住”
④ 运维层：日志 + 异常处理 + 资源释放，负责“查得清、退得干净”
```

### 6.2 服务端骨架

```java
public class ChatServer {
    private static final int PORT = 8888;
    private static final OnlineUserManager registry = new OnlineUserManager();

    public static void main(String[] args) throws IOException {
        try (ServerSocket server = new ServerSocket(PORT)) {
            log("服务器启动，端口 " + PORT);
            while (true) {
                Socket socket = server.accept();                  // ① 接入
                new Thread(new ClientHandler(socket, registry))   // ② 一线程一连接
                        .start();
            }
        }
    }
}
```

要点：

- `accept()` 只负责接入，不处理业务；
- 每个连接的处理逻辑封装在 `Runnable` 中；
- `ServerSocket` 用 `try-with-resources` 管理；
- 主循环里不要放耗时业务，否则会阻塞新连接接入。

### 6.3 连接处理器骨架

```java
public class ClientHandler implements Runnable {
    private final Socket socket;
    private final OnlineUserManager registry;
    private String nickName;            // 本连接私有，注册后赋值

    public void run() {
        try {
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter out = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

            // ① 握手 / 注册：循环直到成功或对端断开
            if (!register(in, out)) {
                return;
            }

            // ② 消息循环：readLine 返回 null 表示对端正常关闭
            String line;
            while ((line = in.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                handleMessage(line);
            }
        } catch (IOException e) {
            log("连接异常：" + e.getMessage());
        } finally {
            cleanup();                  // ③ 无论成功失败都要清理
        }
    }
}
```

这个骨架适用于：聊天室、命令行远程控制、简单的多人游戏、消息推送、在线协作等“长连接 + 行协议”场景。

### 6.4 共享状态管理器骨架

```java
public class OnlineUserManager {
    private final Map<String, PrintWriter> users = new ConcurrentHashMap<>();

    // 复合操作：检查 + 写入必须原子
    public synchronized boolean addUser(String nick, PrintWriter out) {
        if (users.containsKey(nick)) {
            return false;
        }
        users.put(nick, out);
        return true;
    }

    public synchronized void removeUser(String nick) {
        users.remove(nick);
    }

    // 锁内取快照，锁外发送
    public void broadcast(String message, String excludeNick) {
        List<PrintWriter> targets;
        synchronized (this) {
            targets = new ArrayList<>(users.size());
            for (Map.Entry<String, PrintWriter> entry : users.entrySet()) {
                if (!entry.getKey().equals(excludeNick)) {
                    targets.add(entry.getValue());
                }
            }
        }
        for (PrintWriter out : targets) {
            out.println(message);
            out.flush();
        }
    }
}
```

如果只需要“昵称唯一插入”，可以将 `addUser()` 简化为：

```java
public boolean addUser(String nick, PrintWriter out) {
    return users.putIfAbsent(nick, out) == null;
}
```

### 6.5 客户端骨架

```java
public class ChatClient {
    public static void main(String[] args) {
        try (Socket socket = new Socket(ip, port);
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(
                     new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {

            // ① 注册握手
            register(in, out);

            // ② 接收线程：独占输入流
            Thread receiver = new Thread(() -> {
                try {
                    String line;
                    while ((line = in.readLine()) != null) {
                        System.out.println(line);
                    }
                } catch (IOException e) {
                    System.out.println("与服务器断开连接");
                }
            });
            receiver.start();

            // ③ 主线程：读取键盘并发送
            Scanner scanner = new Scanner(System.in);
            while (true) {
                String line = scanner.nextLine().trim();
                if (line.isBlank()) {
                    continue;
                }
                if ("/quit".equals(line)) {
                    break;
                }
                out.println(line);
            }
        } catch (IOException e) {
            System.out.println("连接失败：" + e.getMessage());
        }
        // try-with-resources 关闭 Socket，接收线程随之退出
    }
}
```

要点：

- 输入流和输出流可以分别由不同线程使用，但**同一个流不要被多个线程同时读**；
- 关闭 Socket 是唤醒阻塞 `readLine()` 的有效手段；
- `try-with-resources` 让客户端退出路径更简单；
- 上面的接收线程是简化版：正常 `/quit` 关闭 Socket 时也会进入 `catch`。实际项目用 `volatile running` 标志区分“主动退出”和“异常断线”，避免误报断线提示。

### 6.6 线程安全日志骨架

```java
public final class ServerLogger {
    private static final Object LOCK = new Object();

    public static void log(String file, String content) {
        synchronized (LOCK) {
            try (PrintWriter out = new PrintWriter(new FileWriter(file, true))) {
                out.printf("[%s] %s%n", LocalDateTime.now(), content);
            } catch (IOException ignored) {
                // 日志失败不抛出，避免影响主业务
            }
        }
    }
}
```

### 6.7 推荐开发顺序

沿用“每完成一步都能运行验证”的思路：

| 步骤 | 实现内容 | 验证方式 |
| --- | --- | --- |
| 1 | `OnlineUserManager`：在线表 + 加锁的注册/移除/广播 | 单元测试或临时 `main` 测试 |
| 2 | `ServerLogger`：能向 `logs/` 追加写文件 | 启动后检查日志文件 |
| 3 | `ChatServer`：绑定端口 + `accept()` 循环 | 用 telnet 能连上，服务端不退出 |
| 4 | `ClientHandler`：昵称注册协议 | telnet 发送 `NICK:test` 收到 `OK` / `ERROR_NICK` |
| 5 | `ChatClient`：连接 + 注册 | 能成功进入聊天室 |
| 6 | `ReceiveMsgThread`：接收线程 | 两个客户端能互相看到消息 |
| 7 | 收尾：`/quit`、空消息、异常断开、日志 | 正常退出、强杀客户端、重名等场景逐一验证 |

先让“能连上”，再让“能注册”，然后“能广播”，最后补“异常与清理”。这种增量式开发可以把问题定位在较小的范围内。

### 6.8 通用检查清单

实现同类项目时，可以逐项核对：

- [ ] `accept()` 循环与业务处理分离，接入不被消息处理阻塞；
- [ ] 每个连接有独立的读线程，阻塞读不会影响其他连接；
- [ ] 客户端输入与接收分离，避免“能发不能收”；
- [ ] 在线表使用线程安全容器；
- [ ] “检查后写入”的复合操作有原子性保证；
- [ ] 广播前复制快照，锁内不做网络 IO；
- [ ] 广播按需求排除发送者；
- [ ] 下线顺序正确：先移除、再广播、最后关闭资源；
- [ ] 资源清理放在 `finally`；
- [ ] 关闭 Socket 能唤醒阻塞中的读线程；
- [ ] 停止标志使用 `volatile`；
- [ ] 日志写入线程安全，失败不影响主业务；
- [ ] 客户端和服务端都做输入校验；
- [ ] 异常信息对用户友好，详细堆栈/原因写入日志；
- [ ] 协议有文档，字段有边界校验（长度、空值、非法前缀）；
- [ ] 端口冲突、连接失败、字符集等常见问题有排查说明。

### 6.9 常见变体与改动点

| 需求 | 改动点 |
| --- | --- |
| 私聊 | 协议增加 `@昵称 消息` 或 `PRIVATE:昵称:内容`；广播改为按昵称查表定向发送 |
| 聊天室分组 | 在线表的值改为“用户 + 房间”，广播时按房间过滤 |
| 线程池 | 用 `ExecutorService` 提交 `ClientHandler`；注意长连接会长期占用线程 |
| 虚拟线程 | JDK 21+ 使用 `Executors.newVirtualThreadPerTaskExecutor()`，代码结构基本不变 |
| NIO 高并发 | 用 `Selector` + `Channel` 替代“一连接一线程”，引入事件循环和缓冲区管理 |
| 心跳与超时 | 增加 `PING/PONG` 消息；`Socket.setSoTimeout()` 设置读超时；定时清理僵尸连接 |
| 文件传输 | 不能再只用行协议，需要“长度前缀 + 二进制帧”，区分文本帧和文件帧 |
| 消息持久化 | 广播前写入数据库/文件；启动时加载历史消息 |
| 优雅停机 | 注册 `ShutdownHook`，关闭 `ServerSocket`，通知所有处理线程退出并清理 |
| 图形界面 | 用 Swing/JavaFX 替换 `Scanner`/`System.out`，网络层保持不变 |

### 6.10 公式总结

```text
服务端：
  ServerSocket(PORT)
    + while(true) { Socket s = accept(); new Thread(new Handler(s, registry)).start(); }
    + Registry { ConcurrentHashMap + synchronized 复合操作 + 快照广播 }
    + Handler { 注册握手 + readLine 循环 + finally 清理 }
    + Logger { 静态锁 + 追加写文件 }

客户端：
  Socket(IP, PORT)
    + 注册握手
    + Thread(receiver) { readLine -> System.out }
    + 主线程 { Scanner -> PrintWriter }
    + finally { close socket }

并发安全：
  原子复合操作(synchronized / putIfAbsent)
  + 线程安全容器(ConcurrentHashMap)
  + 最小临界区(锁内快照、锁外 IO)
  + 可见性(volatile)
  + 无环锁顺序(避免死锁)

健壮性：
  EOF 处理 + IOException 捕获 + finally 清理 + 友好提示 + 日志留痕
```

---

## 七、总结与改进方向

### 7.1 项目完成情况

本项目完成了题目要求的全部核心功能：

- 多客户端同时在线，服务端一连接一线程；
- 昵称注册、唯一性校验、重复重试；
- 消息广播、上线欢迎、下线通知；
- 主动退出与异常断开都能清理在线表并释放资源；
- 客户端输入与接收并行；
- 运行日志与聊天日志分离，写入线程安全；
- 在线表与广播路径具备并发安全性。

### 7.2 当前实现的局限

| 局限 | 说明 | 改进方向 |
| --- | --- | --- |
| 阻塞 IO + 一线程一连接 | 线程数随连接数线性增长，海量连接下开销大 | 线程池、虚拟线程或 NIO |
| 默认字符集 | 跨平台时可能乱码 | 两端显式 `StandardCharsets.UTF_8` |
| `PrintWriter` 不抛 IO 异常 | 发送失败不能立即感知 | `checkError()` 或改用 `BufferedWriter` |
| 服务端无优雅停机 | 只能通过终止进程退出 | `ShutdownHook` + 关闭 `ServerSocket` + 通知处理线程 |
| 读超时缺失 | 恶意/异常客户端长期不发数据会占住线程 | `setSoTimeout()` + 心跳机制 |
| 服务端断线时客户端主线程仍阻塞 | 接收线程只打印提示，主线程还在等键盘输入 | 接收线程关闭 Socket 或设置共享状态，主线程检测后退出 |
| 控制台输出可能交错 | 提示语与广播消息可能穿插 | 统一输出队列或图形界面 |
| 无自动化测试 | 目前主要靠手工联调 | 增加注册、重名、广播、断线清理的集成测试 |

### 7.3 收获与体会

1. **网络编程的本质是“协议 + 并发 + 资源管理”**。Socket API 本身不难，难的是多个连接同时读写时，如何保证共享状态正确、异常路径也能释放资源。
2. **线程模型要从阻塞点出发设计**。服务端的 `accept()`、处理线程的 `readLine()`、客户端的 `nextLine()` 都是阻塞点，一个阻塞源通常需要一个独立线程或事件驱动机制。
3. **锁不是越多越安全，而是越精确越安全**。`synchronized` 应保护必要的复合操作；网络 IO、文件 IO 等慢操作应尽量移到锁外。
4. **异常路径和正常路径同样重要**。用户主动退出、直接关窗口、网络中断、服务端重启，都会走到 `finally` 和清理逻辑；这些路径写对了，系统才算真正可用。
5. **日志是最便宜的调试工具**。上线欢迎、下线通知、连接异常、资源释放都有日志后，很多并发问题可以通过时间线还原。
6. **先跑通，再加固**。从“能连上”到“能广播”，再到“并发安全”和“异常清理”，增量式开发让每一层问题都容易被定位。

---

## 附录：核心代码索引

| 主题 | 文件 | 关键位置 |
| --- | --- | --- |
| 端口与接入循环 | `src/com/msghub/server/ChatServer.java` | `PORT`、`main()`、`accept()` |
| 每连接一线程 | `src/com/msghub/server/ChatServer.java` | `new Thread(new ClientHandler(...)).start()` |
| 昵称注册 | `src/com/msghub/server/ClientHandler.java` | `run()` 中的注册循环 |
| 消息广播 | `src/com/msghub/server/ClientHandler.java` | `broadcast(...)` 调用 |
| 下线清理 | `src/com/msghub/server/ClientHandler.java` | `cleanUp()` |
| 在线表并发安全 | `src/com/msghub/server/OnlineUserManager.java` | `addUser()`、`removeUser()`、`broadcast()` |
| 快照广播 | `src/com/msghub/server/OnlineUserManager.java` | `broadcast()` 中的 `synchronized` 块 |
| 日志并发安全 | `src/com/msghub/server/ServerLogger.java` | `WRITE_LOCK`、`logToFile()` |
| 客户端连接与输入 | `src/com/msghub/client/ChatClient.java` | `main()`、发送循环 |
| 客户端接收线程 | `src/com/msghub/client/ReceiveMsgThread.java` | `run()`、`volatile running` |
