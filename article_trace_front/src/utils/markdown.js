import {marked} from 'marked'
import {MATH_SEGMENT_RE} from '@/utils/mathRender.js'

/**
 * Markdown 转 HTML。
 *
 * 公式先抽成占位符再交给 marked：marked 会把公式里的 `_` `*` `\` 当排版标记（`a_1` 会变斜体）。
 * 顺手把 `$..$` 规范成 `\(..\)`——展示端不认单独的 `$`（它会把「价格 $5 到 $10」当成公式）。
 *
 * 不改变存储格式：转换出来的仍是 HTML，与存量文章一致，保存前后端照旧过 jsoup 白名单。
 */
export function markdownToHtml(markdown) {
  const slots = []
  const protectedText = String(markdown || '').replace(
      MATH_SEGMENT_RE,
      (raw, blockDollar, blockBracket, inlineParen, inlineDollar) => {
        const isBlock = blockDollar !== undefined || blockBracket !== undefined
        const isParenInline = inlineParen !== undefined
        const normalized = isBlock || isParenInline ? raw : `\\(${inlineDollar}\\)`
        const key = `@@MATH${slots.length}@@`
        slots.push(normalized)
        return key
      })
  const html = marked.parse(protectedText, {gfm: true, breaks: true})
  return sanitizeImportedHtml(html.replace(/@@MATH(\d+)@@/g, (_, i) => slots[Number(i)]))
}

/**
 * 导入内容的一层保险：真正的门槛在后端 jsoup 白名单（保存与回显都会过），
 * 这里只是别让粘贴进来的 HTML 在编辑器里就先跑起来。
 */
export function sanitizeImportedHtml(html) {
  const doc = new DOMParser().parseFromString(html, 'text/html')
  doc.querySelectorAll('script, style, iframe, object, embed, link, meta, form').forEach((el) => el.remove())
  doc.querySelectorAll('*').forEach((el) => {
    Array.from(el.attributes).forEach((attr) => {
      const name = attr.name.toLowerCase()
      const value = (attr.value || '').trim().toLowerCase()
      if (name.startsWith('on')) {
        el.removeAttribute(attr.name)
      } else if (/^(href|src|xlink:href)$/.test(name) && /^(javascript|vbscript|data:text)/.test(value)) {
        el.removeAttribute(attr.name)
      }
    })
  })
  return doc.body.innerHTML
}

/**
 * 把转换结果按「表格 / 非表格」切开。
 *
 * Quill 1.3 没有表格格式，表格只能以只读嵌入块插入（见 ArticleManage.vue 的 TableEmbed），
 * 其余部分照旧走剪贴板粘贴。这里只做正则切分、不碰 DOM，好让这段纯逻辑能单独在 node 里验。
 */
export function splitMarkdownTables(html) {
  const segments = []
  const tableRe = /<table[\s\S]*?<\/table>/gi
  let last = 0
  let match
  while ((match = tableRe.exec(html)) !== null) {
    if (match.index > last) {
      segments.push({type: 'html', html: html.slice(last, match.index)})
    }
    segments.push({type: 'table', html: match[0]})
    last = match.index + match[0].length
  }
  if (last < html.length) {
    segments.push({type: 'html', html: html.slice(last)})
  }
  return segments
}
