<h3 align='center'>PinMe：磁贴截屏识别 → Flyme 实况通知 / 桌面插件</h3>

包名（`applicationId`）：`com.brycewg.pinme`

## 功能

- 控制中心磁贴一键触发：请求一次截屏权限并抓取当前屏幕。
- 调用 vLLM（OpenAI 兼容接口）进行识别与关键信息抽取（例如取餐码、乘车信息等）。
- 结果同步到 Flyme 实况通知（胶囊）与普通通知。
- 桌面插件（Glance AppWidget）展示最近识别内容。
- 自定义识别类型与模型提供商（智谱 AI / 硅基流动 / 自定义）。

## 使用

1. 打开应用 → `设置`：配置 `Model` / `API Key`（默认使用智谱渠道,可以免费调用 glm-4v-flash）。
2. 系统里编辑控制中心，把 `PinMe` 磁贴拖进去。
3. 点击磁贴：首次会弹出系统截屏授权；识别完成后会推送通知并刷新桌面插件。
4. 无障碍服务：可选，开启后静默截图，无需每次申请录屏权限。

## 配置

1. 启动软件,允许通知权限
2. 进入设置页配置模型 API,推荐使用智谱的免费模型 glm-4v-flash,速度快,效果稳.注册并申请:https://bigmodel.cn/usercenter/proj-mgmt/apikeys
3. 可以将快捷方式发送到桌面,使用快捷小窗触发截图
4. 可以固定磁贴到控制中心,触发截图

## 致谢

- 感谢 [StarSchedule](https://github.com/lightStarrr/starSchedule) 提供的 Flyme 实况通知调用参考。
- 感谢群友 Ruyue 提供的实况通知代码支持。

## 赞赏

开源项目开发不易，喜欢我的项目可以请我喝一杯咖啡~
<img width="1213" height="1213" alt="mm_reward_qrcode_1765881183724" src="https://github.com/user-attachments/assets/6b9ba58a-d0c0-4cd9-85a8-0aabc70d9697" />
