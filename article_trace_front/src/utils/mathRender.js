import katex from 'katex'
import renderMathInElement from 'katex/contrib/auto-render'

/**
 * 公式段的匹配规则：`$$..$$` 块级、`\[..\]` 块级、`\(..\)` 行内、`$..$` 行内。
 *
 * 这里比展示端多认一个 `$..$`（并要求首尾非空白），因为 Markdown 导入需要把它认出来并规范化；
 * 「价格 $5 到 $10」这类文本靠首尾空白这条规则排除。
 */
export const MATH_SEGMENT_RE = /\$\$([\s\S]+?)\$\$|\\\[([\s\S]+?)\\\]|\\\(([\s\S]+?)\\\)|\$(?!\s)([^\n$]*?)(?<!\s)\$/g

/**
 * 展示端认的分隔符：**不含单独的 `$..$`**。
 *
 * KaTeX 的扫描器只按分隔符配对、完全不看边界，认了 `$` 就会把「价格 $5 到 $10」整段当公式渲染；
 * 它又没有反斜杠转义（源码里没有这个分支），转义 `\$` 只会在页面上露出反斜杠。
 * 所以行内公式统一用 `\(..\)`：编辑器里的公式对话框与 Markdown 导入都会产出这个形态。
 */
const DELIMITERS = [
  {left: '$$', right: '$$', display: true},
  {left: '\\[', right: '\\]', display: true},
  {left: '\\(', right: '\\)', display: false}
]

/**
 * 渲染容器里的公式。正文页、预览抽屉、审核面板共用。
 *
 * 幂等：第二次跑时公式已被换成渲染结果，容器里再没有可配对的分隔符。
 */
export function renderMathIn(el) {
  if (!el) return
  try {
    renderMathInElement(el, {
      delimiters: DELIMITERS,
      throwOnError: false,
      ignoredTags: ['script', 'noscript', 'style', 'textarea', 'pre', 'code', 'option']
    })
  } catch (e) {
    // 单个公式渲染失败不该让整页崩掉，页面上留着 LaTeX 原文即可
  }
}

/** 把一条公式渲染成 HTML 字符串，供编辑器里的实时预览用 */
export function renderTex(tex, displayMode) {
  return katex.renderToString(tex, {displayMode: !!displayMode, throwOnError: false})
}
