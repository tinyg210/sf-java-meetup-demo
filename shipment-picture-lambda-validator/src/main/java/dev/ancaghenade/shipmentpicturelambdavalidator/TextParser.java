package dev.ancaghenade.shipmentpicturelambdavalidator;

public class TextParser {

    public static String replaceSuffix(String input, String replacement) {
        int lastSlashIndex = input.lastIndexOf('/');
        int lastDashIndex = input.lastIndexOf('-');

        if (lastSlashIndex == -1 || lastDashIndex == -1 || lastDashIndex < lastSlashIndex) {
            return input;
        }

        String basePath = input.substring(0, lastDashIndex + 1);

        return basePath + replacement;
    }
}