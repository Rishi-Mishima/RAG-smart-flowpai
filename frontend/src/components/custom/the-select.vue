<script setup lang="ts">
import { NSelect, type SelectGroupOption, type SelectOption } from 'naive-ui';
import type { SelectBaseOption } from 'naive-ui/es/select/src/interface';

defineOptions({
  name: 'TheSelect',
  inheritAttrs: false
});

const model = defineModel<string | number | null>('value', { required: true });

const opts = ref<Array<SelectOption | SelectGroupOption>>([]);

const {
  url,
  immediate = true,
  params = {},
  keyField = '',
  selectFirst = false
} = defineProps<{
  url: string;
  immediate?: boolean;
  params?: Record<string, any>;
  keyField?: string;
  selectFirst?: boolean;
}>();

const attrs = useAttrs() as any;
const fetchOpts = async () => {
  const { error, data } = await request({ url, params });
  if (!error) {
    if (keyField) opts.value = data[keyField];
    else opts.value = data;

    if (selectFirst && opts.value.length > 0) model.value = opts.value[0][attrs['value-field']] as string | number;
  }
};

watch(
  () => params,
  (newVal, oldVal) => {
    if (JSON.stringify(newVal) !== JSON.stringify(oldVal)) {
      fetchOpts();
    }
  }
);

onMounted(() => {
  if (immediate) fetchOpts();
});

const emit = defineEmits<{
  change: [SelectBaseOption[] | SelectBaseOption | null];
}>();
function onUpdate(
  _: string[] | number[] | string | number | null,
  option: SelectBaseOption[] | SelectBaseOption | null
) {
  emit('change', option);
}
</script>

<template>
  <NSelect v-model:value="model" remote filterable :options="opts" clearable v-bind="$attrs" @update:value="onUpdate" />
</template>

<style scoped></style>
