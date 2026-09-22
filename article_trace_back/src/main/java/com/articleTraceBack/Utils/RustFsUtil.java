package com.articleTraceBack.Utils;

import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class RustFsUtil {
    // 定义redis缓存
    @Qualifier("stringRedisTemplate")
    private final StringRedisTemplate stringRedisTemplate;
    // RustFs配置
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private S3Client s3Client;
    private S3Presigner s3Presigner;
    @Value("${S3.endpoint}")
    private String endpoint;
    @Value("${S3.accessKey}")
    private String accessKey;
    @Value("${S3.secretKey}")
    private String secretKey;
    @Value("${S3.picBucket}")
    private String picBucket;
    @Value("${S3.contentBucket}")
    private String contentBucket;
    @Value("${S3.avatarBucket}")
    private String avatarBucket;
    public static final String THUMB_PREFIX = "thumb_";
    private static final int THUMB_WIDTH = 400;

    public RustFsUtil(@Qualifier("stringRedisTemplate") StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /** 初始化 S3 客户端与预签名器 */
    @PostConstruct
    public void init() {
        this.s3Client = createS3Client(endpoint, accessKey, secretKey);
        this.s3Presigner = createS3Presigner(endpoint, accessKey, secretKey);
    }

    /**
     * 按业务类型解析目标桶。
     *
     * @param type json=正文内容 image=封面等既有图片 avatar=用户头像待审
     * @return 桶名；类型未知时为 null
     */
    private String bucketOf(String type) {
        return switch (type) {
            case "json" -> contentBucket;
            case "image" -> picBucket;
            case "avatar" -> avatarBucket;
            default -> null;
        };
    }

    /** 上传对象：json 进内容桶，image 进图片桶并生成缩略图 */
    public boolean upload(Object file, String type, String key) {
        String bucket = bucketOf(type);
        if (bucket == null) {
            return false;
        }
        return "json".equals(type)
                ? uploadJson(objectMapper.convertValue(file, new TypeReference<>() {
        }), bucket, key)
                : uploadImage((MultipartFile) file, bucket, key);
    }

    private boolean uploadImage(MultipartFile file, String bucket, String key) {
        String contentType = file.getContentType();
        long fileSize = file.getSize();
        // 上传原图
        if (!putObject(file, bucket, key, contentType, fileSize)) {
            return false;
        }
        // 生成并上传缩略图（失败不阻断主流程）
        try {
            byte[] thumb = ThumbnailUtil.thumbnail(file, THUMB_WIDTH);
            if (thumb != null) {
                putObjectBytes(thumb, bucket, THUMB_PREFIX + key, "image/jpeg");
            }
        } catch (Exception e) {
            log.error("thumbnail upload failed", e);
        }
        return true;
    }

    /**
     * 上传 MultipartFile 到指定桶
     */
    private boolean putObject(MultipartFile file, String bucket, String key, String contentType, long contentLength) {
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType)
                    .contentLength(contentLength)
                    .build();
            s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), contentLength));
            return true;
        } catch (Exception e) {
            log.error("put object failed: key={}", key, e);
            return false;
        }
    }

    /**
     * 上传字节数组到指定桶
     */
    private boolean putObjectBytes(byte[] bytes, String bucket, String key, String contentType) {
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType)
                    .build();
            s3Client.putObject(request, RequestBody.fromBytes(bytes));
            return true;
        } catch (Exception e) {
            log.error("put object bytes failed: key={}", key, e);
            return false;
        }
    }

    private boolean uploadJson(Map<String, Object> file, String bucket, String key) {
        String jsonString;
        try {
            jsonString = objectMapper.writeValueAsString(file);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType("application/json")
                    .build();
            RequestBody requestBody = RequestBody.fromString(jsonString);
            s3Client.putObject(putObjectRequest, requestBody);
            return true;
        } catch (Exception e) {
            log.error("upload json failed: key={}", key, e);
            return false;
        }
    }

    /** 删除对象（图片连带删除缩略图） */
    public boolean delete(String key, String fileType) {
        if (key == null || key.isEmpty()) {
            return true;
        }
        String bucket = bucketOf(fileType);
        if (bucket == null) {
            return false;
        }
        boolean deleted = deleteFile(bucket, key);
        if (!"json".equals(fileType)) {
            // 连带删除缩略图（不存在则静默跳过）
            deleteThumb(bucket, key);
        }
        return deleted;
    }

    private boolean deleteFile(String bucket, String key) {
        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();
            s3Client.deleteObject(deleteObjectRequest);
            return true;
        } catch (Exception e) {
            log.error("delete object failed: key={}", key, e);
            return false;
        }
    }

    /**
    * 把对象及其缩略图复制到另一种业务类型的桶（对象名不变），用于头像审核通过后从 avatar 桶转到 pic 桶，使 {@code user_pic} 读取侧可一律按 pic 桶解析。
    * @return 原图复制成功为 true；缩略图可能因生成失败本就不存在，缺失不算失败
    */
    public boolean copyTo(String key, String fromType, String toType) {
        String from = bucketOf(fromType);
        String to = bucketOf(toType);
        if (from == null || to == null || key == null || key.isEmpty()) {
            return false;
        }
        if (!copyObject(from, to, key)) {
            return false;
        }
        copyObject(from, to, THUMB_PREFIX + key);
        return true;
    }

    private boolean copyObject(String fromBucket, String toBucket, String key) {
        try {
            s3Client.copyObject(CopyObjectRequest.builder()
                    .sourceBucket(fromBucket)
                    .sourceKey(key)
                    .destinationBucket(toBucket)
                    .destinationKey(key)
                    .build());
            return true;
        } catch (Exception e) {
            log.error("copy object failed: {} -> {}, key={}", fromBucket, toBucket, key, e);
            return false;
        }
    }

    /**
     * 删除缩略图（不存在则静默跳过）
     */
    private void deleteThumb(String bucket, String key) {
        try {
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(THUMB_PREFIX + key)
                    .build();
            s3Client.deleteObject(request);
        } catch (Exception e) {
            log.error("delete thumbnail failed: key={}", key, e);
        }
    }


    /** 读取内容桶中的正文 JSON */
    public String getContent(String fileKey) {
        try {
            // 完整读取并解析JSON
            return extractFieldFromJson(s3Client, contentBucket, fileKey);
        } catch (Exception e) {
            log.error("read content failed: key={}", fileKey, e);
            return "";
        }
    }

    /** 生成图片临时访问链接（预签名 URL，缓存 3 天） */
    public String getPciUrl(String fileKey) {
        return getPciUrl(fileKey, "image");
    }

    /**
     * 生成图片临时访问链接。
     *
     * @param fileType 对象所在业务类型；用户头像传 avatar，其余图片传 image
     */
    public String getPciUrl(String fileKey, String fileType) {
        String bucket = bucketOf(fileType);
        if (bucket == null) {
            return null;
        }
        // 缓存键必须带桶：两个桶可能出现同名对象，只用 key 会串味
        String cacheKey = bucket + ":" + fileKey;
        String url = stringRedisTemplate.opsForValue().get(cacheKey);
        if (url != null) {
            return url;
        }
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(fileKey)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .getObjectRequest(getObjectRequest)
                .signatureDuration(Duration.ofDays(3)) // 有效期 3 天
                .build();

        PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
        stringRedisTemplate.opsForValue().set(cacheKey, presignedRequest.url().toString(), 3, TimeUnit.DAYS);
        return presignedRequest.url().toString();
    }

    /**
     * 由原图 key 得到缩略图 key
     */
    public static String getThumbKey(String key) {
        return THUMB_PREFIX + key;
    }

    /**
     * 生成缩略图访问链接
     */
    public String getThumbUrl(String fileKey) {
        return getThumbUrl(fileKey, "image");
    }

    /**
     * 生成缩略图访问链接
     *
     * @param fileType 同 {@link #getPciUrl(String, String)}
     */
    public String getThumbUrl(String fileKey, String fileType) {
        return getPciUrl(THUMB_PREFIX + fileKey, fileType);
    }

    /**
     * 判断对象是否存在
     */
    public boolean exists(String key, String fileType) {
        String bucket = bucketOf(fileType);
        if (bucket == null || key == null || key.isEmpty()) {
            return false;
        }
        try {
            HeadObjectRequest request = HeadObjectRequest.builder().bucket(bucket).key(key).build();
            s3Client.headObject(request);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 为已存在的原图生成缩略图（读取原图 → 缩放 → 上传缩略图）
     */
    public boolean generateThumbFor(String fileKey) {
        return generateThumbFor(fileKey, "image");
    }

    /**
     * 为已存在的原图生成缩略图（读取原图 → 缩放 → 上传缩略图）
     *
     * @param fileType 同 {@link #getPciUrl(String, String)}
     */
    public boolean generateThumbFor(String fileKey, String fileType) {
        String bucket = bucketOf(fileType);
        if (bucket == null) {
            return false;
        }
        try {
            GetObjectRequest request = GetObjectRequest.builder().bucket(bucket).key(fileKey).build();
            byte[] original = s3Client.getObject(request, ResponseTransformer.toBytes()).asByteArray();
            byte[] thumb = ThumbnailUtil.thumbnail(new ByteArrayInputStream(original), THUMB_WIDTH);
            if (thumb == null) {
                return false;
            }
            return putObjectBytes(thumb, bucket, THUMB_PREFIX + fileKey, "image/jpeg");
        } catch (Exception e) {
            log.error("generate thumbnail failed: key={}", fileKey, e);
            return false;
        }
    }

    //创建S3客户端
    private static S3Client createS3Client(String endpoint, String accessKey, String secretKey) {
        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .region(Region.US_EAST_1)
                .forcePathStyle(true) // 重要：对于本地S3服务
                .build();
    }

    // 创建S3Presigner
    private S3Presigner createS3Presigner(String endpoint, String accessKey, String secretKey) {
        return S3Presigner.builder()
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .region(Region.US_EAST_1)
                // 必须与 S3Client 一致：不开 path style 时预签名 URL 会把桶名拼成子域
                // （https://<bucket>.<endpoint>/...），公网解析不了，图片全裂
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    /** 从对象存储的 JSON 中取出 content 字段 */
    public static String extractFieldFromJson(S3Client s3Client, String bucket,
                                              String key) throws IOException {

        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        try (ResponseInputStream<GetObjectResponse> s3Object =
                     s3Client.getObject(request, ResponseTransformer.toInputStream())) {

            // 将整个JSON读取为Map
            Map<String, Object> data = objectMapper.readValue(s3Object, new TypeReference<>() {
            });
            // 提取content
            String content = data.get("content").toString();
            if (!Objects.equals(content, "")) {
                return content;
            } else {
                return "未找到！";
            }
        }
    }
}
