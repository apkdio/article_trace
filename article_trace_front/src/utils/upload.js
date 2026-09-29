import {ElMessage} from 'element-plus';

// 上限受服务器带宽限制，与后端 spring.servlet.multipart.max-file-size 保持一致
export const MAX_IMAGE_SIZE = 2 * 1024 * 1024;
/** 供提示文案复用：改了上限，文案跟着变，不会两边不一致 */
export const MAX_IMAGE_SIZE_TEXT = `${MAX_IMAGE_SIZE / 1024 / 1024}MB`;
/**
 * 原图上限（仅用于「先裁剪再上传」的入口）。
 *
 * 它比产物上限宽松得多：原图不会进服务器，只在浏览器里解码后导出裁剪结果。
 * 若按 2MB 卡原图，「手机原图想去掉多余边角」这种最常见的用法会直接被挡。
 */
export const MAX_SOURCE_IMAGE_SIZE = 10 * 1024 * 1024;
export const MAX_SOURCE_IMAGE_SIZE_TEXT = `${MAX_SOURCE_IMAGE_SIZE / 1024 / 1024}MB`;
export const ALLOWED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/gif', 'image/bmp', 'image/webp'];

/**
 * 判断 MIME 类型是否为允许的图片类型。
 */
export function isAllowedImageType(type) {
    return typeof type === 'string' && ALLOWED_IMAGE_TYPES.includes(type);
}

/**
 * 校验上传图片（类型 + 大小），不合法时弹出提示并返回 false。
 */
/**
 * 校验上传图片（类型 + 大小），不合法时弹出提示并返回 false。
 *
 * @param maxSize 大小上限，默认是产物上限；「先裁剪再上传」的入口传原图上限
 */
export function checkImageFile(file, maxSize = MAX_IMAGE_SIZE) {
    if (!file) return false;
    // 后端判的是 >= 2MB 就拒（UserController），默认口径必须同样用 >=：
    // 用 > 的话恰好 2MB 的文件会「前端放行、后端拒」，用户只看到一句无来由的失败
    if (file.size >= maxSize) {
        ElMessage.error(`上传图片不能大于${maxSize / 1024 / 1024}MB!`);
        return false;
    }
    if (file.type && file.type.startsWith('image/')) {
        if (ALLOWED_IMAGE_TYPES.includes(file.type)) {
            return true;
        }
        ElMessage.error('只支持 JPG、PNG、GIF、BMP、WEBP 格式的图片！');
        return false;
    }
    ElMessage.error('请上传图片类型文件！');
    return false;
}

/** 裁剪口径：比例、导出宽度、落地文件名（后端按扩展名决定对象名，所以文件名不能省） */
export const CROP_PRESETS = {
    avatar: {title: '裁剪头像', aspect: 1, ratioLabel: '1:1', outputWidth: 512, fileName: 'avatar.jpg'},
    cover: {title: '裁剪封面', aspect: 3 / 2, ratioLabel: '3:2', outputWidth: 1600, fileName: 'cover.jpg'}
};

/** canvas 导出的是没有文件名的 Blob，直接塞进 FormData 会得 filename=blob（无扩展名），后端按扩展名取对象名会失败 */
export function blobToImageFile(blob, fileName) {
    return new File([blob], fileName, {type: blob.type || 'image/jpeg'});
}
