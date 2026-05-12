import request from "@/utils/request.js";


export function getArticleWithConditions(condition) {
    return request.get("/article/list", {params: condition})
}

export function removeCover(key) {
    return request.delete("/article/removeCover", {params: {"key": key}})
}

export function addArticleService(articleData) {
    return request.post("/article/add", articleData)
}

export function getArticleDetail(id) {
    return request.get("/reader/article/" + id)
}

export function updateArticleService(articleData) {
    return request.patch("/article/update/" + articleData.id, articleData)
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