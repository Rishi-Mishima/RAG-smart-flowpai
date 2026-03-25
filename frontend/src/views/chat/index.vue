<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import ChatList from './modules/chat-list.vue';
import InputBox from './modules/input-box.vue';
import ReferencePreviewPage from './modules/reference-preview-page.vue';

const route = useRoute();
const showReferencePreview = computed(() => route.query.preview === 'reference');
</script>

<template>
  <div v-if="showReferencePreview">
    <ReferencePreviewPage />
  </div>
  <div v-else class="flex-col gap-4">
    <ChatList />
    <InputBox />
  </div>
</template>

<style scoped></style>
