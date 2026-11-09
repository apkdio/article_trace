package com.articleTraceBack;

import com.articleTraceBack.Utils.CurrentUser;
import com.articleTraceBack.Utils.NotLoggedInException;
import com.articleTraceBack.Utils.ThreadLocalUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 当前用户取值口径：require 取不到直接抛（→ 401），optional 允许匿名，字段按 TokenCheck 写入的 key 映射。
 *
 * <pre>mvn test -Dtest=CurrentUserTest</pre>
 */
public class CurrentUserTest {

    @AfterEach
    public void clean() {
        ThreadLocalUtil.remove();
    }

    @Test
    public void requireThrowsWhenAbsent() {
        ThreadLocalUtil.remove();
        assertThrows(NotLoggedInException.class, CurrentUser::require);
    }

    @Test
    public void optionalReturnsNullWhenAbsent() {
        ThreadLocalUtil.remove();
        assertNull(CurrentUser.optional());
    }

    @Test
    public void mapsFieldsWrittenByTokenCheck() {
        ThreadLocalUtil.set(Map.of("id", 7, "name", "zhangsan", "type", 0));
        CurrentUser.Session me = CurrentUser.require();
        assertEquals(7, me.id());
        assertEquals("zhangsan", me.username());
        assertEquals(0, me.type());
    }
}
