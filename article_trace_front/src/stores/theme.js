import {defineStore} from 'pinia'
import {ref} from 'vue'

export const themeStore = defineStore('theme', () => {
    const isDark = ref(document.documentElement.classList.contains('dark'))

    const toggleTheme = () => {
        isDark.value = !isDark.value
        document.documentElement.classList.toggle('dark', isDark.value)
    }

    const setDark = (val) => {
        isDark.value = Boolean(val)
        document.documentElement.classList.toggle('dark', isDark.value)
    }

    return {
        isDark,
        toggleTheme,
        setDark
    }
}, {
    persist: {
        key: 'article_trace_theme',
        paths: ['isDark']
    }
})
