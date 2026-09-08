import {ElMessage} from 'element-plus';

export const MAX_IMAGE_SIZE = 5 * 1024 * 1024;
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
        ElMessage.error('上传图片不能大于5MB!');
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
