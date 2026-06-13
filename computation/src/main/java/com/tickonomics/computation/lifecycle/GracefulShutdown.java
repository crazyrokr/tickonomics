package com.tickonomics.computation.lifecycle;

import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

@Component
public class GracefulShutdown implements DisposableBean {

  private static final Logger log = LoggerFactory.getLogger(GracefulShutdown.class);

  private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

  public boolean isShuttingDown() {
    return shuttingDown.get();
  }

  @Override
  public void destroy() {
    log.info("Graceful shutdown initiated");
    shuttingDown.set(true);
  }
}
