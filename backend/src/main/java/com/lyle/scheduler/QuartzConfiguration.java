package com.lyle.scheduler;
import org.quartz.spi.TriggerFiredBundle;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.autoconfigure.quartz.SchedulerFactoryBeanCustomizer;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;
@Configuration
public class QuartzConfiguration {
 @Bean SchedulerFactoryBeanCustomizer autowiringJobs(AutowireCapableBeanFactory factory) {
  return scheduler -> scheduler.setJobFactory(new SpringBeanJobFactory() {
   @Override protected Object createJobInstance(TriggerFiredBundle bundle) throws Exception {
    Object job=super.createJobInstance(bundle); factory.autowireBean(job); return job;
   }
  });
 }
}
