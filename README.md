# OsuMapSuggester

一个可以为 **osu!standard** 玩家推图的基于 [Shiro](https://github.com/MisakaTAT/Shiro) 的机器人。

English: [README-en.md](README-en.md)

[![CI](https://github.com/StageGuard/OsuMapSuggester/actions/workflows/build.yml/badge.svg)](https://github.com/StageGuard/OsuMapSuggester/actions/workflows/build.yml) [![CodeFactor](https://www.codefactor.io/repository/github/stageguard/osumapsuggester/badge/main)](https://www.codefactor.io/repository/github/stageguard/osumapsuggester/overview/main)

查看开发进度：[#1](https://github.com/StageGuard/OsuMapSuggester/issues/1)

## 特性

插件可以从 **osu!standard** 玩家的 **Best Performance** 分析 **aim**, **speed** and **accuracy** 能力和 [PerformancePlus](https://syrin.me/pp+/) 以及其他因素来推断玩家的类型（跳跳人或串串人之类的）。

然后插件可以**针对这个玩家的弱点或强项**给这个玩家推荐特定类型的谱面。(未实现)

用户也可以通过[以下方式](https://github.com/StageGuard/OsuMapSuggester/wiki/Beatmap-Type-Ruleset)来自定义谱面类型规则。

除此之外，还有其他以下特性：

- [x] 查询玩家的 **Best Performance** 并以图片显示。
- [x] 和其他玩家对比 **Best Performance**。
- [x] 以 Full Combo 重新计算 **Best Performance** 和排名。
- [ ] 显示玩家技能雷达图。
- [x] 查询玩家最近一次成绩，包括类似 osu!lazer 的 Accuracy Heatmap 和 PP 曲线图等属性，并以图片显示。
- [ ] ...

## 插件如何实现处理 OAuth 链接和绑定 osu 账号

当用户点击 OAuth 链接并且授权之后，将会自动重定向 OAuth 配置中的回调网址。

OsuMapSuggester 将会开启一个 HTTP 前端来处理这些数据。

## 开始

### 使用

如果你已经加了拥有此功能的 BOT 所在的群，想查看使用方法，请前往 [Wiki](https://github.com/StageGuard/OsuMapSuggester/wiki) 界面。

### 部署

#### 准备工作

- **MySQL** 或 **MariaDB** 数据库, 并需要为插件创建一个数据库。

- 有公网 IP 的服务器。

- <details> <summary>osu! OAuth 应用</summary>
  1. 前往 <a href="https://osu.ppy.sh/home/account/edit">https://osu.ppy.sh/home/account/edit</a><br><br>
      2. 点击 <b>New OAuth Application</b><br>
      <img src="static/new_oauth_app_button.png" alt="new_oauth_app_button"/><br><br>
      3. 把 <b>Application Callback URL</b> 设为 <b>http://&lt;你的服务器 IP 或域名&gt;:端口/authCallback</b><br>
      <img src="static/new_oauth_app.png" height="200" alt="new_oauth_app"/><br><br>
      4. 复制 <b>Client Id</b> 和 <b>Client Secret</b>.<br>
      <img src="static/oauth.png" height="200" alt="oauth"/>
  </details>

- <details> <summary>osu! v1 api 密钥</summary>
      点击申请一个 v1 api 密钥: <a href="https://osu.ppy.sh/p/api/">https://osu.ppy.sh/p/api/</a>
  </details>

#### 运行

1. 克隆项目和子模块 (`git clone --recurse-submodules https://github.com/StageGuard/OsuMapSuggester.git`) 并用 IntelliJ IDEA 打开工程. 同步 gradle 项目后运行 `build/bootJar` gradle 任务来构建项目。

> 在构建之前，首先需要安装 rust 工具链 [`cargo`](https://doc.rust-lang.org/cargo/)，gradle 同步过程中会检测 cargo 安装状态，请保证 cargo 已添加到在 `PATH` 环境变量中。

> 如果你不想用 IntelliJ IDEA，也可以克隆后在命令行运行 `chmod +x gradlew && ./gradlew bootJar` 指令来构建. 构建完成后的 jar 输出在 `build/libs`.

3. 创建配置文件 `application.yaml` 编辑以下内容

```yaml
server:
  port: 5000 # 你的 OneBot 实现端反代服务器
shiro:
  ws:
    server:
      enable: true
      url: "/ws/shiro"
qq: 1234567890 # 为这个 BOT 启用插件
database: 
  address: localhost # 数据库地址
  port: 3306 # 端口
  user: root # 账号
  password: testpwd # 密码
  table: obmsug # 数据库名称（在准备工作第一步创建的数据库）
  maximumPoolSize: 10
osuAuth: 
  clientId: 0 # OAuth clientId
  secret: '' # OAuth client secret
  # 回调地址，必须和 OAuth 设置的相同（不包含 /authCallback)
  # 注意这个地址是为了生成绑定账号的 OAuth 链接。
  authCallbackBaseUrl: 'http://localhost:8081' 
  v1ApiKey: '' # vi api 密钥
frontend:
  host: localhost # 前端主机地址，注意这个地址是实际主机地址
  port: 8081 # 前端端口
```

4. 将 jar 和 application.yaml 放在同一个目录下，运行 `java -jar OsuMapSuggester-xxx.jar` 启动。若看到一下输出则证明启动成功

```
 INFO 11372 --- [atcher-worker-2] m.s.obms.frontend.NettyHttpServer        : Autoreload is disabled because the development mode is off.
 INFO 11372 --- [atcher-worker-2] m.s.obms.frontend.NettyHttpServer        : Application started in 0.057 seconds.
 INFO 11372 --- [atcher-worker-1] m.s.obms.frontend.NettyHttpServer        : Responding at http://localhost:8081
 INFO 11372 --- [atcher-worker-2] m.s.obms.frontend.NettyHttpServer        : Frontend server respond at http://localhost:8081
```

## 问题反馈

这个项目仍在活跃开发中，并不稳定并且有许多 BUG。

如果你在使用过程中遇到了致命 BUG，请新建一个 Issue 并加上 `bug` 标签。

同时欢迎 pr；或者如果有好的想法，也可以新建一个 Issue 加上`feature` 标签。

## 使用到的库

- OneBot: [Shiro](https://github.com/MisakaTAT/Shiro)
- Database: [Ktorm](https://github.com/kotlin-orm/ktorm), [HikariCP](https://github.com/brettwooldridge/HikariCP)
- Web Server: [ktor](https://github.com/ktorio/ktor)
- Graphics: [skija](https://github.com/JetBrains/skija)
- osu! Related: [peace-performance](https://github.com/Pure-Peace/peace-performance), [pp+ algorithm](https://github.com/Syriiin/osu), [osuReplayAnalyzer](https://github.com/firedigger/osuReplayAnalyzer)
- Utilities: apache utilities(commons-io, commons-math3, commons-compress), [xz](https://tukaani.org/xz/java.html)

## 许可证

```
OsuMapSuggester
Copyright (C) 2021-2024 StageGuard

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published
by the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.
```