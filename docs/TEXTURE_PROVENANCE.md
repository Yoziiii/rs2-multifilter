# 自带纹理来源核验

日期：2026-10-18。只核验并保留许可材料，不改两张 PNG。项目原创源码已采用 MIT，但项目元数据作者、Git 提交人或源码许可，均不能单独证明第三方素材版权归属。

## 1. filter_card.png

- 路径：`src/main/resources/assets/rs2multifilter/textures/item/filter_card.png`。
- 560 字节，16×16；本地 SHA-256：`cf8ba8c77b02491c14513b79254525333bc2bf1a091ea06470342b2117695404`。
- 本地最早 Git 记录是导入快照 `45fd118`，说明引用上游项目快照 `62b3339`；当前历史没有更早创作或下载步骤。导入者 `Codex` 不是确认过的素材作者。
- 核对 [RS v1.6.15 固定提交](https://github.com/refinedmods/refinedstorage/tree/f26165533aabccb4a2735272248632a73928843e) 的 `src/main/resources/assets/refinedstorage/textures/items/filter.png`，以及 [RS v1.12.4 固定提交](https://github.com/refinedmods/refinedstorage/tree/8ed764669911a5e40c15075bb430d10e4eec483d) 的 `textures/item/filter.png`。
- 两版上游 PNG 的 SHA-256 均为 `75ce13feed39aee7008c3b9d9bd907a3d84e49859b6279fe6408ab601f4d23d1`。本地 PNG 字节不同，但解码为 RGBA 后所有像素一致，不是主观相似判断。
- GitHub 返回的上游文件已核对 Git blob 校验。两固定树中找到根目录 MIT 许可；核验的两个树未找到额外 LICENSE/COPYING/NOTICE 文件。
- 保留 v1.6.15 的原始 MIT：Copyright © 2015 - 2018 Refined Storage contributors。完整正文见[许可副本](../licenses/RefinedStorage-v1.6.15-LICENSE.md)。v1.12.4 许可署名是 2015–2023 Refined Mods，不能代改前版声明。
- 结论：可追踪到像素相同、带 MIT 的上游作品，并保留该上游署名与许可；不声称确认本地实际导入自哪一版，不填写未知个人画师。图标独立适用上游 MIT，不作为本项目作者自有作品重新授权。

## 2. legacy_filter_slots.png

- 路径：`src/main/resources/assets/rs2multifilter/textures/gui/legacy_filter_slots.png`。
- 272 字节，31×82；SHA-256：`e2d90e61b50887e31e537b9d71ae878b6174a31f97cbd07f143f6bb419b294d3`。
- 后续只读追踪原项目历史，找到真正新增提交 `849655e6ee3ee7c1cbb5423be39127a07df46208`，时间为 2026-10-12 23:11:33（北京时间）；说明为 `assets(gui): render original RS four filter-slot bezel around actual vanilla card slots`。图片 blob `39f2d037514ed54ee40839f403e4f1b44000b7b0` 与当前相同。`45fd118` 只是后续本地导入快照，不是素材首次创作记录。
- 已确认的上游原图：[Refined Storage v1.12.4 / grid.png](https://github.com/refinedmods/refinedstorage/blob/8ed764669911a5e40c15075bb430d10e4eec483d/src/main/resources/assets/refinedstorage/textures/gui/grid.png)，固定提交 `8ed764669911a5e40c15075bb430d10e4eec483d`，路径 `src/main/resources/assets/refinedstorage/textures/gui/grid.png`。原图 SHA-256 为 `d0b6064c35a13ec2d0356424a08e6e174eece7ec49eb0dc2341668a3b6381eaf`；PNG 和许可均已与固定树的 Git blob 校验。
- 精确变换：裁剪 `(197,0)–(228,82)` 得 31×82，再将 `(7,6)–(23,22)`、`(7,24)–(23,40)`、`(7,42)–(23,58)`、`(7,60)–(23,76)` 四个区域设为 RGBA `(0,0,0,0)`；以上坐标右/下端均不含。重建后全部 2542 个 RGBA 像素与本地文件完全一致，改动恰为四个槽内共 1024 像素，其余不变。PNG 重新编码后的文件字节不同，不能宣称文件直接复制。
- 早期“找不到未修改的精确裁剪”只涵盖未透明化的比较；补充遮罩与重建后已确认是修改后的裁剪素材。RS v1.6.15 也有相同区域，多个版本共享画面内容，不据此声称知道唯一历史下载版本或个人画师。
- 对应 [v1.12.4 根目录 MIT](https://github.com/refinedmods/refinedstorage/blob/8ed764669911a5e40c15075bb430d10e4eec483d/LICENSE.md) 为 `Copyright © 2015 - 2023 Refined Mods`；完整原文见[许可副本](../licenses/RefinedStorage-v1.12.4-LICENSE.md)。该固定树与 README 未发现针对纹理的单独排除条款。MIT 允许在其条件下修改与再分发，需保留上游版权、完整许可及免责声明；不以本项目署名替换。原路径、版本与修改说明作为追溯记录保留。
- 当前使用状态：`ebedc8a` 移除旧常量/绘制，现行 `GridTabs.renderAttachedPanels` 使用 RS 自带 `generic_filter.png`；源码、资源和当前稳定 JAR 的 83 个 class 常量池均无旧图引用，但 PNG 仍在包内。不能把“不使用”误写成“已删除”。
- 结论：**来源与上游 MIT 授权依据已确认**。这张图片作为第三方派生素材单独适用上游许可，不由项目原创源码 MIT 重新授权。本轮不重复调查、不修改或删除图片；公开分发前仍核验实际打包的版权通知与完整许可。

## 3. logo.png（模组显示图标）

- 路径：`src/main/resources/logo.png`。
- 1,074,855 字节，1254×1254 RGB PNG；SHA-256：`797395d2b1bac9f7d8129003e59203c3fbbf8f9d24b947b424c04f8293c59a27`。
- 2026-10-19，项目维护者确认其为最终选定的 ChatGPT 生成模组图标，并要求随项目开源。当前文件与用户提供的最终 PNG 逐字节一致；记录以该确认和文件比对为依据，未独立核验生成会话、模型或提示词。
- 用于模组元数据 `logoFile="logo.png"`，不替换游戏内 `filter_card.png`。不修改图像，不编造其他作者、版权声明或单独许可证，也不将本图标归入 Refined Storage 的两张上游素材。
- 项目源码 MIT 声明不自动将该图标重新授权为原创源码；来源说明同时记录于[第三方及素材通知](../THIRD_PARTY_NOTICES.md)。

## 4. 发行范围

项目原创源码按 [LICENSE](../LICENSE) 采用 MIT，署名为 `Copyright (c) 2026 Yo_zi`。这两张 PNG 均为独立适用上游 MIT 的第三方素材，不因源码采用 MIT 就成为项目作者自有作品：图标保留已记录的上游许可，槽位图按 v1.12.4 保留 `Copyright © 2015 - 2023 Refined Mods` 及完整正文。

旧 JAR 不会被修改。新的候选包必须检查实际许可内容，不能以仓库文档存在或素材授权已确认，就宣称旧包或新包已完成发行验收。
