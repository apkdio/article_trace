import {defineStore} from "pinia";
import {ref} from "vue";

export const searchConditions = {}
    = defineStore("searchConditions", () => {
    const searchCondition = ref({
        categoryId: null,
        search: null,
        searchType: 0,
        nickName: null
    })
    const state = ref(0)

    function clear() {
        searchCondition.value = ref({
            categoryId: null,
            search: null,
            searchType: 0,
            nickName: null
        })
        state.value = 0
    }


    return {searchCondition,clear,state}
})