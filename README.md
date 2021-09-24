# OsuMapSuggester

一个可以为 **osu!standard** 玩家推图的 [mirai-console](https://github.com/mamoe/mirai-console) 插件。

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

- <details> <summary>mirai-console 运行环境</summary>
      点击查看如何部署 mirai-console 环境: <a href="https://github.com/mamoe/mirai/blob/dev/docs/UserManual.md">https://github.com/mamoe/mirai/blob/dev/docs/UserManual.md</a>
  </details>

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

1. 克隆并用 IntelliJ IDEA 打开工程. 同步 gradle 项目后运行 `mirai/buildPlugin` gradle 任务来构建项目。

> 如果你不想用 IntelliJ IDEA，也可以克隆后在命令行运行 `chmod +x gradlew && ./gradlew buildPlugin` 指令来构建. 构建完成后的 jar 输出在 `build/mirai`.

2. 把构建好的 jar 包放入 `<mirai-console目录>/plugins/` 中，启动 mirai console，不出意外的话你会看到以下输出：

```
2021-07-26 20:22:37 E/OsuMapSuggester: Failed to connect database: com.zaxxer.hikari.pool.HikariPool$PoolInitializationException: Failed to initialize pool: Access denied for user 'root'@'localhost' (using password: YES).
2021-07-26 20:22:37 E/OsuMapSuggester: Retry to connect database in 10 seconds.
```

3. 停止 mirai console, 编辑配置文件 `config/OsuMapSuggester/OsuMapSuggester.Config.yml`

```yaml
qq: 1234567890 # 为这个 BOT 启用插件
database: 
  address: localhost # 数据库地址
  port: 3306 # 端口
  user: root # 账号
  password: testpwd # 密码
  table: osu!beatmap suggester # 数据库名称（在准备工作第一步创建的数据库）
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

4. 保存，重新运行 mirai console，登录设定的账号后，看到以下输出则意味着工作正常：

```
2021-07-26 20:34:27 I/OsuMapSuggester: Subscribed group and friend messages.
```

## 问题反馈

这个项目仍在活跃开发中，并不稳定并且有许多 BUG。

如果你在使用过程中遇到了致命 BUG，请新建一个 Issue 并加上 `bug` 标签。

同时欢迎 pr；或者如果有好的想法，也可以新建一个 Issue 加上`feature` 标签。

## 使用到的库

- Mirai Framework: [mirai](https://github.com/mamoe/mirai/), [mirai-console](https://github.com/mamoe/mirai-console), [mirai-slf4j-bridge](https://github.com/project-mirai/mirai-slf4j-bridge)
- Database: [Ktorm](https://github.com/kotlin-orm/ktorm), [HikariCP](https://github.com/brettwooldridge/HikariCP)
- Web Server: [ktor](https://github.com/ktorio/ktor)
- Graphics: [skija](https://github.com/JetBrains/skija)
- osu! Related: [peace-performance](https://github.com/Pure-Peace/peace-performance), [pp+ algorithm](https://github.com/Syriiin/osu), [osuReplayAnalyzer](https://github.com/firedigger/osuReplayAnalyzer)
- Utilities: apache utilities(commons-io, commons-math3, commons-compress), [xz](https://tukaani.org/xz/java.html)

## 许可证

```
OsuMapSuggester
Copyright (C) 2021 StageGuard

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

```
mirai
Copyright (C) 2019-2021 Mamoe Technologies and contributors.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as
published by the Free Software Foundation, either version 3 of the
License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
```
