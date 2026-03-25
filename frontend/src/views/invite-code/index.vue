<script setup lang="tsx">
import type { DataTableColumns, DataTableRowKey, FormRules, PaginationProps, SelectOption } from 'naive-ui';
import { NButton, NInput, NInputNumber, NPopconfirm, NTag } from 'naive-ui';
import { buildInviteCodeShareMessage } from '@/constants/invite-channel';
import {
  fetchCreateInviteCode,
  fetchDeleteInviteCode,
  fetchDisableInviteCode,
  fetchGetInviteCodeList,
  fetchUpdateInviteCode
} from '@/service/api';

const appStore = useAppStore();

interface InviteCodeFormModel {
  code: string;
  count: number | null;
  maxUses: number | null;
}

const enabledOptions: SelectOption[] = [
  { label: '全部状态', value: 'all' },
  { label: '仅启用', value: 'enabled' },
  { label: '仅禁用', value: 'disabled' }
];

const loading = ref(false);
const data = ref<Api.InviteCode.Item[]>([]);
const checkedRowKeys = ref<number[]>([]);

const filterStatus = ref<'all' | 'enabled' | 'disabled'>('all');
const visible = ref(false);
const submitting = ref(false);
const editingId = ref<number | null>(null);
const { formRef, validate, restoreValidation } = useNaiveForm();
const { defaultRequiredRule } = useFormRules();

const model = ref<InviteCodeFormModel>(createDefaultModel());
const isEditing = computed(() => editingId.value !== null);

const rules = ref<FormRules>({
  code: [
    {
      validator(_, value: string) {
        return !isEditing.value || Boolean(value?.trim());
      },
      message: '编辑时邀请码不能为空',
      trigger: ['blur', 'input']
    },
    {
      validator(_, value: string) {
        return !(value?.trim() && Number(model.value.count) > 1);
      },
      message: '批量创建时不能指定自定义邀请码',
      trigger: 'blur'
    }
  ],
  count: [
    defaultRequiredRule,
    {
      validator(_, value) {
        return Number.isInteger(value) && value > 0 && value <= 100;
      },
      message: '批量数量必须是 1 到 100 的整数',
      trigger: 'change'
    }
  ],
  maxUses: [
    defaultRequiredRule,
    {
      validator(_, value) {
        return Number.isInteger(value) && value > 0;
      },
      message: '最大使用次数必须是大于 0 的整数',
      trigger: 'change'
    }
  ]
});

const pagination = reactive<PaginationProps>({
  page: 1,
  pageSize: 10,
  itemCount: 0,
  showSizePicker: true,
  pageSizes: [10, 15, 20, 25, 30],
  onUpdatePage: async (page: number) => {
    pagination.page = page;
    await getData();
  },
  onUpdatePageSize: async (pageSize: number) => {
    pagination.pageSize = pageSize;
    pagination.page = 1;
    await getData();
  }
});

const mobilePagination = computed(() => ({
  ...pagination,
  pageSlot: appStore.isMobile ? 3 : 9
}));

const hasCheckedRows = computed(() => checkedRowKeys.value.length > 0);

const inviteShareBaseUrl = 'https://smart.paicoding.com/#/login/register';

const columns = computed<DataTableColumns<Api.InviteCode.Item>>(() => [
  {
    type: 'selection'
  },
  {
    key: 'index',
    title: '序号',
    width: 64,
    render: (_, index) => (Number(pagination.page) - 1) * Number(pagination.pageSize) + index + 1
  },
  {
    key: 'code',
    title: '邀请码',
    minWidth: 240,
    render: row => (
      <div class="min-w-0">
        <div class="truncate text-3.5 leading-5 font-mono">{row.code}</div>
        <div class="mt-2 flex flex-wrap gap-2">
          <NButton
            size="tiny"
            quaternary
            onClick={() => {
              navigator.clipboard.writeText(row.code);
              window.$message?.success('邀请码已复制');
            }}
          >
            复制
          </NButton>
          <NButton
            size="tiny"
            quaternary
            onClick={() => {
              navigator.clipboard.writeText(createInviteShareLink(row.code));
              window.$message?.success('注册链接已复制');
            }}
          >
            复制链接
          </NButton>
          <NButton
            size="tiny"
            quaternary
            onClick={() => {
              navigator.clipboard.writeText(buildInviteCodeShareMessage(createInviteShareLink(row.code), row.code));
              window.$message?.success('邀请话术已复制');
            }}
          >
            复制话术
          </NButton>
        </div>
      </div>
    )
  },
  {
    key: 'usage',
    title: '使用情况',
    width: 92,
    render: row => `${row.usedCount}/${row.maxUses}`
  },
  {
    key: 'remaining',
    title: '剩余',
    width: 76,
    render: row => Math.max(0, row.maxUses - row.usedCount)
  },
  {
    key: 'enabled',
    title: '状态',
    width: 84,
    render: row => <NTag type={row.enabled ? 'success' : 'default'}>{row.enabled ? '已启用' : '已禁用'}</NTag>
  },
  {
    key: 'availability',
    title: '可用性',
    width: 92,
    render: row => {
      if (!row.enabled) return <NTag type="default">不可用</NTag>;
      if (row.usedCount >= row.maxUses) return <NTag type="error">已耗尽</NTag>;
      return <NTag type="success">可使用</NTag>;
    }
  },
  {
    key: 'createdBy',
    title: '创建人',
    width: 88,
    render: row => row.createdBy?.username || '-'
  },
  {
    key: 'createdAt',
    title: '创建日期',
    width: 120,
    render: row => dayjs(row.createdAt).format('YYYY-MM-DD')
  },
  {
    key: 'operate',
    title: '操作',
    width: 260,
    render: row => (
      <div class="flex items-center gap-2">
        {row.usedCount === 0 ? (
          <NButton type="primary" ghost size="small" onClick={() => openEditDialog(row)}>
            编辑
          </NButton>
        ) : null}
        {row.enabled ? (
          <NPopconfirm onPositiveClick={() => handleDisable(row.id)}>
            {{
              default: () => '禁用后该邀请码将无法继续使用，确认继续吗？',
              trigger: () => (
                <NButton type="warning" ghost size="small">
                  禁用
                </NButton>
              )
            }}
          </NPopconfirm>
        ) : (
          <NTag type="default">已停用</NTag>
        )}
        <NPopconfirm onPositiveClick={() => handleDelete(row.id)}>
          {{
            default: () => '删除后无法恢复，确认删除该邀请码吗？',
            trigger: () => (
              <NButton type="error" ghost size="small">
                删除
              </NButton>
            )
          }}
        </NPopconfirm>
      </div>
    )
  }
]);

function createDefaultModel(): InviteCodeFormModel {
  return {
    code: '',
    count: 1,
    maxUses: 1
  };
}

function createInviteShareLink(code: string) {
  return `${inviteShareBaseUrl}?inviteCode=${encodeURIComponent(code)}`;
}

async function getData() {
  loading.value = true;

  const { data: payload, error } = await fetchGetInviteCodeList({
    page: Number(pagination.page),
    size: Number(pagination.pageSize),
    enabled: filterStatus.value === 'all' ? null : filterStatus.value === 'enabled'
  });

  if (!error && payload) {
    data.value = payload.records || [];
    pagination.page = payload.current || Number(pagination.page);
    pagination.pageSize = payload.size || Number(pagination.pageSize);
    pagination.itemCount = payload.total || 0;
    checkedRowKeys.value = [];
  }

  loading.value = false;
}

function handleCheck(rowKeys: DataTableRowKey[]) {
  checkedRowKeys.value = rowKeys.map(Number);
}

async function handleFilterChange(value: 'all' | 'enabled' | 'disabled') {
  filterStatus.value = value;
  pagination.page = 1;
  await getData();
}

function openCreateDialog() {
  editingId.value = null;
  model.value = createDefaultModel();
  visible.value = true;
  nextTick(() => {
    restoreValidation();
  });
}

function openEditDialog(row: Api.InviteCode.Item) {
  editingId.value = row.id;
  model.value = {
    code: row.code,
    count: 1,
    maxUses: row.maxUses
  };
  visible.value = true;
  nextTick(() => {
    restoreValidation();
  });
}

function closeDialog() {
  visible.value = false;
  editingId.value = null;
}

async function handleCreate() {
  await validate();

  submitting.value = true;
  const payload = {
    code: model.value.code.trim(),
    maxUses: Number(model.value.maxUses),
    expiresAt: null
  };
  const { error } = isEditing.value
    ? await fetchUpdateInviteCode(editingId.value!, payload)
    : await fetchCreateInviteCode({
        ...payload,
        code: payload.code || undefined,
        count: Number(model.value.count)
      });

  if (!error) {
    window.$message?.success(isEditing.value ? '邀请码已更新' : `已创建 ${Number(model.value.count)} 个邀请码`);
    closeDialog();
    await getData();
  }

  submitting.value = false;
}

async function handleDisable(id: number) {
  const { error } = await fetchDisableInviteCode(id);

  if (!error) {
    window.$message?.success('邀请码已禁用');
    await getData();
  }
}

async function handleDelete(id: number) {
  const { error } = await fetchDeleteInviteCode(id);

  if (!error) {
    window.$message?.success('邀请码已删除');

    if (data.value.length === 1 && Number(pagination.page) > 1) {
      pagination.page = Number(pagination.page) - 1;
    }

    await getData();
  }
}

async function handleBatchDelete() {
  const ids = [...checkedRowKeys.value];

  if (ids.length === 0) return;

  const results = await Promise.all(ids.map(async id => ({ id, ...(await fetchDeleteInviteCode(id)) })));
  const successCount = results.filter(item => !item.error).length;
  const failedCount = ids.length - successCount;

  if (successCount > 0) {
    window.$message?.success(
      failedCount > 0 ? `已删除 ${successCount} 个邀请码，${failedCount} 个删除失败` : `已删除 ${successCount} 个邀请码`
    );

    if (successCount === data.value.length && Number(pagination.page) > 1) {
      pagination.page = Number(pagination.page) - 1;
    }

    await getData();
    return;
  }

  window.$message?.error('批量删除失败，请稍后重试');
}

onMounted(() => {
  getData();
});
</script>

<template>
  <div class="min-h-500px flex-col-stretch gap-16px overflow-auto">
    <NCard
      title="邀请码管理"
      :bordered="false"
      size="small"
      class="sm:flex-1-hidden card-wrapper"
      content-class="flex-col-stretch min-h-0 sm:h-full"
    >
      <template #header-extra>
        <NSpace :size="12" align="center" wrap>
          <NSelect
            v-model:value="filterStatus"
            :options="enabledOptions"
            class="w-140px"
            @update:value="handleFilterChange"
          />
          <NButton type="primary" @click="openCreateDialog">创建邀请码</NButton>
          <NPopconfirm @positive-click="handleBatchDelete">
            <template #trigger>
              <NButton type="error" ghost :disabled="!hasCheckedRows || loading">批量删除</NButton>
            </template>
            已选择 {{ checkedRowKeys.length }} 个邀请码，确认删除吗？
          </NPopconfirm>
          <NButton @click="getData">刷新</NButton>
        </NSpace>
      </template>

      <div class="mb-4 text-13px text-#8a6b43">
        邀请码留空时，后端会自动生成 16 位随机码；批量创建时会连续生成多条随机邀请码，默认长期有效。
      </div>

      <div class="min-h-0 sm:flex-1">
        <NDataTable
          :columns="columns"
          :data="data"
          size="small"
          :flex-height="!appStore.isMobile"
          :scroll-x="1100"
          :loading="loading"
          remote
          :row-key="row => row.id"
          :pagination="mobilePagination"
          :checked-row-keys="checkedRowKeys"
          class="sm:h-full"
          @update:checked-row-keys="handleCheck"
        />
      </div>
    </NCard>

    <NModal
      v-model:show="visible"
      preset="dialog"
      :title="isEditing ? '编辑邀请码' : '创建邀请码'"
      :show-icon="false"
      :mask-closable="false"
      class="w-520px!"
    >
      <NForm ref="formRef" :model="model" :rules="rules" label-placement="left" :label-width="110" mt-10>
        <NFormItem label="邀请码" path="code">
          <NInput
            v-model:value="model.code"
            :placeholder="isEditing ? '请输入邀请码' : '单条创建可自定义，批量创建时请留空'"
            maxlength="64"
            clearable
          />
        </NFormItem>
        <NFormItem v-if="!isEditing" label="批量数量" path="count">
          <NInputNumber
            v-model:value="model.count"
            class="w-full"
            :min="1"
            :max="100"
            :precision="0"
            placeholder="默认 1"
          />
        </NFormItem>
        <NFormItem label="最大使用次数" path="maxUses">
          <NInputNumber v-model:value="model.maxUses" class="w-full" :min="1" :precision="0" placeholder="请输入次数" />
        </NFormItem>
      </NForm>

      <template #action>
        <NSpace :size="12">
          <NButton @click="closeDialog">取消</NButton>
          <NButton type="primary" :loading="submitting" @click="handleCreate">
            {{ isEditing ? '保存' : '创建' }}
          </NButton>
        </NSpace>
      </template>
    </NModal>
  </div>
</template>

<style scoped></style>
