# 第三方素材通知与授权边界

核验日期：2026-10-18。本文件记录来源及现状，不为未确认作品创设许可，也不替代本项目权利人的授权决定。

## 本项目原创源码：MIT

用户已于 2026-10-18 确认项目原创源码采用 MIT，公开版权声明为 `Copyright (c) 2026 Yo_zi`。完整标准正文见 [LICENSE](LICENSE)。该确认不将第三方纹理、依赖或其他素材重新授权为本项目原创作品；各自适用其原有许可和署名要求。

## 过滤升级图标：独立的上游 MIT 作品

本地文件：`src/main/resources/assets/rs2multifilter/textures/item/filter_card.png`。

解码后的 16×16 RGBA 像素，与以下固定版本的 Refined Storage 图像完全相同；PNG 文件字节不同，不据此伪称知道原始导入/重编码步骤。

- [RS v1.6.15 的 textures/items/filter.png](https://github.com/refinedmods/refinedstorage/blob/f26165533aabccb4a2735272248632a73928843e/src/main/resources/assets/refinedstorage/textures/items/filter.png)。对应[该版本 MIT 许可](https://github.com/refinedmods/refinedstorage/blob/f26165533aabccb4a2735272248632a73928843e/LICENSE.md)声明：Copyright © 2015 - 2018 Refined Storage contributors。
- RS v1.12.4 同名作品（目录为 `textures/item/filter.png`）也有完全相同像素；不据此断定本地最早复制自哪一版，也不虚构某位个人画师。

上游 v1.6.15 许可原文保存在 [licenses/RefinedStorage-v1.6.15-LICENSE.md](licenses/RefinedStorage-v1.6.15-LICENSE.md)，未改署名、年份或正文。该 MIT 允许在其条件下复制、修改和分发，要求保留版权及许可声明；它不是本项目作者对这张图重新授予的自有版权许可。

日后若公开分发这张图，应随副本保留对应通知与完整上游许可。“文件已加入仓库”不等于旧稳定 JAR 已携带通知；最终发行打包必须另行验收，旧稳定包不会被追溯修改。

## 槽位纹理：Refined Storage v1.12.4 的 MIT 裁剪素材

本地文件：`src/main/resources/assets/rs2multifilter/textures/gui/legacy_filter_slots.png`。

已确认对应上游：[Refined Storage v1.12.4 的 grid.png](https://github.com/refinedmods/refinedstorage/blob/8ed764669911a5e40c15075bb430d10e4eec483d/src/main/resources/assets/refinedstorage/textures/gui/grid.png)，固定提交 `8ed764669911a5e40c15075bb430d10e4eec483d`。

裁剪上游 `(197,0)–(228,82)` 区域（右/下端不含），再将四个 16×16 槽内区域透明化，即与本地图片全部 RGBA 像素一致；不是未经修改的原文件，也不是仅凭外观推断。多个版本共享同一区域，不据此推断唯一历史下载操作。

对应 [v1.12.4 MIT 原文](https://github.com/refinedmods/refinedstorage/blob/8ed764669911a5e40c15075bb430d10e4eec483d/LICENSE.md) 的版权声明为：`Copyright © 2015 - 2023 Refined Mods`。完整、未经改写的许可正文保存在 [licenses/RefinedStorage-v1.12.4-LICENSE.md](licenses/RefinedStorage-v1.12.4-LICENSE.md)。该许可允许按其条件修改和再分发，须随副本或实质部分保留版权及许可通知（包括完整免责声明）。

这是独立适用上游 MIT 的第三方派生素材，不属于 `Copyright (c) 2026 Yo_zi` 的原创源码授权范围，不修改上游署名或猜测个人画师。当前代码已不再引用，但文件仍随既有 JAR 打包；本轮依要求保留原图，不删除或替换。发行时仍须核验包中实际包含相应版权及完整许可证，不能用授权已确认代替打包检查。

## 模组显示图标：用户确认的 ChatGPT 生成图片

文件：`src/main/resources/logo.png`，1254×1254 RGB PNG。SHA-256：`797395d2b1bac9f7d8129003e59203c3fbbf8f9d24b947b424c04f8293c59a27`。

项目维护者于 2026-10-19 确认，该文件是其最终选定的 ChatGPT 生成模组图标，并要求随项目开源。已核对其与用户提供的最终 PNG 文件字节完全一致，未修改图片，也未替换游戏内物品贴图。

本节依据维护者确认记录来源；没有独立核验生成会话、模型或提示词，不添加未经确认的其他作者、版权声明或单独许可证。项目原创源码的 MIT 声明不自动将该图标重新授权为原创源码；本图标也不归入上述两张 Refined Storage 纹理的来源与许可证记录。

## 运行时引用的其他模组资源

代码通过资源地址使用 Minecraft/RS 自己提供的纹理，并在安装相应模组后加载；本项目没有因此获得对所有 Minecraft、RS 或 Create 素材的自有版权。此类运行依赖与本地两张 PNG 的再分发应分开处理，不随本包重新打包这些依赖的完整资源或二进制。

完整哈希、Git 线索、比对范围与来源说明见[素材核验记录](docs/TEXTURE_PROVENANCE.md)。源码、两张 Refined Storage 纹理和模组显示图标分别记录，不能用一个全局 MIT 字段替代。
