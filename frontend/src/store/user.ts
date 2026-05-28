import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { User, LoginRequest, RegisterRequest } from '@/types'
import { login as loginApi, register as registerApi, getUserInfo as getUserInfoApi } from '@/api/auth'
import { getToken, setToken, removeToken, setUser, removeUser, getUser } from '@/utils/storage'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(getToken() || '')
  const userInfo = ref<User | null>(getUser())
  const isLoggedIn = computed(() => !!token.value)
  const isAdmin = computed(() => userInfo.value?.role === 'ADMIN')

  async function login(data: LoginRequest) {
    const res = await loginApi(data)
    // 后端返回: { code, data: { token, userId, username, nickname, availableCash, initialCapital }, message }
    const loginData = res.data.data
    token.value = loginData.token
    const user: User = {
      id: loginData.userId,
      username: loginData.username,
      nickname: loginData.nickname,
      role: loginData.role,
      availableCash: loginData.availableCash,
      initialCapital: loginData.initialCapital
    }
    userInfo.value = user
    setToken(loginData.token)
    setUser(user)
    return loginData
  }

  async function register(data: RegisterRequest) {
    const res = await registerApi(data)
    return res.data
  }

  async function getUserInfo() {
    try {
      const res = await getUserInfoApi()
      userInfo.value = res.data.data
      setUser(res.data.data)
      return res.data.data
    } catch (error) {
      return null
    }
  }

  function logout() {
    token.value = ''
    userInfo.value = null
    removeToken()
    removeUser()
  }

  return {
    token,
    userInfo,
    isLoggedIn,
    isAdmin,
    login,
    register,
    getUserInfo,
    logout
  }
})
