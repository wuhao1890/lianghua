/**
 * 金额格式化：保留2位小数，千分位分隔
 */
export function formatMoney(value: number | string | undefined | null): string {
  if (value === null || value === undefined || value === '') return '0.00'
  const num = typeof value === 'string' ? parseFloat(value) : value
  if (isNaN(num)) return '0.00'
  return num.toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  })
}

/**
 * 百分比格式化
 */
export function formatPercent(value: number | string | undefined | null): string {
  if (value === null || value === undefined || value === '') return '0.00%'
  const num = typeof value === 'string' ? parseFloat(value) : value
  if (isNaN(num)) return '0.00%'
  return `${num >= 0 ? '+' : ''}${num.toFixed(2)}%`
}

/**
 * 数量格式化
 */
export function formatNumber(value: number | string | undefined | null): string {
  if (value === null || value === undefined || value === '') return '0'
  const num = typeof value === 'string' ? parseFloat(value) : value
  if (isNaN(num)) return '0'
  if (num >= 100000000) {
    return `${(num / 100000000).toFixed(2)}亿`
  }
  if (num >= 10000) {
    return `${(num / 10000).toFixed(2)}万`
  }
  return num.toLocaleString('zh-CN', {
    minimumFractionDigits: 0,
    maximumFractionDigits: 0
  })
}

/**
 * 日期时间格式化
 */
export function formatDateTime(value: string | Date | undefined | null, format: string = 'YYYY-MM-DD HH:mm:ss'): string {
  if (!value) return '-'
  const date = typeof value === 'string' ? new Date(value) : value
  if (isNaN(date.getTime())) return '-'

  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  const seconds = String(date.getSeconds()).padStart(2, '0')

  return format
    .replace('YYYY', String(year))
    .replace('MM', month)
    .replace('DD', day)
    .replace('HH', hours)
    .replace('mm', minutes)
    .replace('ss', seconds)
}

/**
 * 涨跌颜色 class
 * 返回 Element Plus 的 text-color 类型
 */
export function getColorClass(value: number | string | undefined | null): string {
  if (value === null || value === undefined || value === '' || value === 0) return ''
  const num = typeof value === 'string' ? parseFloat(value) : value
  if (num > 0) return 'text-danger'
  if (num < 0) return 'text-success'
  return ''
}

/**
 * 涨跌颜色值
 */
export function getColor(value: number | string | undefined | null): string {
  if (value === null || value === undefined || value === '' || value === 0) return '#333'
  const num = typeof value === 'string' ? parseFloat(value) : value
  if (num > 0) return '#f56c6c'
  if (num < 0) return '#67c23a'
  return '#333'
}

/**
 * 价格格式化（保留指定小数位）
 */
export function formatPrice(value: number | string | undefined | null, decimals: number = 2): string {
  if (value === null || value === undefined || value === '') return '0.00'
  const num = typeof value === 'string' ? parseFloat(value) : value
  if (isNaN(num)) return '0.00'
  return num.toFixed(decimals)
}
