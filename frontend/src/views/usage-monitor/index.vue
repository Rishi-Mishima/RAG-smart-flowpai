<script setup lang="tsx">
import { computed, onMounted, ref, watch } from 'vue';
import { NButton, NCard, NEmpty, NTag } from 'naive-ui';
import type { ECOption } from '@/hooks/common/echarts';
import { useEcharts } from '@/hooks/common/echarts';

const trendWindow = ref<7 | 30>(7);
const overviewLoading = ref(false);
const overview = ref<Api.Admin.UsageOverview | null>(null);
const rateLimitLoading = ref(false);
const rateLimitSaving = ref(false);
const rateLimits = ref<Api.Admin.RateLimitSettings | null>(null);

async function getOverview() {
  overviewLoading.value = true;
  const { error, data } = await request<Api.Admin.UsageOverview>({
    url: '/admin/usage/overview',
    params: {
      days: trendWindow.value
    }
  });

  if (!error && data) {
    overview.value = data;
  }
  overviewLoading.value = false;
}

function createDefaultRateLimitSettings(): Api.Admin.RateLimitSettings {
  return {
    chatMessage: {
      max: 30,
      windowSeconds: 60
    },
    llmGlobalToken: {
      minuteMax: 120000,
      minuteWindowSeconds: 60,
      dayMax: 8000000,
      dayWindowSeconds: 86400
    },
    embeddingUploadToken: {
      minuteMax: 200000,
      minuteWindowSeconds: 60,
      dayMax: 20000000,
      dayWindowSeconds: 86400
    },
    embeddingQueryRequest: {
      minuteMax: 60,
      minuteWindowSeconds: 60,
      dayMax: 5000,
      dayWindowSeconds: 86400
    },
    embeddingQueryGlobalToken: {
      minuteMax: 60000,
      minuteWindowSeconds: 60,
      dayMax: 4000000,
      dayWindowSeconds: 86400
    }
  };
}

function cloneTokenBudgetLimit(payload?: Partial<Api.Admin.TokenBudgetLimit> | null): Api.Admin.TokenBudgetLimit {
  return {
    minuteMax: Number(payload?.minuteMax || 0),
    minuteWindowSeconds: Number(payload?.minuteWindowSeconds || 0),
    dayMax: Number(payload?.dayMax || 0),
    dayWindowSeconds: Number(payload?.dayWindowSeconds || 0)
  };
}

function cloneDualWindowLimit(payload?: Partial<Api.Admin.DualWindowLimit> | null): Api.Admin.DualWindowLimit {
  return {
    minuteMax: Number(payload?.minuteMax || 0),
    minuteWindowSeconds: Number(payload?.minuteWindowSeconds || 0),
    dayMax: Number(payload?.dayMax || 0),
    dayWindowSeconds: Number(payload?.dayWindowSeconds || 0)
  };
}

function cloneRateLimitSettings(payload?: Api.Admin.RateLimitSettings | null): Api.Admin.RateLimitSettings {
  return {
    chatMessage: {
      max: Number(payload?.chatMessage.max || 0),
      windowSeconds: Number(payload?.chatMessage.windowSeconds || 0)
    },
    llmGlobalToken: cloneTokenBudgetLimit(payload?.llmGlobalToken),
    embeddingUploadToken: cloneTokenBudgetLimit(payload?.embeddingUploadToken),
    embeddingQueryRequest: cloneDualWindowLimit(payload?.embeddingQueryRequest),
    embeddingQueryGlobalToken: cloneTokenBudgetLimit(payload?.embeddingQueryGlobalToken)
  };
}

async function getRateLimits() {
  rateLimitLoading.value = true;
  const { error, data } = await request<Api.Admin.RateLimitSettings>({
    url: '/admin/rate-limits'
  });

  if (!error && data) {
    rateLimits.value = cloneRateLimitSettings(data);
  } else if (!rateLimits.value) {
    rateLimits.value = createDefaultRateLimitSettings();
  }
  rateLimitLoading.value = false;
}

async function submitRateLimits() {
  if (!rateLimits.value) {
    return;
  }

  rateLimitSaving.value = true;
  const { error, data } = await request<Api.Admin.RateLimitSettings>({
    url: '/admin/rate-limits',
    method: 'put',
    data: cloneRateLimitSettings(rateLimits.value)
  });

  if (!error && data) {
    rateLimits.value = cloneRateLimitSettings(data);
    window.$message?.success('限流配置已更新');
  }

  rateLimitSaving.value = false;
}

const { domRef: trendChartRef, updateOptions } = useEcharts<ECOption>(() => ({
  tooltip: {
    trigger: 'axis'
  },
  legend: {
    top: 0
  },
  grid: {
    top: 48,
    left: 24,
    right: 24,
    bottom: 24,
    containLabel: true
  },
  xAxis: {
    type: 'category',
    data: []
  },
  yAxis: [
    {
      type: 'value',
      name: 'Tokens'
    },
    {
      type: 'value',
      name: 'Requests'
    }
  ],
  series: []
}));

watch([overview, trendWindow], async () => {
  const trends = overview.value?.trends || [];
  await updateOptions(() => ({
    tooltip: {
      trigger: 'axis'
    },
    legend: {
      top: 0
    },
    grid: {
      top: 48,
      left: 24,
      right: 24,
      bottom: 24,
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: trends.map(item => dayjs(item.day).format('MM-DD'))
    },
    yAxis: [
      {
        type: 'value',
        name: 'Tokens'
      },
      {
        type: 'value',
        name: 'Requests'
      }
    ],
    series: [
      {
        name: 'LLM Tokens',
        type: 'line',
        smooth: true,
        data: trends.map(item => item.llmUsedTokens)
      },
      {
        name: 'Embedding Tokens',
        type: 'line',
        smooth: true,
        data: trends.map(item => item.embeddingUsedTokens)
      },
      {
        name: 'Chat Messages',
        type: 'bar',
        yAxisIndex: 1,
        barMaxWidth: 18,
        itemStyle: {
          opacity: 0.4
        },
        data: trends.map(item => item.chatRequestCount)
      },
      {
        name: 'LLM Requests',
        type: 'bar',
        yAxisIndex: 1,
        barMaxWidth: 18,
        itemStyle: {
          opacity: 0.32
        },
        data: trends.map(item => item.llmRequestCount)
      },
      {
        name: 'Embedding Requests',
        type: 'bar',
        yAxisIndex: 1,
        barMaxWidth: 18,
        itemStyle: {
          opacity: 0.22
        },
        data: trends.map(item => item.embeddingRequestCount)
      }
    ]
  }));
});

const alertCount = computed(() => overview.value?.alerts.length || 0);
const criticalAlertCount = computed(() => overview.value?.alerts.filter(item => item.level === 'critical').length || 0);
const todaySummary = computed(() => overview.value?.today);

function formatNumber(value?: number) {
  return Number(value || 0).toLocaleString();
}

function scopeLabel(scope: 'llm' | 'embedding') {
  return scope === 'llm' ? 'LLM' : 'Embedding';
}

function alertType(level: 'critical' | 'warning') {
  return level === 'critical' ? 'error' : 'warning';
}

onMounted(() => {
  getOverview();
  getRateLimits();
});
</script>

<template>
  <div class="min-h-500px flex-col-stretch gap-16px overflow-auto">
    <NCard :bordered="false" size="small" class="card-wrapper">
      <template #header>调用限流配置</template>
      <template #header-extra>
        <div class="flex items-center gap-2">
          <span class="text-xs text-stone-400">保存后立即对新请求生效</span>
          <NButton type="primary" size="small" :loading="rateLimitSaving" @click="submitRateLimits">
            保存配置
          </NButton>
        </div>
      </template>

      <NSpin :show="rateLimitLoading">
        <div class="mb-4 rounded-2xl border border-stone-200 bg-stone-50 px-4 py-3 text-xs text-stone-500">
          这里集中管理聊天消息、LLM 全网 Token 预算，以及 Embedding 上传/查询两条链路的运行时限流配置。保存后对新请求立即生效，无需改 `application.yml`。
        </div>

        <div v-if="rateLimits" class="grid gap-4 xl:grid-cols-2">
          <div class="limit-card">
            <div class="limit-title">聊天消息</div>
            <div class="limit-grid">
              <div>
                <div class="limit-label">次数上限</div>
                <NInputNumber v-model:value="rateLimits.chatMessage.max" :min="1" class="w-full" />
              </div>
              <div>
                <div class="limit-label">窗口秒数</div>
                <NInputNumber v-model:value="rateLimits.chatMessage.windowSeconds" :min="1" class="w-full" />
              </div>
            </div>
          </div>

          <div class="limit-card">
            <div class="limit-title">LLM 全网 Token 预算</div>
            <div class="limit-grid">
              <div>
                <div class="limit-label">分钟 Token 上限</div>
                <NInputNumber v-model:value="rateLimits.llmGlobalToken.minuteMax" :min="1" class="w-full" />
              </div>
              <div>
                <div class="limit-label">分钟窗口秒数</div>
                <NInputNumber v-model:value="rateLimits.llmGlobalToken.minuteWindowSeconds" :min="1" class="w-full" />
              </div>
              <div>
                <div class="limit-label">日 Token 上限</div>
                <NInputNumber v-model:value="rateLimits.llmGlobalToken.dayMax" :min="1" class="w-full" />
              </div>
              <div>
                <div class="limit-label">日窗口秒数</div>
                <NInputNumber v-model:value="rateLimits.llmGlobalToken.dayWindowSeconds" :min="1" class="w-full" />
              </div>
            </div>
          </div>

          <div class="limit-card">
            <div class="limit-title">Embedding 上传 Token 预算</div>
            <div class="limit-grid">
              <div>
                <div class="limit-label">分钟 Token 上限</div>
                <NInputNumber v-model:value="rateLimits.embeddingUploadToken.minuteMax" :min="1" class="w-full" />
              </div>
              <div>
                <div class="limit-label">分钟窗口秒数</div>
                <NInputNumber v-model:value="rateLimits.embeddingUploadToken.minuteWindowSeconds" :min="1" class="w-full" />
              </div>
              <div>
                <div class="limit-label">日 Token 上限</div>
                <NInputNumber v-model:value="rateLimits.embeddingUploadToken.dayMax" :min="1" class="w-full" />
              </div>
              <div>
                <div class="limit-label">日窗口秒数</div>
                <NInputNumber v-model:value="rateLimits.embeddingUploadToken.dayWindowSeconds" :min="1" class="w-full" />
              </div>
            </div>
          </div>

          <div class="limit-card">
            <div class="limit-title">Embedding 查询</div>
            <div class="limit-grid">
              <div>
                <div class="limit-label">单用户分钟次数</div>
                <NInputNumber v-model:value="rateLimits.embeddingQueryRequest.minuteMax" :min="1" class="w-full" />
              </div>
              <div>
                <div class="limit-label">分钟窗口秒数</div>
                <NInputNumber v-model:value="rateLimits.embeddingQueryRequest.minuteWindowSeconds" :min="1" class="w-full" />
              </div>
              <div>
                <div class="limit-label">单用户日次数</div>
                <NInputNumber v-model:value="rateLimits.embeddingQueryRequest.dayMax" :min="1" class="w-full" />
              </div>
              <div>
                <div class="limit-label">日窗口秒数</div>
                <NInputNumber v-model:value="rateLimits.embeddingQueryRequest.dayWindowSeconds" :min="1" class="w-full" />
              </div>
              <div>
                <div class="limit-label">全网分钟 Token</div>
                <NInputNumber v-model:value="rateLimits.embeddingQueryGlobalToken.minuteMax" :min="1" class="w-full" />
              </div>
              <div>
                <div class="limit-label">查询分钟窗口秒数</div>
                <NInputNumber v-model:value="rateLimits.embeddingQueryGlobalToken.minuteWindowSeconds" :min="1" class="w-full" />
              </div>
              <div>
                <div class="limit-label">全网日 Token</div>
                <NInputNumber v-model:value="rateLimits.embeddingQueryGlobalToken.dayMax" :min="1" class="w-full" />
              </div>
              <div>
                <div class="limit-label">查询日窗口秒数</div>
                <NInputNumber v-model:value="rateLimits.embeddingQueryGlobalToken.dayWindowSeconds" :min="1" class="w-full" />
              </div>
            </div>
          </div>
        </div>
        <NEmpty v-else size="small" description="暂无限流配置" />
      </NSpin>
    </NCard>

    <NCard :bordered="false" size="small" class="card-wrapper">
      <template #header>
        <div class="flex items-center gap-3">
          <span>用量总览</span>
          <NTag size="small" type="warning">今日告警 {{ alertCount }}</NTag>
          <NTag size="small" type="error">超额 {{ criticalAlertCount }}</NTag>
        </div>
      </template>
      <template #header-extra>
        <div class="flex items-center gap-2">
          <NButton size="small" :type="trendWindow === 7 ? 'primary' : 'default'" @click="trendWindow = 7; getOverview()">近7天</NButton>
          <NButton size="small" :type="trendWindow === 30 ? 'primary' : 'default'" @click="trendWindow = 30; getOverview()">近30天</NButton>
        </div>
      </template>

      <NSpin :show="overviewLoading">
        <div class="flex flex-col gap-4">
          <div class="grid gap-4 xl:grid-cols-5 sm:grid-cols-2">
            <div class="summary-card">
              <div class="summary-label">今日聊天消息</div>
              <div class="summary-value">{{ formatNumber(todaySummary?.chatRequestCount) }}</div>
              <div class="summary-sub">按通过限流的消息数统计</div>
            </div>
            <div class="summary-card">
              <div class="summary-label">今日 LLM Tokens</div>
              <div class="summary-value">{{ formatNumber(todaySummary?.llmUsedTokens) }}</div>
              <div class="summary-sub">请求 {{ formatNumber(todaySummary?.llmRequestCount) }} 次</div>
            </div>
            <div class="summary-card">
              <div class="summary-label">今日 Embedding Tokens</div>
              <div class="summary-value">{{ formatNumber(todaySummary?.embeddingUsedTokens) }}</div>
              <div class="summary-sub">请求 {{ formatNumber(todaySummary?.embeddingRequestCount) }} 次</div>
            </div>
            <div class="summary-card is-alert">
              <div class="summary-label">高风险用户</div>
              <div class="summary-value">{{ criticalAlertCount }}</div>
              <div class="summary-sub">额度已耗尽</div>
            </div>
            <div class="summary-card">
              <div class="summary-label">总告警数</div>
              <div class="summary-value">{{ alertCount }}</div>
              <div class="summary-sub">含 80% 以上预警</div>
            </div>
          </div>

          <div class="grid gap-4 xl:grid-cols-[minmax(0,1.4fr)_minmax(320px,0.6fr)]">
            <NCard size="small" embedded class="overview-section">
              <template #header>调用趋势</template>
              <div ref="trendChartRef" class="h-360px w-full" />
            </NCard>

            <div class="flex flex-col gap-4">
              <NCard size="small" embedded class="overview-section">
                <template #header>超额与预警</template>
                <div v-if="overview?.alerts?.length" class="flex flex-col gap-3">
                  <div
                    v-for="alert in overview.alerts.slice(0, 6)"
                    :key="`${alert.userId}-${alert.scope}`"
                    class="alert-item"
                  >
                    <div class="flex items-center justify-between gap-3">
                      <div class="flex items-center gap-2">
                        <span class="font-medium text-stone-700">{{ alert.username }}</span>
                        <NTag size="small" :type="alertType(alert.level)">{{ scopeLabel(alert.scope) }}</NTag>
                      </div>
                      <NTag size="small" :type="alertType(alert.level)">{{ alert.message }}</NTag>
                    </div>
                    <div class="text-xs text-stone-500">
                      {{ formatNumber(alert.usedTokens) }} / {{ formatNumber(alert.limitTokens) }}，剩余 {{ formatNumber(alert.remainingTokens) }}，{{ alert.requestCount }} 次
                    </div>
                  </div>
                </div>
                <NEmpty v-else size="small" description="暂无告警" />
              </NCard>

              <NCard size="small" embedded class="overview-section">
                <template #header>今日用量排行</template>
                <div class="flex flex-col gap-4">
                  <div>
                    <div class="mb-2 text-xs font-semibold uppercase tracking-0.12em text-stone-400">LLM</div>
                    <div v-if="overview?.llmRankings?.length" class="flex flex-col gap-2">
                      <div
                        v-for="(item, index) in overview.llmRankings"
                        :key="`llm-${item.userId}`"
                        class="ranking-item"
                      >
                        <span class="ranking-index">{{ index + 1 }}</span>
                        <div class="flex-1">
                          <div class="font-medium text-stone-700">{{ item.username }}</div>
                          <div class="text-xs text-stone-500">
                            {{ formatNumber(item.usedTokens) }} / {{ formatNumber(item.limitTokens) }} · {{ item.requestCount }} 次
                          </div>
                        </div>
                      </div>
                    </div>
                    <NEmpty v-else size="small" description="暂无数据" />
                  </div>

                  <div>
                    <div class="mb-2 text-xs font-semibold uppercase tracking-0.12em text-stone-400">Embedding</div>
                    <div v-if="overview?.embeddingRankings?.length" class="flex flex-col gap-2">
                      <div
                        v-for="(item, index) in overview.embeddingRankings"
                        :key="`emb-${item.userId}`"
                        class="ranking-item"
                      >
                        <span class="ranking-index">{{ index + 1 }}</span>
                        <div class="flex-1">
                          <div class="font-medium text-stone-700">{{ item.username }}</div>
                          <div class="text-xs text-stone-500">
                            {{ formatNumber(item.usedTokens) }} / {{ formatNumber(item.limitTokens) }} · {{ item.requestCount }} 次
                          </div>
                        </div>
                      </div>
                    </div>
                    <NEmpty v-else size="small" description="暂无数据" />
                  </div>
                </div>
              </NCard>
            </div>
          </div>
        </div>
      </NSpin>
    </NCard>
  </div>
</template>

<style scoped lang="scss">
.limit-card {
  @apply rounded-2xl border border-stone-200 bg-[linear-gradient(180deg,_rgba(255,255,255,0.98),_rgba(248,250,252,0.94))] p-4 shadow-sm;
}

.limit-title {
  @apply mb-4 text-sm font-semibold text-stone-700;
}

.limit-grid {
  @apply grid gap-3 sm:grid-cols-2;
}

.limit-label {
  @apply mb-2 text-xs font-semibold uppercase tracking-0.08em text-stone-400;
}

.summary-card {
  @apply rounded-2xl border border-stone-200 bg-[linear-gradient(180deg,_rgba(255,255,255,0.96),_rgba(248,250,252,0.92))] p-4 shadow-sm;
}

.summary-card.is-alert {
  @apply border-amber-200 bg-[linear-gradient(180deg,_rgba(255,251,235,0.98),_rgba(254,243,199,0.7))];
}

.summary-label {
  @apply text-xs font-semibold uppercase tracking-0.12em text-stone-400;
}

.summary-value {
  @apply mt-3 text-7 font-semibold text-stone-800;
}

.summary-sub {
  @apply mt-1 text-xs text-stone-500;
}

.overview-section {
  border-radius: 20px;
}

.alert-item {
  @apply rounded-2xl border border-stone-200 bg-stone-50 px-3 py-3;
}

.ranking-item {
  @apply flex items-center gap-3 rounded-2xl border border-stone-200 bg-stone-50 px-3 py-3;
}

.ranking-index {
  @apply inline-flex h-8 w-8 items-center justify-center rounded-full bg-stone-900 text-xs font-semibold text-white;
}
</style>
