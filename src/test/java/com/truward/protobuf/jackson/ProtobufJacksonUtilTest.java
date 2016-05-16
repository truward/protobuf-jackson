package com.truward.protobuf.jackson;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.truward.protobuf.test.AddressBookModel;
import org.junit.Test;

import javax.annotation.Nonnull;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public final class ProtobufJacksonUtilTest {
  private final AddressBookModel.Person person = AddressBookModel.Person.newBuilder()
      .setId(1).setName("n").setEmail("e")
      .addPhone(AddressBookModel.Person.PhoneNumber.newBuilder()
          .setType(AddressBookModel.Person.PhoneType.HOME)
          .setNumber("111")
          .build())
      .build();

  private final String personJsonHead = "{\"name\":\"n\",\"id\":1,\"email\":\"e\"," +
      "\"phone\":[{\"number\":\"111\",\"type\":\"HOME\"}]";

  @Test
  public void shouldWriteAsJson() throws IOException {
    // Given:
    final AddressBookModel.Person person = this.person;

    // When:
    final ByteArrayOutputStream os = new ByteArrayOutputStream();
    try (JsonGenerator jg = jsonGenerator(os)) {
      ProtobufJacksonUtil.writeJson(person, jg);
    }

    // Then:
    final String json = os.toString("UTF-8");
    assertNotNull(json);
  }

  @Test(expected = JsonParseException.class)
  public void shouldNotReadMalformedInput1() throws IOException {
    try (JsonParser jp = jsonParser(new ByteArrayInputStream("{".getBytes()))) {
      ProtobufJacksonUtil.readJson(AddressBookModel.Person.class, jp);
    }
  }

  @Test(expected = JsonParseException.class)
  public void shouldNotReadMalformedInput2() throws IOException {
    try (JsonParser jp = jsonParser(new ByteArrayInputStream("{\"phone\":[".getBytes()))) {
      ProtobufJacksonUtil.readJson(AddressBookModel.Person.class, jp);
    }
  }

  @Test(expected = JsonParseException.class)
  public void shouldNotReadMalformedInput3() throws IOException {
    try (JsonParser jp = jsonParser(new ByteArrayInputStream("{\"phone\":[{".getBytes()))) {
      ProtobufJacksonUtil.readJson(AddressBookModel.Person.class, jp);
    }
  }

  @Test(expected = JsonParseException.class)
  public void shouldNotReadMalformedInput4() throws IOException {
    try (JsonParser jp = jsonParser(new ByteArrayInputStream("{\"phone\":[{\"number\":\"111\",".getBytes()))) {
      ProtobufJacksonUtil.readJson(AddressBookModel.Person.class, jp);
    }
  }

  @Test
  public void shouldIgnoreUnknownField1() throws IOException {
    AddressBookModel.Person p;
    try (JsonParser jp = jsonParser(new ByteArrayInputStream((personJsonHead + ",\"unk\":5}").getBytes()))) {
      p = ProtobufJacksonUtil.readJson(AddressBookModel.Person.class, jp);
    }
    assertEquals(person, p);
  }

  @Test
  public void shouldIgnoreUnknownField2() throws IOException {
    AddressBookModel.Person p;
    try (JsonParser jp = jsonParser(new ByteArrayInputStream((personJsonHead + ",\"unk\":[[], 1, {}]}").getBytes()))) {
      p = ProtobufJacksonUtil.readJson(AddressBookModel.Person.class, jp);
    }
    assertEquals(person, p);
  }

  @Test
  public void shouldIgnoreUnknownField3() throws IOException {
    AddressBookModel.Person p;
    try (JsonParser jp = jsonParser(new ByteArrayInputStream((personJsonHead +
        ",\"unk\":{\"a\":1,\"b\":\"2\",\"c\":null,\"d\":true,\"f\":[{}],\"e\":{\"g\":1}}}").getBytes()))) {
      p = ProtobufJacksonUtil.readJson(AddressBookModel.Person.class, jp);
    }
    assertEquals(person, p);
  }

  @Test
  public void shouldReadJson() throws IOException {
    // Given:
    final AddressBookModel.Person person = this.person;

    // When:
    final ByteArrayOutputStream os = new ByteArrayOutputStream();
    try (JsonGenerator jg = jsonGenerator(os)) {
      ProtobufJacksonUtil.writeJson(person, jg);
    }

    // Then
    final AddressBookModel.Person p2;
    try (JsonParser jp = jsonParser(new ByteArrayInputStream(os.toByteArray()))) {
      p2 = ProtobufJacksonUtil.readJson(AddressBookModel.Person.class, jp);
    }

    assertEquals(person, p2);
  }

  //
  // Private
  //

  @Nonnull
  private static JsonGenerator jsonGenerator(@Nonnull OutputStream outputStream) throws IOException {
    final JsonFactory jsonFactory = new JsonFactory();
    return jsonFactory.createGenerator(outputStream);
  }

  @Nonnull
  private static JsonParser jsonParser(@Nonnull InputStream inputStream) throws IOException {
    final JsonFactory jsonFactory = new JsonFactory();
    return jsonFactory.createParser(inputStream);
  }
}
