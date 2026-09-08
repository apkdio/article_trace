import {ElMessage} from 'element-plus';
import router from '@/router/index.js';
import {userInfoStore} from '@/stores/userInfo.js';
import {tokenStorage} from '@/stores/tokenStorage.js';
import {logoutService} from '@/api/user.js';

/**
 * 统一登出：调用服务端登出 → 清理本地状态 → 跳转公共首页。
 * 服务端不可用时降级为本地登出。
 */
export async function logout() {
    try {
        await logoutService();
        ElMessage.success('用户已登出！');
    } catch (e) {
        ElMessage.warning('服务端未响应！执行本地登出！');
    } finally {
        await userInfoStore().clearUserInfo();
        await tokenStorage().clearToken();
        router.push({name: 'PublicHome'});
    }
}
