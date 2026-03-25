<script setup lang="ts">
import { inviteChannelConfig } from '@/constants/invite-channel';
import { $t } from '@/locales';

defineOptions({
  name: 'Register'
});

const route = useRoute();
const { toggleLoginModule } = useRouterPush();
const { formRef, validate } = useNaiveForm();

interface FormModel {
  username: string;
  password: string;
  confirmPassword: string;
  inviteCode: string;
}

const model: FormModel = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  inviteCode: ''
});

const rules = computed<Record<keyof FormModel, App.Global.FormRule[]>>(() => {
  const { formRules, defaultRequiredRule, createConfirmPwdRule } = useFormRules();

  return {
    username: formRules.userName,
    password: formRules.pwd,
    confirmPassword: createConfirmPwdRule(model.password),
    inviteCode: [defaultRequiredRule]
  };
});

const loading = ref(false);
const inviteChannelKeywords = [...inviteChannelConfig.replyKeywords];

async function handleSubmit() {
  await validate();
  loading.value = true;
  const { error } = await fetchRegister(model.username, model.password, model.inviteCode.trim());
  if (!error) {
    window.$message?.success('注册成功');
    toggleLoginModule('pwd-login');
  }
  loading.value = false;
}

function syncInviteCodeFromQuery(inviteCode: unknown) {
  if (typeof inviteCode !== 'string') return;
  model.inviteCode = inviteCode.trim();
}

async function copyInviteKeywords() {
  try {
    await navigator.clipboard.writeText(inviteChannelKeywords.join(' / '));
    window.$message?.success('回复关键词已复制');
  } catch {
    window.$message?.error('复制失败，请手动复制');
  }
}

watch(
  () => route.query.inviteCode,
  inviteCode => {
    syncInviteCodeFromQuery(inviteCode);
  },
  { immediate: true }
);
</script>

<template>
  <div class="register-layout">
    <aside class="invite-side-panel">
      <div class="invite-side-header">
        <div class="invite-side-kicker">邀请码获取</div>
        <div class="invite-side-title">派聪明RAG实战项目，祝你求职无敌。</div>
        <div class="invite-side-desc">
          微信搜索
          <span class="invite-emphasis">{{ inviteChannelConfig.officialAccountName }}</span>
          或者扫下面的二维码，后台回复关键字
          <span class="invite-keyword-mark">{{ inviteChannelKeywords.join(' / ') }}</span>
          拿内测邀请码。
        </div>
      </div>

      <div class="invite-side-bottom">
        <div class="invite-qr-shell">
          <img
            v-if="inviteChannelConfig.qrCodeImageUrl"
            :src="inviteChannelConfig.qrCodeImageUrl"
            alt="公众号二维码"
            class="invite-qr-image"
          />
          <div v-else class="invite-qr-placeholder">
            <icon-ant-design:wechat-outlined class="invite-qr-icon" />
            <div class="invite-qr-title">公众号二维码位</div>
            <div class="invite-qr-desc">后续把二维码地址填到 `qrCodeImageUrl`，这里会直接展示扫码入口。</div>
          </div>
        </div>

        <div class="invite-side-steps">
          <div class="invite-step-row">
            <span class="invite-channel-step">1</span>
            <span>
              微信搜索并关注公众号
              <span class="invite-emphasis">{{ inviteChannelConfig.officialAccountName }}</span>
            </span>
          </div>
          <div class="invite-step-row">
            <span class="invite-channel-step">2</span>
            <span class="invite-step-copy">
              后台回复关键字
              <span class="invite-keyword-mark">{{ inviteChannelKeywords.join(' / ') }}</span>
              <NButton text type="primary" class="invite-inline-copy" @click="copyInviteKeywords">复制</NButton>
            </span>
          </div>
          <div class="invite-step-row">
            <span class="invite-channel-step">3</span>
            <span>拿到邀请码后，在右侧完成注册</span>
          </div>
        </div>
      </div>
    </aside>

    <NForm
      ref="formRef"
      :model="model"
      :rules="rules"
      size="large"
      :show-label="false"
      class="register-form-panel"
      @keyup.enter="handleSubmit"
    >
      <NFormItem path="username">
        <NInput v-model:value="model.username" :placeholder="$t('page.login.common.userNamePlaceholder')">
          <template #prefix>
            <icon-ant-design:user-outlined />
          </template>
        </NInput>
      </NFormItem>
      <NFormItem path="password">
        <NInput
          v-model:value="model.password"
          type="password"
          show-password-on="click"
          :placeholder="$t('page.login.common.passwordPlaceholder')"
        >
          <template #prefix>
            <icon-ant-design:key-outlined />
          </template>
        </NInput>
      </NFormItem>
      <NFormItem path="confirmPassword">
        <NInput
          v-model:value="model.confirmPassword"
          type="password"
          show-password-on="click"
          :placeholder="$t('page.login.common.confirmPasswordPlaceholder')"
        >
          <template #prefix>
            <icon-ant-design:key-outlined />
          </template>
        </NInput>
      </NFormItem>
      <NFormItem path="inviteCode">
        <NInput v-model:value="model.inviteCode" :placeholder="$t('page.login.common.inviteCodePlaceholder')">
          <template #prefix>
            <icon-ant-design:safety-certificate-outlined />
          </template>
        </NInput>
      </NFormItem>
      <div class="register-form-tip mb-4">
        {{ $t('page.login.register.inviteCodeTip') }}
      </div>
      <NSpace vertical :size="18" class="w-full">
        <NButton type="primary" size="large" round block :loading="loading" @click="handleSubmit">
          {{ $t('page.login.common.register') }}
        </NButton>
        <NButton block @click="toggleLoginModule('pwd-login')">
          {{ $t('page.login.common.back') }}
        </NButton>
      </NSpace>

      <div class="mt-4 text-center">
        {{ $t('page.login.register.agreement') }}
        <NButton text type="primary">{{ $t('page.login.register.protocol') }}</NButton>
        {{ $t('page.login.register.and') }}
        <NButton text type="primary">{{ $t('page.login.register.policy') }}</NButton>
      </div>
    </NForm>
  </div>
</template>

<style scoped>
.register-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.08fr) minmax(280px, 0.92fr);
  gap: 20px;
  align-items: stretch;
}

.register-form-panel {
  min-width: 0;
}

.register-form-tip {
  font-size: 12px;
  line-height: 1.7;
  color: rgb(var(--base-text-color) / 0.66);
}

.invite-side-panel {
  display: flex;
  min-width: 0;
  flex-direction: column;
  border: 1px solid rgb(var(--primary-color) / 0.16);
  border-radius: 24px;
  padding: 20px;
  background:
    radial-gradient(circle at top right, rgb(var(--primary-color) / 0.16), transparent 36%),
    linear-gradient(
      145deg,
      rgb(var(--primary-50-color) / 0.94),
      rgb(var(--primary-100-color) / 0.82) 55%,
      rgb(var(--container-bg-color)) 100%
    );
  box-shadow: 0 18px 40px rgb(var(--primary-color) / 0.08);
}

.invite-side-kicker {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.14em;
  color: rgb(var(--primary-700-color));
}

.invite-side-title {
  margin-top: 8px;
  font-size: 22px;
  font-weight: 700;
  line-height: 1.45;
  color: rgb(var(--primary-700-color));
}

.invite-side-desc {
  margin-top: 10px;
  font-size: 13px;
  line-height: 1.8;
  color: rgb(var(--base-text-color) / 0.72);
}

.invite-emphasis,
.invite-keyword-mark {
  display: inline-flex;
  align-items: center;
  border: 1px solid rgb(var(--primary-color) / 0.18);
  border-radius: 999px;
  padding: 2px 8px;
  font-weight: 700;
  line-height: 1.4;
  color: rgb(var(--primary-700-color));
  background: rgb(var(--primary-color) / 0.08);
  box-shadow: inset 0 1px 0 rgb(var(--container-bg-color) / 0.6);
}

.invite-keyword-mark {
  background: linear-gradient(135deg, rgb(var(--primary-color) / 0.16), rgb(var(--primary-200-color) / 0.18));
}

.invite-step-copy {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.invite-inline-copy {
  font-size: 12px;
}

.invite-side-steps {
  margin-top: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.invite-step-row {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  line-height: 1.6;
  color: rgb(var(--base-text-color) / 0.78);
}

.invite-side-bottom {
  margin-top: auto;
  padding-top: 18px;
}

.invite-qr-shell {
  width: min(180px, 52%);
  overflow: hidden;
  border: 1px solid rgb(var(--primary-color) / 0.14);
  border-radius: 20px;
  background: rgb(var(--container-bg-color) / 0.84);
  backdrop-filter: blur(8px);
}

.invite-qr-image {
  display: block;
  width: 100%;
  aspect-ratio: 1 / 1;
  object-fit: cover;
}

.invite-qr-placeholder {
  display: flex;
  aspect-ratio: 1 / 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 20px;
  text-align: center;
}

.invite-qr-icon {
  font-size: 42px;
  color: rgb(var(--primary-600-color));
}

.invite-qr-title {
  margin-top: 12px;
  font-size: 14px;
  font-weight: 700;
  color: rgb(var(--primary-700-color));
}

.invite-qr-desc {
  margin-top: 8px;
  font-size: 12px;
  line-height: 1.7;
  color: rgb(var(--base-text-color) / 0.66);
}

.invite-channel-step {
  display: inline-flex;
  width: 20px;
  height: 20px;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(135deg, rgb(var(--primary-color)), rgb(var(--primary-700-color)));
}

@media (max-width: 900px) {
  .register-layout {
    grid-template-columns: 1fr;
  }
}
</style>
