# 平台适配

只读当前实现栈相关部分。下列经验用于检查现有实现，不是可盲目粘贴的通用代码。

## Android Views / XML

- 复用项目实际使用的 AppCompat/Material 版本，将颜色、文字和组件样式放入现有资源体系。日夜资源尽量只覆盖对应 token，避免复制两套逐渐漂移的组件定义。
- 用最小高度、可增长文本和合适的滚动容器保证全部操作可达。短静态表单可以整体滚动；长 RecyclerView 不应为方便排版无界展开进外层滚动容器，可把头部作为列表项实现。
- 窗口边到边显示时明确谁消费系统栏、刘海和 IME inset；避免父子重复加 padding。核实边到边行为与目标 SDK，再检查键盘弹出、隐藏和旋转后的布局。
- 若系统栏和键盘都从同一容器底部计量，核对是否应取重叠区域最大值而非相加；有独立底部栏时先明确各自的 inset 所有权。不要复制固定 padding 公式到所有项目。
- 新增 layout-land 等资源时保留需要的控件 ID/类型和逻辑顺序，验证 saved state。共享 Activity/Fragment 初始化和恢复状态必须保持正确。
- Material 控件的单色 tint 可能覆盖状态色；保留 StateList 或框架默认行为。读屏标签包含实际值和动作，不留下 `%s`、`%d` 等格式占位符。
- 自绘文字的 Paint 样式应显式设置；环形 STROKE 与文字 FILL 分开，垂直对齐依据字体度量。

## Android Compose

映射到现有 ColorScheme、Typography、Shapes 与语义状态；按项目组件库定义 disabled、selected 等状态。沿用已有单向数据流与状态恢复方式，不把网络/付费请求放入可重复重组的渲染路径。

依据窗口与字体排版，明确 Scaffold、内容、底部动作和键盘的 inset 分工。长列表保持 Lazy 容器；真实滚动和语义树均需验收。

Android 触摸目标采用至少 48×48dp 的常用平台建议；触摸范围可大于可见图标，但相邻目标不应重叠。正文与装饰图标避免重复朗读。依据：[Android 无障碍说明](https://developer.android.com/guide/topics/ui/accessibility/apps.html)（核对日期：2026-09-10）。

## iOS / SwiftUI / UIKit

保留现有导航、返回、Sheet、Form/List 和平台组件习惯；颜色映射到项目 Asset Catalog/语义颜色，字号优先使用支持 Dynamic Type 的文字样式。不要把 Android 的 sp 字号、工具栏、返回图标和卡片外观逐项搬过去。

触摸区域以 44×44pt 为常用设计目标，结合实际组件及辅助功能检查；大字体下让信息重排。检查 safe area、键盘避让、VoiceOver 阅读顺序和减少动态效果设置。金额、错误等关键内容不得靠单纯缩小文字挤入固定宽度。

此部分是平台适配指导，来源项目没有 iOS 运行验证。实现时以实际 iOS/SDK 版本和项目组件为准。参考：[Apple Accessibility](https://developer.apple.com/design/human-interface-guidelines/accessibility/)、[Typography](https://developer.apple.com/design/human-interface-guidelines/typography)（核对日期：2026-09-10）。

## Flutter / React Native 等跨平台项目

把语义 token 映射到已有主题和组件层，沿用现有导航、状态管理和虚拟化列表。分别检查两个平台的安全区域、字体缩放、键盘、返回和辅助功能；共用源码不能证明两端呈现一致。具体 API、单位和最低支持版本读项目配置及相应官方文档，不自行移植原生片段或引入另一套 UI 框架。
