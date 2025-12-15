<h3 align='center'>PinMe：磁贴截屏识别 → Flyme 实况通知 / 桌面插件</h3>

包名（`applicationId`）：`com.brycewg.pinme`

## 功能

- 控制中心磁贴一键触发：请求一次截屏权限并抓取当前屏幕。
- 调用 vLLM（OpenAI 兼容接口）进行识别与关键信息抽取（例如取餐码、乘车信息等）。
- 结果同步到 Flyme 实况通知（胶囊）与普通通知。
- 桌面插件（Glance AppWidget）展示最近识别内容。

## 使用

1. 打开应用 → `设置`：配置 `Base URL` / `Model` / `API Key`（可选）。
2. 系统里编辑控制中心，把 `PinMe` 磁贴拖进去。
3. 点击磁贴：首次会弹出系统截屏授权；识别完成后会推送通知并刷新桌面插件。
