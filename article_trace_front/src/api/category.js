import request from "@/utils/request.js";


export function getAllCategories() {
    return request.get("/category")
}

export function addCategory(category) {
    return request.post("/category/add", category)
}

export function updateCategory(category) {
    return request.patch("/category/update/" + category.categoryId,
        {categoryName: category.categoryName, categoryAlias: category.categoryAlias})
}

export function deleteCategory(id) {
    return request.delete("/category/delete/" + id)
}
