package com.lyle.scheduler;
public record TaskResult(String status,String message,Integer httpStatus,Long responseTimeMs,String errorCategory) {
 public static TaskResult success(String message) { return new TaskResult("SUCCESS",message,null,null,null); }
 public static TaskResult failure(String message,String category) { return new TaskResult("FAILED",message,null,null,category); }
}
