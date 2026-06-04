import request from './request'
import type { ApiResponse, LoginRequest, RegisterRequest, LoginResponse, User } from '@/types'

export function login(data: LoginRequest) {
  return request.post<ApiResponse<LoginResponse>>('/auth/login', data)
}

export function register(data: RegisterRequest) {
  return request.post<ApiResponse<null>>('/auth/register', data)
}

export function getUserInfo() {
  return request.get<ApiResponse<User>>('/auth/info')
}

export function getAlertSettings() {
  return request.get<ApiResponse<{
    email: string
    emailEnabled: boolean
    kingTradeOnly: boolean
    includeTopFive: boolean
    updatedAt?: string
  }>>('/ai/lab/alert-settings')
}

export function saveAlertSettings(data: {
  email: string
  emailEnabled: boolean
  kingTradeOnly: boolean
  includeTopFive: boolean
}) {
  return request.post<ApiResponse<any>>('/ai/lab/alert-settings', data)
}
