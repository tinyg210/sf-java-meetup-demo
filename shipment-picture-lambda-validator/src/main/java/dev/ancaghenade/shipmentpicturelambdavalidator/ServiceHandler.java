package dev.ancaghenade.shipmentpicturelambdavalidator;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.jayway.jsonpath.JsonPath;
import org.apache.http.entity.ContentType;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class ServiceHandler implements RequestStreamHandler {

  private static final String BUCKET_NAME = System.getenv("BUCKET");

  @Override
  public void handleRequest(InputStream inputStream, OutputStream outputStream, Context context) throws IOException {
    S3Client s3Client = acquireS3Client();
    SnsClient snsClient = acquireSnsClient();
    String objectKey = getObjectKey(inputStream, context);

    if (objectKey == null) {
      context.getLogger().log("Object key is null");
      return;
    }

    context.getLogger().log("Object key: " + objectKey);
    processObject(s3Client, snsClient, objectKey, context);
  }

  private void processObject(S3Client s3Client, SnsClient snsClient, String objectKey, Context context) {
    try {
      GetObjectRequest getObjectRequest = GetObjectRequest.builder()
              .bucket(BUCKET_NAME)
              .key(objectKey)
              .build();

      ResponseInputStream<GetObjectResponse> s3ObjectResponse = s3Client.getObject(getObjectRequest);
      context.getLogger().log("Object fetched.");

      if (isObjectAlreadyProcessed(s3ObjectResponse)) {
        context.getLogger().log("Object already present.");
        return;
      }

      boolean isValid = validateObject(s3ObjectResponse, context);
      byte[] objectData = s3ObjectResponse.readAllBytes();
      String newObjectKey = objectKey;
      if (!isValid) {
       newObjectKey = handleInvalidObject(s3Client, objectKey, context);
      } else {
        handleValidObject(s3Client, objectKey, s3ObjectResponse, objectData, context);
      }

      publishToSns(snsClient, newObjectKey, context);

    } catch (Exception e) {
      context.getLogger().log(e.getMessage());
    } finally {
      closeClients(s3Client, snsClient);
    }
  }

  private boolean isObjectAlreadyProcessed(ResponseInputStream<GetObjectResponse> s3ObjectResponse) {
    return s3ObjectResponse.response().metadata().entrySet().stream()
            .anyMatch(entry -> entry.getKey().equals("skip-processing") && entry.getValue().equals("true"));
  }

  private boolean validateObject(ResponseInputStream<GetObjectResponse> s3ObjectResponse, Context context) {
    String contentType = s3ObjectResponse.response().contentType();
    boolean isValid = List.of(
            ContentType.IMAGE_JPEG.getMimeType(),
            ContentType.IMAGE_PNG.getMimeType(),
            ContentType.IMAGE_BMP.getMimeType()
    ).contains(contentType);

    if (!isValid) {
      context.getLogger().log("Object invalid due to wrong format.");
    }

    return isValid;
  }

  private String handleInvalidObject(S3Client s3Client, String objectKey, Context context) {
    try {
      File imageFile = new File("placeholder.jpg");
      BufferedImage image = ImageIO.read(imageFile);

      try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
        ImageIO.write(image, "jpg", baos);
        byte[] imageBytes = baos.toByteArray();

        objectKey = TextParser.replaceSuffix(objectKey, "placeholder.jpg");
        context.getLogger().log("NEW IMAGE LINK: " + objectKey);

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(BUCKET_NAME)
                .key(objectKey)
                .metadata(Collections.singletonMap("skip-processing", "true"))
                .build();

        s3Client.putObject(putObjectRequest, RequestBody.fromBytes(imageBytes));
      }
    } catch (IOException e) {
      context.getLogger().log("Error handling invalid object: " + e.getMessage());
    }

    return objectKey;
  }

  private void handleValidObject(S3Client s3Client, String objectKey, ResponseInputStream<GetObjectResponse> s3ObjectResponse, byte[] objectData, Context context) throws IOException {
    String extension = s3ObjectResponse.response().contentType();

    PutObjectRequest putObjectRequest = PutObjectRequest.builder()
            .bucket(BUCKET_NAME)
            .key(objectKey)
            .metadata(Collections.singletonMap("skip-processing", "true"))
            .build();

    s3Client.putObject(putObjectRequest, RequestBody.fromBytes(Watermark.watermarkImage(objectData, extension.substring(extension.lastIndexOf("/") + 1))));
    context.getLogger().log("Watermark has been added.");
  }

  private void publishToSns(SnsClient snsClient, String objectKey, Context context) {
    PublishRequest request = PublishRequest.builder()
            .message(objectKey)
            .topicArn(SNSClientHelper.topicARN())
            .build();

    snsClient.publish(request);
    context.getLogger().log("Published to topic: " + request.topicArn());
  }

  private void closeClients(S3Client s3Client, SnsClient snsClient) {
    s3Client.close();
    snsClient.close();
  }

  private String getObjectKey(InputStream inputStream, Context context) {
    try {
      List<String> keys = JsonPath.read(inputStream, "$.Records[*].s3.object.key");
      return keys.isEmpty() ? null : keys.get(0);
    } catch (IOException ioe) {
      context.getLogger().log("Caught IOException reading input stream: " + ioe.getMessage());
      return null;
    }
  }

  private S3Client acquireS3Client() {
    try {
      return S3ClientHelper.getS3Client();
    } catch (IOException e) {
      throw new RuntimeException("Failed to acquire S3 client", e);
    }
  }

  private SnsClient acquireSnsClient() {
    return SNSClientHelper.getSnsClient();
  }
}
