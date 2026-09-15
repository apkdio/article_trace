import request from "@/utils/request.js";


export function getArticleWithConditions(condition) {
    return request.get("/article/list", {params: condition})
}

/**
 * 组装「文章 + 封面」的 multipart 请求体。
 *
 * 后端用 @RequestPart("article") 接收 JSON 部分，所以这里必须显式声明
 * type 为 application/json —— 否则会被当成普通表单字段解析而报错。
 * cover 为空时只提交文章部分，后端据此判断「不换封面」。
 */
function buildArticleForm(articleData, coverFile) {
    const form = new FormData()
    form.append('article', new Blob([JSON.stringify(articleData)], {type: 'application/json'}))
    if (coverFile) {
        form.append('cover', coverFile)
    }
    return form
}

export function addArticleService(articleData, coverFile) {
    return request.post("/article/add", buildArticleForm(articleData, coverFile))
}

export function getArticleDetail(id) {
    return request.get("/reader/article/" + id)
}

export function updateArticleService(articleData, coverFile) {
    return request.patch("/article/update/" + articleData.id, buildArticleForm(articleData, coverFile))
}

export function deleteArticleService(id, pass) {
    return request.delete("/article/delete", {params: {articleId: id, masterPass: pass}})
}

export function getArticleWithConditionsMaster(condition) {
    return request.get("/article/manageArticles", {params: condition})
}

export function assessArticleService(id, state) {
    return request.patch("/article/assess", null, {
        params: {id: id, state: state}
    })
}

export function getPublicArticlesService(conditions) {
    return request.get("/reader/getArticles", {params: conditions})
}

export function getTopArticlesService() {
    return request.get("/reader/article/hotArticles")
}

export function getWriterInfoService(nickName) {
    return request.get("/reader/writerInfo", {params: {nickName: nickName}})
}
export function getMasterInfoService() {
    return request.get("/reader/masterInfo")
}

export function addViewService(articleId) {
    request.patch("/reader/article/addViews/" + articleId).catch()
}

export function getCommentsService(conditions) {
    return request.get("/reader/comments", {params: conditions})
}

export function addCommentService(commentData) {
    return request.post("/reader/addComment", commentData)
}

export function deleteCommentService(id, articleId) {
    return request.delete("/reader/deleteComment", {params: {commentId: id, articleId: articleId}})
}