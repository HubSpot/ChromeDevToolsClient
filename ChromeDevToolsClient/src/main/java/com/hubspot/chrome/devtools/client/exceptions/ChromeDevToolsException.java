package com.hubspot.chrome.devtools.client.exceptions;

public class ChromeDevToolsException extends RuntimeException {
  private final Integer code;

  public ChromeDevToolsException(String message) {
    super(message);
    this.code = null;
  }

  public ChromeDevToolsException(String message, Integer code) {
    super(message);
    this.code = code;
  }

  public ChromeDevToolsException(Throwable cause) {
    super(cause);
    this.code = null;
  }

  public ChromeDevToolsException(String message, Throwable cause) {
    super(message, cause);
    this.code = null;
  }

  public Integer getCode() {
    return code;
  }
}
