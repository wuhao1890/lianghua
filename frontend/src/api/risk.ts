import request from './request'

export function getRiskSettings() { return request.get('/risk/settings', { params: { userId: 1 } }) }
export function saveRiskSettings(data: any) { return request.post('/risk/settings', data, { params: { userId: 1 } }) }
export function checkRisk(data: any) { return request.post('/risk/check', data) }
