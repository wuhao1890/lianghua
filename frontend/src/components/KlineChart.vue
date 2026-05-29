<template>
  <div ref="chartRef" :style="{ width: '100%', height: height }"></div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import * as echarts from 'echarts'
import type { KlineData } from '@/types'

const props = withDefaults(defineProps<{
  klineData: KlineData | null
  indicators?: string[]
  chartType?: string
  drawingTool?: string
  height?: string
}>(), {
  indicators: () => ['MA'],
  chartType: 'candlestick',
  drawingTool: 'crosshair',
  height: '500px'
})

const chartRef = ref<HTMLElement>()
let chart: echarts.ECharts | null = null

function calcMA(data: number[], dayCount: number): (number | null)[] {
  const r: (number | null)[] = []
  for (let i = 0; i < data.length; i++) {
    if (i < dayCount - 1) { r.push(null); continue }
    let s = 0; for (let j = 0; j < dayCount; j++) s += data[i - j]
    r.push(+(s / dayCount).toFixed(2))
  }
  return r
}

function calcMACD(data: number[]) {
  const ema12: number[] = [], ema26: number[] = [], dif: number[] = [], dea: number[] = [], macd: number[] = []
  for (let i = 0; i < data.length; i++) {
    if (i === 0) { ema12.push(data[0]); ema26.push(data[0]) }
    else { ema12.push(ema12[i-1]*11/13+data[i]*2/13); ema26.push(ema26[i-1]*25/27+data[i]*2/27) }
    dif.push(ema12[i]-ema26[i])
  }
  for (let i = 0; i < dif.length; i++) {
    if (i===0) dea.push(dif[0]); else dea.push(dea[i-1]*8/10+dif[i]*2/10)
    macd.push(+(dif[i]-dea[i]).toFixed(4))
  }
  return { dif: dif.map(v=>+v.toFixed(2)), dea: dea.map(v=>+v.toFixed(2)), macd }
}

function calcRSI(data: number[], period: number = 14): (number | null)[] {
  const r: (number | null)[] = [null]
  let gain = 0, loss = 0
  for (let i = 1; i < data.length; i++) {
    const diff = data[i] - data[i-1]
    if (i <= period) {
      if (diff > 0) gain += diff; else loss -= diff
      if (i === period) r.push(+(100 - 100/(1+gain/loss)).toFixed(1))
      else r.push(null)
    } else {
      const prevGain = gain, prevLoss = loss
      gain = ((prevGain*13)+(diff>0?diff:0))/14
      loss = ((prevLoss*13)+(diff<0?-diff:0))/14
      r.push(+(100 - 100/(1+gain/loss)).toFixed(1))
    }
  }
  return r
}

function calcKDJ(data: number[], prices: number[][], period: number = 9) {
  const k: (number|null)[] = [], d: (number|null)[] = [], j: (number|null)[] = []
  let prevK = 50, prevD = 50
  for (let i = 0; i < data.length; i++) {
    if (i < period-1) { k.push(null); d.push(null); j.push(null); continue }
    let h = -Infinity, l = Infinity
    for (let t = i-period+1; t <= i; t++) { h = Math.max(h, prices[t][3]); l = Math.min(l, prices[t][2]) }
    const rsv = (h-l)===0 ? 50 : ((data[i]-l)/(h-l)*100)
    const kVal = prevK*2/3 + rsv/3
    const dVal = prevD*2/3 + kVal/3
    k.push(+kVal.toFixed(1)); d.push(+dVal.toFixed(1)); j.push(+(3*kVal-2*dVal).toFixed(1))
    prevK = kVal; prevD = dVal
  }
  return { k, d, j }
}

function calcBOLL(data: number[], period: number = 20): {mid:(number|null)[];up:(number|null)[];dn:(number|null)[]} {
  const mid:(number|null)[]=[], up:(number|null)[]=[], dn:(number|null)[]=[]
  for (let i = 0; i < data.length; i++) {
    if (i < period-1) { mid.push(null); up.push(null); dn.push(null); continue }
    let s=0; for(let j=i-period+1;j<=i;j++) s+=data[j]
    const m=s/period
    let v=0; for(let j=i-period+1;j<=i;j++) v+=(data[j]-m)**2
    const std=Math.sqrt(v/period)
    mid.push(+m.toFixed(2)); up.push(+(m+2*std).toFixed(2)); dn.push(+(m-2*std).toFixed(2))
  }
  return { mid, up, dn }
}

function calcOBV(closes: number[], volumes: number[]): number[] {
  const r: number[] = [volumes[0]||0]
  for (let i = 1; i < closes.length; i++) {
    if (closes[i] > closes[i-1]) r.push(r[i-1] + (volumes[i]||0))
    else if (closes[i] < closes[i-1]) r.push(r[i-1] - (volumes[i]||0))
    else r.push(r[i-1])
  }
  return r
}

function calcATR(prices: number[][], period: number = 14): (number|null)[] {
  const r:(number|null)[]=[null]
  for (let i = 1; i < prices.length; i++) {
    const tr = Math.max(prices[i][3]-prices[i][2], Math.abs(prices[i][3]-prices[i-1][1]), Math.abs(prices[i][2]-prices[i-1][1]))
    if (i===1) r.push(+(tr).toFixed(2))
    else if (i < period) { const prev = r[i-1]||0; r.push(+(((prev*(i-1))+tr)/i).toFixed(2)) }
    else { const prev = r[i-1]||0; r.push(+((prev*13+tr)/14).toFixed(2)) }
  }
  return r
}

function calcCCI(prices: number[][], period: number = 20): (number|null)[] {
  const r:(number|null)[]=[]
  for (let i = 0; i < prices.length; i++) {
    if (i < period-1) { r.push(null); continue }
    let tp=0; for(let j=i-period+1;j<=i;j++) tp+=(prices[j][3]+prices[j][2]+prices[j][1])/3
    const avgTp=tp/period
    let md=0; for(let j=i-period+1;j<=i;j++) md+=Math.abs((prices[j][3]+prices[j][2]+prices[j][1])/3-avgTp)
    const meanDev=md/period
    const ctp = (prices[i][3]+prices[i][2]+prices[i][1])/3
    r.push(meanDev===0?0:+((ctp-avgTp)/(0.015*meanDev)).toFixed(1))
  }
  return r
}

function calcWR(prices: number[][], period: number = 14): (number|null)[] {
  const r:(number|null)[]=[]
  for (let i = 0; i < prices.length; i++) {
    if (i < period-1) { r.push(null); continue }
    let h=-Infinity, l=Infinity
    for(let j=i-period+1;j<=i;j++) { h=Math.max(h,prices[j][3]); l=Math.min(l,prices[j][2]) }
    r.push(h===l?0:+((h-prices[i][1])/(h-l)*(-100)).toFixed(1))
  }
  return r
}

function buildOption() {
  if (!props.klineData || !chart) return

  let dates: string[] = [], prices: number[][] = [], volumes: number[] = []

  if (Array.isArray((props.klineData as any).dates)) {
    const d = props.klineData as any
    dates = d.dates; prices = d.prices || []; volumes = d.volumes || []
  } else if (Array.isArray(props.klineData) && (props.klineData as any[]).length > 0) {
    const klines = props.klineData as any[]
    dates = klines.map(k => k.date); prices = klines.map(k => [k.open, k.close, k.low, k.high]); volumes = klines.map(k => k.volume)
  }
  if (!dates || dates.length === 0) return

  const closes = prices.map(p => p[1])
  const showMA = props.indicators.includes('MA')
  const showMACD = props.indicators.includes('MACD')
  const showRSI = props.indicators.includes('RSI')
  const showKDJ = props.indicators.includes('KDJ')
  const showBOLL = props.indicators.includes('BOLL')
  const showVOL = props.indicators.includes('VOL')
  const showOBV = props.indicators.includes('OBV')
  const showATR = props.indicators.includes('ATR')
  const showCCI = props.indicators.includes('CCI')
  const showWR = props.indicators.includes('WR')

  // Calculate indicators
  const ma5 = calcMA(closes, 5), ma10 = calcMA(closes, 10), ma20 = calcMA(closes, 20), ma60 = calcMA(closes, 60)
  const macdData = calcMACD(closes)
  const rsiData = calcRSI(closes)
  const kdjData = calcKDJ(closes, prices)
  const bollData = calcBOLL(closes)
  const obvData = calcOBV(closes, volumes)
  const atrData = calcATR(prices)
  const cciData = calcCCI(prices)
  const wrData = calcWR(prices)

  // Build sub-charts: main + sub indicators
  const subIndicators: string[] = []
  if (showMACD) subIndicators.push('MACD')
  if (showRSI) subIndicators.push('RSI')
  if (showKDJ) subIndicators.push('KDJ')
  if (showCCI) subIndicators.push('CCI')
  if (showWR) subIndicators.push('WR')
  if (showOBV) subIndicators.push('OBV')
  if (showATR) subIndicators.push('ATR')
  // VOL shares main grid row with volume bars
  const subCount = Math.min(subIndicators.length, 2) // max 2 sub-charts

  // Grid layout
  const mainH = subCount > 0 ? '52%' : '72%'
  const subH = subCount > 0 ? '16%' : '0%'
  const grids: any[] = [{ left: '6%', right: '3%', top: '6%', height: mainH }]
  const xAxes: any[] = [{ type: 'category', data: dates, gridIndex: 0, boundaryGap: true, axisLabel: { fontSize: 10, interval: Math.floor(dates.length/6) }, axisLine: { show: false } }]
  const yAxes: any[] = [{ scale: true, gridIndex: 0, splitLine: { lineStyle: { type: 'dashed', color: '#f0f0f0' } }, axisLabel: { fontSize: 10 } }]
  const series: any[] = []
  const zoomXIndices = [0]

  // Main chart: candlestick / line / area
  const chartType = props.chartType || 'candlestick'
  if (chartType === 'candlestick') {
    series.push({
      name: 'K线', type: 'candlestick', data: prices, xAxisIndex: 0, yAxisIndex: 0,
      itemStyle: { color: '#f56c6c', color0: '#67c23a', borderColor: '#f56c6c', borderColor0: '#67c23a' }
    })
    if (showVOL) {
      series.push({
        name: '成交量', type: 'bar', data: prices.map((p,i) => ({ value: volumes[i] || 0, itemStyle: { color: p[1]>=p[0]?'rgba(245,108,108,0.4)':'rgba(103,194,58,0.4)' } })),
        xAxisIndex: 0, yAxisIndex: 0, barWidth: '50%'
      })
    }
  } else if (chartType === 'line') {
    series.push({ name: '收盘价', type: 'line', data: closes, xAxisIndex: 0, yAxisIndex: 0, smooth: true, symbol: 'none', lineStyle: { width: 2, color: '#409eff' } })
  } else if (chartType === 'area') {
    series.push({ name: '收盘价', type: 'line', data: closes, xAxisIndex: 0, yAxisIndex: 0, smooth: true, symbol: 'none', lineStyle: { width: 2, color: '#409eff' }, areaStyle: { color: new echarts.graphic.LinearGradient(0,0,0,1, [{offset:0,color:'rgba(64,158,255,0.3)'},{offset:1,color:'rgba(64,158,255,0.02)'}]) } })
  }

  if (showMA) {
    series.push(
      { name: 'MA5', type: 'line', data: ma5, xAxisIndex: 0, yAxisIndex: 0, smooth: true, symbol: 'none', lineStyle: { width: 1, color: '#f56c6c' } },
      { name: 'MA10', type: 'line', data: ma10, xAxisIndex: 0, yAxisIndex: 0, smooth: true, symbol: 'none', lineStyle: { width: 1, color: '#e6a23c' } },
      { name: 'MA20', type: 'line', data: ma20, xAxisIndex: 0, yAxisIndex: 0, smooth: true, symbol: 'none', lineStyle: { width: 1, color: '#409eff' } },
      { name: 'MA60', type: 'line', data: ma60, xAxisIndex: 0, yAxisIndex: 0, smooth: true, symbol: 'none', lineStyle: { width: 1, color: '#909399' } }
    )
  }

  if (showBOLL) {
    series.push(
      { name: 'BOLL上轨', type: 'line', data: bollData.up, xAxisIndex: 0, yAxisIndex: 0, smooth: true, symbol: 'none', lineStyle: { width: 1, color: '#9c27b0', type: 'dashed' } },
      { name: 'BOLL中轨', type: 'line', data: bollData.mid, xAxisIndex: 0, yAxisIndex: 0, smooth: true, symbol: 'none', lineStyle: { width: 1, color: '#9c27b0' } },
      { name: 'BOLL下轨', type: 'line', data: bollData.dn, xAxisIndex: 0, yAxisIndex: 0, smooth: true, symbol: 'none', lineStyle: { width: 1, color: '#9c27b0', type: 'dashed' } }
    )
  }

  // Sub-charts
  let subIdx = 0
  const subConfigs: Array<{name:string;data:any;color:string;type?:string}> = []

  if (showMACD) {
    subConfigs.push({name:'MACD柱',data:macdData.macd,color:''})
    subConfigs.push({name:'DIF',data:macdData.dif,color:'#409eff',type:'line'})
    subConfigs.push({name:'DEA',data:macdData.dea,color:'#e6a23c',type:'line'})
    const gi = subIdx + 1; subIdx++
    grids.push({ left: '6%', right: '3%', top: `calc(6% + ${mainH} + 2%)`, height: subH })
    xAxes.push({ type: 'category', data: dates, gridIndex: gi, axisLabel: { show: false }, axisLine: { show: false } })
    yAxes.push({ scale: true, gridIndex: gi, splitLine: { show: false }, axisLabel: { fontSize: 9 } })
    zoomXIndices.push(gi)
    series.push(
      { name: 'MACD', type: 'bar', data: macdData.macd, xAxisIndex: gi, yAxisIndex: gi, itemStyle: { color: (p:any)=>p.value>=0?'#f56c6c':'#67c23a' } },
      { name: 'DIF', type: 'line', data: macdData.dif, xAxisIndex: gi, yAxisIndex: gi, lineStyle: { width:1, color:'#409eff' }, symbol:'none' },
      { name: 'DEA', type: 'line', data: macdData.dea, xAxisIndex: gi, yAxisIndex: gi, lineStyle: { width:1, color:'#e6a23c' }, symbol:'none' }
    )
  }

  if (showRSI) {
    const gi = subIdx + 1; subIdx++
    grids.push({ left: '6%', right: '3%', top: `calc(6% + ${mainH} + ${subIdx}*${subH} + ${subIdx}*2%)`, height: subH })
    xAxes.push({ type: 'category', data: dates, gridIndex: gi, axisLabel: { show: false }, axisLine: { show: false } })
    yAxes.push({ scale: true, gridIndex: gi, splitLine: { show: false }, axisLabel: { fontSize: 9 }, min: 0, max: 100 })
    zoomXIndices.push(gi)
    // Overbought/oversold lines
    series.push(
      { name: 'RSI', type: 'line', data: rsiData, xAxisIndex: gi, yAxisIndex: gi, smooth: true, symbol: 'none', lineStyle: { width: 1.5, color: '#e6a23c' } },
      { name: '超买线', type: 'line', data: rsiData.map(()=>70), xAxisIndex: gi, yAxisIndex: gi, symbol: 'none', lineStyle: { width: 1, color: '#f56c6c', type: 'dashed' } },
      { name: '超卖线', type: 'line', data: rsiData.map(()=>30), xAxisIndex: gi, yAxisIndex: gi, symbol: 'none', lineStyle: { width: 1, color: '#67c23a', type: 'dashed' } }
    )
  }

  if (showKDJ) {
    const gi = subIdx + 1; subIdx++
    grids.push({ left: '6%', right: '3%', top: `calc(6% + ${mainH} + ${subIdx}*${subH} + ${subIdx}*2%)`, height: subH })
    xAxes.push({ type: 'category', data: dates, gridIndex: gi, axisLabel: { show: false }, axisLine: { show: false } })
    yAxes.push({ scale: true, gridIndex: gi, splitLine: { show: false }, axisLabel: { fontSize: 9 } })
    zoomXIndices.push(gi)
    series.push(
      { name: 'K', type: 'line', data: kdjData.k, xAxisIndex: gi, yAxisIndex: gi, smooth: true, symbol: 'none', lineStyle: { width:1, color:'#409eff' } },
      { name: 'D', type: 'line', data: kdjData.d, xAxisIndex: gi, yAxisIndex: gi, smooth: true, symbol: 'none', lineStyle: { width:1, color:'#e6a23c' } },
      { name: 'J', type: 'line', data: kdjData.j, xAxisIndex: gi, yAxisIndex: gi, smooth: true, symbol: 'none', lineStyle: { width:1, color:'#f56c6c' } }
    )
  }

  // Data zoom
  const dataZoom: any[] = [{ type: 'inside', xAxisIndex: zoomXIndices, start: 50, end: 100 }]
  if (subCount === 0) {
    dataZoom.push({ type: 'slider', xAxisIndex: zoomXIndices, bottom: '2%', start: 50, end: 100, height: 12 })
  }

  chart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'cross' }, backgroundColor: 'rgba(255,255,255,0.95)', borderColor: '#e4e7ed', textStyle: { color: '#303133', fontSize: 11 } },
    legend: { data: series.filter(s => s.name && s.name !== '成交量' && s.name !== '超买线' && s.name !== '超卖线'&& s.name !== 'MACD').map(s=>s.name), top: 0, textStyle: { fontSize: 10 }, selectedMode: 'multiple' },
    grid: grids, xAxis: xAxes, yAxis: yAxes, dataZoom, series, animation: true
  })
}

function handleResize() { chart?.resize() }

onMounted(async () => {
  await nextTick()
  if (chartRef.value) { chart = echarts.init(chartRef.value); buildOption(); window.addEventListener('resize', handleResize) }
})

onBeforeUnmount(() => { window.removeEventListener('resize', handleResize); chart?.dispose() })

watch(() => [props.klineData, props.indicators, props.chartType, props.drawingTool], () => { nextTick(() => buildOption()) }, { deep: true })
</script>
<style scoped></style>
