package com.hubspot.chrome.devtools.client;

import java.io.IOException;
import java.net.URI;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.rholder.retry.RetryException;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import com.google.common.base.Predicates;
import com.hubspot.chrome.devtools.base.ChromeRequest;
import com.hubspot.chrome.devtools.base.ChromeResponse;
import com.hubspot.chrome.devtools.base.ChromeSessionCore;
import com.hubspot.chrome.devtools.client.core.EventType;
import com.hubspot.chrome.devtools.client.core.accessibility.Accessibility;
import com.hubspot.chrome.devtools.client.core.animation.Animation;
import com.hubspot.chrome.devtools.client.core.applicationcache.ApplicationCache;
import com.hubspot.chrome.devtools.client.core.audits.Audits;
import com.hubspot.chrome.devtools.client.core.browser.Browser;
import com.hubspot.chrome.devtools.client.core.cachestorage.CacheStorage;
import com.hubspot.chrome.devtools.client.core.css.CSS;
import com.hubspot.chrome.devtools.client.core.database.Database;
import com.hubspot.chrome.devtools.client.core.debugger.Debugger;
import com.hubspot.chrome.devtools.client.core.deviceorientation.DeviceOrientation;
import com.hubspot.chrome.devtools.client.core.dom.BoxModel;
import com.hubspot.chrome.devtools.client.core.dom.DOM;
import com.hubspot.chrome.devtools.client.core.dom.NodeId;
import com.hubspot.chrome.devtools.client.core.dom.Quad;
import com.hubspot.chrome.devtools.client.core.domdebugger.DOMDebugger;
import com.hubspot.chrome.devtools.client.core.domsnapshot.DOMSnapshot;
import com.hubspot.chrome.devtools.client.core.domstorage.DOMStorage;
import com.hubspot.chrome.devtools.client.core.emulation.Emulation;
import com.hubspot.chrome.devtools.client.core.headlessexperimental.HeadlessExperimental;
import com.hubspot.chrome.devtools.client.core.heapprofiler.HeapProfiler;
import com.hubspot.chrome.devtools.client.core.indexeddb.IndexedDB;
import com.hubspot.chrome.devtools.client.core.input.Input;
import com.hubspot.chrome.devtools.client.core.inspector.Inspector;
import com.hubspot.chrome.devtools.client.core.io.IO;
import com.hubspot.chrome.devtools.client.core.layertree.LayerTree;
import com.hubspot.chrome.devtools.client.core.log.Log;
import com.hubspot.chrome.devtools.client.core.memory.Memory;
import com.hubspot.chrome.devtools.client.core.network.Network;
import com.hubspot.chrome.devtools.client.core.overlay.Overlay;
import com.hubspot.chrome.devtools.client.core.page.FrameId;
import com.hubspot.chrome.devtools.client.core.page.NavigateResult;
import com.hubspot.chrome.devtools.client.core.page.Page;
import com.hubspot.chrome.devtools.client.core.page.PrintToPDFResult;
import com.hubspot.chrome.devtools.client.core.performance.Performance;
import com.hubspot.chrome.devtools.client.core.profiler.Profiler;
import com.hubspot.chrome.devtools.client.core.runtime.CallArgument;
import com.hubspot.chrome.devtools.client.core.runtime.CallFunctionOnResult;
import com.hubspot.chrome.devtools.client.core.runtime.EvaluateResult;
import com.hubspot.chrome.devtools.client.core.runtime.RemoteObject;
import com.hubspot.chrome.devtools.client.core.runtime.RemoteObjectId;
import com.hubspot.chrome.devtools.client.core.runtime.Runtime;
import com.hubspot.chrome.devtools.client.core.security.Security;
import com.hubspot.chrome.devtools.client.core.serviceworker.ServiceWorker;
import com.hubspot.chrome.devtools.client.core.storage.Storage;
import com.hubspot.chrome.devtools.client.core.systeminfo.SystemInfo;
import com.hubspot.chrome.devtools.client.core.target.Target;
import com.hubspot.chrome.devtools.client.core.tethering.Tethering;
import com.hubspot.chrome.devtools.client.core.tracing.Tracing;
import com.hubspot.chrome.devtools.client.exceptions.ChromeDevToolsException;

public class ChromeDevToolsSession implements ChromeSessionCore {
  private final Logger LOG = LoggerFactory.getLogger(ChromeDevToolsSession.class);

  public static final long DEFAULT_TIMEOUT_MILLIS = 10000L;
  public static final long DEFAULT_PERIOD_MILLIS = 10L;

  private final ChromeWebSocketClient websocket;
  private final ObjectMapper objectMapper;
  private final ExecutorService executorService;
  private final UUID id;

  private final Map<String, ChromeEventListener> chromeEventListeners;

  public ChromeDevToolsSession(URI uri,
                               ObjectMapper objectMapper,
                               ExecutorService executorService,
                               long actionTimeoutMillis) {
    this.chromeEventListeners = new ConcurrentHashMap<>();
    this.websocket = new ChromeWebSocketClient(uri, objectMapper, chromeEventListeners, executorService, actionTimeoutMillis);
    this.objectMapper = objectMapper;
    this.executorService = executorService;
    this.id = UUID.randomUUID();

    try {
      this.websocket.connectBlocking();
    } catch (Throwable t) {
      throw new ChromeDevToolsException(String.format("Could not connect to uri %s", uri), t);
    }
  }

  public ChromeDevToolsSession(Map<String, ChromeEventListener> chromeEventListeners,
                               ChromeWebSocketClient chromeWebSocketClient,
                               ObjectMapper objectMapper,
                               ExecutorService executorService) {
    this.chromeEventListeners = chromeEventListeners;
    this.websocket = chromeWebSocketClient;
    this.objectMapper = objectMapper;
    this.executorService = executorService;
    this.id = UUID.randomUUID();
  }

  @Override
  public void send(ChromeRequest request) {
    sendChromeRequest(request);
    websocket.getResponse(request.getId());
  }

  @Override
  public <T> T send(ChromeRequest request, TypeReference<T> valueType) {
    sendChromeRequest(request);
    ChromeResponse response = websocket.getResponse(request.getId());
    return parseChromeResponse(response, valueType);
  }

  public CompletableFuture<Void> sendAsync(ChromeRequest request) {
    return CompletableFuture.runAsync(() -> {
      sendChromeRequest(request);
      websocket.getResponse(request.getId());
    }, executorService);
  }

  @Override
  public <T> CompletableFuture<T> sendAsync(ChromeRequest request, TypeReference<T> valueType) {
    return CompletableFuture.supplyAsync(() -> {
      sendChromeRequest(request);
      return parseChromeResponse(websocket.getResponse(request.getId()), valueType);
    }, executorService);
  }

  private void sendChromeRequest(ChromeRequest request) {
    try {
      String json = objectMapper.writeValueAsString(request);
      LOG.trace("Sending request: {}", json);
      websocket.send(json);
    } catch (IOException e) {
      throw new ChromeDevToolsException(e);
    }
  }

  private <T> T parseChromeResponse(ChromeResponse response, TypeReference<T> valueType) {
    // Most methods return a single element of data. To eliminate the user needing to access this
    // single element via a pass through method, we skip the root node and map the element's data right
    // into the data structure we want (i.e. we don't parse the root node itself).
    //
    //   e.g. { "result" : { "browserContextId" : "some_id" } }
    //                               ^--- ignore      ^--- consume directly so user can act directly on string
    //
    //       Allows the user to do `callingMethod()` instead of `callingMethod().getBrowserContextId()`.
    //
    // If this fails, then the method is one of the few cases where there are multiple
    // results that need to be mapped into a parent data structure.
    //
    //   e.g. { "result" : { "protocolVersion" : "1.2.3" }, { "jsVersion" : "6.6.8" } }
    //                    |__________________________________________________________|
    //                                       `--------- must consume all so user can select which element to work with
    //
    //       Here the user must do `callingMethod().getProtocolVersion()` or `someMethod().getJsVersion()`.
    Iterator<JsonNode> elements = response.getResult().elements();
    JsonNode first = elements.next();
    try {
      // We do our best to predict which kind of result to consume the response as, but there's
      // a small chance that a multi-result response has optional, absent members, and we try and
      // fail to parse it as a single-result response, which is why we catch the inner JsonMappingException.
      if (elements.hasNext()) {
        return objectMapper.readValue(response.getResult().toString(), valueType);
      } else {
        return objectMapper.readValue(objectMapper.treeAsTokens(first), valueType);
      }
    } catch (JsonMappingException e) {
      try {
        return objectMapper.readValue(response.getResult().toString(), valueType);
      } catch (IOException e1) {
        throw new ChromeDevToolsException(e1);
      }
    } catch (IOException e2) {
      throw new ChromeDevToolsException(e2);
    }
  }

  @Override
  public void close() throws Exception {
    chromeEventListeners.clear();
    websocket.closeBlocking();
  }

  public boolean isConnected() {
    return websocket.isOpen();
  }

  public void waitDocumentReady() {
    waitDocumentReady(DEFAULT_TIMEOUT_MILLIS, DEFAULT_PERIOD_MILLIS);
  }

  public void waitDocumentReady(long timeoutMillis) {
    waitDocumentReady(timeoutMillis, DEFAULT_PERIOD_MILLIS);
  }

  public void waitDocumentReady(long timeoutMillis, long periodMillis) {
    Retryer<Boolean> retryer = RetryerBuilder.<Boolean>newBuilder()
        .retryIfResult(Predicates.equalTo(false))
        .withStopStrategy(StopStrategies.stopAfterDelay(timeoutMillis))
        .withWaitStrategy(WaitStrategies.fixedWait(periodMillis, TimeUnit.MILLISECONDS))
        .build();
    try {
      retryer.call(() -> (Boolean) evaluate("document.readyState === \"complete\"").result.getValue());
    } catch (ExecutionException| RetryException e) {
      throw new ChromeDevToolsException(e);
    }
  }

  public boolean waitUntil(Predicate<ChromeDevToolsSession> predicate) {
    return waitUntil(predicate, DEFAULT_TIMEOUT_MILLIS);
  }

  public boolean waitUntil(Predicate<ChromeDevToolsSession> predicate, long timeoutMillis) {
    return waitUntil(predicate, timeoutMillis, DEFAULT_PERIOD_MILLIS);
  }

  public boolean waitUntil(Predicate<ChromeDevToolsSession> predicate, long timeoutMillis, long periodMillis) {
    Retryer<Boolean> retryer = RetryerBuilder.<Boolean>newBuilder()
        .retryIfResult(Predicates.equalTo(false))
        .withStopStrategy(StopStrategies.stopAfterDelay(timeoutMillis))
        .withWaitStrategy(WaitStrategies.fixedWait(periodMillis, TimeUnit.MILLISECONDS))
        .build();

    try {
      retryer.call(() -> predicate.test(this));
    } catch (ExecutionException | RetryException e) {
      return false;
    }
    return true;
  }

  public NavigateResult navigate(String url) {
    return getPage().navigate(url, null, null, null);
  }

  public String getUrl() {
    return getDOM().getDocument(null, null).getDocumentURL();
  }

  public EvaluateResult evaluate(String javascript) {
    return getRuntime().evaluate(javascript, null, null, null, null, null, null, null, null, null, null);
  }

  public FrameId getFrameId() {
    return getDOM().getDocument(null, null).getFrameId();
  }

  public List<NodeId> getNodeIds(String selector) {
    return getDOM().querySelectorAll(getDOM().getDocument(1, null).getNodeId(), selector);
  }

  public NodeId getNodeId(String selector) {
    List<NodeId> nodeIds = getNodeIds(selector);
    return nodeIds.isEmpty() ? null : nodeIds.get(0);
  }

  public byte[] captureScreenshot() {
    return captureScreenshot(FileExtension.PNG);
  }

  public byte[] captureScreenshot(FileExtension extension) {
    String data = getPage().captureScreenshot(extension.name().toLowerCase(), null, null, null);
    return Base64.getDecoder().decode(data);
  }

  public byte[] printToPDF() {
    PrintToPDFResult result = getPage().printToPDF(null, null, null, null,
        null, null, null, null, null, null,
        null, null, null, null, null, null);
    return Base64.getDecoder().decode(result.data);
  }

  public String getId() {
    return id.toString();
  }

  public void addEventListener(String listenerId, ChromeEventListener chromeEventListener) {
    if (chromeEventListener != null && listenerId != null) {
      chromeEventListeners.put(listenerId, chromeEventListener);
    } else {
      LOG.warn("Event listener or listenerId was null, not adding");
    }
  }

  /**
   * Registers a ChromeEventListener that consumes all events of a given type.
   *
   * Some CDTP domains must be explicitly enabled for their events to be captured. For example,
   * to receive RUNTIME_CONSOLE_APICALLED events, the client must first enable the runtime domain
   * (`session.getRuntime().enable()`).
   *
   * Example:
   *
   *    EventType eventType = EventType.RUNTIME_CONSOLE_APICALLED;
   *    List<ConsoleAPICalledEvent> events = new ArrayList<>();
   *    chromeDevToolsSession.addEventConsumer(eventType, e -> LOG.info("Event occurred: {}", (ConsoleAPICalledEvent) e));
   *
   * @param  eventType The type of event to listen for.
   * @param  eventConsumer A `Consumer` that consumes events related to `eventType` (i.e. `eventType.getClazz()`).
   * @return The id of the listener created. Passing this to `removeEventListener` will remove the listener and stop the capturing of events.
   */
  public <T> String addEventConsumer(EventType eventType, Consumer<T> eventConsumer) {
    String listenerId = String.format("ChromeDevToolsSession-%s-%sConsumer-%s", id, eventType.getClazz().toString(), chromeEventListeners.size() + 1);
    addEventListener(listenerId, createEventListener(eventType, eventConsumer));
    return listenerId;
  }

  private <T> ChromeEventListener createEventListener(EventType eventType, Consumer<T> eventConsumer) {
    return (type, event) -> {
      try {
        if (type == eventType) {
          eventConsumer.accept((T) event);
        }
      } catch (Throwable t) {
        LOG.warn("Could not get {}", eventType.getClazz().toString(), t);
      }
    };
  }

  public void removeEventListener(String listenerId) {
    if (listenerId != null) {
      chromeEventListeners.remove(listenerId);
    }
  }

  /**
   * Creates and registers a ChromeEventListener that adds all events of a given type to a
   * collection as the events occur.
   *
   * Some CDTP domains must be explicitly enabled for their events to be captured. For example,
   * to receive RUNTIME_CONSOLE_APICALLED events, the client must first enable the runtime domain
   * (`session.getRuntime().enable()`).
   *
   * Example:
   *
   *    EventType eventType = EventType.RUNTIME_CONSOLE_APICALLED;
   *    List<ConsoleAPICalledEvent> events = new ArrayList<>();
   *    chromeDevToolsSession.collectEvents(eventType, events);
   *
   *    // Do things that trigger CONSOLE_MESSAGE_ADDED events
   *    // All events will appear in
   *
   *    assertThat(events.getsize()).isGreaterThan(0);
   *
   * @param  eventType The type of event to listen for.
   * @param  events The collection that event data will be added to whenever an event of `eventType` occurs.
   *                Note that the data type of this collection must match that of `eventType.getClazz()` or else
   *                a `ClassCastException` will be thrown.
   * @return The id of the listener created. Passing this to `removeEventListener` will remove the listener and stop the capturing of events.
   */
  public <T> String collectEvents(EventType eventType, Collection<T> events) {
    String listenerId = String.format("ChromeDevToolsSession-%s-%sCollector-%s", id, eventType.getClazz().toString(), chromeEventListeners.size() + 1);
    addEventListener(listenerId, createEventListener(eventType, events::add));
    return listenerId;
  }

  public Object getProperty(String selector, String property) {
    RemoteObjectId remoteObjectId = getObjectId(selector);
    if (remoteObjectId == null) {
      return null;
    }
    System.out.println(remoteObjectId);
    return getValueFromObjectId(remoteObjectId, property);
  }

  public Object getValueFromObjectId(RemoteObjectId remoteObjectId, String property) {
    CallFunctionOnResult callfunction = getRuntime().callFunctionOn(
        "function(property) { return property.split('.').reduce((o, i) => o[i], this); }",
        remoteObjectId,
        Collections.singletonList(CallArgument.builder().setValue(property).build()),
        false, true, false, false, null, null, null
    );
    if (callfunction == null || callfunction.result == null) {
      return null;
    }
    RemoteObject result = callfunction.result;
    getRuntime().releaseObject(remoteObjectId);
    return result.getValue();
  }

  public RemoteObjectId getObjectId(String selector) {
    DOM dom = getDOM();
    NodeId root = dom.getDocument(null, null).getNodeId();
    if (root == null) {
      return null;
    }
    NodeId selectedNodeId = dom.querySelector(root, selector);
    if (selectedNodeId == null) {
      return null;
    }
    RemoteObject remoteObject = dom.resolveNode(selectedNodeId, null, null, null);
    if (remoteObject == null) {
      return null;
    }
    return remoteObject.getObjectId();
  }

  public String getLocation() {
    return getDOM().getDocument(null, null).getDocumentURL();
  }

  public boolean click(String selector) {
    NodeId selectedNodeId = getNodeId(selector);
    if (selectedNodeId == null) {
      return false;
    }
    BoxModel boxModel = getDOM().getBoxModel(selectedNodeId, null, null);
    if (boxModel == null) {
      return false;
    }
    Quad content = boxModel.getContent();
    if (content == null || content.getValue().isEmpty() || content.getValue().size() < 2) {
      return false;
    }
    double left = Math.floor(content.getValue().get(0).doubleValue());
    double top  = Math.floor(content.getValue().get(1).doubleValue());
    int clickCount = 1;
    Input input = getInput();
    input.dispatchMouseEvent("mousePressed", left, top, null, null,"left", null, clickCount, null, null, null);
    input.dispatchMouseEvent("mouseReleased", left, top, null, null, "left", null, clickCount, null, null, null);
    return true;
  }

  @Override
  public String toString() {
    return "ChromeSessionID:" + getId();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (!ChromeDevToolsSession.class.isAssignableFrom(obj.getClass())) {
      return false;
    }
    final ChromeDevToolsSession other = (ChromeDevToolsSession) obj;
    return getId().equals(other.getId());
  }

  @Override
  public int hashCode() {
    return Objects.hash(websocket);
  }

  // Note: I've left out two deprecated classes. This could make it harder for users
  // to transition, but if they're transitioning to this anyways, they may as well.
  public Accessibility getAccessibility() { return new Accessibility(this, objectMapper); }
  public Animation getAnimation() { return new Animation(this, objectMapper); }
  public ApplicationCache getApplicationCache() { return new ApplicationCache(this, objectMapper); }
  public Audits getAudits() { return new Audits(this, objectMapper); }
  public Browser getBrowser() { return new Browser(this, objectMapper); }
  public CacheStorage getCacheStorage() { return new CacheStorage(this, objectMapper); }
  public CSS getCSS() { return new CSS(this, objectMapper); }
  public Database getDatabase() { return new Database(this, objectMapper); }
  public Debugger getDebugger() { return new Debugger(this, objectMapper); }
  public DeviceOrientation getDeviceOrientation() { return new DeviceOrientation(this, objectMapper); }
  public DOM getDOM() { return new DOM(this, objectMapper); }
  public DOMDebugger getDOMDebugger() { return new DOMDebugger(this, objectMapper); }
  public DOMSnapshot getDOMSnapshot() { return new DOMSnapshot(this, objectMapper); }
  public DOMStorage getDOMStorage() { return new DOMStorage(this, objectMapper); }
  public Emulation getEmulation() { return new Emulation(this, objectMapper); }
  public HeadlessExperimental getHeadlessExperimental() { return new HeadlessExperimental(this, objectMapper); }
  public HeapProfiler getHeapProfiler() { return new HeapProfiler(this, objectMapper); }
  public IndexedDB getIndexedDB() { return new IndexedDB(this, objectMapper); }
  public Input getInput() { return new Input(this, objectMapper); }
  public Inspector getInspector() { return new Inspector(this, objectMapper); }
  public IO getIO() { return new IO(this, objectMapper); }
  public LayerTree getLayerTree() { return new LayerTree(this, objectMapper); }
  public Log getLog() { return new Log(this, objectMapper); }
  public Memory getMemory() { return new Memory(this, objectMapper); }
  public Network getNetwork() { return new Network(this, objectMapper); }
  public Overlay getOverlay() { return new Overlay(this, objectMapper); }
  public Page getPage() { return new Page(this, objectMapper); }
  public Performance getPerformance() { return new Performance(this, objectMapper); }
  public Profiler getProfiler() { return new Profiler(this, objectMapper); }
  public Runtime getRuntime() { return new Runtime(this, objectMapper); }
  public Security getSecurity() { return new Security(this, objectMapper); }
  public ServiceWorker getServiceWorker() { return new ServiceWorker(this, objectMapper); }
  public Storage getStorage() { return new Storage(this, objectMapper); }
  public SystemInfo getSystemInfo() { return new SystemInfo(this, objectMapper); }
  public Target getTarget() { return new Target(this, objectMapper); }
  public Tethering getTethering() { return new Tethering(this, objectMapper); }
  public Tracing getTracing() { return new Tracing(this, objectMapper); }
}
