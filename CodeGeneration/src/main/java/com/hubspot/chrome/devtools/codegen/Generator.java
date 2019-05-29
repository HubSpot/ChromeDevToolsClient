package com.hubspot.chrome.devtools.codegen;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import javax.lang.model.element.Modifier;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.google.common.annotations.VisibleForTesting;
import com.hubspot.chrome.devtools.base.ChromeRequest;
import com.hubspot.chrome.devtools.base.ChromeSessionCore;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.MethodSpec.Builder;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

public class Generator {
  private static final String GENERATED_CODE_PACKAGE_NAME = "com.hubspot.chrome.devtools.client.core";
  private static final Logger LOG = LoggerFactory.getLogger(Generator.class);

  ObjectMapper objectMapper;

  public Generator() {
    this.objectMapper = new ObjectMapper();
    configure(this.objectMapper);
  }

  private ObjectMapper configure(ObjectMapper mapper) {
    mapper.registerModule(new GuavaModule());
    mapper.registerModule(new Jdk8Module());

    mapper.configure(JsonParser.Feature.ALLOW_COMMENTS, false);
    mapper.configure(JsonParser.Feature.AUTO_CLOSE_SOURCE, true);
    mapper.configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, true);

    return mapper;
  }

  public static void main(String[] args) {
    Path path = Paths.get(args[0]);

    List<Domain> domains = new ArrayList<>();
    domains.addAll(parseProtocol(path, "/browser_protocol.json"));
    domains.addAll(parseProtocol(path, "/js_protocol.json"));

    generateProtocol(path, domains);
  }

  private static List<Domain> parseProtocol(Path path, String protocolFileName) {
    InputStream resourceStream = Generator.class.getResourceAsStream(protocolFileName);
    return parseProtocol(path, resourceStream);
  }

  private static List<Domain> parseProtocol(Path path, InputStream resourceStream) {
    Generator generator = new Generator();

    try {
      String json = new String(IOUtils.toByteArray(resourceStream), Charset.forName("UTF-8"));
      return generator.parseProtocol(json);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static void generateProtocol(Path path, List<Domain> domains) {
    Generator generator = new Generator();

    Map<Domain, List<TypeSpec>> pojos = new HashMap<>();
    for (Domain domain : domains) {
      generator.generateTypesForDomain(domain, path);
      generator.generateCommandsForDomain(domain, path);
      List<TypeSpec> value = generator.generateEventsForDomain(domain, path);
      pojos.put(domain, value);
    }

    generator.generateEventBase(path);
    generator.generateEventDeserializer(pojos, path);
    generator.generateEventTypeEnum(pojos, path);
  }

  private void generateEventBase(Path packageRoot) {
    TypeSpec event = TypeSpec.classBuilder("Event")
        .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
        .build();

    writeJavaFile(packageRoot, GENERATED_CODE_PACKAGE_NAME, event);
  }

  private void generateEventTypeEnum(Map<Domain, List<TypeSpec>> pojos, Path packageRoot) {
    TypeSpec.Builder builder = TypeSpec.enumBuilder("EventType")
        .addModifiers(Modifier.PUBLIC)
        .addField(String.class, "type", Modifier.PRIVATE, Modifier.FINAL)
        .addField(Class.class, "clazz", Modifier.PRIVATE, Modifier.FINAL)
        .addMethod(MethodSpec.constructorBuilder()
            .addParameter(String.class, "type")
            .addParameter(Class.class, "clazz")
            .addStatement("this.$1N= $1N", "type")
            .addStatement("this.$1N= $1N", "clazz")
            .build())
        .addMethod(MethodSpec.methodBuilder("getType")
            .addAnnotation(JsonValue.class)
            .addModifiers(Modifier.PUBLIC)
            .addStatement("return $N", "type")
            .returns(String.class)
            .build())
        .addMethod(MethodSpec.methodBuilder("getClazz")
            .addAnnotation(JsonValue.class)
            .addModifiers(Modifier.PUBLIC)
            .addStatement("return $N", "clazz")
            .returns(Class.class)
            .build())
        .addMethod(MethodSpec.methodBuilder("toString")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .addStatement("return $N", "type")
            .returns(String.class)
            .build());
    for (Domain domain : pojos.keySet()) {
      for (TypeSpec typeSpec : pojos.get(domain)) {
        String eventName = replaceAtEnd(typeSpec.name, "Event", "");
        String enumName = String.format("%s_%s", domain.getName(), eventName);
        builder.addEnumConstant(
            formatEnumName(String.format("%s_%s", domain.getName(), eventName)),
            TypeSpec.anonymousClassBuilder("$S, $T.class",
                domain.getName() + "." + uncapitalize(eventName),
                getTypeName(typeSpec.name, getPackageName(domain))).build());
      }
    }
    writeJavaFile(packageRoot, GENERATED_CODE_PACKAGE_NAME, builder.build());
  }

  private String replaceAtEnd(String s, String toReplace, String replaceWith) {
    if (s.endsWith(toReplace)) {
      return s.substring(0, s.lastIndexOf(toReplace)) + replaceWith;
    }
    return s;
  }

  private void generateEventDeserializer(Map<Domain, List<TypeSpec>> pojos, Path packageRoot) {
    TypeName abstractEvent = getTypeName("Event", GENERATED_CODE_PACKAGE_NAME);
    TypeName superClass = ParameterizedTypeName.get(ClassName.get(StdDeserializer.class), abstractEvent);

    TypeSpec.Builder builder = TypeSpec.classBuilder("EventDeserializer")
        .superclass(superClass)
        .addField(ObjectMapper.class, "objectMapper", Modifier.PRIVATE, Modifier.FINAL)
        .addModifiers(Modifier.PUBLIC)
        .addMethod(MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addStatement("super($T.class)", abstractEvent)
            .addStatement("this.objectMapper = new ObjectMapper()")
            .addStatement("this.objectMapper.configure($T.$N, false);", DeserializationFeature.class, "FAIL_ON_UNKNOWN_PROPERTIES")
            .build());

    MethodSpec.Builder deserializationBuilder = MethodSpec.methodBuilder("deserialize")
        .addAnnotation(Override.class)
        .addModifiers(Modifier.PUBLIC)
        .returns(abstractEvent)
        .addParameter(JsonParser.class, "p")
        .addParameter(DeserializationContext.class, "context")
        .addException(IOException.class)
        .addException(JsonProcessingException.class)
        .addStatement("$T node = p.readValueAsTree()", JsonNode.class)
        .addStatement("$T field = node.findValue(\"method\")", JsonNode.class)
        .addStatement("String method = field.asText()")
        .beginControlFlow("switch (method)");

    for (Domain domain : pojos.keySet()) {
      for (TypeSpec typeSpec : pojos.get(domain)) {
        deserializationBuilder
            .addCode("case $S: ", domain.getName() + "." + uncapitalize(replaceAtEnd(typeSpec.name, "Event", "")))
            .addCode("{\n$>")
            .addStatement("return objectMapper.readValue(node.findValue(\"params\").toString(), $T.class)", getTypeName(typeSpec.name, getPackageName(domain)))
            .addCode("$<}\n");
      }
    }

    deserializationBuilder.endControlFlow();
    deserializationBuilder.addStatement("throw new $T(\"Unable to deserialize \" + node.toString())", IOException.class);

    builder.addMethod(deserializationBuilder.build());

    writeJavaFile(packageRoot, GENERATED_CODE_PACKAGE_NAME, builder.build());
  }

  private List<TypeSpec> generateEventsForDomain(Domain domain, Path packageRoot) {
    List<TypeSpec> pojos = new ArrayList<>();

    String packageName = getPackageName(domain);
    for (Command event : domain.getEvents()) {
      TypeSpec pojoTypeSpec = generateEventPOJO(event, packageName);
      pojos.add(pojoTypeSpec);
      writeJavaFile(packageRoot, packageName, pojoTypeSpec);
    }
    return pojos;
  }

  @VisibleForTesting
  List<Domain> parseProtocol(String json) throws IOException {
    return objectMapper.readValue(json, Protocol.class).getDomains();
  }

  private void generateTypesForDomain(Domain domain, Path packageRoot) {
    for (Type type : domain.getTypes()) {
      String packageName = getPackageName(domain);
      writeJavaFile(packageRoot, packageName, generateTypeSpec(type, packageName));
    }
  }

  private String getPackageName(Domain domain) {
    return GENERATED_CODE_PACKAGE_NAME + "." + domain.getName().toLowerCase();
  }

  private TypeSpec generateTypeSpec(Type type, String packageName) {
    TypeSpec.Builder builder;
    if (type.getEnum().isPresent()) {
      builder = generateEnumType(type);
    } else if (type.getProperties().isPresent()) {
      builder = generateObjectType(type, packageName);
    } else {
      builder = generatePODType(type, packageName);
    }

    if (type.getDescription().isPresent()) {
      builder.addJavadoc(type.getDescription().get() + "\n");
    }
    return builder.build();
  }

  private TypeSpec generateEventPOJO(Command event, String packageName) {
    TypeSpec.Builder builder = TypeSpec.classBuilder(getEventClassName(event))
        .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
        .superclass(getTypeName("Event", GENERATED_CODE_PACKAGE_NAME));

    MethodSpec.Builder ctorBuilder = MethodSpec.constructorBuilder()
        .addAnnotation(JsonCreator.class)
        .addModifiers(Modifier.PUBLIC);

    for (Property parameter : event.getParameters().orElse(Collections.emptyList())) {
      String name = parameter.getName();

      TypeName fieldType = getTypeName(parameter, packageName);
      builder
          .addField(fieldType, parameter.getName(), Modifier.PRIVATE)
          .addMethod(MethodSpec.methodBuilder("get" + capitalize(name))
          .addModifiers(Modifier.PUBLIC)
          .addStatement("return $N", name)
          .returns(fieldType)
          .build());

      ctorBuilder
          .addParameter(ParameterSpec.builder(fieldType, name)
              .addAnnotation(AnnotationSpec.builder(JsonProperty.class)
                  .addMember("value", "$S", name)
                  .build())
              .build())
          .addStatement("this.$1N = $1N", name);
    }

    builder.addMethod(ctorBuilder.build());

    if (event.getDescription().isPresent()) {
      builder.addJavadoc(event.getDescription().get() + "\n");
    }

    return builder.build();
  }

  private TypeSpec.Builder generateEnumType(Type type) {
    String valueName = "value";
    Class<String> valueType = String.class;
    TypeSpec.Builder builder = TypeSpec.enumBuilder(type.getName())
        .addModifiers(Modifier.PUBLIC)
        .addField(valueType, valueName, Modifier.PRIVATE, Modifier.FINAL)
        .addMethod(MethodSpec.constructorBuilder()
            .addParameter(valueType, valueName)
            .addStatement("this.$1N= $1N", valueName)
            .build())
        .addMethod(MethodSpec.methodBuilder("getValue")
            .addAnnotation(JsonValue.class)
            .addModifiers(Modifier.PUBLIC)
            .addStatement("return $N", valueName)
            .returns(valueType)
            .build())
        .addMethod(MethodSpec.methodBuilder("toString")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .addStatement("return $N", valueName)
            .returns(valueType)
            .build());

    for (String e : type.getEnum().get()) {
      builder.addEnumConstant(
          formatEnumName(e),
          TypeSpec.anonymousClassBuilder("$S", e).build());
    }

    return builder;
  }

  private String formatEnumName(String e) {
    return e
        .replace("-", "_")
        .replaceAll("(\\p{Lower})(\\p{Upper})", "$1_$2")
        .toUpperCase();
  }

  private TypeSpec.Builder generateObjectType(Type type, String packageName) {
    TypeSpec.Builder outerBuilder = TypeSpec.classBuilder(type.getName())
        .addModifiers(Modifier.PUBLIC, Modifier.FINAL);
    MethodSpec.Builder ctorBuilder = MethodSpec.constructorBuilder()
        .addAnnotation(JsonCreator.class)
        .addModifiers(Modifier.PUBLIC);
    TypeSpec.Builder innerBuilder = TypeSpec.classBuilder("Builder")
        .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
        .addMethod(MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PRIVATE)
            .build());
    List<String> ctorParams = new ArrayList<>();

    ClassName builderType = ClassName.get(packageName, type.getName() + ".Builder");

    for (Property property : type.getProperties().get()) {
      String name = property.getName();
      if (name.equals("this")) {
        continue;
      }
      TypeName fieldType = getTypeName(property, packageName);
      outerBuilder
          .addField(fieldType, name, Modifier.PRIVATE)
          .addMethod(MethodSpec.methodBuilder("get" + capitalize(name))
              .addModifiers(Modifier.PUBLIC)
              .addStatement("return $N", name)
              .returns(fieldType)
              .build());

      ctorBuilder
          .addParameter(ParameterSpec.builder(fieldType, name)
              .addAnnotation(AnnotationSpec.builder(JsonProperty.class)
                  .addMember("value", "$S", name)
                  .build())
              .build())
          .addStatement("this.$1N = $1N", name);
      ctorParams.add(name);

      innerBuilder
          .addField(fieldType, name, Modifier.PRIVATE)
          .addMethod(MethodSpec.methodBuilder("set" + capitalize(name))
              .addModifiers(Modifier.PUBLIC)
              .addParameter(fieldType, name)
              .addStatement("this.$1N = $1N", name)
              .addStatement("return this")
              .returns(builderType)
              .build());
    }

    ClassName outerType = ClassName.get(packageName, type.getName());
    innerBuilder.addMethod(MethodSpec.methodBuilder("build")
        .addModifiers(Modifier.PUBLIC)
        .addStatement(String.format("return new $T(%s)", String.join(", ", ctorParams)), outerType)
        .returns(outerType)
        .build());

    outerBuilder
        .addMethod(ctorBuilder.build())
        .addType(innerBuilder.build())
        .addMethod(MethodSpec.methodBuilder("builder")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addStatement("return new $T()", builderType)
            .returns(builderType)
            .build());

    return outerBuilder;
  }

  private TypeSpec.Builder generatePODType(Type type, String packageName) {
    TypeName valueType = getJavaLangTypeName(type.getType(), type.getItems(), packageName);
    String valueName = "value";
    String toStringStatement =
        type.getType().equals("string")
            ? "return " + valueName
            : "return getValue().toString()";

    return TypeSpec.classBuilder(type.getName())
        .addModifiers(Modifier.PUBLIC)
        .addField(valueType, valueName, Modifier.PRIVATE)
        .addMethod(MethodSpec.constructorBuilder()
            .addAnnotation(JsonCreator.class)
            .addModifiers(Modifier.PUBLIC)
            .addParameter(valueType, valueName)
            .addStatement("this.$1N = $1N", valueName)
            .build())
        .addMethod(MethodSpec.methodBuilder("getValue")
            .addAnnotation(JsonValue.class)
            .addModifiers(Modifier.PUBLIC)
            .addStatement("return $N", valueName)
            .returns(valueType)
            .build())
        .addMethod(MethodSpec.methodBuilder("toString")
          .addAnnotation(Override.class)
          .addModifiers(Modifier.PUBLIC)
          .addStatement(toStringStatement)
          .returns(String.class)
          .build());
  }

  private void generateCommandsForDomain(Domain domain, Path packageRoot) {
    String packageName = getPackageName(domain);
    List<TypeSpec> typeSpecs = generateCommandTypeSpec(domain);

    for (TypeSpec typeSpec : typeSpecs) {
      writeJavaFile(packageRoot, packageName, typeSpec);
    }
  }

  private List<TypeSpec> generateCommandTypeSpec(Domain domain) {
    List<TypeSpec> specs = new ArrayList<>();

    String chromeSessionVar = "chromeSession";
    String objectMapperVar = "objectMapper";

    Class<ChromeSessionCore> chromeSessionType = ChromeSessionCore.class;
    Class<ObjectMapper> objectMapperType = ObjectMapper.class;

    TypeSpec.Builder builder = TypeSpec.classBuilder(domain.getName())
        .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
        .addField(chromeSessionType, chromeSessionVar)
        .addField(objectMapperType, objectMapperVar)
        .addMethod(MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addParameter(chromeSessionType, chromeSessionVar)
            .addParameter(objectMapperType, objectMapperVar)
            .addStatement("this.$1N = $1N", chromeSessionVar)
            .addStatement("this.$1N = $1N", objectMapperVar)
            .build());

    for (Command command : domain.getCommands()) {
      Optional<TypeSpec> returnTypeSpec = maybeBuildContainerForMultipleReturns(command, domain);
      if (returnTypeSpec.isPresent()) {
        specs.add(returnTypeSpec.get());
      }

      builder.addMethod(generateMethodSpec(command, domain, Optional.of(domain.getName() + "." + getResultClassName(command)), false));
      builder.addMethod(generateMethodSpec(command, domain, Optional.of(domain.getName() + "." + getResultClassName(command)), true));
    }

    if (domain.getDescription().isPresent()) {
      builder.addJavadoc(domain.getDescription().get() + "\n");
    }
    if (domain.getDeprecated().isPresent()) {
      builder.addAnnotation(Deprecated.class);
    }

    specs.add(builder.build());
    return specs;
  }

  private Optional<TypeSpec> maybeBuildContainerForMultipleReturns(Command command, Domain domain) {
    // Sometimes chrome will return multiple objects. This creates classes that will
    // encapsulate those types into a single class.
    List<Property> returnValues = command.getReturns().orElse(Collections.emptyList());
    if (returnValues.size() > 1) {
      return Optional.of(buildContainerForMultipleReturns(command, domain, returnValues));
    }
    return Optional.empty();
  }

  private TypeSpec buildContainerForMultipleReturns(Command command, Domain domain, List<Property> returnValues) {
    String packageName = getPackageName(domain);

    TypeSpec.Builder builder = TypeSpec.classBuilder(getResultClassName(command))
        .addModifiers(Modifier.PUBLIC, Modifier.FINAL);

    MethodSpec.Builder ctorBuilder = MethodSpec.constructorBuilder()
        .addAnnotation(JsonCreator.class);

    for (Property property : returnValues) {
      String name = property.getName();
      TypeName type = getTypeName(property, packageName);

      builder.addField(type, name, Modifier.PUBLIC);

      AnnotationSpec.Builder jsonPropertyBuilder = AnnotationSpec.builder(JsonProperty.class)
          .addMember("value", "$S",name);
      if (!property.getOptional().orElse(false)) {
        jsonPropertyBuilder.addMember("required", "$L", true);
      }

      ctorBuilder
          .addParameter(ParameterSpec.builder(type, name)
              .addAnnotation(jsonPropertyBuilder.build())
              .build())
          .addStatement("this.$1N = $1N", name);
    }

    return builder
        .addMethod(ctorBuilder.build())
        .build();
  }

  private String getResultClassName(Command command) {
    return capitalize(command.getName()) + "Result";
  }

  private String getEventClassName(Command event) {
    return capitalize(event.getName()) + "Event";
  }

  private MethodSpec generateMethodSpec(Command command, Domain domain, Optional<String> returnTypeName, boolean async) {
    String methodName = command.getName();
    String sendCommand = "chromeSession.send";
    if (async) {
      methodName += "Async";
      sendCommand += "Async";
    }
    Builder methodBuilder = MethodSpec.methodBuilder(methodName)
        .addModifiers(Modifier.PUBLIC)
        .addStatement("$1T chromeRequest = new $1T($2S)", ChromeRequest.class, domain.getName() + "." + command.getName());

    boolean hasParams = false;
    List<String> parameterDescriptions = new ArrayList<>();
    String packageName = getPackageName(domain);
    CodeBlock.Builder putParamsBuilder = CodeBlock.builder().add("chromeRequest");

    for (Property property : command.getParameters().orElse(Collections.emptyList())) {
      hasParams = true;
      methodBuilder.addParameter(getTypeName(property, packageName), property.getName());
      if (property.getDescription().isPresent()) {
        parameterDescriptions.add(formatParamForJavadoc(property));
      }
      putParamsBuilder.add("\n.putParams($1S, $1N)", property.getName());
    }
    if (hasParams) {
      methodBuilder.addStatement(putParamsBuilder.build());
    }

    List<Property> returnValues = command.getReturns().orElse(Collections.emptyList());
    if (returnValues.size() == 0) {
      methodBuilder.addStatement(sendCommand + "(chromeRequest)");
    } else {
      ClassPackageResolver classPackageResolver = new ClassPackageResolver(packageName, returnTypeName.get());
      TypeName valueType = returnValues.size() == 1
          ? getTypeName(returnValues.get(0), packageName)
          : ClassName.get(classPackageResolver.getPackageName(),
                          classPackageResolver.getClassName());

      TypeName returnType = valueType;
      if (async) {
        returnType = ParameterizedTypeName.get(ClassName.get(CompletableFuture.class), returnType);
      }
      methodBuilder
          .returns(returnType)
          .addStatement("return " + sendCommand + "(chromeRequest, new $T<$T>(){})", ClassName.get(TypeReference.class), valueType);
    }

    if (command.getDescription().isPresent()) {
      String description = command.getDescription().get() + "\n";
      if (parameterDescriptions.size() > 0) {
        description += "\n" + String.join("\n", parameterDescriptions) + "\n";
      }

      // TODO: javapoet tries to interpolate "$"'s. Not a great workaround, but it's fine for now.
      description = description.replace("$", "");

      methodBuilder.addJavadoc(description);
    }
    if (command.getDeprecated().orElse(false)) {
      methodBuilder.addAnnotation(Deprecated.class);
    }

    return methodBuilder.build();
  }

  private String formatParamForJavadoc(Property property) {
    String optional = property.getOptional().orElse(false) ? "[Optional]" : "";
    return String.format("@param %s %s %s", property.getName(), optional, property.getDescription().get());
  }

  private TypeName getTypeName(Property property, String packageName) {
    if (property.getRef().isPresent()) {
      ClassPackageResolver resolver = new ClassPackageResolver(packageName, property.getRef().get());
      return ClassName.get(resolver.getPackageName(), resolver.getClassName());
    }
    return getJavaLangTypeName(property.getType().get(), property.getItems(), packageName);
  }

  private TypeName getTypeName(Item item, String packageName) {
    if (item.getRef().isPresent()) {
      ClassPackageResolver resolver = new ClassPackageResolver(packageName, item.getRef().get());
      return ClassName.get(resolver.getPackageName(), resolver.getClassName());
    }
    return getJavaLangTypeName(item.getType().get(), Optional.empty(), packageName);
  }

  private TypeName getTypeName(String referenceName, String packageName) {
    ClassPackageResolver resolver = new ClassPackageResolver(packageName, referenceName);
    return ClassName.get(resolver.getPackageName(), resolver.getClassName());
  }

  private TypeName getJavaLangTypeName(String typeName, Optional<Item> typeParam, String packageName) {
    if (typeName.equals("number")) {
      return ClassName.get(Number.class);
    }
    if (typeName.equals("any")) {
      return ClassName.get(Object.class);
    }
    if (typeName.equals("array")) {
      return ParameterizedTypeName.get(
          ClassName.get(List.class),
          getTypeName(typeParam.get(), packageName));
    }
    return ClassName.get("java.lang", capitalize(typeName));
  }

  private void writeJavaFile(Path packageRoot, String packageName, TypeSpec typeSpec) {
    JavaFile javaFile = JavaFile.builder(packageName, typeSpec)
        .skipJavaLangImports(true)
        .build();
    try {
      javaFile.writeTo(packageRoot);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  static String capitalize(String s) {
    return s.substring(0, 1).toUpperCase() + s.substring(1);
  }

  static String uncapitalize(String s) {
    return s.substring(0, 1).toLowerCase() + s.substring(1);
  }
}
