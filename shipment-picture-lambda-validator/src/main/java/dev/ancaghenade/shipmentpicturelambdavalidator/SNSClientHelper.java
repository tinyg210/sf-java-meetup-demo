package dev.ancaghenade.shipmentpicturelambdavalidator;

import java.net.URI;
import java.util.Objects;
import software.amazon.awssdk.services.sns.SnsClient;

public class SNSClientHelper {

  private static final String AWS_ENDPOINT_URL = System.getenv("AWS_ENDPOINT_URL");
  private static String snsTopicArn;

  public static SnsClient getSnsClient() {

    var clientBuilder = SnsClient.builder();

    if (Objects.nonNull(AWS_ENDPOINT_URL)) {
      snsTopicArn = String.format("arn:aws:sns:%s:000000000000:update_shipment_picture_topic",
          Location.REGION.getRegion());

      return clientBuilder
          .region(Location.REGION.getRegion())
          .endpointOverride(URI.create(AWS_ENDPOINT_URL))
          .build();
    } else {
      snsTopicArn = String.format("arn:aws:sns:%s:%s:update_shipment_picture_topic",
          Location.REGION.getRegion(), "932043840972");
      return clientBuilder.build();
    }
  }

  public static String topicARN() {
    return snsTopicArn;
  }

}
