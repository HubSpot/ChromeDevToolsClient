package com.hubspot.chrome.devtools.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hubspot.chrome.devtools.base.ChromeRequest;
import com.hubspot.chrome.devtools.client.core.browser.BrowserContextID;
import com.hubspot.chrome.devtools.client.core.target.SessionID;
import com.hubspot.chrome.devtools.client.core.target.Target;
import com.hubspot.chrome.devtools.client.core.target.TargetID;
import java.net.URI;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChromeDevToolsBrowserContext extends ChromeDevToolsSession {

  private static final Logger LOG = LoggerFactory.getLogger(
    ChromeDevToolsBrowserContext.class
  );
  private static final String BLANK_TAB = "about:blank";
  private BrowserContextID browserContextId;
  private SessionID sessionId;

  ChromeDevToolsBrowserContext(
    final URI uri,
    final ObjectMapper objectMapper,
    final ExecutorService executorService,
    final long actionTimeoutMillis
  ) {
    super(uri, objectMapper, executorService, actionTimeoutMillis);
    this.browserContextId = null;
    this.sessionId = null;
  }

  public void attach() {
    if (sessionId == null) {
      final Target target = getTarget();
      browserContextId = target.createBrowserContext();
      final TargetID targetId = target.createTarget(
        BLANK_TAB,
        null, // left
        null, // top
        null, // width
        null, // height
        null, // windowState
        browserContextId
      );

      sessionId = target.attachToTarget(targetId, true);
    } else {
      LOG.warn("Already attached. sessionId={}", sessionId);
    }
  }

  public BrowserContextID getBrowserContextId() {
    return browserContextId;
  }

  @Override
  public void close() throws Exception {
    if (browserContextId != null) {
      sessionId = null;
      getTarget().disposeBrowserContext(browserContextId);
      browserContextId = null;
      super.close();
    } else {
      LOG.debug("Not attached");
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!super.equals(o)) {
      return false;
    }
    if (getClass() != o.getClass()) {
      return false;
    }
    final ChromeDevToolsBrowserContext other = (ChromeDevToolsBrowserContext) o;
    return (
      Objects.equals(
        browserContextId != null ? browserContextId.getValue() : null,
        other.browserContextId != null ? other.browserContextId.getValue() : null
      ) &&
      Objects.equals(
        sessionId != null ? sessionId.getValue() : null,
        other.sessionId != null ? other.sessionId.getValue() : null
      )
    );
  }

  @Override
  public int hashCode() {
    return Objects.hash(
      super.hashCode(),
      browserContextId != null ? browserContextId.getValue() : null,
      sessionId != null ? sessionId.getValue() : null
    );
  }

  @Override
  void sendChromeRequest(ChromeRequest request) {
    addBrowserContextSessionIdIfRequired(request);
    super.sendChromeRequest(request);
  }

  private void addBrowserContextSessionIdIfRequired(ChromeRequest request) {
    if (sessionId != null) {
      request.setSessionId(sessionId.getValue());
    }
  }
}
