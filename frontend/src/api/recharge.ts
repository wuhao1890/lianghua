import request from './request'

// 用户提交充值申请
export function applyRecharge(data: { amount: number; remark?: string }) {
  return request.post('/recharge/apply', data)
}

// 管理员给用户充值
export function adminRecharge(data: { userId: number; amount: number; remark?: string }) {
  return request.post('/recharge/admin/recharge', data)
}

// 管理员确认待处理的充值
export function confirmRecharge(orderId: number) {
  return request.post(`/recharge/admin/confirm/${orderId}`)
}

// 获取用户自己的充值记录
export function getRechargeRecords(params: { page: number; pageSize: number }) {
  return request.get('/recharge/records', { params })
}

// 管理员获取所有充值记录
export function getAllRechargeRecords(params: { page: number; pageSize: number; status?: string }) {
  return request.get('/recharge/admin/records', { params })
}
