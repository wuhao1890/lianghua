import axios, { type AxiosInstance, type AxiosRequestConfig, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { getToken, removeToken } from '@/utils/storage'
import router from '@/router'
import type { ApiResponse } from '@/types'

type RequestConfigWithSilent = AxiosRequestConfig & {
  silentError?: boolean
}

declare global {
  interface Window {
    __LIANGHUA_API_BASE_URL__?: string
  }
}

const LEGACY_API_BASE_STORAGE_KEY = 'LIANGHUA_API_BASE_URL'
const API_BASE_SESSION_KEY = 'LIANGHUA_SESSION_API_BASE_URL'
const API_BASE_PERSIST_KEY = 'LIANGHUA_PERSISTED_API_BASE_URL'

const normalizeApiBaseURL = (value?: string | null) => {
  const apiBase = value?.trim()
  if (!apiBase) return ''
  return apiBase.endsWith('/') ? apiBase.slice(0, -1) : apiBase
}

const resolveApiBaseURL = () => {
  if (typeof window === 'undefined') {
    return import.meta.env.VITE_API_BASE_URL || '/api'
  }

  const params = new URLSearchParams(window.location.search)
  const queryApiBase = params.get('apiBase') || params.get('api')

  if (queryApiBase === 'reset') {
    window.localStorage.removeItem(LEGACY_API_BASE_STORAGE_KEY)
    window.localStorage.removeItem(API_BASE_PERSIST_KEY)
    window.sessionStorage.removeItem(API_BASE_SESSION_KEY)
  } else if (queryApiBase) {
    const normalizedApiBase = normalizeApiBaseURL(queryApiBase)
    if (params.get('apiPersist') === '1') {
      window.localStorage.setItem(API_BASE_PERSIST_KEY, normalizedApiBase)
    } else {
      window.sessionStorage.setItem(API_BASE_SESSION_KEY, normalizedApiBase)
    }
    window.localStorage.removeItem(LEGACY_API_BASE_STORAGE_KEY)
  }

  return (
    normalizeApiBaseURL(window.__LIANGHUA_API_BASE_URL__) ||
    normalizeApiBaseURL(window.sessionStorage.getItem(API_BASE_SESSION_KEY)) ||
    normalizeApiBaseURL(window.localStorage.getItem(API_BASE_PERSIST_KEY)) ||
    import.meta.env.VITE_API_BASE_URL ||
    '/api'
  )
}

const service: AxiosInstance = axios.create({
  baseURL: resolveApiBaseURL(),
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截器
service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = getToken()
    if (token) {
      // 尝试解析token获取userId
      try {
        const payload = JSON.parse(atob(token.split('.')[1]))
        config.headers['X-User-Id'] = payload.userId
      } catch (e) {
        // ignore
      }
    }
    return config
  },
  (error) => {
    console.error('请求错误:', error)
    return Promise.reject(error)
  }
)

// 响应拦截器
service.interceptors.response.use(
  (response: AxiosResponse<ApiResponse>) => {
    const res = response.data
    const silentError = (response.config as RequestConfigWithSilent).silentError
    if (res.code !== 200 && res.code !== 0) {
      if (!silentError) {
        ElMessage.error(res.message || '请求失败')
      }
      // 401 未授权，跳转登录
      if (res.code === 401) {
        removeToken()
        router.push('/login')
      }
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return response
  },
  (error) => {
    const silentError = (error.config as RequestConfigWithSilent | undefined)?.silentError
    if (silentError) {
      return Promise.reject(error)
    }
    if (error.response) {
      const status = error.response.status
      switch (status) {
        case 401:
          ElMessage.error('登录已过期，请重新登录')
          removeToken()
          router.push('/login')
          break
        case 403:
          ElMessage.error('没有权限访问')
          break
        case 404:
          ElMessage.error('请求的资源不存在')
          break
        case 500:
          ElMessage.error('服务器内部错误')
          break
        default:
          ElMessage.error(error.response.data?.message || '请求失败')
      }
    } else if (error.message.includes('timeout')) {
      ElMessage.error('请求超时，请稍后重试')
    } else if (error.message.includes('Network Error')) {
      ElMessage.error('网络错误，请检查网络连接')
    }
    return Promise.reject(error)
  }
)

export default service
