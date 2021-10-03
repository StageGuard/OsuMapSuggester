## Frontend endpoint

### `authorize`

重定向至 `osu.ppy.sh`，用于绑定 BOT 或验证身份。

### `/authCallback`

从 `osu.ppy.sh` 重定向至此，用于处理 oAuth 结果的 `state` 和 `code`。

### `/import/{bid}`

将重定向至 `osu://b/{bid}` 用于 osu!supporter 的一键导入谱面。

### `/ruleset` 

编辑谱面时的身份认证流程：

1. 用户请求 `http://host/ruleset/{id}` 或 `http://host/ruleset/new` ，回应 `ruleset.html` 前端。
2. `ruleset.html` 前端获取 cookies 中的 `token`，认证身份(请求 `http://host/ruleset/verify` )。
3. 如果没有 `token`， `ruleset.html` 前端会请求 `http://host/ruleset/getVerifyLink` 获取 oAuth 链接。
4. 点击链接完成认证后，结果会在 `AUTH_CALLBACK_PATH` 中处理(在数据库中创建一个 `token` 并绑定到用户 QQ 上)， 处理后会重定向至第一步，并带有 `Set-Cookie: token`。
5. 编辑完成后提交，表单将会 post 到 `http://host/ruleset/submit` 进行验证并记录。