package com.truward.protobuf.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for protobuf to jackson bridge and vice versa.
 *
 * @author Alexander Shabanov
 */
public final class ProtobufJacksonUtil {
  private ProtobufJacksonUtil() {} // hidden

  @Nonnull
  public static <T extends Message> T readJson(@Nonnull Class<T> messageClass, @Nonnull JsonParser jp) throws IOException {
    final Message message;
    try {
      message = getDefaultInstance(messageClass);
    } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
      throw new IOException("Reflection Error: unable to read an instance of " + messageClass, e);
    }

    // construct the result object
    jp.nextToken();
    final Message result = readMessage(message.newBuilderForType(), jp);
    return messageClass.cast(result);
  }

  public static void writeJson(@Nonnull Message message, @Nonnull JsonGenerator jg) throws IOException {
    final Descriptors.Descriptor descriptor = message.getDescriptorForType();

    jg.writeStartObject();
    for (final Descriptors.FieldDescriptor fieldDescriptor : descriptor.getFields()) {
      if (!(fieldDescriptor.isRepeated() || fieldDescriptor.isRequired()) && !message.hasField(fieldDescriptor)) {
        continue;
      }

      jg.writeFieldName(fieldDescriptor.getName());
      final Object value = message.getField(fieldDescriptor);
      if (!writeValue(value, jg)) {
        throw new IOException("Unable to serialize field '" + fieldDescriptor.getName() + "' in " + message +
            ": unhandled field value");
      }
    }
    jg.writeEndObject();
  }

  public static boolean writeValue(@Nullable Object object, @Nonnull JsonGenerator jg) throws IOException {
    if (object == null) {
      throw new IOException("Protobuf error: can't have null fields");
    }

    if (object instanceof Message) {
      writeJson((Message) object, jg);
      return true;
    }

    if (object instanceof ByteString) {
      final ByteString byteString = (ByteString) object;
      try (final InputStream inputStream = byteString.newInput()) {
        jg.writeBinary(inputStream, byteString.size());
      }
    }

    if ((object instanceof String) || (object instanceof Number) || (object instanceof Boolean)) {
      jg.writeObject(object); // let jackson decide
      return true;
    }

    if (object instanceof Iterable) {
      jg.writeStartArray();
      final Iterable<?> iterable = (Iterable) object;
      for (final Object value : iterable) {
        writeValue(value, jg);
      }
      jg.writeEndArray();
      return true;
    }

    if (object instanceof Descriptors.EnumValueDescriptor) {
      final Descriptors.EnumValueDescriptor enumVal = (Descriptors.EnumValueDescriptor) object;
      jg.writeString(enumVal.getName());
      return true;
    }

    return false;
  }

  @Nonnull
  public static Message getDefaultInstance(@Nonnull Class<?> messageClass)
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException, IOException {

    // use reflection to get descriptor
    final Method method = messageClass.getMethod("getDefaultInstance");
    final Object message = method.invoke(null);
    try {
      return (Message) message;
    } catch (ClassCastException e) {
      throw new IOException("Non-protobuf-generated class: " + messageClass, e);
    }
  }

  //
  // Private
  //

  @Nonnull
  private static Message readMessage(@Nonnull Message.Builder builder,
                                     @Nonnull JsonParser jp) throws IOException {
    // make sure current token is the start object
    JsonToken token = jp.getCurrentToken();
    if (token != JsonToken.START_OBJECT) {
      throw new JsonParseException("Object expected", jp.getCurrentLocation());
    }

    // read fields
    for (token = jp.nextToken(); token != JsonToken.END_OBJECT; token = jp.nextToken()) {
      if (token != JsonToken.FIELD_NAME) {
        throw new JsonParseException("Field name expected", jp.getCurrentLocation()); // unlikely
      }

      // get field name
      final String fieldName = jp.getText();

      // advance to the next token (field value)
      jp.nextToken();

      final Descriptors.FieldDescriptor field = builder.getDescriptorForType().findFieldByName(fieldName);
      if (field == null) {
        // unknown field - ignore
        //throw new JsonParseException("Unknown field " + fieldName, jp.getCurrentLocation());
        ignoreValue(jp);
        continue;
      }

      final Object object = readObject(builder, field, jp);
      builder.setField(field, object);
    }

    // construct the result object
    final Message result = builder.build();
    if (result == null) {
      throw new IllegalStateException("Builder failed to construct a message of " +
          "type " + builder.getDescriptorForType()); // unlikely
    }
    return result;
  }

  private static void ignoreValue(@Nonnull JsonParser jp) throws IOException {
    JsonToken token = jp.getCurrentToken();
    if (!token.isStructStart()) {
      return; // skip primitive value in one turn
    }

    // skip structure, keeping in mind that braces should be balanced
    int nesting = 1;
    for (;;) {
      token = jp.nextToken();
      if (token.isStructEnd()) {
        --nesting;
        if (nesting == 0) {
          break;
        }
      } else if (token.isStructStart()) {
        ++nesting;
      }
    }
  }

  @Nonnull
  private static Object readObject(@Nonnull Message.Builder parentBuilder,
                                   @Nonnull Descriptors.FieldDescriptor descriptor,
                                   @Nonnull JsonParser jp) throws IOException {
    if (descriptor.isRepeated()) {
      if (jp.getCurrentToken() != JsonToken.START_ARRAY) {
        throw new JsonParseException("Array expected for field=" + descriptor, jp.getCurrentLocation());
      }

      final List<Object> messages = new ArrayList<>();
      for (JsonToken token = jp.nextToken(); token != JsonToken.END_ARRAY; token = jp.nextToken()) {
        messages.add(readNonRepeatedObject(parentBuilder, descriptor, jp));
      }
      return messages;
    }

    return readNonRepeatedObject(parentBuilder, descriptor, jp);
  }

  @Nonnull
  private static Object readNonRepeatedObject(@Nonnull Message.Builder parentBuilder,
                                              @Nonnull Descriptors.FieldDescriptor descriptor,
                                              @Nonnull JsonParser jp) throws IOException {
    switch (descriptor.getJavaType()) {
      case INT:case LONG:case FLOAT:case DOUBLE:
        return jp.getNumberValue();

      case BOOLEAN:
        return jp.getBooleanValue();

      case STRING:
        return jp.getText();

      case BYTE_STRING:
        return jp.getBinaryValue();

      case ENUM:
        final Descriptors.EnumValueDescriptor valueDescriptor;
        switch (jp.getCurrentToken()) {
          case VALUE_NUMBER_INT:
            valueDescriptor = descriptor.getEnumType().findValueByNumber(jp.getIntValue());
            break;

          case VALUE_STRING:
            valueDescriptor = descriptor.getEnumType().findValueByName(jp.getText());
            break;

          default:
            throw new JsonParseException("Unexpected value for enum=" + descriptor, jp.getCurrentLocation());
        }

        if (valueDescriptor == null) {
          throw new JsonParseException("Unknown enum value", jp.getCurrentLocation());
        }

        return valueDescriptor;

      case MESSAGE:
        final Message.Builder builder = parentBuilder.newBuilderForField(descriptor);
        if (builder == null) {
          throw new JsonParseException("Unable to create a builder for field=" + descriptor, jp.getCurrentLocation());
        }

        return readMessage(builder, jp);
    }

    throw new IOException("Unknown descriptor=" + descriptor);
  }
}
