package com.lyle.scheduler;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.nio.channels.UnresolvedAddressException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
@Component
public class HttpCheckRunner {
 // Reuse connection pools. A job's request timeout also bounds waiting for headers.
 private final HttpClient client=HttpClient.newBuilder()
  .connectTimeout(Duration.ofSeconds(30)).followRedirects(HttpClient.Redirect.NEVER).build();
 public TaskResult run(ScheduledTask task) throws InterruptedException {
  var request=HttpRequest.newBuilder(HttpTarget.parse(task.targetUrl))
   .timeout(Duration.ofSeconds(task.timeoutSeconds)).header("User-Agent","LyleJobScheduler/0.2")
   .header("Accept","*/*").GET().build();
  long started=System.nanoTime();
  try {
   var response=client.send(request,HttpResponse.BodyHandlers.ofInputStream());
   long elapsed=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started);
   // We check status/headers, never buffer or retain an arbitrary response body.
   try(var body=response.body()) {
    boolean matches=response.statusCode()==task.expectedStatus;
    return new TaskResult(matches?"SUCCESS":"FAILED",
     "HTTP "+response.statusCode()+"; expected "+task.expectedStatus+". Response headers received in "+elapsed+" ms.",
     response.statusCode(),elapsed,matches?null:"HTTP_STATUS");
   }
  } catch(HttpTimeoutException e) {
   return TaskResult.failure("HTTP check timed out waiting for a connection or response headers (limit: "+task.timeoutSeconds+" s).","TIMEOUT");
  } catch(IOException e) {
   if(hasCause(e,UnknownHostException.class)||hasCause(e,UnresolvedAddressException.class))
    return TaskResult.failure("DNS lookup failed: target hostname could not be resolved.","DNS");
   if(hasCause(e,SSLException.class))
    return TaskResult.failure("TLS connection failed: verify the target certificate and HTTPS configuration.","TLS");
   if(hasCause(e,ConnectException.class))
    return TaskResult.failure("Connection failed: target host/port could not be reached.","CONNECTION");
   return TaskResult.failure("HTTP connection ended before a response could be checked ("+e.getClass().getSimpleName()+").","NETWORK");
  }
 }
 private boolean hasCause(Throwable error,Class<? extends Throwable> type) {
  for(Throwable cause=error;cause!=null;cause=cause.getCause())if(type.isInstance(cause))return true;
  return false;
 }
 @PreDestroy void close() { client.shutdownNow(); }
}
