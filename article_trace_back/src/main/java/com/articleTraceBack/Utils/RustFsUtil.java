package com.articleTraceBack.Utils;

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
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

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
    String endpoint;
    @Value("${S3.accessKey}")
    String accessKey;
    @Value("${S3.secretKey}")
    String secretKey;
    @Value("${S3.picBucket}")
    String picBucket;
    @Value("${S3.contentBucket}")
    String contentBucket;
    String bucketName;
    String FileKey;

    public RustFsUtil(@Qualifier("stringRedisTemplate") StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @PostConstruct
    public void init() {
        this.s3Client = createS3Client(endpoint, accessKey, secretKey);
        this.s3Presigner = createS3Presigner(endpoint, accessKey, secretKey);
    }

    public boolean upload(Object file, String type, String key) {
        this.FileKey = key;
        return switch (type) {
            case "json" -> {
                bucketName = contentBucket;
                yield uploadJson(objectMapper.convertValue(file, new TypeReference<>() {
                }));
            }
            case "image" -> {
                bucketName = picBucket;
                MultipartFile multipartFile = (MultipartFile) file;
                yield uploadImage(multipartFile);
            }
            default -> false;
        };
    }

    private boolean uploadImage(MultipartFile file) {
        String contentType = file.getContentType();
        long fileSize = file.getSize();
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(FileKey)
                .contentType(contentType)
                .contentLength(fileSize)
                .build();
        try {
            InputStream inputStream = file.getInputStream();
            RequestBody requestBody = RequestBody.fromInputStream(inputStream, fileSize);
            s3Client.putObject(putObjectRequest, requestBody);
            return true;
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return false;
        }
    }

    private boolean uploadJson(Map<String, Object> file) {
        String jsonString;
        try {
            jsonString = objectMapper.writeValueAsString(file);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(FileKey)
                    .contentType("application/json")
                    .build();
            RequestBody requestBody = RequestBody.fromString(jsonString);
            s3Client.putObject(putObjectRequest, requestBody);
            return true;
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return false;
        }
    }

    public boolean delete(String key, String fileType) {
        this.FileKey = key;
        if (key.isEmpty()) {
            return true;
        }
        return switch (fileType) {
            case "json" -> {
                bucketName = contentBucket;
                yield deleteFile();
            }
            case "image" -> {
                bucketName = picBucket;
                yield deleteFile();
            }
            default -> false;
        };
    }

    private boolean deleteFile() {
        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder().
                    bucket(bucketName).
                    key(FileKey).
                    build();
            s3Client.deleteObject(deleteObjectRequest);
            return true;
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return false;
        }
    }


    public String getContent(String fileKey) {
        try {
            // 完整读取并解析JSON
            return extractFieldFromJson(s3Client, contentBucket, fileKey);
        } catch (Exception e) {
            System.out.println("错误: " + e.getMessage());
            return "";
        }
    }

    // 生成图片临时访问链接
    public String getPciUrl(String fileKey) {
        String url = stringRedisTemplate.opsForValue().get(fileKey);
        if (url != null) {
            return url;
        }
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(picBucket)
                .key(fileKey)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .getObjectRequest(getObjectRequest)
                .signatureDuration(Duration.ofDays(3)) // 有效期 3 天
                .build();

        PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
        stringRedisTemplate.opsForValue().set(fileKey, presignedRequest.url().toString(), 3, TimeUnit.DAYS);
        return presignedRequest.url().toString();
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
                .build();
    }

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