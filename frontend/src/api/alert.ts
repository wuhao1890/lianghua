import request from './request'

export function getAlertList() { return request.get('/stock/alert/list') }
export function addAlert(data: any) { return request.post('/stock/alert/add', data) }
export function deleteAlert(id: number) { return request.delete(`/stock/alert/${id}`) }
export function toggleAlert(id: number) { return request.put(`/stock/alert/${id}/toggle`) }
export function checkAlert(code: string, currentPrice: number) { return request.get(`/stock/alert/check/${code}`, { params: { currentPrice } }) }
