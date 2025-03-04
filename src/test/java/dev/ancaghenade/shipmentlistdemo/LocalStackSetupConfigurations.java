package dev.ancaghenade.shipmentlistdemo;

import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.File;
import java.net.URI;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

@Testcontainers
@SpringBootTest(webEnvironment = WebEnvironment.DEFINED_PORT)
public class LocalStackSetupConfigurations {

    @Container
    protected static LocalStackContainer localStack =
            new LocalStackContainer(DockerImageName.parse("localstack/localstack-pro:latest"))
                    .withEnv("LAMBDA_REMOVE_CONTAINERS", "1")
                    .withEnv("LOCALSTACK_AUTH_TOKEN", System.getenv("LOCALSTACK_AUTH_TOKEN"))
                    // remember to use the correct bucket name for the cloud pod
                    .withEnv("AUTO_LOAD_POD", "devnexus-demo-pod")
                                                .withEnv("DEBUG", "1")
                    .waitingFor(Wait.forLogMessage(".*Ready\\..*", 1))
                    .withStartupTimeout(Duration.ofMinutes(3));
    protected static final Logger LOGGER = LoggerFactory.getLogger(LocalStackSetupConfigurations.class);
    protected static Slf4jLogConsumer logConsumer = new Slf4jLogConsumer(LOGGER);
    protected TestRestTemplate restTemplate = new TestRestTemplate();

    protected static final String BUCKET_NAME = "shipment-picture-bucket-grown-vervet";
    protected static String BASE_URL = "http://localhost:8081";
    protected static Region region = Region.of(localStack.getRegion());

    protected static LambdaClient lambdaClient;

    protected static S3Client s3Client;

    protected static DynamoDbClient dynamoDbClient;


    protected static Logger logger = LoggerFactory.getLogger(LocalStackSetupConfigurations.class);
    protected static ObjectMapper objectMapper = new ObjectMapper();
    protected static URI localStackEndpoint;

    @BeforeAll()
    protected static void setupConfig() {
        localStackEndpoint = localStack.getEndpoint();

        lambdaClient = LambdaClient.builder()
                .region(region)
                .endpointOverride(localStack.getEndpointOverride(LocalStackContainer.Service.LAMBDA))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(localStack.getAccessKey(), localStack.getSecretKey())))
                .build();

        s3Client = S3Client.builder()
                .region(region)
                .endpointOverride(localStack.getEndpointOverride(LocalStackContainer.Service.S3))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(localStack.getAccessKey(), localStack.getSecretKey())))
                .build();
        dynamoDbClient = DynamoDbClient.builder()
                .region(region)
                .endpointOverride(localStack.getEndpointOverride(LocalStackContainer.Service.DYNAMODB))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(localStack.getAccessKey(), localStack.getSecretKey())))
                .build();
    }

    @DynamicPropertySource
    static void overrideConfigs(DynamicPropertyRegistry registry) {
        registry.add("aws.s3.endpoint",
                () -> localStackEndpoint);
        registry.add(
                "aws.dynamodb.endpoint", () -> localStackEndpoint);
        registry.add(
                "aws.sqs.endpoint", () -> localStackEndpoint);
        registry.add(
                "aws.sns.endpoint", () -> localStackEndpoint);
        registry.add("aws.credentials.secret-key", localStack::getSecretKey);
        registry.add("aws.credentials.access-key", localStack::getAccessKey);
        registry.add("aws.region", localStack::getRegion);
        registry.add("shipment-picture-bucket", () -> BUCKET_NAME);
    }

    protected static org.testcontainers.containers.Container.ExecResult executeInContainer(String command) throws Exception {

        final var execResult = localStack.execInContainer(formatCommand(command));
        // assertEquals(0, execResult.getExitCode());

        final var logs = execResult.getStdout() + execResult.getStderr();
        logger.info(logs);
        logger.error(execResult.getExitCode() != 0 ? execResult + " - DOES NOT WORK" : "");
        return execResult;
    }

    private static String[] formatCommand(String command) {
        return command.split(" ");
    }

}
