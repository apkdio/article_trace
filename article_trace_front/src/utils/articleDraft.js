/**
 * 未提交稿件的本地草稿：编辑抽屉里防抖写入，刷新或关页后回来还在。
 *
 * 刻意不用 pinia persist：没有任何视图读它，几十 KB 的正文放进响应式状态只会白走一层代理；
 * 存储用 localStorage（与登录态提示同一块地方），读取只在打开抽屉时发生一次。
 *
 * 存不下的东西说明白：封面如果还没提交，它是浏览器里的 File，进不了这里——恢复时得重新选。
 */

/** localStorage 的键；草稿只有一份（同一浏览器的多账号靠 username 字段区分） */
const KEY = 'article_draft'

/** 正文上限：localStorage 总共约 5MB，留出余量，超了就放弃本地存（草稿只是保险，不该拖慢编辑） */
const MAX_CONTENT_LENGTH = 1024 * 1024

/**
 * 写草稿；标题与正文都空着就当没写过，顺手清掉旧草稿。
 *
 * @return 是否写入成功（配额满、隐私模式下静默失败，调用方不必处理）
 */
export function saveDraft({username, id, title, categoryId, content}) {
  if (!title?.trim() && !content?.trim()) {
    clearDraft()
    return false
  }
  if ((content || '').length > MAX_CONTENT_LENGTH) {
    return false
  }
  try {
    localStorage.setItem(KEY, JSON.stringify({
      username: username || '',
      id: id || '',
      title: title || '',
      categoryId: categoryId || '',
      content: content || '',
      savedAt: Date.now()
    }))
    return true
  } catch (e) {
    return false
  }
}

/** 读草稿；只认当前账号的那一份，别人的（或格式坏掉的）一律当作没有 */
export function loadDraft(username) {
  try {
    const raw = localStorage.getItem(KEY)
    if (!raw) {
      return null
    }
    const draft = JSON.parse(raw)
    if (!draft || draft.username !== (username || '')) {
      return null
    }
    return draft
  } catch (e) {
    return null
  }
}

export function clearDraft() {
  try {
    localStorage.removeItem(KEY)
  } catch (e) {
    // 存不进去也删不掉（隐私模式）：忽略，草稿本来就是保险
  }
}
