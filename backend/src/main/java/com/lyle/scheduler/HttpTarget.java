package com.lyle.scheduler;
import java.net.URI;
/** URL syntax validation, without making a network request during job creation. */
public final class HttpTarget {
 private HttpTarget() {}
 public static URI parse(String value) {
  if(value==null||value.isBlank())throw new IllegalArgumentException("An HTTP health check requires a target URL.");
  URI uri;
  try { uri=URI.create(value.strip()); }
  catch(IllegalArgumentException e) { throw new IllegalArgumentException("Enter a valid absolute HTTP or HTTPS URL."); }
  if(!("http".equalsIgnoreCase(uri.getScheme())||"https".equalsIgnoreCase(uri.getScheme()))
    ||uri.getHost()==null||uri.getUserInfo()!=null||uri.getFragment()!=null||uri.getPort()==0||uri.getPort()>65535)
   throw new IllegalArgumentException("Use an absolute HTTP or HTTPS URL without credentials or a fragment.");
  return uri;
 }
}
