package com.hubspot.chrome.devtools.client.examples;

import com.hubspot.chrome.devtools.client.ChromeDevToolsBrowserContext;
import com.hubspot.chrome.devtools.client.ChromeDevToolsClient;
import com.hubspot.chrome.devtools.client.core.EventType;
import com.hubspot.chrome.devtools.client.core.browser.BrowserContextID;
import com.hubspot.chrome.devtools.client.core.network.Cookie;
import java.net.URISyntaxException;
import java.util.List;
import java.util.concurrent.Semaphore;

// Run chrome with args --headless --disable-gpu --remote-debugging-port=9292
public class BrowserContextExample {

  private static final String URL = "https://www.example.com/";

  public static void main(String[] args) throws URISyntaxException {
    // Create the client
    ChromeDevToolsClient client = ChromeDevToolsClient.defaultClient();

    // Get a browser context
    try (
      ChromeDevToolsBrowserContext context = client.createBrowserContext(
        "127.0.0.1",
        9292
      )
    ) {
      context.attach();

      final Semaphore sem = new Semaphore(0);
      context.addEventConsumer(EventType.PAGE_LOAD_EVENT_FIRED, event -> sem.release());
      context.getPage().enable();

      context.navigate(URL);
      final boolean loaded = sem.tryAcquire(1);
      if (loaded) {
        System.out.println("Loaded: " + URL);

        final BrowserContextID browserContextId = context.getBrowserContextId();
        List<Cookie> cookies = context.getStorage().getCookies(browserContextId);
        for (var cookie : cookies) {
          System.out.println("cookie: " + cookie.getName() + "=" + cookie.getValue());
        }
      } else {
        System.out.println("Failed to load: " + URL);
      }
    }

    // Close the client when we are done with it to cleanly shut down executors
    client.close();

    System.exit(0);
  }
}
