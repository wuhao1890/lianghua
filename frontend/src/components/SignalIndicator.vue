<template>
  <div class="signal-indicator">
    <div v-if="!signalData" class="no-signal">
      <el-empty description="暂无信号数据" :image-size="60" />
    </div>
    <template v-else>
      <!-- 各指标信号详情 -->
      <div class="indicator-list">
        <div
          v-for="(msg, indicator) in signalData.indicatorSignals"
          :key="indicator"
          class="indicator-item"
          :class="getSignalClass(msg)"
        >
          <div class="indicator-header">
            <span class="indicator-tag">{{ indicator }}</span>
            <span class="indicator-fullname">{{ indicatorName(String(indicator)) }}</span>
            <el-tag :type="getTagType(msg)" size="small" effect="light">
              {{ getSignalLabel(msg) }}
            </el-tag>
          </div>
          <div class="indicator-desc">
            {{ getSignalDesc(String(indicator), msg) }}
          </div>
        </div>
      </div>

      <!-- 综合信号 -->
      <div class="overall-signal" :class="signalData.signal?.toLowerCase()">
        <div class="overall-header">
          <span class="overall-title">综合研判</span>
          <el-tag
            :type="signalData.signal === 'BUY' ? 'danger' : signalData.signal === 'SELL' ? 'success' : 'info'"
            size="large" effect="dark"
          >
            {{ overallLabel }}
          </el-tag>
        </div>
        <div class="overall-strength">
          <span class="strength-text">信号强度</span>
          <div class="strength-stars">
            <el-icon
              v-for="i in 5"
              :key="i"
              :class="{ active: i <= (signalData.strength || 0) }"
            ><StarFilled /></el-icon>
          </div>
          <span class="strength-hint">{{ strengthHint }}</span>
        </div>
        <div class="overall-desc">{{ signalData.description }}</div>
      </div>

      <!-- 说明 -->
      <div class="signal-legend">
        <div class="legend-title">指标说明</div>
        <div class="legend-items">
          <div class="legend-item"><b>MA</b> 均线 — 趋势方向判断，金叉看涨、死叉看跌</div>
          <div class="legend-item"><b>MACD</b> 指数平滑 — 中期动量，金叉/死叉为强信号</div>
          <div class="legend-item"><b>RSI</b> 强弱指数 — 超买(&gt;70)看跌、超卖(&lt;30)看涨</div>
          <div class="legend-item"><b>KDJ</b> 随机指标 — 短线超买超卖，金叉/死叉为强信号</div>
          <div class="legend-item"><b>BOLL</b> 布林带 — 触及上轨看跌、触及下轨看涨</div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { StarFilled } from '@element-plus/icons-vue'

const props = defineProps<{
  signals: any
  stockCode: string
}>()

// 后端返回的是单个 TradeSignal 对象，不是数组
const signalData = computed(() => {
  if (!props.signals) return null
  // 如果是数组取第一个，否则直接用
  const raw = Array.isArray(props.signals) ? props.signals[0] : props.signals
  if (!raw || !raw.indicatorSignals) return null
  return raw
})

const overallLabel = computed(() => {
  const s = signalData.value?.signal
  if (s === 'BUY') return '买入'
  if (s === 'SELL') return '卖出'
  return '观望'
})

const strengthHint = computed(() => {
  const s = signalData.value?.strength || 0
  if (s >= 4) return '非常强烈'
  if (s >= 3) return '较强'
  if (s >= 2) return '中等'
  return '较弱'
})

function indicatorName(key: string): string {
  const map: Record<string, string> = {
    MA: '移动平均线',
    MACD: '指数平滑异同移动平均线',
    RSI: '相对强弱指标',
    KDJ: '随机指标',
    BOLL: '布林带'
  }
  return map[key] || key
}

function parseSignal(msg: string): { type: string; action: string } {
  if (!msg) return { type: 'HOLD', action: '' }
  const parts = msg.split(' - ')
  const action = parts[parts.length - 1]?.trim() || 'HOLD'
  return { type: action, action }
}

function getSignalClass(msg: string): string {
  return parseSignal(msg).type.toLowerCase()
}

function getSignalLabel(msg: string): string {
  const action = parseSignal(msg).action
  const map: Record<string, string> = { BUY: '买入', SELL: '卖出', HOLD: '观望' }
  return map[action] || action
}

function getTagType(msg: string): string {
  const action = parseSignal(msg).action
  if (action === 'BUY') return 'danger'
  if (action === 'SELL') return 'success'
  return 'info'
}

function getSignalDesc(indicator: string, msg: string): string {
  const parts = msg.split(' - ')
  const desc = parts.slice(0, -1).join(' - ')
  if (desc) return desc

  // 根据指标和信号类型给出通俗解释
  const action = parseSignal(msg).action
  const explanations: Record<string, Record<string, string>> = {
    MA: {
      BUY: '短期均线向上穿越长期均线，趋势转多',
      SELL: '短期均线向下穿越长期均线，趋势转空',
      HOLD: '均线交织，趋势不明朗'
    },
    MACD: {
      BUY: '快线向上穿越慢线，动能增强',
      SELL: '快线向下穿越慢线，动能减弱',
      HOLD: '多空动能均衡，方向待确认'
    },
    RSI: {
      BUY: '指标进入超卖区域，可能有反弹',
      SELL: '指标进入超买区域，可能有回调',
      HOLD: '指标处于正常区间'
    },
    KDJ: {
      BUY: 'K线上穿D线形成金叉，短线看涨',
      SELL: 'K线下穿D线形成死叉，短线看跌',
      HOLD: '指标处于中性区域'
    },
    BOLL: {
      BUY: '价格触及布林带下轨，可能超跌反弹',
      SELL: '价格触及布林带上轨，可能超涨回落',
      HOLD: '价格在布林带中轨附近运行'
    }
  }
  return explanations[indicator]?.[action] || '指标信号中性'
}
</script>

<style scoped lang="scss">
.signal-indicator {
  padding: 4px 0;
}

.no-signal {
  padding: 20px 0;
}

/* 指标列表 */
.indicator-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 16px;
}

.indicator-item {
  border-radius: 8px;
  padding: 12px 14px;
  border-left: 4px solid;
  transition: transform 0.2s;

  &:hover {
    transform: translateX(2px);
  }

  &.buy {
    background: rgba(245, 108, 108, 0.05);
    border-left-color: #f56c6c;
  }

  &.sell {
    background: rgba(103, 194, 58, 0.05);
    border-left-color: #67c23a;
  }

  &.hold {
    background: rgba(144, 147, 153, 0.05);
    border-left-color: #909399;
  }
}

.indicator-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;

  .indicator-tag {
    font-size: 14px;
    font-weight: 700;
    color: #303133;
    min-width: 44px;
  }

  .indicator-fullname {
    font-size: 12px;
    color: #909399;
    flex: 1;
  }
}

.indicator-desc {
  font-size: 13px;
  color: #606266;
  line-height: 1.5;
  padding-left: 52px;
}

/* 综合信号 */
.overall-signal {
  border-radius: 10px;
  padding: 18px 20px;
  margin-bottom: 16px;
  border: 1px solid #ebeef5;

  &.buy {
    background: linear-gradient(135deg, rgba(245, 108, 108, 0.08), rgba(245, 108, 108, 0.02));
    border-color: rgba(245, 108, 108, 0.3);
  }

  &.sell {
    background: linear-gradient(135deg, rgba(103, 194, 58, 0.08), rgba(103, 194, 58, 0.02));
    border-color: rgba(103, 194, 58, 0.3);
  }

  &.hold {
    background: #f5f7fa;
    border-color: #ebeef5;
  }
}

.overall-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;

  .overall-title {
    font-size: 15px;
    font-weight: 600;
    color: #303133;
  }
}

.overall-strength {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;

  .strength-text {
    font-size: 13px;
    color: #909399;
  }

  .strength-stars {
    display: flex;
    gap: 2px;

    .el-icon {
      font-size: 16px;
      color: #dcdfe6;
      transition: color 0.2s;

      &.active {
        color: #e6a23c;
      }
    }
  }

  .strength-hint {
    font-size: 12px;
    color: #e6a23c;
    font-weight: 500;
  }
}

.overall-desc {
  font-size: 13px;
  color: #909399;
  line-height: 1.5;
}

/* 指标说明 */
.signal-legend {
  background: #fafbfc;
  border-radius: 8px;
  padding: 14px 16px;
  border: 1px dashed #e4e7ed;

  .legend-title {
    font-size: 13px;
    font-weight: 600;
    color: #909399;
    margin-bottom: 8px;
  }

  .legend-items {
    display: flex;
    flex-direction: column;
    gap: 4px;
  }

  .legend-item {
    font-size: 12px;
    color: #909399;
    line-height: 1.6;

    b {
      color: #606266;
      display: inline-block;
      min-width: 44px;
    }
  }
}
</style>
