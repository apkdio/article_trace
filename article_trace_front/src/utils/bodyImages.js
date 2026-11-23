/**
 * 正文图片的压缩与限额。
 *
 * 正文里的图片是 base64 内联，体积会直接进保存请求（multipart 上限 10MB）；
 * 这里的口径与后端 `BodyImageLimits` 保持一致，改数字时两边一起改。
 */

/** 单张图片的 base64 上限（解码后约 300KB） */
export const BODY_IMAGE_MAX_BASE64 = 400 * 1024
/** 单篇正文里所有图片的 base64 总量上限 */
export const BODY_IMAGE_TOTAL_MAX_BASE64 = 5 * 1024 * 1024
/** 单篇正文的图片张数上限 */
export const BODY_IMAGE_MAX_COUNT = 15
/** 压缩后的最长边（与正文展示宽度匹配） */
export const BODY_IMAGE_MAX_EDGE = 1600
/** 逐档压缩质量，压到单张上限以内为止 */
export const BODY_IMAGE_QUALITY_STEPS = [0.85, 0.75, 0.65]
/** 原图上限：再大就不进浏览器解码了 */
export const BODY_IMAGE_SOURCE_MAX = 20 * 1024 * 1024

export const BODY_IMAGE_MAX_BASE64_TEXT = '400KB'
export const BODY_IMAGE_TOTAL_MAX_TEXT = '5MB'

/** 只匹配位图 data: 图片，外链图片不计入限额（后端同样只统计位图） */
const RASTER_DATA_IMAGE_RE =
    /<img\b[^>]*\bsrc\s*=\s*["']data:image\/(?:png|jpe?g|gif|webp|bmp);base64,([^"']*)["']/gi

/** base64 字符串长度（去掉 `data:image/...;base64,` 前缀），即正文里实际传输的字节数 */
export function base64Length(dataUrl) {
    const comma = String(dataUrl || '').indexOf(',')
    return comma < 0 ? 0 : dataUrl.length - comma - 1
}

/** 统计正文里的位图数量与 base64 总量，供插入拦截、编辑器计数和保存前校验共用 */
export function measureBodyImages(html) {
    let count = 0
    let totalBytes = 0
    if (!html) return {count, totalBytes}
    for (const match of html.matchAll(RASTER_DATA_IMAGE_RE)) {
        count += 1
        totalBytes += match[1].length
    }
    return {count, totalBytes}
}

/** 还能不能再加一张：返回提示文案，没超限返回空串。只描述限额，插入/保存的后续动作由调用方补 */
export function bodyImageLimitError(stats, extraCount = 1, extraBytes = 0) {
    if (stats.count + extraCount > BODY_IMAGE_MAX_COUNT) {
        return `正文最多 ${BODY_IMAGE_MAX_COUNT} 张图片`
    }
    if (stats.totalBytes + extraBytes > BODY_IMAGE_TOTAL_MAX_BASE64) {
        return `正文图片总量不能超过 ${BODY_IMAGE_TOTAL_MAX_TEXT}`
    }
    return ''
}

/** 给提示文案用的可读大小 */
export function formatImageSize(bytes) {
    if (bytes >= 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)}MB`
    return `${Math.round(bytes / 1024)}KB`
}

const isGif = (file) => file.type === 'image/gif' || /\.gif$/i.test(file.name || '')
const isSvg = (file) => file.type === 'image/svg+xml' || /\.svg$/i.test(file.name || '')

/**
 * 压缩一张正文图片，返回可直接插入编辑器的 data URL。
 *
 * - 原图 base64 已在限额内 → 原样返回（避免把清晰的截图再压糊）；
 * - 动图会被 canvas 拍成静态图，所以不压缩，只校验大小；
 * - 其余按最长边 1600 缩放，再按质量档逐次重试，仍超限则抛错。
 */
export async function compressBodyImage(file) {
    if (!file) throw new Error('没有读到图片文件')
    if (isSvg(file)) throw new Error('正文不支持 SVG 图片，请换成 PNG / JPG')
    if (file.size > BODY_IMAGE_SOURCE_MAX) {
        throw new Error(`图片不能超过 ${formatImageSize(BODY_IMAGE_SOURCE_MAX)}`)
    }
    const original = await readAsDataURL(file)
    if (isGif(file)) {
        if (base64Length(original) > BODY_IMAGE_MAX_BASE64) {
            throw new Error(`动图不做压缩，单张不能超过 ${BODY_IMAGE_MAX_BASE64_TEXT}`)
        }
        return {dataUrl: original, compressed: false}
    }
    if (base64Length(original) <= BODY_IMAGE_MAX_BASE64) {
        return {dataUrl: original, compressed: false}
    }

    const image = await decodeImage(file)
    try {
        const scale = Math.min(1, BODY_IMAGE_MAX_EDGE / Math.max(image.width, image.height))
        const width = Math.max(1, Math.round(image.width * scale))
        const height = Math.max(1, Math.round(image.height * scale))
        const canvas = document.createElement('canvas')
        canvas.width = width
        canvas.height = height
        const ctx = canvas.getContext('2d')
        // 透明底导出成 JPEG 会变黑，统一垫白
        ctx.fillStyle = '#fff'
        ctx.fillRect(0, 0, width, height)
        ctx.drawImage(image, 0, 0, width, height)
        for (const quality of BODY_IMAGE_QUALITY_STEPS) {
            const dataUrl = exportDataUrl(canvas, quality)
            if (base64Length(dataUrl) <= BODY_IMAGE_MAX_BASE64) {
                return {dataUrl, compressed: true}
            }
        }
        throw new Error(`图片压缩后仍超过 ${BODY_IMAGE_MAX_BASE64_TEXT}，请换一张或先裁剪`)
    } finally {
        image.close?.()
    }
}

/** 解码原图；createImageBitmap 支持把手机照片的 EXIF 方向烘焙进像素 */
async function decodeImage(file) {
    if (typeof createImageBitmap === 'function') {
        try {
            return await createImageBitmap(file, {imageOrientation: 'from-image'})
        } catch {
            // 老浏览器不认 imageOrientation，退回默认解码
        }
        try {
            return await createImageBitmap(file)
        } catch {
            // 个别格式不支持 bitmap 解码，继续走 <img>
        }
    }
    const url = URL.createObjectURL(file)
    try {
        const img = new Image()
        img.src = url
        await img.decode()
        return img
    } finally {
        URL.revokeObjectURL(url)
    }
}

/** WebP 通常比 JPEG 小；浏览器不支持导出 WebP 时会悄悄回退成 PNG，这里改回 JPEG */
function exportDataUrl(canvas, quality) {
    const webp = canvas.toDataURL('image/webp', quality)
    return webp.startsWith('data:image/webp') ? webp : canvas.toDataURL('image/jpeg', quality)
}

function readAsDataURL(file) {
    return new Promise((resolve, reject) => {
        const reader = new FileReader()
        reader.onload = () => resolve(String(reader.result || ''))
        reader.onerror = () => reject(new Error('图片读取失败'))
        reader.readAsDataURL(file)
    })
}
