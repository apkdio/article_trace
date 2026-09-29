<script setup>
import {computed, nextTick, reactive, ref} from 'vue'
import {blobToImageFile} from '@/utils/upload.js'

/**
 * 图片裁剪对话框：固定比例取景框，拖拽选位置、滚轮或滑块缩放，确定后导出图片文件。
 *
 * 导出的是带扩展名的 File（后端按扩展名决定对象名），格式固定 JPEG——头像与封面都不需要透明通道。
 */
const props = defineProps({
  title: {type: String, default: '裁剪图片'},
  ratioLabel: {type: String, default: ''},
  aspect: {type: Number, default: 1},
  outputWidth: {type: Number, default: 512},
  fileName: {type: String, default: 'crop.jpg'}
})
const emit = defineEmits(['confirm'])

const FRAME_W = 300
const MAX_ZOOM = 4

const visible = ref(false)
const canvasRef = ref()
const busy = ref(false)
const zoom = ref(1)
const offset = reactive({x: 0, y: 0})
const frameH = computed(() => Math.round(FRAME_W / props.aspect))

let bitmap = null
let bitmapW = 0
let bitmapH = 0
// 恰好铺满取景框的缩放比例（等比 cover），缩放从它算起
let minScale = 1
let fallbackUrl = ''
let dragging = false
let lastX = 0
let lastY = 0

defineExpose({open})

async function open(file) {
  busy.value = true
  try {
    const loaded = await loadBitmap(file)
    bitmap = loaded.bitmap
    fallbackUrl = loaded.fallbackUrl
    bitmapW = bitmap.width
    bitmapH = bitmap.height
    minScale = Math.max(FRAME_W / bitmapW, frameH.value / bitmapH)
    zoom.value = 1
    offset.x = 0
    offset.y = 0
    visible.value = true
    await nextTick()
    draw()
  } catch (e) {
    ElMessage.error('这张图片读不出来，换一张试试（HEIC 等格式浏览器不支持）')
  } finally {
    busy.value = false
  }
}

/** 优先 createImageBitmap：它按 EXIF 方向烘焙，竖拍照片不会躺倒；不支持时退回 img */
async function loadBitmap(file) {
  if (window.createImageBitmap) {
    try {
      return {bitmap: await createImageBitmap(file, {imageOrientation: 'from-image'}), fallbackUrl: ''}
    } catch (e) {
      // 少数格式 createImageBitmap 解不了，落到下面的 img
    }
  }
  const url = URL.createObjectURL(file)
  const img = await new Promise((resolve, reject) => {
    const el = new Image()
    el.onload = () => resolve(el)
    el.onerror = () => reject(new Error('decode failed'))
    el.src = url
  })
  return {bitmap: img, fallbackUrl: url}
}

function draw() {
  const canvas = canvasRef.value
  if (!canvas || !bitmap) return
  const dpr = window.devicePixelRatio || 1
  const w = FRAME_W
  const h = frameH.value
  if (canvas.width !== Math.round(w * dpr)) {
    canvas.width = Math.round(w * dpr)
    canvas.height = Math.round(h * dpr)
  }
  const ctx = canvas.getContext('2d')
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
  ctx.clearRect(0, 0, w, h)
  const scale = minScale * zoom.value
  const dw = bitmapW * scale
  const dh = bitmapH * scale
  ctx.drawImage(bitmap, w / 2 + offset.x - dw / 2, h / 2 + offset.y - dh / 2, dw, dh)
}

/** 图片任何时候都要盖满取景框，所以位移不能超过「多出来的那一半」 */
function clampOffset() {
  const scale = minScale * zoom.value
  const maxX = Math.max(0, (bitmapW * scale - FRAME_W) / 2)
  const maxY = Math.max(0, (bitmapH * scale - frameH.value) / 2)
  offset.x = Math.min(maxX, Math.max(-maxX, offset.x))
  offset.y = Math.min(maxY, Math.max(-maxY, offset.y))
}

function setZoom(value) {
  zoom.value = Math.min(MAX_ZOOM, Math.max(1, value))
  clampOffset()
  draw()
}

function onWheel(event) {
  setZoom(zoom.value - Math.sign(event.deltaY) * 0.1)
}

function onPointerDown(event) {
  dragging = true
  lastX = event.clientX
  lastY = event.clientY
  event.currentTarget?.setPointerCapture?.(event.pointerId)
}

function onPointerMove(event) {
  if (!dragging) return
  offset.x += event.clientX - lastX
  offset.y += event.clientY - lastY
  lastX = event.clientX
  lastY = event.clientY
  clampOffset()
  draw()
}

function onPointerUp(event) {
  dragging = false
  event.currentTarget?.releasePointerCapture?.(event.pointerId)
}

async function confirm() {
  if (!bitmap) return
  busy.value = true
  try {
    const scale = minScale * zoom.value
    // 取景框对应的原图区域（原图像素）
    const sw = FRAME_W / scale
    const sh = frameH.value / scale
    const sx = -(FRAME_W / 2 + offset.x - (bitmapW * scale) / 2) / scale
    const sy = -(frameH.value / 2 + offset.y - (bitmapH * scale) / 2) / scale
    // 不放大超出原图：原图比目标小就按原尺寸导出
    const outW = Math.max(1, Math.min(props.outputWidth, Math.round(sw)))
    const outH = Math.max(1, Math.round(outW / props.aspect))
    const out = document.createElement('canvas')
    out.width = outW
    out.height = outH
    const ctx = out.getContext('2d')
    // JPEG 不带透明通道，透明区域会变黑，先铺白底
    ctx.fillStyle = '#ffffff'
    ctx.fillRect(0, 0, outW, outH)
    ctx.drawImage(bitmap, sx, sy, sw, sh, 0, 0, outW, outH)
    const blob = await new Promise((resolve) => out.toBlob(resolve, 'image/jpeg', 0.9))
    if (!blob) throw new Error('toBlob failed')
    emit('confirm', blobToImageFile(blob, props.fileName))
    visible.value = false
  } catch (e) {
    ElMessage.error('裁剪失败，换一张图片试试')
  } finally {
    busy.value = false
  }
}

function onClosed() {
  bitmap = null
  if (fallbackUrl) {
    URL.revokeObjectURL(fallbackUrl)
    fallbackUrl = ''
  }
}
</script>

<template>
  <el-dialog
      v-model="visible"
      :title="title"
      width="400px"
      append-to-body
      :close-on-click-modal="false"
      @closed="onClosed"
  >
    <div class="crop-stage" :style="{height: frameH + 'px'}">
      <canvas
          ref="canvasRef"
          class="crop-canvas"
          :style="{width: FRAME_W + 'px', height: frameH + 'px'}"
          @pointerdown="onPointerDown"
          @pointermove="onPointerMove"
          @pointerup="onPointerUp"
          @pointercancel="onPointerUp"
          @wheel.prevent="onWheel"
      />
      <div class="crop-grid"></div>
    </div>
    <div class="crop-zoom">
      <span class="crop-zoom-label">缩放</span>
      <el-slider :model-value="zoom" :min="1" :max="MAX_ZOOM" :step="0.01" @input="setZoom"/>
    </div>
    <p class="crop-hint">
      <span v-if="ratioLabel">{{ ratioLabel }} ·</span> 拖动图片选位置，滚轮或滑块缩放
    </p>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="busy" @click="confirm">确定</el-button>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
.crop-stage {
  position: relative;
  display: flex;
  justify-content: center;
  overflow: hidden;
  background: #1f2937;
  border-radius: 6px;
}

.crop-canvas {
  display: block;
  cursor: move;
  touch-action: none;
}

/* 三分线，纯粹为了取景时看着有参照 */
.crop-grid {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background-image: linear-gradient(to right, rgba(255, 255, 255, .35) 1px, transparent 1px),
  linear-gradient(to bottom, rgba(255, 255, 255, .35) 1px, transparent 1px);
  background-size: 33.33% 33.33%;
}

.crop-zoom {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 16px;

  .crop-zoom-label {
    font-size: 13px;
    color: #606266;
    flex-shrink: 0;
  }

  :deep(.el-slider) {
    flex: 1;
  }
}

.crop-hint {
  margin: 4px 0 0;
  font-size: 12px;
  color: #909399;
}
</style>
