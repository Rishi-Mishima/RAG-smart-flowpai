<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { NEmpty, NSpin } from 'naive-ui';
import FilePreview from '@/components/custom/file-preview.vue';
import { request } from '@/service/request';

defineOptions({ name: 'ReferencePreviewPage' });

const route = useRoute();
const router = useRouter();

const loading = ref(false);
const loadError = ref('');
const fileName = ref('');
const fileMd5 = ref('');
const pageNumber = ref<number | undefined>(undefined);
const anchorText = ref('');
const retrievalMode = ref<Api.Chat.ReferenceEvidence['retrievalMode']>(null);
const retrievalLabel = ref('');
const retrievalQuery = ref('');
const evidenceSnippet = ref('');
const matchedChunkText = ref('');
const score = ref<number | null>(null);
const chunkId = ref<number | null>(null);
const referenceNumber = ref<number | undefined>(undefined);
const sessionId = ref('');
const previewKey = computed(() => String(route.query.previewKey || ''));
const hasPreviewTarget = computed(() => Boolean(fileName.value || fileMd5.value));

function readOptionalNumber(value: unknown) {
  const parsed = Number.parseInt(String(value || ''), 10);
  return Number.isNaN(parsed) ? undefined : parsed;
}

function syncFallbackFromQuery() {
  fileName.value = String(route.query.fileName || '');
  fileMd5.value = String(route.query.fileMd5 || '');
  pageNumber.value = readOptionalNumber(route.query.pageNumber);
  anchorText.value = String(route.query.anchorText || '');
  retrievalQuery.value = String(route.query.retrievalQuery || '');
  sessionId.value = String(route.query.sessionId || '');
  referenceNumber.value = readOptionalNumber(route.query.referenceNumber);
}

function syncFromStorage() {
  if (!previewKey.value) return false;

  const raw = localStorage.getItem(previewKey.value);
  if (!raw) return false;

  try {
    const payload = JSON.parse(raw) as Partial<Api.Document.ReferenceDetailResponse> & {
      fileName?: string;
      fileMd5?: string;
      pageNumber?: number | null;
      anchorText?: string | null;
      sessionId?: string | null;
      referenceNumber?: number | null;
    };

    fileName.value = payload.fileName || fileName.value;
    fileMd5.value = payload.fileMd5 || fileMd5.value;
    pageNumber.value = payload.pageNumber || pageNumber.value;
    anchorText.value = payload.anchorText || anchorText.value;
    retrievalMode.value = payload.retrievalMode ?? retrievalMode.value;
    retrievalLabel.value = payload.retrievalLabel || retrievalLabel.value;
    retrievalQuery.value = payload.retrievalQuery || retrievalQuery.value;
    evidenceSnippet.value = payload.evidenceSnippet || evidenceSnippet.value;
    matchedChunkText.value = payload.matchedChunkText || matchedChunkText.value;
    score.value = payload.score ?? score.value;
    chunkId.value = payload.chunkId ?? chunkId.value;
    sessionId.value = payload.sessionId || sessionId.value;
    referenceNumber.value = payload.referenceNumber || referenceNumber.value;
    return true;
  } catch {
    return false;
  }
}

async function loadReferenceDetail() {
  syncFallbackFromQuery();
  const restoredFromStorage = syncFromStorage();
  loadError.value = '';

  if (restoredFromStorage && (fileMd5.value || matchedChunkText.value || evidenceSnippet.value)) {
    return;
  }

  if (!sessionId.value || !referenceNumber.value) {
    return;
  }

  loading.value = true;

  try {
    const { error, data } = await request<Api.Document.ReferenceDetailResponse>({
      url: 'documents/reference-detail',
      params: {
        sessionId: sessionId.value,
        referenceNumber: String(referenceNumber.value)
      },
      baseURL: '/proxy-api'
    });

    if (error || !data) {
      loadError.value = error?.message || '引用详情加载失败';
      return;
    }

    fileName.value = data.fileName || fileName.value;
    fileMd5.value = data.fileMd5 || fileMd5.value;
    pageNumber.value = data.pageNumber || pageNumber.value;
    anchorText.value = data.anchorText || anchorText.value;
    retrievalMode.value = data.retrievalMode ?? null;
    retrievalLabel.value = data.retrievalLabel || '';
    retrievalQuery.value = data.retrievalQuery || '';
    evidenceSnippet.value = data.evidenceSnippet || '';
    matchedChunkText.value = data.matchedChunkText || '';
    score.value = data.score ?? null;
    chunkId.value = data.chunkId ?? null;
  } catch (error: any) {
    loadError.value = error?.message || '引用详情加载失败';
  } finally {
    loading.value = false;
  }
}

function handleBack() {
  if (window.history.length > 1) {
    router.back();
    return;
  }

  router.push('/chat');
}

watch(
  () => route.query,
  () => {
    void loadReferenceDetail();
  },
  { immediate: true }
);
</script>

<template>
  <div class="flex-col gap-4">
    <div v-if="loading && !hasPreviewTarget" class="preview-page-state">
      <NSpin size="large" />
      <span>正在加载引用详情...</span>
    </div>

    <div v-else-if="!hasPreviewTarget" class="preview-page-empty">
      <NEmpty :description="loadError || '没有拿到可预览的引用信息'" />
    </div>

    <div v-else class="preview-page-shell">
      <div v-if="loadError && !previewKey" class="preview-page-tip">
        {{ loadError }}
      </div>
      <FilePreview
        :file-name="fileName"
        :file-md5="fileMd5 || undefined"
        :page-number="pageNumber"
        :anchor-text="anchorText"
        :retrieval-mode="retrievalMode"
        :retrieval-label="retrievalLabel"
        :retrieval-query="retrievalQuery"
        :evidence-snippet="evidenceSnippet"
        :matched-chunk-text="matchedChunkText"
        :score="score"
        :chunk-id="chunkId"
        :visible="true"
        @close="handleBack"
      />
    </div>
  </div>
</template>

<style scoped lang="scss">
.preview-page-state {
  @apply flex min-h-220px flex-col items-center justify-center gap-4 rounded-12px bg-white text-stone-500;
}

.preview-page-empty {
  @apply rounded-12px bg-white py-10;
}

.preview-page-shell {
  @apply flex min-h-0 flex-col gap-3;
}

.preview-page-tip {
  @apply rounded-12px border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-700;
}
</style>
