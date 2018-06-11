package com.hubspot.chrome.devtools.codegen;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;

import org.apache.commons.io.IOUtils;
import org.junit.Test;

public class GeneratorTest {
  @Test
  public void itReadsJson() throws Exception {
    InputStream resourceStream = getClass().getResourceAsStream("/sample.json");
    String json = new String(IOUtils.toByteArray(resourceStream));

    Generator generator = new Generator();

    Domain domain = Domain.builder()
        .setName("MyDomain")
        .setDescription("A description.")
        .setExperimental(true)
        .setDeprecated(false)
        .setDependencies(Arrays.asList("DomainX", "DomainY"))
        .setTypes(Arrays.asList(
            Type.builder()
                .setName("MyType")
                .setDescription("Description of type.")
                .setType("object")
                .setProperties(Arrays.asList(
                    Property.builder()
                        .setName("fullProp")
                        .setDescription("")
                        .setType("string")
                        .setOptional(false)
                        .setEnumeration(Arrays.asList("A", "B", "C"))
                        .build(),
                    Property.builder()
                        .setName("minimalProp")
                        .setDescription("")
                        .setType("string")
                        .build()
                )).build(),
            Type.builder()
                .setName("MyType2")
                .setDescription("")
                .setType("string")
                .setEnum(Arrays.asList("valA", "valB"))
                .build()
        ))
        .setCommands(Arrays.asList(
            Command.builder()
                .setName("commandA")
                .setDescription("Run the command")
                .setParameters(Arrays.asList(
                    Property.builder()
                        .setName("paramA")
                        .setDescription("")
                        .setRef("MyType")
                        .build()
                ))
                .build(),
            Command.builder()
                .setName("commandB")
                .setDescription("")
                .setReturns(Arrays.asList(
                    Property.builder()
                        .setName("returnValue")
                        .setDescription("")
                        .setExperimental(true)
                        .setRef("MyType")
                        .build()
                ))
                .build()
        ))
        .setEvents(Arrays.asList(
            Command.builder()
                .setName("eventCall")
                .setDescription("")
                .setParameters(Arrays.asList(
                    Property.builder()
                        .setName("paramA")
                        .setDescription("")
                        .setRef("MyType")
                        .build()
                ))
                .build(),
            Command.builder()
                .setName("eventCall2")
                .setDescription("")
                .setParameters(Arrays.asList(
                    Property.builder()
                        .setName("paramA")
                        .setDescription("")
                        .setType("integer")
                        .build()
                ))
                .build()
        ))
        .build();

    assertThat(generator.parseProtocol(json)).isEqualTo(Collections.singletonList(domain));
  }
}
