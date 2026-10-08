package com.articleTraceBack;

import com.articleTraceBack.Utils.RustFsUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 对象存储的两个地址各管一头：数据面走内网（{@code S3.internalEndpoint}），
 * 预签名 URL 的 host 用对外地址（{@code S3.endpoint}，浏览器要能解析）；正文按 key 缓存。
 *
 * <pre>mvn test -Dtest=RustFsUtilTest</pre>
 */
public class RustFsUtilTest {

    private static final String PUBLIC_ENDPOINT = "https://files.example.com";
    /** 指向没人监听的端口：真去读必然失败，用来验证「读失败不写缓存」 */
    private static final String INTERNAL_ENDPOINT = "http://127.0.0.1:1";

    private StringRedisTemplate redis;
    private ValueOperations<String, String> ops;
    private RustFsUtil util;

    @SuppressWarnings("unchecked")
    @BeforeEach
    public void setUp() {
        redis = mock(StringRedisTemplate.class);
        ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        util = newUtil(PUBLIC_ENDPOINT, INTERNAL_ENDPOINT);
    }

    private RustFsUtil newUtil(String endpoint, String internalEndpoint) {
        RustFsUtil fs = new RustFsUtil(redis);
        ReflectionTestUtils.setField(fs, "endpoint", endpoint);
        ReflectionTestUtils.setField(fs, "internalEndpoint", internalEndpoint);
        ReflectionTestUtils.setField(fs, "accessKey", "ak");
        ReflectionTestUtils.setField(fs, "secretKey", "sk");
        ReflectionTestUtils.setField(fs, "picBucket", "pic");
        ReflectionTestUtils.setField(fs, "contentBucket", "content");
        ReflectionTestUtils.setField(fs, "avatarBucket", "avatar");
        fs.init();
        return fs;
    }

    private URI endpointOf(RustFsUtil fs) {
        S3Client client = (S3Client) ReflectionTestUtils.getField(fs, "s3Client");
        assertNotNull(client, "s3Client 应当在 init() 后建好");
        return client.serviceClientConfiguration().endpointOverride().orElse(null);
    }

    @Test
    public void clientUsesInternalEndpoint() {
        assertEquals(URI.create(INTERNAL_ENDPOINT), endpointOf(util), "服务端读写走内网");
    }

    @Test
    public void blankInternalEndpointFallsBackToPublic() {
        RustFsUtil fs = newUtil(PUBLIC_ENDPOINT, "");
        assertEquals(URI.create(PUBLIC_ENDPOINT), endpointOf(fs), "不填内网地址时与对外地址同值");
    }

    @Test
    public void presignedUrlUsesPublicEndpoint() {
        when(ops.get(anyString())).thenReturn(null);
        String url = util.getPciUrl("a.png");
        assertTrue(url.startsWith(PUBLIC_ENDPOINT), "预签名 URL 必须落在浏览器能解析的地址上：" + url);
        verify(ops).set(eq("pic:a.png"), anyString(), eq(3L), eq(TimeUnit.DAYS));
    }

    @Test
    public void contentServedFromCacheWithoutReadingS3() {
        when(ops.get("content-body:k1")).thenReturn("<p>缓存里的正文</p>");
        assertEquals("<p>缓存里的正文</p>", util.getContent("k1"));
        verify(ops, never()).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
    }

    @Test
    public void failedReadIsNotCached() {
        when(ops.get(anyString())).thenReturn(null);
        assertEquals("", util.getContent("k2"), "读不到就返回空串（现状行为不变）");
        verify(ops, never()).set(startsWith("content-body:"), anyString(), anyLong(), any(TimeUnit.class));
    }

    @Test
    public void successfulReadIsCachedByKey() {
        when(ops.get("content-body:k3")).thenReturn(null);
        S3Client fake = mock(S3Client.class);
        String json = "{\"content\":\"<p>读回来的正文</p>\"}";
        ResponseInputStream<GetObjectResponse> body = new ResponseInputStream<>(GetObjectResponse.builder().build(),
                AbortableInputStream.create(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))));
        doReturn(body).when(fake).getObject(any(GetObjectRequest.class), any(ResponseTransformer.class));
        ReflectionTestUtils.setField(util, "s3Client", fake);

        assertEquals("<p>读回来的正文</p>", util.getContent("k3"));
        verify(ops).set("content-body:k3", "<p>读回来的正文</p>", 24L, TimeUnit.HOURS);
    }
}
