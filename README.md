# ChromeDevToolsClient

A java websocket client for the [Chrome DevTools Protocol](https://chromedevtools.github.io/debugger-protocol-viewer/tot/). Latest release built against protocol version `1.3` (will generally track the latest stable version of the protocol)

## Including Via Maven

```xml
<dependency>
  <groupId>com.hubspot.chrome</groupId>
  <artifactId>ChromeDevToolsClient</artifactId>
  <version>0.1.0</version>
</dependency>
```

## Usage

Run Chrome with remote debugging enabled in headless mode
```bash
google-chrome --headless --disable-gpu --remote-debugging-port=9292
```

Connect to Chrome using the `ChromeDevToolsClient` class. The `ChromeDevToolsClient` can create multiple connections to separate instances of Chrome. Each connection is a separate `ChromeDevToolsSession`.

```java
// Create a client
ChromeDevToolsClient client = ChromeDevToolsClient.defaultClient();

// Connect to Chrome Dev Tools Running on port 9292 on your local machine
try (ChromeDevToolsSession session = client.connect("127.0.0.1", 9292)) {
  // Control Chrome remotely
  session.navigate("https://www.hubspot.com/");
}

// Close the client when your finished
client.close();
```

The `ChromeDevToolsSession` class provides all methods for interacting with Chrome. There are synchronous and asynchronous versions available for each method.

You can also check out our [examples](TODO) for more.

## Configuring ChromeDevToolsClient

You can construct your dev tools client with the following options:

```java
ChromeDevToolsClient client = new ChromeDevToolsClient.Builder()

    /*
     * An instance of com.hubspot.horizon.HttpClient. The HttpClient is used hit the devtools /json/list endpoint to grab the first available target id to control
     * Default: the default com.hubspot.horizon.ning.NingHttpClient
     */
    .setHttpClient()

    /*
     * The timeout for getting a valid response from the /json/list endpoint via http. If no valid response is received in this time a ChromeDevToolsException is thrown
     * Default: 5 seconds
     */
    .setSessionConnectTimeoutMillis()

    /*
     * A global timeout for any action performed in the client. If the client does not receive a response for an action taken in this amount of time
     * a ChromeDevToolsException is thrown
     * Default: 60 seconds
     */
    .setActionTimeoutMillis()

    /*
     * An instnace of java.util.concurrent.ExecutorService which provides the thread pool for event listeners and any async actions performed in the client
     * Default: A ThreadPoolExecutor with a max pool size of 10 threads
     */
    .setExecutorService()

    /*
     * An instance of com.fasterxml.jackson.databind.ObjectMapper responsible for (de)serializing the json sent to and received from Chrome.
     * The default ObjectMapper for the client is configured with a custom deserializer for Chrome events. You will generally not need to update this
     */
    .setObjectMapper()
    .build();
```
