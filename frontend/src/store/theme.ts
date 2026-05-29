import { ref, watch } from 'vue'

const isDark = ref(false)
const KEY = 'stock-theme'

export function useTheme() {
  function init() {
    isDark.value = localStorage.getItem(KEY) === 'dark'
    applyTheme()
  }
  function toggle() {
    isDark.value = !isDark.value
    localStorage.setItem(KEY, isDark.value ? 'dark' : 'light')
    applyTheme()
  }
  function applyTheme() {
    document.documentElement.setAttribute('data-theme', isDark.value ? 'dark' : 'light')
  }
  // Apply immediately when changed
  watch(isDark, applyTheme)
  return { isDark, toggle, init }
}
