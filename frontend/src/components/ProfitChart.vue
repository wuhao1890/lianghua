<template>
  <div ref="chartRef" :style="{ width: '100%', height: height }"></div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import * as echarts from 'echarts'
import type { ProfitRecord } from '@/types'

const props = withDefaults(defineProps<{
  records: ProfitRecord[]
  height?: string
}>(), {
  height: '360px'
})

const chartRef = ref<HTMLElement>()
let chart: echarts.ECharts | null = null

function buildOption() {
  if (!chart || !props.records || props.records.length === 0) {
    chart?.setOption({
      title: {
        text: '暂无收益数据',
        left: 'center',
        top: 'center',
        textStyle: { color: '#909399', fontSize: 14 }
      }
    })
    return
  }

  const dates = props.records.map(r => r.date)
  const profits = props.records.map(r => r.profit)
  const totalAssets = props.records.map(r => r.totalAssets)
  const profitPercents = props.records.map(r => r.profitPercent)

  chart.setOption({
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(255,255,255,0.95)',
      borderColor: '#e4e7ed',
      textStyle: { color: '#303133', fontSize: 12 },
      formatter: (params: any) => {
        const date = params[0].axisValue
        const profit = params.find((p: any) => p.seriesName === '累计收益')
        const asset = params.find((p: any) => p.seriesName === '总资产')
        return `${date}<br/>` +
          `总资产: <b>${asset ? asset.value : '-'}</b><br/>` +
          `累计收益: <b style="color:${profit && profit.value >= 0 ? '#f56c6c' : '#67c23a'}">${profit ? profit.value : '-'}</b>`
      }
    },
    legend: {
      data: ['总资产', '累计收益'],
      top: 0,
      textStyle: { fontSize: 12 }
    },
    grid: { left: 70, right: 30, top: 40, bottom: 60 },
    xAxis: {
      type: 'category',
      data: dates,
      axisLabel: {
        color: '#909399',
        fontSize: 11,
        rotate: dates.length > 30 ? 45 : 0
      }
    },
    yAxis: [
      {
        type: 'value',
        name: '总资产',
        nameTextStyle: { color: '#909399', fontSize: 11 },
        axisLabel: { color: '#909399', formatter: (v: number) => (v / 10000).toFixed(0) + '万' },
        splitLine: { lineStyle: { color: '#f0f2f5' } }
      },
      {
        type: 'value',
        name: '收益',
        nameTextStyle: { color: '#909399', fontSize: 11 },
        axisLabel: { color: '#909399' },
        splitLine: { show: false }
      }
    ],
    dataZoom: [
      { type: 'inside', start: 0, end: 100 },
      { type: 'slider', start: 0, end: 100, bottom: 5 }
    ],
    series: [
      {
        name: '总资产',
        type: 'line',
        data: totalAssets,
        smooth: true,
        symbol: 'none',
        lineStyle: { width: 2, color: '#409eff' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(64,158,255,0.3)' },
            { offset: 1, color: 'rgba(64,158,255,0.02)' }
          ])
        },
        yAxisIndex: 0
      },
      {
        name: '累计收益',
        type: 'bar',
        data: profits.map(v => ({
          value: v,
          itemStyle: { color: v >= 0 ? '#f56c6c' : '#67c23a' }
        })),
        barWidth: '60%',
        yAxisIndex: 1
      }
    ]
  })
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

watch(() => props.records, () => {
  nextTick(() => buildOption())
}, { deep: true })
</script>

<style scoped>
</style>
