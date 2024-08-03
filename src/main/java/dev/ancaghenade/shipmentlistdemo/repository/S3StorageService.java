package dev.ancaghenade.shipmentlistdemo.repository;

import dev.ancaghenade.shipmentlistdemo.buckets.BucketName;
import dev.ancaghenade.shipmentlistdemo.util.FileUtil;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

@Service
public class S3StorageService {

  private final S3Client s3;
  private static final Logger LOGGER = LoggerFactory.getLogger(S3StorageService.class);
  private final BucketName bucketName;

  @Autowired
  public S3StorageService(S3Client s3, BucketName bucketName) {
    this.s3 = s3;
    this.bucketName = bucketName;
  }

  public void save(String path, String fileName, MultipartFile multipartFile) throws IOException {
    PutObjectRequest putObjectRequest = PutObjectRequest.builder()
            .bucket(bucketName.getShipmentPictureBucket())
            .key(path + "/" + fileName)
            .contentType(multipartFile.getContentType())
            .contentLength(multipartFile.getSize())
            .build();

    try {
      s3.putObject(putObjectRequest, RequestBody.fromFile(FileUtil.convertMultipartFileToFile(multipartFile)));
      LOGGER.info("File {} saved to S3 at {}", fileName, path);
    } catch (SdkException e) {
      LOGGER.error("Failed to save file to S3: {}", e.getMessage());
      throw new IOException("Failed to save file to S3", e);
    }
  }

  public byte[] download(String key) throws IOException {
    GetObjectRequest getObjectRequest = GetObjectRequest.builder()
            .bucket(bucketName.getShipmentPictureBucket())
            .key(key)
            .build();

    try {
      return s3.getObject(getObjectRequest).readAllBytes();
    } catch (NoSuchKeyException e) {
      LOGGER.warn("Could not find object: {}", key);
      return new byte[0]; // Return empty byte array when object is not found
    } catch (SdkException e) {
      LOGGER.error("Failed to download file from S3: {}", e.getMessage());
      throw new IOException("Failed to download file from S3", e);
    }
  }

  public void delete(String folderPrefix) {
    List<ObjectIdentifier> keysToDelete = new ArrayList<>();

    s3.listObjectsV2Paginator(builder -> builder.bucket(bucketName.getShipmentPictureBucket()).prefix(folderPrefix + "/"))
            .contents().stream()
            .map(S3Object::key)
            .forEach(key -> keysToDelete.add(ObjectIdentifier.builder().key(key).build()));

    DeleteObjectsRequest deleteRequest = DeleteObjectsRequest.builder()
            .bucket(bucketName.getShipmentPictureBucket())
            .delete(builder -> builder.objects(keysToDelete).build())
            .build();

    try {
      DeleteObjectsResponse response = s3.deleteObjects(deleteRequest);
      handleDeleteErrors(response.errors());
    } catch (SdkException e) {
      LOGGER.error("Error occurred during object deletion: {}", e.getMessage());
    }
  }

  private void handleDeleteErrors(List<S3Error> errors) {
    if (!errors.isEmpty()) {
      LOGGER.error("Errors occurred while deleting objects:");
      errors.forEach(error -> LOGGER.error("Object: {}, Error Code: {}, Error Message: {}",
              error.key(), error.code(), error.message()));
    } else {
      LOGGER.info("Objects deleted successfully.");
    }
  }
}
