import { ref } from 'vue'

export interface PriceAlert {
  id: string
  code: string
  name: string
  targetPrice: number
  direction: 'above' | 'below' | 'any'
  enabled: boolean
  createdAt: string
  triggered?: boolean
}

const KEY = 'stock-alerts'
const alerts = ref<PriceAlert[]>(load())

function load(): PriceAlert[] {
  try { return JSON.parse(localStorage.getItem(KEY) || '[]') } catch { return [] }
}
function save() { localStorage.setItem(KEY, JSON.stringify(alerts.value)) }

export function useAlertStore() {
  function add(a: Omit<PriceAlert, 'id' | 'createdAt'>) {
    alerts.value.push({ ...a, id: Date.now().toString(), createdAt: new Date().toISOString() })
    save()
  }
  function remove(id: string) { alerts.value = alerts.value.filter(a => a.id !== id); save() }
  function toggle(id: string) { const a = alerts.value.find(x => x.id === id); if (a) { a.enabled = !a.enabled; save() } }
  function checkAndClearCurrent(code: string, currentPrice: number) {
    // Simple check - in production this would use websocket
  }
  return { alerts, add, remove, toggle }
}
