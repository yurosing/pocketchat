<script setup>
import { ref } from 'vue'
import { withBase } from 'vitepress'

const props = defineProps({
  src: { type: String, required: true },
  caption: { type: String, default: '' },
})

const failed = ref(false)
// Имя файла без ведущего слэша — для подсказки «куда класть».
const fname = 'docs/public' + props.src
</script>

<template>
  <figure class="shot">
    <div class="shot-frame" :class="{ missing: failed }">
      <img
        v-show="!failed"
        :src="withBase(src)"
        :alt="caption"
        loading="lazy"
        @error="failed = true"
      />
      <div v-if="failed" class="shot-ph">
        <div class="shot-ph-ico">🖼️</div>
        <div class="shot-ph-text">Сюда встанет скриншот</div>
        <code class="shot-ph-name">{{ fname }}</code>
      </div>
    </div>
    <figcaption v-if="caption">{{ caption }}</figcaption>
  </figure>
</template>

<style scoped>
.shot {
  margin: 24px 0;
}
.shot-frame {
  border: 1px solid var(--vp-c-border);
  border-radius: 14px;
  overflow: hidden;
  background: var(--vp-c-bg-soft);
}
.shot-frame img {
  display: block;
  width: 100%;
  height: auto;
}
.shot-frame.missing {
  background:
    repeating-linear-gradient(45deg, transparent 0 18px, rgba(127,127,127,0.05) 18px 36px),
    var(--vp-c-bg-soft);
}
.shot-ph {
  padding: 44px 24px;
  text-align: center;
}
.shot-ph-ico { font-size: 2.2rem; opacity: 0.6; }
.shot-ph-text { color: var(--vp-c-text-2); margin: 6px 0 12px; font-size: 0.95rem; }
.shot-ph-name {
  font-size: 0.82rem;
  color: var(--vp-c-brand-1);
  border: 1px dashed var(--vp-c-brand-1);
  background: var(--vp-c-bg);
  padding: 5px 12px;
  border-radius: 8px;
}
figcaption {
  text-align: center;
  color: var(--vp-c-text-2);
  font-size: 0.85rem;
  margin-top: 8px;
}
</style>
