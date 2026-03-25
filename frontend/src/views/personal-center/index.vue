<script setup lang="ts">
import { computed, onMounted, ref, h } from 'vue';
import { NTag } from 'naive-ui';
const { userInfo } = storeToRefs(useAuthStore());

const tags = ref<Api.OrgTag.Mine>({
  orgTags: [],
  primaryOrg: '',
  orgTagDetails: []
});

const usage = ref<Api.User.UsageSnapshot>({
  day: '',
  chatRequestCount: 0,
  llm: {
    enabled: false,
    usedTokens: 0,
    limitTokens: 0,
    remainingTokens: 0,
    requestCount: 0
  },
  embedding: {
    enabled: false,
    usedTokens: 0,
    limitTokens: 0,
    remainingTokens: 0,
    requestCount: 0
  }
});

const loading = ref(false);

// Token 记录相关变量
const tokenRecords = ref<Api.User.TokenRecord[]>([]);
const tokenRecordLoading = ref(false);
const pagination = ref({
  page: 1,
  pageSize: 10,
  total: 0,
  pageCount: 0
});
const getPersonalData = async () => {
  loading.value = true;
  const [{ error: orgError, data: orgData }, { error: usageError, data: usageData }] = await Promise.all([
    request<Api.OrgTag.Mine>({
      url: '/users/org-tags'
    }),
    request<Api.User.UsageSnapshot>({
      url: '/users/usage'
    })
  ]);

  if (!orgError) {
    tags.value = orgData;
  }

  if (!usageError) {
    usage.value = usageData;
  }

  // 获取 Token 记录
  getTokenRecords();

  loading.value = false;
};

const getOrgTags = async () => {
  const { error, data } = await request<Api.OrgTag.Mine>({
    url: '/users/org-tags'
  });
  if (!error) {
    tags.value = data;
  }
};

onMounted(() => {
  getPersonalData();
});

const visible = ref(false);
const currentTagId = ref('');
const showModal = (tagId: string) => {
  if (tagId === tags.value.primaryOrg) return;
  visible.value = true;
  currentTagId.value = tagId;
};
const submitLoading = ref(false);
const setPrimaryOrg = async () => {
  submitLoading.value = true;
  const { error } = await request({
    url: '/users/primary-org',
    method: 'PUT',
    data: { primaryOrg: currentTagId.value, userId: userInfo.value.id }
  });
  if (!error) {
    visible.value = false;
    getOrgTags();
  }
  submitLoading.value = false;
};

// Token 记录相关方法
const getTokenRecords = async () => {
  tokenRecordLoading.value = true;
  try {
    const { error, data } = await request({
      url: '/users/token-records',
      method: 'GET',
      params: {
        page: pagination.value.page - 1,
        size: pagination.value.pageSize
      }
    });

    if (!error && data) {
      tokenRecords.value = data.content || [];
      pagination.value.total = data.totalElements || 0;
      pagination.value.pageCount = data.totalPages || 0;
    }
  } finally {
    tokenRecordLoading.value = false;
  }
};

const handlePageChange = (page: number) => {
  pagination.value.page = page;
  getTokenRecords();
};

// Token 记录表格列定义
const tokenRecordColumns = computed(() => [
  {
    title: '日期',
    key: 'recordDate',
    width: 100,
    render: (row: Api.User.TokenRecord) => row.recordDate
  },
  {
    title: 'Token 类型',
    key: 'tokenType',
    width: 100,
    render: (row: Api.User.TokenRecord) => {
      const typeMap: Record<string, { text: string; type: any }> = {
        LLM: { text: 'LLM', type: 'info' },
        EMBEDDING: { text: 'Embedding', type: 'success' }
      };
      const type = typeMap[row.tokenType] || { text: row.tokenType, type: 'default' };
      return h(NTag, { type: type.type }, () => type.text);
    }
  },
  {
    title: '变动类型',
    key: 'changeType',
    width: 100,
    render: (row: Api.User.TokenRecord) => {
      const typeMap: Record<string, { text: string; type: any }> = {
        INCREASE: { text: '充值', type: 'success' },
        CONSUME: { text: '消耗', type: 'warning' }
      };
      const type = typeMap[row.changeType] || { text: row.changeType, type: 'default' };
      return h(NTag, { type: type.type }, () => type.text);
    }
  },
  {
    title: '变动数量',
    key: 'amount',
    width: 120,
    render: (row: Api.User.TokenRecord) => {
      const sign = row.changeType === 'INCREASE' ? '+' : '-';
      return `${sign}${row.amount.toLocaleString()}`;
    }
  },
  {
    title: '变动前余额',
    key: 'balanceBefore',
    width: 120,
    render: (row: Api.User.TokenRecord) => row.balanceBefore?.toLocaleString() || '-'
  },
  {
    title: '变动后余额',
    key: 'balanceAfter',
    width: 120,
    render: (row: Api.User.TokenRecord) => row.balanceAfter?.toLocaleString() || '-'
  },
  {
    title: '原因',
    key: 'reason',
    minWidth: 100,
    ellipsis: { tooltip: true },
    render: (row: Api.User.TokenRecord) => row.reason || '-'
  },
  {
    title: '请求次数',
    key: 'requestCount',
    width: 80,
    render: (row: Api.User.TokenRecord) => row.requestCount?.toLocaleString() || '0'
  },
  {
    title: '创建时间',
    key: 'createdAt',
    width: 180,
    render: (row: Api.User.TokenRecord) => new Date(row.createdAt).toLocaleString('zh-CN')
  }
]);
</script>

<template>
  <NSpin :show="loading">
    <div class="flex-cc">
      <NCard class="min-h-400px min-w-600px w-70vw card-wrapper" :segmented="{ content: true, footer: 'soft' }">
        <template #header>
          <div class="flex items-center gap-4">
            <NAvatar size="large">
              <icon-solar:user-circle-linear class="text-icon-large" />
            </NAvatar>
            <div class="flex flex-col gap-1">
              <div>{{ userInfo.username }}</div>
              <span class="text-xs text-stone-400">今日额度 · {{ usage.day || '未统计' }}</span>
            </div>
          </div>
        </template>
        <NScrollbar class="max-h-60vh">
          <div class="flex flex-col gap-4 p-4">
            <div class="grid gap-4 md:grid-cols-2">
              <NCard size="small" embedded class="quota-card">
                <div class="text-sm font-semibold text-stone-700">LLM Token</div>
                <div v-if="usage.llm.enabled" class="mt-3 flex flex-col gap-2 text-sm text-stone-500">
                  <div>已用 {{ usage.llm.usedTokens.toLocaleString() }} / {{ usage.llm.limitTokens.toLocaleString() }}</div>
                  <div>剩余 {{ usage.llm.remainingTokens.toLocaleString() }}</div>
                  <div>请求 {{ usage.llm.requestCount.toLocaleString() }} 次</div>
                </div>
                <div v-else class="mt-3 text-sm text-stone-400">当前未启用配额</div>
              </NCard>
              <NCard size="small" embedded class="quota-card">
                <div class="text-sm font-semibold text-stone-700">Embedding Token</div>
                <div v-if="usage.embedding.enabled" class="mt-3 flex flex-col gap-2 text-sm text-stone-500">
                  <div>已用 {{ usage.embedding.usedTokens.toLocaleString() }} / {{ usage.embedding.limitTokens.toLocaleString() }}</div>
                  <div>剩余 {{ usage.embedding.remainingTokens.toLocaleString() }}</div>
                  <div>请求 {{ usage.embedding.requestCount.toLocaleString() }} 次</div>
                </div>
                <div v-else class="mt-3 text-sm text-stone-400">当前未启用配额</div>
              </NCard>
            </div>

            <div class="flex flex-wrap gap-4">
            <NCard
              v-for="tag in tags.orgTagDetails"
              :key="tag.tagId"
              size="small"
              embedded
              hoverable
              class="w-[calc((100%-32px)/3)]"
              :segmented="{ content: true, footer: 'soft' }"
              @click="showModal(tag.tagId)"
            >
              <div class="flex items-center justify-between">
                <div>{{ tag.name }}</div>
                <NTag v-if="tag.tagId === tags.primaryOrg" type="primary" size="small">
                  主标签
                  <template #icon>
                    <icon-solar:verified-check-bold-duotone class="text-icon" />
                  </template>
                </NTag>
              </div>
              <template #footer>
                <NEllipsis :line-clamp="3">{{ tag.description }}</NEllipsis>
              </template>
            </NCard>
            </div>
          </div>
        </NScrollbar>
        <template #footer>
          <div class="flex flex-col gap-4">
            <NDivider>Token 变动记录</NDivider>
            <NSpin :show="tokenRecordLoading">
              <NDataTable
                v-if="tokenRecords.length > 0"
                :columns="tokenRecordColumns"
                :data="tokenRecords"
                :loading="tokenRecordLoading"
                :pagination="{
                  page: pagination.page,
                  pageSize: pagination.pageSize,
                  itemCount: pagination.total,
                  onChange: handlePageChange
                }"
                :scroll-x="1200"
                size="small"
              />
              <NEmpty v-else description="暂无记录" />
            </NSpin>
          </div>
        </template>
      </NCard>

      <NModal
        v-model:show="visible"
        :loading="submitLoading"
        preset="dialog"
        title="设置主标签"
        content="确定将当前标签设置为主标签吗？"
        positive-text="确认"
        negative-text="取消"
        @positive-click="setPrimaryOrg"
        @negative-click="visible = false"
      />
    </div>
  </NSpin>
</template>

<style scoped lang="scss">
:deep(.n-card__content) {
  flex: none m !important;
  height: fit-content;
}

.quota-card {
  border-radius: 16px;
}
</style>
