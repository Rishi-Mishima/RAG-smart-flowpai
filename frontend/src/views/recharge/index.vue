<script setup lang="ts">
import { computed, onMounted, ref, h } from 'vue';
import type { DataTableColumns } from 'naive-ui';
import { NCard, NButton, NInputNumber, NModal, NSpace, NTag, NEmpty, NSpin, NDataTable, NTabs, NTabPane } from 'naive-ui';
import QRCode from 'qrcode.vue';
import { fetchRechargePackages, fetchCreateRechargeOrder, fetchRechargeOrderDetail, fetchRechargeOrders } from '@/service/api';
import { useAuthStore } from '@/store/modules/auth';

const TOKEN_UNIT = 10000;

const authStore = useAuthStore();
const loading = ref(false);
const packages = ref<Api.Recharge.Package[]>([]);
const selectedPackageId = ref<number | null>(null);
const customAmountYuan = ref<number | null>(null); // 自定义充值金额（元）
const isCustomAmount = ref(false);

// 充值记录相关
const orderListLoading = ref(false);
const orders = ref<Api.Recharge.Order[]>([]);
const activeTab = ref<string>('all');

// 支付弹窗相关
const showPayModal = ref(false);
const payLoading = ref(false);
const orderInfo = ref<Api.Recharge.OrderInfo | null>(null);
const currentTradeNo = ref<string>('');

// 获取当前订单金额（分）
const getOrderAmount = computed(() => {
  if (isCustomAmount.value && customAmountYuan.value) {
    return Math.round(customAmountYuan.value * 100);
  }
  if (selectedPackageId.value) {
    const pkg = packages.value.find(p => p.id === selectedPackageId.value);
    return pkg?.packagePrice || 0;
  }
  return 0;
});

// 验证自定义充值金额是否有效
const isValidCustomAmount = computed(() => {
  if (!isCustomAmount.value || !customAmountYuan.value) return false;
  return customAmountYuan.value >= 0.01;
});

function formatTokenWan(value: number) {
  const wan = value / TOKEN_UNIT;
  const hasDecimal = Math.abs(wan - Math.round(wan)) > 0.000001;
  const formatted = wan.toLocaleString('zh-CN', {
    minimumFractionDigits: hasDecimal ? 2 : 0,
    maximumFractionDigits: hasDecimal ? 2 : 0
  });
  return `${formatted} 万`;
}

function packageBenefitLines(content?: string | null) {
  return (content || '')
    .split('\n')
    .map(item => item.trim())
    .filter(Boolean);
}

// 获取充值套餐列表
const getPackages = async () => {
  loading.value = true;
  const { error, data } = await fetchRechargePackages();
  if (!error && data) {
    packages.value = data;
  }
  loading.value = false;
};

// 获取充值记录列表
const getOrders = async () => {
  orderListLoading.value = true;
  try {
    const status = activeTab.value === 'all' ? undefined : activeTab.value as any;
    const { error, data } = await fetchRechargeOrders(status);
    if (!error && data) {
      orders.value = data;
    }
  } finally {
    orderListLoading.value = false;
  }
};

// 切换标签页时刷新订单列表
const handleTabChange = (tab: string) => {
  activeTab.value = tab;
  getOrders();
};

// 选择套餐
const selectPackage = (packageId: number) => {
  selectedPackageId.value = packageId;
  isCustomAmount.value = false;
  customAmountYuan.value = null;
};

// 选择自定义金额
const selectCustomAmount = () => {
  isCustomAmount.value = true;
  selectedPackageId.value = null;
};

// 创建订单
const createOrder = async () => {
  if (!isCustomAmount.value && !selectedPackageId.value) {
    window.$message?.warning('请选择充值套餐或输入充值金额');
    return;
  }

  if (isCustomAmount.value && !isValidCustomAmount.value) {
    window.$message?.warning('请输入有效的充值金额，至少 1 元');
    return;
  }

  payLoading.value = true;
  try {
    const { error, data } = await fetchCreateRechargeOrder(
      isCustomAmount.value ? undefined : selectedPackageId.value || undefined,
      isCustomAmount.value ? getOrderAmount.value : undefined
    );

    if (!error && data) {
      orderInfo.value = data;
      currentTradeNo.value = data.outTradeNo;
      showPayModal.value = true;
      window.$message?.success('订单创建成功，请扫码支付');
    }
  } finally {
    payLoading.value = false;
  }
};

// 检查支付状态
const checkPayStatus = async () => {
  if (!currentTradeNo.value) return;

  try {
    const { error, data } = await fetchRechargeOrderDetail(currentTradeNo.value);
    if (!error && data) {
      if (data.status === 'SUCCEED') {
        window.$message?.success('充值成功！');
        showPayModal.value = false;
        // 刷新用户信息
        await authStore.initUserInfo();
        // 刷新订单列表
        getOrders();
      } else if (data.status === 'NOT_PAY' || data.status === 'PAYING') {
        window.$message?.info('尚未支付，请继续扫码支付');
      } else {
        window.$message?.error('支付失败，请重试');
      }
    }
  } catch (e) {
    window.$message?.error('查询支付状态失败');
  }
};

// 定义订单表格列
const orderColumns = computed<DataTableColumns<Api.Recharge.Order>>(() => [
  {
    key: 'tradeNo',
    title: '订单号',
    width: 200,
    ellipsis: {
      tooltip: true
    }
  },
  {
    key: 'description',
    title: '充值说明',
    minWidth: 200,
    ellipsis: {
      tooltip: true
    }
  },
  {
    key: 'amount',
    title: '充值金额',
    width: 120,
    render: row => `¥${(row.amount / 100).toFixed(2)}`
  },
  {
    key: 'llmToken',
    title: 'LLM Token（万）',
    width: 120,
    render: row => formatTokenWan(row.llmToken)
  },
  {
    key: 'embeddingToken',
    title: 'Embedding Token（万）',
    width: 140,
    render: row => formatTokenWan(row.embeddingToken)
  },
  {
    key: 'status',
    title: '状态',
    width: 100,
    render: row => {
      const statusMap: Record<string, { text: string; type: any }> = {
        NOT_PAY: { text: '未支付', type: 'default' },
        PAYING: { text: '支付中', type: 'warning' },
        SUCCEED: { text: '支付成功', type: 'success' },
        FAIL: { text: '支付失败', type: 'error' },
        CANCELLED: { text: '已取消', type: 'default' }
      };
      const status = statusMap[row.status] || { text: '未知', type: 'default' };
      return h(NTag, { type: status.type }, () => status.text);
    }
  },
  {
    key: 'payTime',
    title: '支付时间',
    width: 180,
    render: row => row.payTime ? new Date(row.payTime).toLocaleString('zh-CN') : '-'
  },
  {
    key: 'createdAt',
    title: '创建时间',
    width: 180,
    render: row => new Date(row.createdAt).toLocaleString('zh-CN')
  }
]);

onMounted(() => {
  getPackages();
  getOrders();
});
</script>

<template>
  <div class="min-h-500px flex-col-stretch gap-16px overflow-auto">
    <NCard title="余额充值" :bordered="false" size="small" class="card-wrapper">
    <!-- 余额充值区域 -->
      <template #header-extra>
        <NTag type="primary">微信支付</NTag>
      </template>

      <NSpin :show="loading">
        <div v-if="packages.length > 0" class="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
          <!-- 充值套餐卡片 -->
          <NCard
            v-for="pkg in packages"
            :key="pkg.id"
            hoverable
            class="cursor-pointer transition-all duration-300"
            :class="{ 'border-primary border-2': selectedPackageId === pkg.id && !isCustomAmount }"
            @click="selectPackage(pkg.id)"
          >
            <div class="flex flex-col gap-4">
              <div class="flex items-center justify-between">
                <h3 class="text-xl font-bold">{{ pkg.packageName }}</h3>
                <NTag v-if="pkg.enabled" type="success" size="small">热销</NTag>
              </div>

              <div class="flex items-baseline gap-2">
                <span class="text-3xl font-bold text-primary">
                  ¥{{ (pkg.packagePrice / 100).toFixed(2) }}
                </span>
                <span class="text-sm text-stone-500">原价 ¥{{ (pkg.packagePrice / 100).toFixed(2) }}</span>
              </div>

              <div class="flex flex-col gap-2 text-sm">
                <div class="flex items-center gap-2">
                  <icon-solar:like-bold-duotone class="text-primary" />
                  <span>LLM Token: <strong>{{ formatTokenWan(pkg.llmToken) }}</strong></span>
                </div>
                <div class="flex items-center gap-2">
                  <icon-solar:like-bold-duotone class="text-primary" />
                  <span>Embedding Token: <strong>{{ formatTokenWan(pkg.embeddingToken) }}</strong></span>
                </div>
              </div>

              <NEllipsis :line-clamp="2" class="text-sm text-stone-500">
                {{ pkg.packageDesc }}
              </NEllipsis>

              <div v-if="packageBenefitLines(pkg.packageBenefit).length" class="flex flex-col gap-2 rounded-lg bg-stone-50 p-3 text-sm text-stone-600">
                <div
                  v-for="line in packageBenefitLines(pkg.packageBenefit)"
                  :key="`${pkg.id}-${line}`"
                  class="flex items-start gap-2"
                >
                  <span class="mt-1 h-1.5 w-1.5 rounded-full bg-primary"></span>
                  <span>{{ line }}</span>
                </div>
              </div>

              <NButton
                v-if="selectedPackageId === pkg.id && !isCustomAmount"
                type="primary"
                block
                size="large"
                @click.stop="createOrder"
              >
                立即充值
              </NButton>
              <NButton v-else secondary block size="large">
                选择套餐
              </NButton>
            </div>
          </NCard>

          <!-- 自定义充值卡片 -->
          <NCard
            hoverable
            class="cursor-pointer transition-all duration-300"
            :class="{ 'border-primary border-2': isCustomAmount }"
            @click="selectCustomAmount"
          >
            <div class="flex flex-col gap-4">
              <h3 class="text-xl font-bold">自定义充值</h3>

              <div class="flex items-baseline gap-2">
                <span class="text-3xl font-bold text-primary">灵活充值</span>
              </div>

              <div class="flex flex-col gap-2 text-sm text-stone-500">
                <div>• 充值金额不限</div>
                <div>• 按比例赠送 Token</div>
                <div>• 随时充值随时用</div>
              </div>

              <div v-if="isCustomAmount" class="mt-4" @click.stop>
                <NInputNumber
                  v-model:value="customAmountYuan"
                  :min="0.01"
                  :step="1"
                  :precision="2"
                  placeholder="请输入充值金额（元）"
                  class="w-full"
                  size="large"
                  type="text"
                />
                <div class="mt-2 text-xs text-stone-400">
                  建议至少充值 1 元，自动转换为分进行支付
                </div>
              </div>

              <NButton
                v-if="isCustomAmount && isValidCustomAmount"
                type="primary"
                block
                size="large"
                @click.stop="createOrder"
              >
                立即充值 ¥{{ customAmountYuan }}
              </NButton>
              <NButton v-else secondary block size="large">
                自定义金额
              </NButton>
            </div>
          </NCard>
        </div>

        <NEmpty v-else description="暂无充值套餐" />
      </NSpin>
    </NCard>

    <!-- 充值记录列表 -->
    <NCard title="充值记录" :bordered="false" size="large" class="w-full card-wrapper">
      <NTabs v-model:value="activeTab" @update:value="handleTabChange">
        <NTabPane name="all" tab="全部订单" />
        <NTabPane name="NOT_PAY" tab="未支付" />
        <NTabPane name="PAYING" tab="支付中" />
        <NTabPane name="SUCCEED" tab="支付成功" />
        <NTabPane name="FAIL" tab="支付失败" />
      </NTabs>

      <NSpin :show="orderListLoading">
        <NDataTable
          v-if="orders.length > 0"
          :columns="orderColumns"
          :data="orders"
          :loading="orderListLoading"
          :scroll-x="1200"
          class="mt-4"
        />
        <NEmpty v-else description="暂无充值记录" />
      </NSpin>
    </NCard>

    <!-- 支付二维码弹窗 -->
    <NModal
      v-model:show="showPayModal"
      preset="dialog"
      title="扫码支付"
      :show-icon="false"
      class="w-480px"
    >
      <div class="flex flex-col items-center gap-6 py-6">
        <div v-if="orderInfo" class="flex flex-col items-center gap-4">
          <div class="rounded-lg border-2 border-primary bg-white p-6">
            <QRCode
              :value="orderInfo.prePayId"
              :size="240"
              :level="'H'"
              include-margin
              render-as="svg"
              foreground="#000000"
              background="#ffffff"
            />
          </div>
          <div class="text-center">
            <p class="text-lg font-semibold">请使用微信扫码支付</p>
            <p class="mt-2 text-sm text-stone-500">
              订单号：{{ currentTradeNo }}
            </p>
            <p class="mt-1 text-sm text-stone-500">
              支付金额：¥{{ (getOrderAmount / 100).toFixed(2) }}
            </p>
          </div>
        </div>

        <NSpace vertical class="w-full px-8">
          <NButton type="primary" size="large" block @click="checkPayStatus">
            我已完成支付
          </NButton>
        </NSpace>
      </div>
    </NModal>
  </div>
</template>

<style scoped lang="scss">
.card-wrapper {
  :deep(.n-card__content) {
    padding: 24px;
  }
}
</style>
