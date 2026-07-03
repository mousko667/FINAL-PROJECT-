package com.oct.invoicesystem.domain.storage.service;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.Result;
import io.minio.messages.Item;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class MinioStorageService {

    private final MinioClient minioClient;
    private final String bucket;
    private final int presignedUrlExpiryMinutes;

    public MinioStorageService(
            @Value("${minio.endpoint}") String endpoint,
            @Value("${minio.access-key}") String accessKey,
            @Value("${minio.secret-key}") String secretKey,
            @Value("${minio.bucket}") String bucket,
            @Value("${minio.presigned-url-expiry-minutes:15}") int presignedUrlExpiryMinutes) {
        this.minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        this.bucket = bucket;
        this.presignedUrlExpiryMinutes = presignedUrlExpiryMinutes;
    }

    @PostConstruct
    public void ensureBucketExists() {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception exception) {
            // Avoid breaking application startup in environments where object storage credentials are not available.
            log.warn("MinIO bucket initialization skipped: {}", exception.getMessage());
        }
    }

    public String upload(String objectKey, byte[] content, String contentType) throws Exception {
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectKey)
                        .stream(new ByteArrayInputStream(content), content.length, -1)
                        .contentType(contentType)
                        .build()
        );
        return objectKey;
    }

    /**
     * Downloads the full object bytes from MinIO (used for integrity verification before presigning).
     *
     * @param objectKey stored object key
     * @return object content
     * @throws Exception if the object cannot be read
     */
    public byte[] download(String objectKey) throws Exception {
        try (InputStream stream = minioClient.getObject(
                GetObjectArgs.builder().bucket(bucket).object(objectKey).build())) {
            return stream.readAllBytes();
        }
    }

    public String generateDownloadUrl(String objectKey) throws Exception {
        return minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                        .method(Method.GET)
                        .bucket(bucket)
                        .object(objectKey)
                        .expiry((int) Duration.ofMinutes(presignedUrlExpiryMinutes).getSeconds())
                        .build()
        );
    }

    public void delete(String objectKey) throws Exception {
        minioClient.removeObject(
                RemoveObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectKey)
                        .build()
        );
    }

    public List<String> listObjects(String prefix) throws Exception {
        List<String> items = new ArrayList<>();
        Iterable<Result<Item>> results = minioClient.listObjects(
            ListObjectsArgs.builder().bucket(bucket).prefix(prefix).build()
        );
        for (Result<Item> result : results) {
            items.add(result.get().objectName());
        }
        return items;
    }
}
