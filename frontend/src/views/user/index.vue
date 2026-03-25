<script setup lang="tsx">
import { ref } from 'vue';
import { NButton, NTag } from 'naive-ui';
import UserSearch from './modules/user-search.vue';
import OrgTagSettingDialog from './modules/org-tag-setting-dialog.vue';

const appStore = useAppStore();

function apiFn(params: Api.User.SearchParams) {
  return request<Api.User.List>({ url: '/admin/users/list', params });
}

const { columns, columnChecks, data, getData, loading, mobilePagination, searchParams, resetSearchParams } = useTable({
  apiFn,
  apiParams: {
    keyword: null,
    orgTag: null,
    status: null
  },
  columns: () => [
    {
      key: 'index',
      title: '序号',
      width: 64
    },
    {
      key: 'username',
      title: '用户名',
      minWidth: 100
    },
    {
      key: 'orgTags',
      title: '标签',
      render: row => (
        <div class="flex flex-wrap gap-2">
          {row.orgTags.map(tag => (
            <NTag key={tag.tagId} type={tag.tagId === row.primaryOrg ? 'primary' : 'default'}>
              {tag.name}
            </NTag>
          ))}
        </div>
      )
    },
    {
      key: 'status',
      title: '是否启用',
      width: 100,
      render: row => <NTag type={row.status ? 'success' : 'warning'}>{row.status ? '已启用' : '已禁用'}</NTag>
    },
    {
      key: 'createdAt',
      title: '创建时间',
      width: 200,
      render: row => dayjs(row.createdAt).format('YYYY-MM-DD HH:mm:ss')
    },
    {
      key: 'chatUsage',
      title: '聊天次数',
      width: 130,
      render: row => (
        <div class="flex flex-col gap-1 text-xs">
          <span>{Number(row.usage?.chatRequestCount || 0).toLocaleString()} 次</span>
          <span class="text-stone-400">今日消息数</span>
        </div>
      )
    },
    {
      key: 'llmUsage',
      title: 'LLM额度',
      width: 220,
      render: row => {
        const quota = row.usage?.llm;
        if (!quota?.enabled) {
          return <span class="text-stone-400">未启用</span>;
        }
        return (
          <div class="flex flex-col gap-1 text-xs">
            <span>{Number(quota.usedTokens || 0).toLocaleString()} / {Number(quota.limitTokens || 0).toLocaleString()}</span>
            <span class="text-stone-400">剩余 {Number(quota.remainingTokens || 0).toLocaleString()} · {quota.requestCount} 次</span>
          </div>
        );
      }
    },
    {
      key: 'embeddingUsage',
      title: 'Embedding额度',
      width: 220,
      render: row => {
        const quota = row.usage?.embedding;
        if (!quota?.enabled) {
          return <span class="text-stone-400">未启用</span>;
        }
        return (
          <div class="flex flex-col gap-1 text-xs">
            <span>{Number(quota.usedTokens || 0).toLocaleString()} / {Number(quota.limitTokens || 0).toLocaleString()}</span>
            <span class="text-stone-400">剩余 {Number(quota.remainingTokens || 0).toLocaleString()} · {quota.requestCount} 次</span>
          </div>
        );
      }
    },
    {
      key: 'operate',
      title: '操作',
      width: 130,
      render: row => (
        <NButton type="primary" ghost size="small" onClick={() => handleOrgTag(row)}>
          分配组织标签
        </NButton>
      )
    }
  ]
});

const visible = ref(false);
const editingData = ref<Api.User.Item | null>(null);

function handleOrgTag(row: Api.User.Item) {
  editingData.value = row;
  visible.value = true;
}
</script>

<template>
  <div class="min-h-500px flex-col-stretch gap-16px overflow-auto">
    <Teleport defer to="#header-extra">
      <UserSearch v-model:model="searchParams" @reset="resetSearchParams" @search="getData" />
    </Teleport>

    <NCard title="用户列表" :bordered="false" size="small" class="sm:flex-1-hidden card-wrapper">
      <template #header-extra>
        <TableHeaderOperation v-model:columns="columnChecks" :addable="false" :loading="loading" @refresh="getData" />
      </template>
      <NDataTable
        :columns="columns"
        :data="data"
        size="small"
        :flex-height="!appStore.isMobile"
        :scroll-x="1400"
        :loading="loading"
        remote
        :row-key="row => row.userId"
        :pagination="mobilePagination"
        class="sm:h-full"
      />
    </NCard>

    <OrgTagSettingDialog v-model:visible="visible" :row-data="editingData!" @submitted="getData" />
  </div>
</template>

<style scoped lang="scss"></style>
