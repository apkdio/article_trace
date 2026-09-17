import {ElMessage} from 'element-plus';

// 上限受服务器带宽限制，与后端 spring.servlet.multipart.max-file-size 保持一致
export const MAX_IMAGE_SIZE = 2 * 1024 * 1024;
/** 供提示文案复用：改了上限，文案跟着变，不会两边不一致 */
export const MAX_IMAGE_SIZE_TEXT = `${MAX_IMAGE_SIZE / 1024 / 1024}MB`;
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
export function checkImageFile(file) {
    if (!file) return false;
    if (file.size > MAX_IMAGE_SIZE) {
        ElMessage.error(`上传图片不能大于${MAX_IMAGE_SIZE_TEXT}!`);
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
