package com.hubspot.chrome.devtools.client.exceptions;

public class ChromeDevToolsException extends RuntimeException {

  public ChromeDevToolsException(String message) {
    super(message);
  }

  public ChromeDevToolsException(Throwable cause) {
    super(cause);
  }

  public ChromeDevToolsException(String message, Throwable cause) {
    super(message, cause);
  }

}
