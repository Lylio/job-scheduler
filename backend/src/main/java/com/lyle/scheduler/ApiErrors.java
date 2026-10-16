package com.lyle.scheduler;
import org.quartz.SchedulerException;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.*;
import java.time.DateTimeException;
@RestControllerAdvice
public class ApiErrors {
 @ExceptionHandler({IllegalArgumentException.class,DateTimeException.class})
 public ProblemDetail invalid(RuntimeException e) { return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,e.getMessage()); }
 @ExceptionHandler(MethodArgumentNotValidException.class)
 public ProblemDetail validation(MethodArgumentNotValidException e) {
  return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,e.getBindingResult().getFieldErrors().stream()
   .map(error->error.getField()+": "+error.getDefaultMessage()).collect(java.util.stream.Collectors.joining("; ")));
 }
 @ExceptionHandler(SchedulerException.class)
 public ProblemDetail scheduler(SchedulerException e) {
  LoggerFactory.getLogger(ApiErrors.class).error("Scheduler operation failed",e);
  return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,"Scheduler unavailable. Please retry.");
 }
}
