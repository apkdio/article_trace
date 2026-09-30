<script setup>

import {Delete, Edit, Plus, Picture, Search, User, Calendar, Timer, UserFilled} from '@element-plus/icons-vue'
import cover from '@/assets/defaultCover.jpg'
import {nextTick, onMounted, ref, watch} from 'vue'
import {addCategory, getAllCategories} from "@/api/category.js";
import {
  addArticleService, deleteArticleService,
  getArticleWithConditions, getArticleWithConditionsMaster,
  updateArticleService
} from "@/api/article.js";
import {Delta, Quill, QuillEditor} from '@vueup/vue-quill'
import '@vueup/vue-quill/dist/vue-quill.snow.css'
import {userInfoStore} from "@/stores/userInfo.js";
import {checkPersonInfo} from "@/api/checkPersonInfo.js";
import router from "@/router/index.js";
import {checkType} from "@/api/user.js";
import {CROP_PRESETS, checkImageFile, MAX_IMAGE_SIZE, MAX_SOURCE_IMAGE_SIZE} from "@/utils/upload.js";
import {confirmCompleteProfile, promptMasterPassword} from "@/utils/confirm.js";
import {markdownToHtml, sanitizeImportedHtml, splitMarkdownTables} from "@/utils/markdown.js";
import {renderMathIn, renderTex} from "@/utils/mathRender.js";
import PageHeader from "@/components/PageHeader.vue";
import ImageCropper from "@/components/ImageCropper.vue";


const categories = ref([])
const articles = ref([])
const isLoading = ref(true)
const conditionRef = ref({})
const errorList = ref({})
const pageNum = ref(1)
const total = ref(0)
const pageSize = ref(5)
const quillEditorRef = ref()
const editorContainerRef = ref()
// 编辑器实例：ready 之前 getQuill() 会抛异常，而打开 / 关闭抽屉都要经过编辑器——统一从这里取，取不到就当没有
const quillReady = ref(false)
// Quill 的按钮只有图标、没有说明（`clean` 那个「T 带叉」尤其容易被当成乱码），补一层原生 title
const TOOLBAR_TITLES = {
  header: '标题层级',
  size: '字号',
  bold: '加粗',
  italic: '斜体',
  underline: '下划线',
  strike: '删除线',
  color: '文字颜色',
  background: '背景色',
  list: '列表',
  indent: '缩进',
  align: '对齐',
  blockquote: '引用',
  'code-block': '代码块',
  link: '链接',
  image: '图片',
  formula: '插入公式（LaTeX）',
  markdown: '导入 Markdown',
  clean: '清除格式（先选中要清理的文字）'
}
const applyToolbarTitles = () => {
  const toolbar = editorContainerRef.value?.querySelector('.ql-toolbar')
  if (!toolbar) return
  const titleOf = (el) => Object.keys(TOOLBAR_TITLES).find(key => el.classList.contains(`ql-${key}`))
  toolbar.querySelectorAll('button').forEach(btn => {
    const key = titleOf(btn)
    if (key) btn.title = TOOLBAR_TITLES[key]
  })
  toolbar.querySelectorAll('.ql-picker').forEach(picker => {
    const key = titleOf(picker)
    if (key) picker.title = TOOLBAR_TITLES[key]
  })
}
// 回填：正文里存的是 <div class="ql-table-embed"><table>…</table></div>，
// 不认回去就会像普通 div 一样被摊平成文字（Quill 1.3 的 matcher 支持选择器）
const applyTableMatcher = () => {
  const quill = currentQuill()
  if (!quill) return
  quill.clipboard.addMatcher('.ql-table-embed', (node, delta) => {
    const table = node.querySelector('table')
    return table ? new Delta().insert({tableEmbed: table.outerHTML}) : delta
  })
}

const onQuillReady = () => {
  quillReady.value = true
  applyTableMatcher()
  applyToolbarTitles()
}
const currentQuill = () => {
  if (!quillReady.value || !quillEditorRef.value) return null
  try {
    return quillEditorRef.value.getQuill()
  } catch {
    return null
  }
}

const previewDrawer = ref(false)
const visibleDrawer = ref(false)
const drawerTitle = ref('')
const coverRef = ref(false)
const cropperRef = ref()
const articleModelRef = ref()
const articleModel = ref({
  id: '',
  title: '',
  categoryId: '',
  coverImgSrc: '',
  coverImg: '',
  content: '',
  state: ''
})
const previewData = ref({})

// 封面延后到提交时才上传：选好文件先本地预览，避免「还没决定发布就落存储」产生孤儿对象
const pendingCover = ref(null)
const coverPreviewUrl = ref('')

// 字号档位：class 驱动（后端白名单已放行 class），展示侧 CSS 在 assets/quill-content.scss
const SIZE_OPTIONS = ['14px', '16px', '18px', '20px', '24px', '28px']
const SizeAttributor = Quill.import('attributors/class/size')
SizeAttributor.whitelist = SIZE_OPTIONS
Quill.register(SizeAttributor, true)

// 表格以「只读块」存在正文里：Quill 1.3 没有表格格式，整张表逃不过被摊平，只能整块嵌进来。
// 类名要留着——回填时靠它把 <div class="ql-table-embed"> 认回嵌入块（见 applyTableMatcher）。
const BlockEmbed = Quill.import('blots/block/embed')
class TableEmbed extends BlockEmbed {
  static create(value) {
    const node = super.create(value)
    node.setAttribute('contenteditable', 'false')
    node.innerHTML = typeof value === 'string' ? value : ''
    return node
  }

  static value(node) {
    return node.innerHTML
  }
}

TableEmbed.blotName = 'tableEmbed'
TableEmbed.tagName = 'DIV'
TableEmbed.className = 'ql-table-embed'
Quill.register(TableEmbed)

const previewContentRef = ref()
const formulaDialog = ref(false)
const formulaTex = ref('')
const formulaDisplay = ref(true)
const formulaPreview = ref('')
const markdownDialog = ref(false)
const markdownText = ref('')
// 打开对话框时记下光标位置：对话框一开编辑器就失焦，插入时取不到 selection
let insertIndex = null

const openPreview = async (row) => {
  previewData.value = row
  previewDrawer.value = true
  await nextTick()
  renderMathIn(previewContentRef.value)
}

const rememberCursor = () => {
  const quill = currentQuill()
  if (!quill) {
    insertIndex = null
    return
  }
  const range = quill.getSelection(true)
  insertIndex = range ? range.index : quill.getLength()
}

const updateFormulaPreview = () => {
  const tex = formulaTex.value.trim()
  formulaPreview.value = tex ? renderTex(tex, formulaDisplay.value) : ''
}

watch([formulaTex, formulaDisplay], updateFormulaPreview)

const openFormulaDialog = () => {
  rememberCursor()
  formulaTex.value = ''
  formulaPreview.value = ''
  formulaDialog.value = true
}

const insertPlainText = (text) => {
  const quill = currentQuill()
  if (!quill) return
  const index = insertIndex === null ? quill.getLength() : insertIndex
  quill.insertText(index, text, 'user')
  quill.setSelection(index + text.length, 0, 'silent')
}

const insertFormula = () => {
  const tex = formulaTex.value.trim()
  if (!tex) {
    ElMessage.warning('请先写一条公式')
    return
  }
  // 块级 $$..$$、行内 \(..\)：展示端只认这两种，不认单独的 $..$（它会吞掉「价格 $5 到 $10」）
  insertPlainText(formulaDisplay.value ? `$$${tex}$$` : `\\(${tex}\\)`)
  formulaDialog.value = false
}

const openMarkdownDialog = () => {
  rememberCursor()
  markdownDialog.value = true
}

const insertMarkdown = (replaceAll) => {
  const html = markdownToHtml(markdownText.value)
  if (!html.trim()) {
    ElMessage.warning('还没有要转换的内容')
    return
  }
  const quill = currentQuill()
  if (!quill) return
  if (replaceAll) {
    quill.setContents([], 'user')
  }
  let index = replaceAll ? 0 : (insertIndex === null ? quill.getLength() : insertIndex)
  // 表格走只读嵌入块（Quill 会把 <table> 摊平），其余照旧粘贴；每插一段按正文长度把游标往后挪
  splitMarkdownTables(html).forEach((segment) => {
    const before = quill.getLength()
    if (segment.type === 'table') {
      quill.insertEmbed(index, 'tableEmbed', sanitizeImportedHtml(segment.html), 'user')
    } else {
      quill.clipboard.dangerouslyPasteHTML(index, segment.html, 'user')
    }
    index += quill.getLength() - before
  })
  markdownDialog.value = false
  markdownText.value = ''
}

// 工具栏：snow 默认配置之外补 6 档字号，以及「插入公式 / 导入 Markdown」两个自定义按钮。
// 这份配置走 quill-editor 的 `toolbar` prop——传对象时会被原样当作 modules.toolbar；
// 而 `modules` prop 是「按 {name, module} 注册第三方模块」的意思，把配置塞进去会让 new Quill() 起不来。
const quillToolbar = {
  container: [
    [{header: [1, 2, 3, 4, false]}],
    [{size: [...SIZE_OPTIONS, false]}],
    ['bold', 'italic', 'underline', 'strike'],
    [{color: []}, {background: []}],
    [{list: 'ordered'}, {list: 'bullet'}],
    [{indent: '-1'}, {indent: '+1'}],
    [{align: []}],
    ['blockquote', 'code-block', 'link', 'image'],
    ['formula', 'markdown'],
    ['clean']
  ],
  handlers: {
    formula: openFormulaDialog,
    markdown: openMarkdownDialog
  }
}

const validateContent = (rule, value, callback) => {
  if (!value || value.trim() === '' || value === '<p><br></p>' || value === '<p></p>') {
    callback(new Error('文章内容不能为空'))
  } else {
    const text = value.replace(/<[^>]+>/g, '').trim()
    if (text === '') {
      callback(new Error('文章内容不能为空'))
    } else {
      callback()
    }
  }
}

const articleModelRule = {
  title: [
    {required: true, message: '请输入标题', trigger: 'blur'},
    {min: 1, max: 30, message: '长度在 1 到 30 个字符', trigger: 'blur'},
    {pattern: /^\S(.*\S)?$/, message: '首尾不能是空格', trigger: 'blur'}
  ],
  content: [{validator: validateContent}],
  categoryId: [{required: true, message: '请选择分类！'}],
}

const conditions = ref({
  pageNum: null,
  pageSize: null,
  categoryId: null,
  state: null,
  search: null,
  searchType: 0
})

onMounted(() => {
  if (!checkType([0, 1])) router.push({name: "ErrorPage"})
  if (checkPersonInfo()) getCategories()
  else {
    confirmCompleteProfile(router)
  }
})

const getCategories = async () => {
  try {
    const data = await getAllCategories()
    categories.value = data.data
    await getArticles()
  } catch (error) {
    ElMessage.error("数据获取失败！")
  }
}

/**
 * 就地新建分类：建完立即刷新下拉并选中，省去「存草稿 → 去分类页 → 再回来」。
 * 命名规则与分类管理页一致；重名判定在后端（分类名全局唯一），前端只如实显示原因。
 */
const quickAddCategory = () => {
  ElMessageBox.prompt("分类名称会直接展示给读者，1-20 个字符，首尾不能是空格。", "新建分类", {
    confirmButtonText: "创建",
    cancelButtonText: "取消",
    inputPlaceholder: "例如：读书笔记",
    inputValidator: (value) => {
      const name = (value || "").trim()
      if (!name) return "请输入分类名称"
      if (name.length > 20) return "分类名称长度需在 1 到 20 个字符"
      return true
    },
    center: true,
  }).then(async ({value}) => {
    const categoryName = value.trim()
    try {
      const res = await addCategory({categoryName, categoryAlias: ""})
      if (res.code !== 0) {
        ElMessage.error(res.message || "新建失败！")
        return
      }
      // 新增接口不回传 id，刷新列表后按名字找回它并选中
      await getCategories()
      const created = categories.value.find((c) => c.categoryName === categoryName)
      if (created) {
        articleModel.value.categoryId = created.id
        await nextTick()
        articleModelRef.value?.validateField("categoryId")
      }
      ElMessage.success("分类已创建并选中")
    } catch (error) {
      ElMessage.error("新建失败！")
    }
  }).catch((action) => {
    // 校验不通过时 action 不是 'cancel'，别误报「已取消」
    if (action === "cancel") ElMessage.info("已取消！")
  })
}

const onSizeChange = (size) => {
  pageSize.value = size
  getArticles()
}
const onCurrentChange = (num) => {
  pageNum.value = num
  getArticles()
}

const getArticles = async () => {
  isLoading.value = true
  conditions.value.pageNum = pageNum.value
  conditions.value.pageSize = pageSize.value
  try {
    const userType = userInfoStore().type
    let data;
    if (userType === 0) data = await getArticleWithConditionsMaster(conditions.value)
    else data = await getArticleWithConditions(conditions.value)
    if (data.code === 0) {
      articles.value = data.data.items
      total.value = data.data.total
    } else {
      articles.value = []
    }
  } catch (error) {
    ElMessage.error("数据获取失败！")
  } finally {
    setTimeout(() => {
      isLoading.value = false
    }, 100)
  }
}

/** 只清条件与分页、不发请求：抽屉开关这类「不该动列表」的场景用它 */
const resetConditions = () => {
  conditions.value = {pageNum: null, pageSize: null, categoryId: null, state: null, search: null, searchType: 0}
  pageNum.value = 1
  pageSize.value = 5
  if (conditionRef.value) conditionRef.value.resetFields()
}

/** 搜索框的「重置」按钮：清空条件后重新查询 */
const reset = async () => {
  resetConditions()
  await getArticles()
}

const revokeCoverPreview = () => {
  if (coverPreviewUrl.value) {
    URL.revokeObjectURL(coverPreviewUrl.value)
    coverPreviewUrl.value = ''
  }
}

/**
 * 选中封面文件：先按 3:2 裁剪，产物只做本地预览、不发任何请求。
 * 真正的上传发生在提交文章时，这样用户中途放弃就不会在服务端留下垃圾对象。
 */
const onCoverChange = (file) => {
  if (!file || !file.raw) return
  // 非动图会先裁剪，按原图上限校验；动图跳过裁剪，仍按产物上限
  const limit = file.raw.type === 'image/gif' ? MAX_IMAGE_SIZE : MAX_SOURCE_IMAGE_SIZE
  if (!checkImageFile(file.raw, limit)) {
    if (coverRef.value) coverRef.value.clearFiles()
    return
  }
  // 动图经 canvas 会被拍成静态图，跳过裁剪直接用原图
  if (file.raw.type === 'image/gif') {
    ElMessage.info("动图不做裁剪，直接使用原图")
    applyCoverFile(file.raw)
    return
  }
  cropperRef.value?.open(file.raw)
}

/** 把（裁剪后或跳过的）封面文件落进本地状态 */
const applyCoverFile = (raw) => {
  revokeCoverPreview()
  pendingCover.value = raw
  coverPreviewUrl.value = URL.createObjectURL(raw)
  articleModel.value.coverImgSrc = coverPreviewUrl.value
}

 /** 删除封面：只清本地状态，提交时由后端删除对象 */
const cleanCover = () => {
  ElMessageBox.confirm("确认删除该封面? 保存后生效。", "警告", {
    confirmButtonText: "确认",
    cancelButtonText: "取消",
    type: "warning",
    buttonSize: "default"
  }).then(() => {
    revokeCoverPreview()
    pendingCover.value = null
    articleModel.value.coverImgSrc = ''
    // 空串表示「删除封面」；后端在写库成功后才删对象
    articleModel.value.coverImg = ''
    if (coverRef.value) coverRef.value.clearFiles()
  })
}

/** 只清稿件状态：不碰抽屉开关，也不动列表的筛选与分页（T27） */
const resetArticleForm = () => {
  errorList.value = {}
  articleModel.value = {title: '', categoryId: '', coverImgSrc: '', coverImg: '', content: '', state: ''}
  revokeCoverPreview()
  pendingCover.value = null
  if (articleModelRef.value) articleModelRef.value.resetFields()
  if (coverRef.value) coverRef.value.clearFiles()
  const quill = currentQuill()
  if (quill) quill.setContents([])
}

/** 关抽屉：先落开关再清表单，清稿过程中出任何问题都不会把抽屉卡在打不开的状态 */
const closeDrawer = () => {
  visibleDrawer.value = false
  resetArticleForm()
}

const handleEditorBlur = () => {
  nextTick(() => {
    if (articleModelRef.value) articleModelRef.value.validateField('content')
  })
}

// 命中违禁词时后端不再驳回，而是转入待审；只有站长会看到提示——
// 对普通作者保持静默，否则等于把词表逐条告诉他。
const alertSensitiveHit = (data) => {
  if (!data || !data.sensitiveHit || userInfoStore().type !== 0) return
  const words = data.sensitiveWords ? `：${data.sensitiveWords}` : ''
  ElMessageBox.alert(
      `标题或正文命中了违禁词${words}。这篇文章已转入「待审核」，可在「审核中心」处理。`,
      "命中违禁词",
      {confirmButtonText: "知道了", type: "warning"}
  ).catch(() => {
  })
}

/**
 * 处理后端返回的字段错误。
 *
 * title 会显示在标题输入框下方，其余字段（content / state / categoryId …）
 * 页面上根本没有可绑的位置——只塞进 errorList 就等于静默丢弃，
 * 用户点完提交什么都不会发生，也看不到为什么。这里统一补一次提示。
 */
const handleSubmitError = (message) => {
  const msg = message || {}
  errorList.value = msg
  const shown = []
  Object.keys(msg).forEach((field) => {
    if (field === 'title') return          // 已有 :error 绑定，就地显示
    const text = msg[field]
    if (text && !shown.includes(text)) shown.push(text)
  })
  if (shown.length) {
    ElMessage.error(shown.join('；'))
  }
}

const addOrUpdateArticle = async (state) => {
  errorList.value = {}
  if (state !== 0) {
    if (userInfoStore().type !== 0) articleModel.value.state = 2
    else articleModel.value.state = 1
  } else articleModel.value.state = state
  if (articleModelRef.value.validate()) {
    if (drawerTitle.value === '添加文章') {
      try {
        const resultData = await addArticleService(articleModel.value, pendingCover.value)
        if (resultData.code === 0) {
          ElMessage.success("添加成功！")
          alertSensitiveHit(resultData.data)
          closeDrawer()
          await getArticles()
        } else {
          handleSubmitError(resultData.message)
        }
      } catch (error) {
        ElMessage.error("添加失败！服务端响应失败！")
      }
    } else if (drawerTitle.value === '修改文章') {
      try {
        const resultData = await updateArticleService(articleModel.value, pendingCover.value)
        if (resultData.code === 0) {
          ElMessage.success("修改成功！")
          alertSensitiveHit(resultData.data)
          closeDrawer()
          await getArticles()
        } else {
          handleSubmitError(resultData.message)
        }
      } catch (error) {
        ElMessage.error("修改失败！服务端响应失败！")
      }
    }
  } else {
    ElMessage.error("未知操作！")
  }
}

const openAddDrawer = () => {
  resetArticleForm()
  drawerTitle.value = '添加文章'
  visibleDrawer.value = true
}
const openEditDrawer = async (row) => {
  resetArticleForm()
  drawerTitle.value = '修改文章'
  articleModel.value = JSON.parse(JSON.stringify(row))
  visibleDrawer.value = true
}

const beforeCloseDrawer = () => {
  ElMessageBox.confirm("这会清空所有待提交数据！", "确定关闭窗口吗？", {
    confirmButtonText: "确认",
    cancelButtonText: "取消",
    type: "warning",
    buttonSize: "default"
  }).then(async () => {
    // 封面此时还在浏览器里，没有产生任何服务端对象，直接清本地即可
    closeDrawer()
    ElMessage.primary("数据已清空！")
  }).catch(() => {
    ElMessage.info("取消关闭！")
  })
}

const deleteArticle = (id, createUser) => {
  ElMessageBox.confirm("确认删除该文章吗？", "警告", {
    confirmButtonText: "确认",
    cancelButtonText: "取消",
    type: "warning",
    buttonSize: "default"
  }).then(async () => {
    try {
      // 删别人的文章要站长密码；这条分支自己收尾，不能放任执行继续往下再删一次
      if (createUser !== userInfoStore().nickname) {
        let pass
        try {
          pass = (await promptMasterPassword()).value
        } catch (e) {
          ElMessage.info("取消删除！")
          return
        }
        const result = await deleteArticleService(id, `${pass}`)
        if (result.code === 0) {
          ElMessage.success("删除成功！")
          await refreshAfterDelete()
        } else {
          ElMessage.error(result.message.error ? result.message.error : "删除失败！")
        }
        return
      }
      const result = await deleteArticleService(id, "")
      if (result.code === 0) {
        ElMessage.success("删除成功！")
        await refreshAfterDelete()
      } else ElMessage.error("删除失败！")
    } catch (error) {
      ElMessage.error("服务端响应失败！")
    }
  }).catch(() => {
    ElMessage.info("取消删除！")
  })
}

/** 删除后只刷新当前页；删掉的正好是这页最后一条时回退一页，否则会停在空页上 */
const refreshAfterDelete = async () => {
  if (articles.value.length === 1 && pageNum.value > 1) {
    pageNum.value -= 1
  }
  await getArticles()
}
</script>

<template>
  <div class="article-manage-container">
    <PageHeader title="文章管理" subtitle="在这里发布和管理您的所有创作内容">
      <template #actions>
        <el-button type="primary" :icon="Plus" size="large" @click="openAddDrawer">添加文章</el-button>
      </template>
    </PageHeader>

    <div class="search-bar">
      <el-form inline ref="conditionRef" :model="conditions">
        <el-form-item label="文章分类">
          <el-select style="width: 140px" placeholder="请选择" v-model="conditions.categoryId" clearable>
            <el-option :value="-1" label="未分类" style="color: #f56c6c"></el-option>
            <el-option v-for="c in categories" :key="c.id" :label="c.categoryName" :value="c.id"></el-option>
          </el-select>
        </el-form-item>

        <el-form-item label="发布状态">
          <el-select style="width:120px" placeholder="请选择" v-model="conditions.state" clearable>
            <el-option label="草稿" value="0"></el-option>
            <el-option label="已发布" value="1"></el-option>
            <el-option label="待审核" value="2"></el-option>
            <el-option label="已驳回" value="3"></el-option>
          </el-select>
        </el-form-item>

        <el-form-item label="搜索">
          <el-switch
              v-model="conditions.searchType"
              v-if="userInfoStore().type===0"
              size="default"
              active-value="1"
              inactive-value="0"
              active-text="作者"
              inactive-text="标题"
              style="padding-right: 10px"
          />
          <el-input style="width:220px" :prefix-icon="Search" placeholder="关键词"
                    v-model="conditions.search"
                    clearable/>
        </el-form-item>


        <el-form-item>
          <el-button type="primary" @click="getArticles()">查询</el-button>
          <el-button @click="reset()">重置</el-button>
        </el-form-item>
      </el-form>
    </div>
    <div class="table-wrapper" v-loading="isLoading" element-loading-text="正在为您加载文章列表...">
      <template v-if="!isLoading">
        <el-table :data="articles" style="width: 100%" class="modern-table">
          <el-table-column label="封面图" width="200">
            <template #default="scope">
              <div class="pic_box">
                <el-image
                    class="full-img"
                    :src="scope.row.coverThumbSrc == null ? cover : scope.row.coverThumbSrc"
                    fit="contain"
                >
                  <template #error>
                    <div class="image-slot">
                      <el-icon>
                        <Picture/>
                      </el-icon>
                    </div>
                  </template>
                </el-image>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="状态" width="130">
            <template #default="scope">
              <div class="state-cell">
                <el-tag v-if="scope.row.state === 0" type="primary" disable-transitions>草稿</el-tag>
                <el-tag v-else-if="scope.row.state === 1" type="success" disable-transitions>发布</el-tag>
                <el-tag v-else-if="scope.row.state === 2" type="warning" disable-transitions>待审核</el-tag>
                <el-tag v-else-if="scope.row.state === 3" type="danger" disable-transitions>已驳回</el-tag>
                <el-tag v-else type="info">未知状态</el-tag>
                <!-- 命中标记只由后端回给站长：作者侧拿不到这个字段，因此不会显示 -->
                <el-tag v-if="scope.row.sensitiveHit === 1" type="danger" effect="dark" disable-transitions>命中违禁词</el-tag>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="文章标题（点击预览）" width="360">
            <template #default="{row}">
              <span class="table-article-title" @click="openPreview(row)">{{ row.title }}</span>
            </template>

          </el-table-column>

          <el-table-column label="分类" width="140">
            <template #default="{row}">
              <el-tag v-if="row.categoryName !== '未分类'" effect="plain" disable-transitions>{{
                  row.categoryName
                }}
              </el-tag>
              <el-tag v-else type="warning" effect="plain" disable-transitions>{{ row.categoryName }}</el-tag>
            </template>
          </el-table-column>

          <el-table-column v-if="userInfoStore().type === 0" label="作者" width="220">
            <template #default="{row}">
              <div class="creator-tag" :class="{ 'is-me': row.createUserName === userInfoStore().nickname }">
                <el-icon v-if="row.createUserName === userInfoStore().nickname">
                  <UserFilled/>
                </el-icon>
                <span>{{ row.createUserName }}</span>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="发布时间" width="260">
            <template #default="scope">
              <div class="time-column">
                <div class="main-time">
                  <el-icon>
                    <Timer style="padding-bottom: 2px"/>
                  </el-icon>
                  <span>{{ scope.row.createTime }}</span>
                </div>
                <div class="sub-time">
                  最后更新: {{ scope.row.updateTime || '暂无' }}
                </div>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="操作" fixed="right" width="150">
            <template #default="{ row }">
              <div class="action-cell">
                <el-tooltip content="编辑文章" placement="top" :enterable="false">
                  <el-button :icon="Edit" v-if="userInfoStore().nickname === row.createUserName" circle plain
                             type="primary" @click="openEditDrawer(row)"></el-button>
                </el-tooltip>
                <el-tooltip content="删除该文章" placement="top" :enterable="false">
                  <el-button :icon="Delete" circle plain type="danger"
                             @click="deleteArticle(row.id,row.createUserName)"></el-button>
                </el-tooltip>
              </div>
            </template>
          </el-table-column>

          <template #empty>
            <el-empty description="暂未发现符合条件的文章" :image-size="100"/>
          </template>
        </el-table>

        <div class="pagination-footer">
          <el-pagination
              v-model:current-page="pageNum"
              v-model:page-size="pageSize"
              :page-sizes="[5, 10, 15, 20]"
              layout="total, sizes, prev, pager, next, jumper"
              background
              :total="total"
              @size-change="onSizeChange"
              @current-change="onCurrentChange"
          />
        </div>
      </template>
    </div>

    <el-drawer v-model="visibleDrawer" :title="drawerTitle" direction="rtl" size="55%"
               :before-close="beforeCloseDrawer">
      <el-form :model="articleModel" ref="articleModelRef" :rules="articleModelRule" label-position="top">
        <el-row :gutter="20">
          <el-col :span="16">
            <el-form-item label="文章标题" prop="title" :error="errorList.title">
              <el-input v-model="articleModel.title" maxlength="30" show-word-limit
                        placeholder="一个好标题是文迹之旅的第一步"></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="文章分类" prop="categoryId">
              <div style="display: flex; gap: 8px; width: 100%">
                <el-select placeholder="请选择" v-model="articleModel.categoryId" style="width: 100%"
                           popper-class="category-select-dropdown">
                  <el-option v-for="c in categories" :key="c.id" :label="c.categoryName" :value="c.id"></el-option>
                </el-select>
                <el-button :icon="Plus" @click.prevent="quickAddCategory">新建</el-button>
              </div>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="封面管理（按 3:2 裁剪，保存文章时一并上传）">
          <div class="cover-upload-wrapper">
            <el-upload
                ref="coverRef"
                class="avatar-uploader"
                :auto-upload="false"
                :show-file-list="false"
                accept="image/*"
                :before-upload="(f) => checkImageFile(f, MAX_SOURCE_IMAGE_SIZE)"
                :on-change="onCoverChange"
            >
              <div v-if="articleModel.coverImgSrc" class="cover-preview">
                <img :src="articleModel.coverImgSrc" class="avatar" alt="封面预览"/>
                <div class="overlay">
                  <el-icon>
                    <Edit/>
                  </el-icon>
                  <span>更换封面</span></div>
              </div>
              <el-icon v-else class="avatar-uploader-icon">
                <Plus/>
              </el-icon>
            </el-upload>
            <el-button v-if="articleModel.coverImgSrc" type="danger" plain @click="cleanCover">删除封面</el-button>
          </div>
        </el-form-item>

        <el-form-item label="文章正文" prop="content" required :error="errorList.content">
          <div class="editor-container" ref="editorContainerRef">
            <quill-editor
                ref="quillEditorRef"
                theme="snow"
                :toolbar="quillToolbar"
                v-model:content="articleModel.content"
                contentType="html"
                @ready="onQuillReady"
                @blur="handleEditorBlur"
            />
          </div>
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="drawer-footer">
          <el-button type="info" @click="addOrUpdateArticle(0)" round size="large">存为草稿</el-button>
          <el-button type="primary" @click="addOrUpdateArticle(1)" round size="large">立即发布</el-button>
        </div>
      </template>
    </el-drawer>
    <el-drawer
        v-model="previewDrawer"
        title="文章预览"
        direction="rtl"
        size="55%"
        custom-class="modern-preview-drawer"
    >
      <div class="preview-container">
        <h1 class="article-title">{{ previewData.title }}</h1>

        <div class="article-meta">
          <div class="meta-left">
        <span class="meta-item author">
          <el-icon><User/></el-icon> {{ previewData.createUserName }}
        </span>
            <span class="meta-item time">
          <el-icon><Calendar/></el-icon> {{ previewData.createTime }}
        </span>
          </div>
          <div class="meta-right">
            <el-tag size="small" effect="plain" round>{{ previewData.categoryName }}</el-tag>
            <el-tag v-if="previewData.state === 0" type="primary" size="small" round effect="dark">草稿</el-tag>
            <el-tag v-else-if="previewData.state === 1" type="success" size="small" round effect="dark">发布</el-tag>
            <el-tag v-else-if="previewData.state === 2" type="warning" size="small" round effect="dark">待审核</el-tag>
            <el-tag v-else-if="previewData.state === 3" type="danger" size="small" round effect="dark">已驳回</el-tag>
            <el-tag v-else type="info" size="small" round effect="dark">未知状态</el-tag>
          </div>
        </div>

        <el-alert
            v-if="previewData.sensitiveHit === 1"
            type="error"
            :closable="false"
            show-icon
            class="sensitive-alert"
            title="命中违禁词：已取消直接发布，转入待审核并排在最前"
        >
          <template #default>命中的词：{{ previewData.sensitiveWords || '（未记录）' }}</template>
        </el-alert>

        <el-divider/>
        <div ref="previewContentRef" class="article-content ql-editor" v-html="previewData.content"></div>
      </div>

      <!-- 审核动作已收进「审核中心」，这里只留预览 -->
    </el-drawer>

    <ImageCropper ref="cropperRef" v-bind="CROP_PRESETS.cover" @confirm="applyCoverFile"/>

    <el-dialog v-model="formulaDialog" title="插入公式" width="520px" append-to-body>
      <el-input v-model="formulaTex" type="textarea" :rows="3"
                placeholder="只写 LaTeX 本体，例如 \frac{1}{2} 或 \sum_{i=1}^{n} i"/>
      <div class="formula-preview">
        <span class="formula-preview-label">预览</span>
        <div v-if="formulaPreview" class="formula-preview-body" v-html="formulaPreview"></div>
        <span v-else class="formula-empty">输入 LaTeX 后这里实时显示</span>
      </div>
      <el-radio-group v-model="formulaDisplay" size="small">
        <el-radio-button :value="false">行内</el-radio-button>
        <el-radio-button :value="true">独立一行</el-radio-button>
      </el-radio-group>
      <template #footer>
        <el-button @click="formulaDialog = false">取消</el-button>
        <el-button type="primary" @click="insertFormula">插入</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="markdownDialog" title="导入 Markdown" width="640px" append-to-body>
      <el-input v-model="markdownText" type="textarea" :rows="10"
                placeholder="把 Markdown 粘进来，确认后转成正文格式；公式写成 $..$ 或 $$..$$ 即可"/>
      <template #footer>
        <el-button @click="markdownDialog = false">取消</el-button>
        <el-button @click="insertMarkdown(false)">插入到光标处</el-button>
        <el-button type="primary" @click="insertMarkdown(true)">替换整篇正文</el-button>
      </template>
    </el-dialog>
  </div>
</template>
<style>
.category-select-dropdown .el-select-dropdown__list {
  max-height: 200px;
}
</style>
<style lang="scss" scoped>
.article-manage-container {
  display: flex;
  flex-direction: column;
  gap: 20px;
  animation: fadeIn 0.5s ease-out;
}


/* 搜索栏背景微调，确保各页面色值统一 */
.search-bar {
  background: #f8fafc; // 使用更清爽的蓝灰色调，替代较暗的 #f4f4f4
  padding: 10px 20px 2px;
  border-radius: 8px;
  margin-bottom: 10px;
}

/* 状态与命中标记竖排、左对齐：并排时「命中违禁词」会被挤到换行，看着像没对齐 */
.state-cell {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
}

.pic_box {
  width: 130px;
  height: 75px;
  border-radius: 6px;
  overflow: hidden;
  border: 1px solid #eee;

  .full-img {
    width: 100%;
    height: 100%;
    transition: transform 0.3s ease;

    &:hover {
      transform: scale(1.08);
    }
  }

  .image-slot {
    display: flex;
    justify-content: center;
    align-items: center;
    width: 100%;
    height: 100%;
    background: #f5f7fa;
    color: #909399;
  }
}

.table-article-title {
  font-weight: 600;
  color: #2c3e50;
  font-size: 15px;

  &:hover {
    color: #409eff;
    cursor: pointer;
  }
}

.time-column {
  .main-time {
    font-size: 14px;
    display: flex;
    align-items: center;
    gap: 5px;
    color: #444;
    font-weight: 500;
  }

  .sub-time {
    font-size: 11px;
    color: #a5a5a5;
    margin-top: 4px;
  }
}

.status-badge {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 4px;
  border: #e1e1e1 1px solid;

  &.is-active {
    background: #f0f9eb;
    color: #74c34d;
  }

  &.is-draft {
    background: #fdf6ec;
    color: #eda434;
  }
}

.action-cell {
  display: flex;
  gap: 8px;
}


.editor-container {
  width: 100%;
  border: 1px solid #dcdfe6;
  border-radius: 4px;

  :deep(.ql-toolbar) {
    border: none;
    border-bottom: 1px solid #dcdfe6;
    background: #fcfcfc;

    /* 只有 markdown 按钮需要自己画图标：Quill 的内置图标表里没有它（formula / clean 都有 SVG），
       再给 formula 补一个就成两个图标叠在一起了 */
    .ql-markdown::before {
      content: 'M↓';
      font-size: 13px;
      font-weight: 700;
    }

    /* 下拉文案：snow 只为 Normal/Small/Large/Huge 配了 ::before，自定义档位与中文标题都得自己补，
       否则每一项都会落到兜底的 'Normal'。档位值要与脚本里的 SIZE_OPTIONS 一致 */
    @each $size, $label in (14px: '14', 16px: '16', 18px: '18', 20px: '20', 24px: '24', 28px: '28') {
      .ql-picker.ql-size .ql-picker-item[data-value='#{$size}']::before,
      .ql-picker.ql-size .ql-picker-label[data-value='#{$size}']::before {
        content: '#{$label}';
      }
    }

    @each $level, $label in (1: '标题1', 2: '标题2', 3: '标题3', 4: '标题4') {
      .ql-picker.ql-header .ql-picker-item[data-value='#{$level}']::before,
      .ql-picker.ql-header .ql-picker-label[data-value='#{$level}']::before {
        content: '#{$label}';
      }
    }

    /* 未设值时 Quill 不写 data-value（配置里的 `false` 那一项就是这种），size 与 header 的默认都叫正文 */
    .ql-picker.ql-size .ql-picker-item:not([data-value])::before,
    .ql-picker.ql-size .ql-picker-label:not([data-value])::before,
    .ql-picker.ql-header .ql-picker-item:not([data-value])::before,
    .ql-picker.ql-header .ql-picker-label:not([data-value])::before {
      content: '正文';
    }
  }

  :deep(.ql-container) {
    border: none;
    min-height: 350px;
    font-size: 15px;
  }

  /* 表格块在编辑器里给一层虚线轮廓：它是整块只读内容，不是普通段落 */
  :deep(.ql-editor .ql-table-embed) {
    outline: 1px dashed #dcdfe6;
    outline-offset: -1px;
  }
}

/* 公式对话框里的实时预览 */
.formula-preview {
  margin: 12px 0;
  padding: 12px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  background: #fafafa;
  min-height: 56px;

  .formula-preview-label {
    display: block;
    margin-bottom: 6px;
    font-size: 12px;
    color: #909399;
  }

  .formula-preview-body {
    overflow-x: auto;
    text-align: center;
  }

  .formula-empty {
    font-size: 13px;
    color: #c0c4cc;
  }
}

.cover-upload-wrapper {
  display: flex;
  align-items: flex-end;
  gap: 15px;
}

.cover-preview {
  width: 178px;
  height: 178px;
  position: relative;
  border-radius: 6px;
  overflow: hidden;

  .avatar {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }

  .overlay {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    background: rgba(0, 0, 0, 0.5);
    color: #fff;
    display: flex;
    flex-direction: column;
    justify-content: center;
    align-items: center;
    opacity: 0;
    transition: 0.3s;

    .el-icon {
      font-size: 24px;
      margin-bottom: 4px;
    }
  }

  &:hover .overlay {
    opacity: 1;
  }
}

.avatar-uploader-icon {
  font-size: 28px;
  color: #8c939d;
  width: 178px;
  height: 178px;
  border: 1px dashed #d9d9d9;
  border-radius: 6px;
  display: flex;
  justify-content: center;
  align-items: center;

  &:hover {
    border-color: #409eff;
  }
}

.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 15px;
  padding-top: 10px;
}



.table-wrapper {
  flex: 1;
  display: flex;
  flex-direction: column;
  margin-top: 10px;
}

.ql-editor {
  padding: 0;
}

:deep(.modern-table) {
  .el-table__header th {
    background-color: #fcfcfc;
    color: #606266;
    font-weight: bold;
  }


  .el-table__row {
    height: 90px;
  }
}





.creator-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 12px;
  background: #f1f5f9;
  border-radius: 6px;
  font-size: 13px;
  color: #475569;

  &.is-me {
    background: #ecf5ff;
    color: #409eff;
    font-weight: 500;
  }
}

/* 抽屉整体背景色调优 */
:deep(.el-drawer__header) {
  margin-bottom: 0 !important;
}

:deep(.modern-preview-drawer) {
  background-color: #fdfdfd;
}

.el-drawer__header {
  margin-bottom: 0 !important;
  padding-bottom: 0 !important;
  border-bottom: 1px solid #f2f2f2;
  color: #333;
  font-weight: bold;
}

.preview-container {
  padding: 0 40px 40px;
  max-width: 800px;
  margin: 0 auto;

  /* 标题样式 */
  .article-title {
    font-size: 28px;
    font-weight: 700;
    color: #1a1a1a;
    line-height: 1.4;
    margin-bottom: 20px;
  }

  /* 元数据栏 */
  .article-meta {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20px;

    .meta-item {
      display: inline-flex;
      align-items: center;
      gap: 5px;
      font-size: 13px;
      color: #888;
      margin-right: 20px;

      .el-icon {
        font-size: 15px;
      }
    }

    .meta-right {
      display: flex;
      gap: 10px;
    }
  }

  /* 封面图美化 */
  .article-cover {
    width: 250px;
    margin-bottom: 30px;
    border-radius: 8px;
    overflow: hidden;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);

    .el-image {
      width: 100%;
      display: block;
      transition: transform 0.3s;

      &:hover {
        transform: scale(1.02);
      }
    }

    .image-placeholder {
      height: 200px;
      background: #f5f7fa;
      display: flex;
      justify-content: center;
      align-items: center;
      color: #909399;
    }
  }

  /* 正文样式排版 */
  .article-content {
    font-size: 16px;
    line-height: 1.8;
    color: #3f3f3f;
    letter-spacing: 0.5px;

    /* 处理 v-html 内部的图片 */
    :deep(img) {
      max-width: 100%;
      height: auto;
      border-radius: 4px;
      display: block;
      margin: 20px auto;
    }

    /* 段落间距 */
    :deep(p) {
      margin-bottom: 1.5em;
    }

    /* 引用块样式 */
    :deep(blockquote) {
      border-left: 4px solid #e2e8f0;
      padding-left: 16px;
      color: #64748b;
      font-style: italic;
      margin: 20px 0;
    }
  }
}

.drawer-footer {
  border-top: 1px solid #f2f2f2;
  padding: 15px 20px;
  text-align: right;
}

</style>