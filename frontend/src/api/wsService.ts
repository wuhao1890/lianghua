/**
 * WebSocket 实时推送服务
 * 连接后端 /api/ws/quote，支持：
 * - 订阅/取消订阅行情
 * - 接收预警推送弹窗
 * - 心跳保活
 */
import { ElNotification } from 'element-plus'
import { useAlertStore } from '@/store/alert'
import { formatPrice } from '@/utils/format'

type MessageHandler = (data: any) => void

class WsService {
  private ws: WebSocket | null = null
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null
  private pingTimer: ReturnType<typeof setInterval> | null = null
  private handlers: Map<string, MessageHandler[]> = new Map()
  private _connected = false

  /** 连接状态 */
  get connected() { return this._connected }

  /** 建立连接 */
  connect() {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) return
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const host = window.location.host
    const url = `${protocol}//${host}/api/ws/quote`

    try {
      this.ws = new WebSocket(url)

      this.ws.onopen = () => {
        this._connected = true
        console.log('[WS] 已连接')
        // 开始心跳
        this.startPing()
      }

      this.ws.onmessage = (event) => {
        try {
          const msg = JSON.parse(event.data)
          this.dispatch(msg)
        } catch (e) {
          console.warn('[WS] 消息解析失败:', event.data)
        }
      }

      this.ws.onclose = () => {
        this._connected = false
        console.log('[WS] 断开连接')
        this.stopPing()
        // 自动重连
        this.reconnectTimer = setTimeout(() => this.connect(), 5000)
      }

      this.ws.onerror = (err) => {
        console.warn('[WS] 连接错误:', err)
        this.ws?.close()
      }
    } catch (e) {
      console.warn('[WS] 创建连接失败:', e)
      this.reconnectTimer = setTimeout(() => this.connect(), 5000)
    }
  }

  /** 断开连接 */
  disconnect() {
    if (this.reconnectTimer) { clearTimeout(this.reconnectTimer); this.reconnectTimer = null }
    this.stopPing()
    if (this.ws) { this.ws.close(); this.ws = null }
    this._connected = false
  }

  /** 订阅行情 */
  subscribe(codes: string[]) {
    this.send({ action: 'subscribe', codes, type: 'quote' })
  }

  /** 取消订阅 */
  unsubscribe() {
    this.send({ action: 'unsubscribe' })
  }

  /** 注册消息处理器 */
  on(type: string, handler: MessageHandler) {
    if (!this.handlers.has(type)) this.handlers.set(type, [])
    this.handlers.get(type)!.push(handler)
  }

  /** 移除消息处理器 */
  off(type: string, handler: MessageHandler) {
    const list = this.handlers.get(type)
    if (list) {
      const idx = list.indexOf(handler)
      if (idx >= 0) list.splice(idx, 1)
    }
  }

  // ===== 内部方法 =====

  private send(data: any) {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(data))
    }
  }

  private dispatch(msg: any) {
    const type = msg.type || 'message'
    const handlers = this.handlers.get(type)
    if (handlers) handlers.forEach(h => h(msg))

    // 预警推送：自动弹窗
    if (type === 'alert') {
      this.showAlertNotification(msg.data)
    }

    // 广播通知所有handler
    const allHandlers = this.handlers.get('*')
    if (allHandlers) allHandlers.forEach(h => h(msg))
  }

  private showAlertNotification(data: any) {
    const title = data.direction === 'above' ? '📈 预警触发：上穿' : '📉 预警触发：下穿'
    const message = `${data.name}(${data.code}) 目标价 ${formatPrice(data.targetPrice)}，当前价 ${formatPrice(data.currentPrice)}`
    ElNotification({
      title,
      message,
      type: data.direction === 'above' ? 'warning' : 'info',
      duration: 8000,
      position: 'top-right'
    })
  }

  private startPing() {
    this.stopPing()
    this.pingTimer = setInterval(() => {
      this.send({ action: 'ping' })
    }, 30000)
  }

  private stopPing() {
    if (this.pingTimer) { clearInterval(this.pingTimer); this.pingTimer = null }
  }
}

// 单例导出
const wsService = new WsService()
export default wsService
