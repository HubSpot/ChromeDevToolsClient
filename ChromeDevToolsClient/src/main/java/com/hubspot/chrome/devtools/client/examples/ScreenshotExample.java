package com.hubspot.chrome.devtools.client.examples;

import java.util.Base64;
import java.util.List;

import com.hubspot.chrome.devtools.client.ChromeDevToolsClient;
import com.hubspot.chrome.devtools.client.ChromeDevToolsSession;
import com.hubspot.chrome.devtools.client.FileExtension;
import com.hubspot.chrome.devtools.client.core.dom.BoxModel;
import com.hubspot.chrome.devtools.client.core.page.Viewport;

// Run chrome with args --headless --disable-gpu --remote-debugging-port=9292
public class ScreenshotExample {
  private static String url = "https://www.google.com/";
  private static String cssSelector = "#searchform.jhp";

  public static void main(String[] args) {
    // Create the client
    ChromeDevToolsClient client = ChromeDevToolsClient.defaultClient();

    // Connect to Chrome and get a session
    try (ChromeDevToolsSession session = client.connect("127.0.0.1", 9292)) {
      // Add an event listener
      session.addEventListener("listenerId", (t, e) -> {
        System.out.println("Event Type Is: " + t.toString());
        System.out.println("Event Class Is: " + e.getClass().getName());
      });

      // Navigate to the specified url
      session.navigate(url);

      // Wait for the page to load with a timeout of 5 seconds
      session.waitDocumentReady(5000);

      // Locate the element on the page
      BoxModel boxModel = session.getDOM().getBoxModel(session.getNodeId(cssSelector), null, null);
      int width = boxModel.getWidth();
      int height = boxModel.getHeight(); // includes shadows

      // [x-top-left, y-top-left, x-top-right, y-top-right, x-bottom-right, y-bottom-right] does not include shadows
      List<Number> contentLocation = boxModel.getContent().getValue();

      // Find the section of the page we want to capture in our screenshot
      Viewport clip = Viewport.builder()
          .setX(contentLocation.get(0))
          .setY(contentLocation.get(1))
          .setWidth(((double) width))
          .setHeight(((double) height))
          .setScale(1.0)
          .build();

      // Get the screenshot data as a base 64 encoded string
      String base64Data = session.getPage().captureScreenshot("png", null, clip, null);
      byte[] data = Base64.getDecoder().decode(base64Data);

      // Alternatively use the client shortcut to capture either a PNG or PDF screenshot
      byte[] pdfData = session.captureScreenshot(FileExtension.PDF);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    // Close the client when we are done with it to cleanly shut down executors
    client.close();
  }
}
