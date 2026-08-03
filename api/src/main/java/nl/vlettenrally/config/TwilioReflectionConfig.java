package nl.vlettenrally.config;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Registers Twilio and Jackson classes for reflection in native image builds.
 * This replaces the META-INF/native-image/reflect-config.json configuration.
 */
@RegisterForReflection(
    targets = {
        // Twilio core classes
        com.twilio.Twilio.class,
        com.twilio.rest.Domains.class,
        com.twilio.rest.api.v2010.Account.class,
        com.twilio.rest.api.v2010.account.Message.class,
        com.twilio.rest.api.v2010.account.Message.Status.class,
        com.twilio.rest.api.v2010.account.MessageCreator.class,
        com.twilio.type.PhoneNumber.class,

        // Twilio exceptions
        com.twilio.exception.TwilioException.class,
        com.twilio.exception.ApiException.class,

        // Twilio HTTP client classes
        com.twilio.http.TwilioRestClient.class,
        com.twilio.http.NetworkHttpClient.class,
        com.twilio.http.Request.class,
        com.twilio.http.Response.class,

        // Twilio converters
        com.twilio.converter.Promoter.class,
        com.twilio.converter.DateConverter.class,
        com.twilio.converter.PrefixedCollapsibleMap.class,

        // Twilio (de)serializers. Jackson instantiates these reflectively from the
        // @JsonDeserialize/@JsonSerialize annotations on the resource classes, so an
        // unregistered one fails at runtime with "has no default (no arg) constructor".
        // Message uses RFC2822Deserializer and CurrencyDeserializer; the rest are
        // registered so sibling date fields on other resources cannot hit the same trap.
        com.twilio.converter.CurrencyDeserializer.class,
        com.twilio.converter.RFC2822Deserializer.class,
        com.twilio.converter.ISO8601Deserializer.class,
        com.twilio.converter.ISO8601Serializer.class,
        com.twilio.converter.LocalDateDeserializer.class,
        com.twilio.converter.ZonedDateTimeListDeserializer.class,

        // Twilio base classes
        com.twilio.base.Resource.class,
        com.twilio.base.Creator.class,
        com.twilio.base.Fetcher.class,
        com.twilio.base.Reader.class,
        com.twilio.base.Updater.class,
        com.twilio.base.Deleter.class,

        // Jackson classes
        com.fasterxml.jackson.databind.ObjectMapper.class,
        com.fasterxml.jackson.databind.JsonNode.class,
        com.fasterxml.jackson.databind.node.ObjectNode.class,
        com.fasterxml.jackson.databind.node.ArrayNode.class,
        com.fasterxml.jackson.databind.node.TextNode.class
    }
)
public class TwilioReflectionConfig {
}

