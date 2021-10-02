## Frontend endpoint

### `authorize`

重定向至 `osu.ppy.sh`，用于绑定 BOT 或验证身份。

### `/authCallback`

从 `osu.ppy.sh` 重定向至此，用于处理 oAuth 结果的 `state` 和 `code`。

### `/import/{bid}`

将重定向至 `osu://b/{bid}` 用于 osu!supporter 的一键导入谱面。
