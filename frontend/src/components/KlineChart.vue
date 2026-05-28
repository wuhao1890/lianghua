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
  height?: string
}>(), {
  indicators: () => ['MA'],
  height: '500px'
})

const chartRef = ref<HTMLElement>()
let chart: echarts.ECharts | null = null

function buildOption() {
  if (!props.klineData || !chart) return

  // 兼容两种格式
  let dates: string[] = []
  let prices: number[][] = []
  let volumes: number[] = []

  // 格式1: { dates: [], prices: [[]], volumes: [] }
  if (Array.isArray(props.klineData.dates)) {
    dates = props.klineData.dates
    prices = props.klineData.prices || []
    volumes = props.klineData.volumes || []
  }
  // 格式2: [{ date, open, high, low, close, volume }]
  else if (Array.isArray(props.klineData) && props.klineData.length > 0) {
    const klines = props.klineData as any[]
    dates = klines.map(k => k.date)
    prices = klines.map(k => [k.open, k.close, k.low, k.high])
    volumes = klines.map(k => k.volume)
  }

  if (!dates || dates.length === 0) return

  const showMA = props.indicators.includes('MA')
  const showMACD = props.indicators.includes('MACD')

  // 计算MA均线 - 使用收盘价 (prices[i][1] = close)
  const closes = prices.map(p => p[1])
  const ma5 = calcMA(closes, 5)
  const ma10 = calcMA(closes, 10)
  const ma20 = calcMA(closes, 20)
  const ma60 = calcMA(closes, 60)

  // 计算MACD
  const macdData = calcMACD(closes)

  const gridConfig = showMACD
    ? [
        { left: '8%', right: '3%', top: '5%', height: '50%' },
        { left: '8%', right: '3%', top: '65%', height: '25%' }
      ]
    : [{ left: '8%', right: '3%', top: '5%', height: '88%' }]

  const xAxisConfig = showMACD
    ? [
        { type: 'category' as const, data: dates, gridIndex: 0, show: false, boundaryGap: true },
        { type: 'category' as const, data: dates, gridIndex: 1, boundaryGap: true }
      ]
    : [{ type: 'category' as const, data: dates, boundaryGap: true }]

  const yAxisConfig = showMACD
    ? [
        { scale: true, gridIndex: 0, splitArea: { show: false } },
        { scale: true, gridIndex: 1, splitArea: { show: false } }
      ]
    : [{ scale: true, splitArea: { show: false } }]

  const dataZoomConfig = showMACD
    ? [
        { type: 'inside', xAxisIndex: [0, 1], start: 60, end: 100 },
        { type: 'slider', xAxisIndex: [0, 1], top: '93%', start: 60, end: 100 }
      ]
    : [
        { type: 'inside', xAxisIndex: 0, start: 60, end: 100 },
        { type: 'slider', xAxisIndex: 0, top: '93%', start: 60, end: 100 }
      ]

  const series: any[] = [
    {
      name: 'K线',
      type: 'candlestick',
      data: prices,
      xAxisIndex: 0,
      yAxisIndex: 0,
      itemStyle: {
        color: '#f56c6c',
        color0: '#67c23a',
        borderColor: '#f56c6c',
        borderColor0: '#67c23a'
      }
    }
  ]

  if (showMA) {
    series.push(
      { name: 'MA5', type: 'line', data: ma5, smooth: true, xAxisIndex: 0, yAxisIndex: 0, lineStyle: { width: 1 }, itemStyle: { color: '#f56c6c' }, symbol: 'none' },
      { name: 'MA10', type: 'line', data: ma10, smooth: true, xAxisIndex: 0, yAxisIndex: 0, lineStyle: { width: 1 }, itemStyle: { color: '#e6a23c' }, symbol: 'none' },
      { name: 'MA20', type: 'line', data: ma20, smooth: true, xAxisIndex: 0, yAxisIndex: 0, lineStyle: { width: 1 }, itemStyle: { color: '#409eff' }, symbol: 'none' },
      { name: 'MA60', type: 'line', data: ma60, smooth: true, xAxisIndex: 0, yAxisIndex: 0, lineStyle: { width: 1 }, itemStyle: { color: '#909399' }, symbol: 'none' }
    )
  }

  if (showMACD) {
    series.push(
      {
        name: 'DIF',
        type: 'bar',
        data: macdData.macd,
        xAxisIndex: 1,
        yAxisIndex: 1,
        itemStyle: {
          color: (params: any) => params.value >= 0 ? '#f56c6c' : '#67c23a'
        }
      },
      {
        name: 'DIF线',
        type: 'line',
        data: macdData.dif,
        xAxisIndex: 1,
        yAxisIndex: 1,
        lineStyle: { width: 1 },
        itemStyle: { color: '#409eff' },
        symbol: 'none'
      },
      {
        name: 'DEA线',
        type: 'line',
        data: macdData.dea,
        xAxisIndex: 1,
        yAxisIndex: 1,
        lineStyle: { width: 1 },
        itemStyle: { color: '#e6a23c' },
        symbol: 'none'
      }
    )
  }

  chart.setOption({
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' },
      backgroundColor: 'rgba(255,255,255,0.95)',
      borderColor: '#e4e7ed',
      textStyle: { color: '#303133', fontSize: 12 }
    },
    legend: {
      data: showMA ? ['MA5', 'MA10', 'MA20', 'MA60'] : [],
      top: 0,
      textStyle: { fontSize: 11 }
    },
    grid: gridConfig,
    xAxis: xAxisConfig,
    yAxis: yAxisConfig,
    dataZoom: dataZoomConfig,
    series
  })
}

function calcMA(data: number[], dayCount: number): (number | null)[] {
  const result: (number | null)[] = []
  for (let i = 0; i < data.length; i++) {
    if (i < dayCount - 1) {
      result.push(null)
      continue
    }
    let sum = 0
    for (let j = 0; j < dayCount; j++) {
      sum += data[i - j]
    }
    result.push(+(sum / dayCount).toFixed(2))
  }
  return result
}

function calcMACD(data: number[]) {
  const ema12: number[] = []
  const ema26: number[] = []
  const dif: number[] = []
  const dea: number[] = []
  const macd: number[] = []

  for (let i = 0; i < data.length; i++) {
    if (i === 0) {
      ema12.push(data[0])
      ema26.push(data[0])
    } else {
      ema12.push(ema12[i - 1] * 11 / 13 + data[i] * 2 / 13)
      ema26.push(ema26[i - 1] * 25 / 27 + data[i] * 2 / 27)
    }
    dif.push(ema12[i] - ema26[i])
  }

  for (let i = 0; i < dif.length; i++) {
    if (i === 0) {
      dea.push(dif[0])
    } else {
      dea.push(dea[i - 1] * 8 / 10 + dif[i] * 2 / 10)
    }
    macd.push(+(dif[i] - dea[i]).toFixed(4))
  }

  return { dif: dif.map(v => +v.toFixed(2)), dea: dea.map(v => +v.toFixed(2)), macd }
}

function handleResize() {
  chart?.resize()
}

onMounted(async () => {
  await nextTick()
  if (chartRef.value) {
    chart = echarts.init(chartRef.value)
    buildOption()
    window.addEventListener('resize', handleResize)
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  chart?.dispose()
})

watch(() => [props.klineData, props.indicators], () => {
  nextTick(() => buildOption())
}, { deep: true })
</script>

<style scoped>
</style>
