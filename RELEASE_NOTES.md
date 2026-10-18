# 0.9.0-rc.1 发布候选记录

日期：2026-10-18（北京时间）。状态：**本地候选验收记录完成，未公开上传，未创建正式发布标签。** 本次收尾只核对既有证据、完善文档和建立本地提交及恢复点；不启动客户端或服务器，不重复执行完整测试，不重建或覆盖 JAR。

## 支持环境与功能基线

已验证基础环境限定为 **Minecraft 1.21.1、NeoForge 21.1.248、Refined Storage 2.0.9、Java 21**。不把元数据允许的其他版本或其他整合包标为已验证。Create、JEI 可选关系见[依赖说明](docs/DEPENDENCIES.md)。

用途为 RS2 终端四卡、多分类图标导航、包含/排除及属性过滤、临时样本和完整词条复制。物品 ID `rs2multifilter:filter_card` 不变，游戏显示“过滤升级”。

功能基线 `8d37536a839231eb22f84e13991e47e8138e7dfc` 保留 H1/H3 无损迁移及异常原数据保护、H2 四卡移除交付、H4 故障卡暂停及恢复、JEI 切屏分类保持、M1/M2 快照及有界生命周期缓存。发布准备仅涉及文档、许可证、版本号及打包，不改 Java、UI、生产资源、依赖、注册 ID、协议或卡片格式。

## 冻结的候选文件

路径相对于工作区根目录，源码仓库位于 `work/rs2-multifilter`。

| 文件 | 路径 | 大小 |
|---|---|---|
| 最终 RC | `work/rs2-multifilter/build/libs/rs2multifilter-0.9.0-rc.1.jar` | 184,998 字节 |
| 已验收稳定测试版 | `outputs/rs2multifilter-0.9.0-alpha-m1-m2-caches.jar` | 177,119 字节 |

RC SHA-256：

```text
0baf2b7ca41ce83a9da12a00ad9785b094554c7707285e9ac49624fbdd64ba6f
```

稳定测试版 SHA-256：

```text
7d1e7be63d2d009b6b5429dfdb033945c8e57e9b99b5437cb6d4552aab4fc94b
```

用户当前游戏实例安装的同名测试版与项目稳定版哈希一致，该实例 latest.log 记录了其加载。

两包 **83 个 class 逐字节相同**，现有纹理、模型、本地化、配方、Mixin 配置及 Manifest 相同，无原有文件删除。RC 元数据仅将 `0.9.0-alpha` 改为 `0.9.0-rc.1`，其余字段、依赖范围、加载顺序及两侧声明不变。RC class 与当前编译目录的 83 个文件一致，不包含测试桩或客户端检查探针。

仅新增五份包内文档，均与当前仓库文件逐字节相同：

- `META-INF/LICENSE`
- `META-INF/THIRD_PARTY_NOTICES.md`
- `META-INF/docs/TEXTURE_PROVENANCE.md`
- `META-INF/licenses/RefinedStorage-v1.6.15-LICENSE.md`
- `META-INF/licenses/RefinedStorage-v1.12.4-LICENSE.md`

## 实际验证结果

以下是本轮发布准备期间已经执行并留存的结果，最后收尾未重新运行：

| 检查 | 实际结果与边界 |
|---|---|
| 完整构建 | 最终 RC 构建 `BUILD SUCCESSFUL in 36s`；compileJava、processResources、test 实际重新执行，未将较早 UP-TO-DATE 结果当最终验证 |
| JUnit | 直接汇总留存的 23 份 XML：265 通过，0 失败、0 错误、0 跳过，覆盖 H1—H4、JEI 生命周期及 M1/M2 回归 |
| 隔离专服 | 加载独立 RC JAR，识别上述精确环境及本模组 0.9.0-rc.1；18:05:34 到达 `Done (4.483s)`，18:06:00 正常 stop，构建正常结束；不等于双人入服或带四卡存档重启 |
| 用户游戏验收 | 用户实际使用稳定测试版未见异常，确认 JEI 原分类消失 Bug 已解决；不虚构每个专项的操作记录 |
| RC 客户端额外启动检查 | **依据游戏验收及包内容一致性豁免**。此前由用户手动关闭，未完成，不据此认定游戏自行崩溃，**不得记为客户端实测通过**；不再启动 Minecraft |

本地证据位于工作区 `work/rc-prep-20261018/`：`rc-build-final.log`、`rc-fresh-tests/TEST-*.xml`、`rc-validation-prestartup.json`、`server-smoke.log`。这些日志不属于生产 JAR；未用历史测试数字代替本轮执行结果。

相同生产 class、资源及加载配置支持将既有游戏验收作为本 RC 的功能回归依据，无须为了增加许可证重跑整套操作。真实第三方属性失效/恢复、全部 JEI 转移路径、多人并发、带四卡专服重启及其他版本/整合包仍未全面验证，按[RC 清单](docs/RC_CHECKLIST.md)保留边界，不宣称全部兼容。

## 许可证、提交与恢复

原创源码采用 [MIT](LICENSE)，版权声明 `Copyright (c) 2026 Yo_zi`。两张纹理独立遵守上游 MIT；槽位图为 RS v1.12.4 `grid.png` 裁剪/透明化素材，保留 `Copyright © 2015 - 2023 Refined Mods` 及完整许可，原图不变。范围见[第三方通知](THIRD_PARTY_NOTICES.md)，许可与实际打包检查已完成。

本地提交收录发布文档和打包准备，恢复分支 `backup/pre-rc-close-20261018` 指向功能基线，`backup/rc1-release-prepared-20261018` 指向本次准备提交（可由 Git 查询具体提交号），均不是正式发布标签。收尾前文档快照与既有 JAR 校验清单位于工作区 `work/rc-prep-20261018/release-close-20261018/`。原稳定包、历史包、备份和原恢复点均保留，不推送远端。

在上述精确支持环境及验证边界内，没有尚待修复的已确认发布阻断项。公开发布仍由用户决定，本记录不执行上传或正式发版。升级前备份世界，mods 内只安装一份，两端同构建，见[安装与卸载](docs/INSTALL.md)。
