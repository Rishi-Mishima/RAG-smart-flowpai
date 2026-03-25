<script setup lang="tsx">
import type { DataTableColumns, FormRules, PaginationProps } from 'naive-ui';
import {
  NButton,
  NInput,
  NInputNumber,
  NSwitch,
  NTag,
  NPopconfirm,
  NModal,
  NForm,
  NFormItem,
  NEllipsis,
  NCard
} from 'naive-ui';
import { ref, reactive, computed, onMounted } from 'vue';
import { request } from '@/service/request';

const TOKEN_UNIT = 10000;

interface RechargePackage {
  id: number;
  packageName: string;
  packagePrice: number;
  packageDesc: string | null;
  packageBenefit: string | null;
  llmToken: number;
  embeddingToken: number;
  enabled: boolean;
  deleted: boolean;
  sortOrder: number;
  createdAt: string;
  updatedAt: string;
}

interface RechargePackageForm {
  packageName: string;
  packagePriceYuan: number | null; // 套餐价格（元）
  packageDesc: string;
  packageBenefit: string;
  llmTokenWan: number | null;
  embeddingTokenWan: number | null;
  enabled: boolean;
  sortOrder: number | null;
}

const loading = ref(false);
const data = ref<RechargePackage[]>([]);
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

const visible = ref(false);
const submitting = ref(false);
const editingId = ref<number | null>(null);
const { formRef, validate, restoreValidation } = useNaiveForm();
const { defaultRequiredRule } = useFormRules();

const model = ref<RechargePackageForm>(createDefaultModel());

const rules = ref<FormRules>({
  packageName: [defaultRequiredRule],
  packagePriceYuan: [
    defaultRequiredRule,
    {
      validator(_, value) {
        return value !== null && value > 0;
      },
      message: '套餐价格必须大于 0',
      trigger: ['blur', 'change']
    }
  ],
  llmTokenWan: [
    defaultRequiredRule,
    {
      validator(_, value) {
        return value !== null && value >= 0;
      },
      message: 'LLM Token 数量不能为负数',
      trigger: ['blur', 'change']
    }
  ],
  embeddingTokenWan: [
    defaultRequiredRule,
    {
      validator(_, value) {
        return value !== null && value >= 0;
      },
      message: 'Embedding Token 数量不能为负数',
      trigger: ['blur', 'change']
    }
  ]
});

function createDefaultModel(): RechargePackageForm {
  return {
    packageName: '',
    packagePriceYuan: null,
    packageDesc: '',
    packageBenefit: '',
    llmTokenWan: null,
    embeddingTokenWan: null,
    enabled: true,
    sortOrder: 0
  };
}

function formatWanValue(value: number) {
  const wan = value / TOKEN_UNIT;
  const hasDecimal = Math.abs(wan - Math.round(wan)) > 0.000001;
  return wan.toLocaleString('zh-CN', {
    minimumFractionDigits: hasDecimal ? 2 : 0,
    maximumFractionDigits: hasDecimal ? 2 : 0
  });
}

function formatTokenWan(value: number) {
  return `${formatWanValue(value)} 万`;
}

function buildPackageDesc() {
  const price = model.value.packagePriceYuan ?? 0;
  const llmTokenWan = model.value.llmTokenWan ?? 0;
  const embeddingTokenWan = model.value.embeddingTokenWan ?? 0;

  if (price >= 49.9 || llmTokenWan >= 1000 || embeddingTokenWan >= 500) {
    return '适合高频问答、团队共享资料和较大规模知识库场景。';
  }

  if (price >= 19.9 || llmTokenWan >= 500 || embeddingTokenWan >= 200) {
    return '适合持续问答、资料整理和中等规模知识库构建。';
  }

  return '适合轻度体验、日常问答和少量知识库上传。';
}

function buildPackageBenefit() {
  const llmTokenWan = model.value.llmTokenWan ?? 0;
  const embeddingTokenWan = model.value.embeddingTokenWan ?? 0;

  return [
    `LLM Token：${llmTokenWan.toLocaleString('zh-CN', { maximumFractionDigits: 2 })} 万`,
    `Embedding Token：${embeddingTokenWan.toLocaleString('zh-CN', { maximumFractionDigits: 2 })} 万`,
    '支持微信支付充值',
    '余额到账后可直接使用'
  ].join('\n');
}

function autofillPackageCopy() {
  model.value.packageDesc = buildPackageDesc();
  model.value.packageBenefit = buildPackageBenefit();
}

const isEditing = computed(() => editingId.value !== null);

const columns = computed<DataTableColumns<RechargePackage>>(() => [
  {
    key: 'sortOrder',
    title: '排序',
    width: 70
  },
  {
    key: 'id',
    title: 'ID',
    width: 60
  },
  {
    key: 'packageName',
    title: '套餐名称',
    width: 150
  },
  {
    key: 'packagePrice',
    title: '套餐价格',
    width: 120,
    render: row => `¥${(row.packagePrice / 100).toFixed(2)}`
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
    key: 'enabled',
    title: '状态',
    width: 80,
    render: row => (
      <NTag type={row.enabled ? 'success' : 'default'}>
        {row.enabled ? '已启用' : '已禁用'}
      </NTag>
    )
  },
  {
    key: 'packageDesc',
    title: '描述',
    minWidth: 200,
    ellipsis: {
      tooltip: true
    },
    render: row => <NEllipsis line-clamp={1}>{row.packageDesc || '-'}</NEllipsis>
  },
  {
    key: 'createdAt',
    title: '创建时间',
    width: 180,
    render: row => dayjs(row.createdAt).format('YYYY-MM-DD HH:mm:ss')
  },
  {
    key: 'actions',
    title: '操作',
    width: 180,
    fixed: 'right',
    render: row => (
      <div class="flex gap-2">
        <NButton size="small" onClick={() => handleEdit(row)}>编辑</NButton>
        <NPopconfirm onPositiveClick={() => handleDelete(row.id)}>
          {{
            trigger: () => <NButton size="small" type="error">删除</NButton>,
            default: () => '确定删除该充值套餐吗？'
          }}
        </NPopconfirm>
      </div>
    )
  }
]);

async function getData() {
  loading.value = true;
  try {
    const { error, data: resData } = await request<RechargePackage[]>({
      url: '/admin/recharge-packages',
      method: 'get'
    });

    if (!error && resData) {
      data.value = resData;
      pagination.itemCount = resData.length;
    }
  } finally {
    loading.value = false;
  }
}

function handleCreate() {
  editingId.value = null;
  model.value = createDefaultModel();
  visible.value = true;
  restoreValidation();
}

function handleEdit(row: RechargePackage) {
  editingId.value = row.id;
  model.value = {
    packageName: row.packageName,
    packagePriceYuan: row.packagePrice / 100, // 分转元
    packageDesc: row.packageDesc || '',
    packageBenefit: row.packageBenefit || '',
    llmTokenWan: row.llmToken / TOKEN_UNIT,
    embeddingTokenWan: row.embeddingToken / TOKEN_UNIT,
    enabled: row.enabled,
    sortOrder: row.sortOrder
  };
  visible.value = true;
}

async function handleSubmit() {
  console.log("开始提交，editingId:", editingId.value, "isEditing:", isEditing.value);

  try {
    // 先进行表单验证
    await validate();
    console.log("表单验证通过");

    submitting.value = true;

    const url = isEditing.value
      ? `/admin/recharge-packages/${editingId.value}`
      : '/admin/recharge-packages';

    const method = isEditing.value ? 'put' : 'post';

    // 将元转换为分
    const submitData = {
      ...model.value,
      packagePrice: model.value.packagePriceYuan !== null ? Math.round(model.value.packagePriceYuan * 100) : null,
      llmToken: model.value.llmTokenWan !== null ? Math.round(model.value.llmTokenWan * TOKEN_UNIT) : null,
      embeddingToken: model.value.embeddingTokenWan !== null ? Math.round(model.value.embeddingTokenWan * TOKEN_UNIT) : null
    };
    // 删除 packagePriceYuan 字段，不传递给后端
    delete (submitData as any).packagePriceYuan;
    delete (submitData as any).llmTokenWan;
    delete (submitData as any).embeddingTokenWan;

    console.log("提交数据:", submitData);

    const { error, data } = await request({
      url,
      method,
      data: submitData
    });

    console.log("返回结果:", error, data);

    if (!error) {
      window.$message?.success(isEditing.value ? '更新成功' : '创建成功');
      visible.value = false;
      await getData();
    } else {
      window.$message?.error('操作失败：' + (error.message || '未知错误'));
    }
  } catch (e: any) {
    console.error("提交异常:", e);
    if (Array.isArray(e)) {
      window.$message?.error('请填写完整的表单信息!');
      return;
    }
    // validate 失败时会抛出异常，包含验证错误信息
    if (e?.errors) {
      // errors 可能是一个数组，取第一个错误的提示信息
      const firstError = Array.isArray(e.errors) ? e.errors[0] : e.errors;
      const errorMessage = firstError?.message || '请填写完整的表单信息';
      window.$message?.warning(errorMessage);
    } else {
      window.$message?.error('操作失败：' + (e as Error).message);
    }
  } finally {
    submitting.value = false;
  }
}

async function handleDelete(id: number) {
  try {
    const { error } = await request({
      url: `/admin/recharge-packages/${id}`,
      method: 'delete'
    });

    if (!error) {
      window.$message?.success('删除成功');
      await getData();
    }
  } catch (e) {
    window.$message?.error('删除失败');
  }
}

onMounted(() => {
  getData();
});
</script>

<template>
  <div class="min-h-500px flex-col-stretch gap-16px overflow-auto">
    <NCard title="充值套餐管理" :bordered="false" size="small" class="card-wrapper">
      <template #header-extra>
        <NButton type="primary" @click="handleCreate">新增套餐</NButton>
      </template>

      <NDataTable
        :columns="columns"
        :data="data"
        :loading="loading"
        :pagination="pagination"
        :scroll-x="1200"
        class="mt-4"
      />
    </NCard>

    <!-- 新增/编辑弹窗 -->
    <NModal
      v-model:show="visible"
      preset="dialog"
      :title="isEditing ? '编辑充值套餐' : '新增充值套餐'"
      :show-icon="false"
      class="w-600px!"
    >
      <NForm
        ref="formRef"
        :model="model"
        :rules="rules"
        label-placement="left"
        label-width="120px"
      >
        <NFormItem label="套餐名称" path="packageName">
          <NInput
            v-model:value="model.packageName"
            placeholder="请输入套餐名称"
          />
        </NFormItem>
        <NFormItem label="套餐价格（元）" path="packagePriceYuan">
          <NInputNumber
            v-model:value="model.packagePriceYuan"
            :min="0.01"
            :step="1"
            :precision="2"
            placeholder="请输入套餐价格（单位：元）"
            class="w-full"
            type="text"
          />
        </NFormItem>
        <NFormItem label="LLM Token（万）" path="llmTokenWan">
          <NInputNumber
            v-model:value="model.llmTokenWan"
            :min="0"
            :step="0.1"
            :precision="2"
            placeholder="请输入 LLM Token 数量，单位：万"
            class="w-full"
          />
        </NFormItem>
        <NFormItem label="Embedding Token（万）" path="embeddingTokenWan">
          <NInputNumber
            v-model:value="model.embeddingTokenWan"
            :min="0"
            :step="0.1"
            :precision="2"
            placeholder="请输入 Embedding Token 数量，单位：万"
            class="w-full"
          />
        </NFormItem>
        <NFormItem label="套餐描述" path="packageDesc">
          <div class="w-full flex flex-col gap-2">
            <NInput
              v-model:value="model.packageDesc"
              type="textarea"
              :rows="3"
              placeholder="请输入套餐描述"
            />
            <div class="flex justify-end">
              <NButton size="small" secondary @click="autofillPackageCopy">一键生成文案</NButton>
            </div>
          </div>
        </NFormItem>
        <NFormItem label="套餐权益" path="packageBenefit">
          <NInput
            v-model:value="model.packageBenefit"
            type="textarea"
            :rows="3"
            placeholder="请输入套餐权益说明，支持换行"
          />
        </NFormItem>
        <NFormItem label="排序顺序" path="sortOrder">
          <NInputNumber
              v-model:value="model.sortOrder"
              :min="0"
              :step="1"
              placeholder="请输入排序顺序（数字越小越靠前）"
              class="w-full"
          />
          <div class="mt-2 text-xs text-stone-400">
            提示：数字越小，在列表中显示越靠前
          </div>
        </NFormItem>
        <NFormItem label="是否启用" path="enabled">
          <NSwitch
            v-model:value="model.enabled"
            :checked-value="true"
            :unchecked-value="false"
          />
        </NFormItem>
      </NForm>

      <div class="flex justify-end gap-4 mt-6">
        <NButton @click="visible = false">取消</NButton>
        <NButton
          type="primary"
          :loading="submitting"
          @click="handleSubmit"
        >
          {{ isEditing ? '保存' : '创建' }}
        </NButton>
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
