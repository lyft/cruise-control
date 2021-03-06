/*
 * Copyright 2017 LinkedIn Corp. Licensed under the BSD 2-Clause License (the "License"). See License in the project root for license information.
 */

package com.linkedin.kafka.cruisecontrol.async.progress;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpSession;

import static com.linkedin.kafka.cruisecontrol.monitor.MonitorUtils.UNIT_INTERVAL_TO_PERCENTAGE;


/**
 * A class to track the progress of a task. This class is used to allow different users to trigger
 * an endpoint which may take a while for Cruise Control to respond, e.g. getting a complicated proposal.
 * Cruise Control will use {@link HttpSession} to keep track the progress of such requests and
 * report the progress to the users.
 */
public class OperationProgress {
  private static final String STEP = "step";
  private static final String DESCRIPTION = "description";
  private static final String TIME_IN_MS = "time-in-ms";
  private static final String COMPLETION_PERCENTAGE = "completionPercentage";
  private boolean _mutable = true;
  private List<OperationStep> _steps = new ArrayList<>();
  private List<Long> _startTimes = new ArrayList<>();

  /**
   * Add a {@link OperationStep} to the progress.
   * @param step the operation step to add.
   */
  public synchronized void addStep(OperationStep step) {
    ensureMutable();
    _steps.add(step);
    _startTimes.add(System.currentTimeMillis());
  }

  /**
   * Refer this operation progress to another one. This is useful when multiple operations are waiting for the
   * same background task to finish.
   *
   * Once this OperationProgress is referring to another OperationProgress, this OperationProgress becomes immutable
   * to avoid accidental change of the referred OperationProgress.
   *
   * @param other the other operation progress to refer to.
   */
  public void refer(OperationProgress other) {
    // ensure the integrity and avoid dead lock.
    List<OperationStep> steps;
    List<Long> startTimes;
    synchronized (other) {
      steps = other._steps;
      startTimes = other._startTimes;
    }
    synchronized (this) {
      ensureMutable();
      this._steps = steps;
      this._startTimes = startTimes;
      this._mutable = false;
    }
  }

  /**
   * @return The list of operation steps in this operation progress.
   */
  public synchronized List<OperationStep> progress() {
    return Collections.unmodifiableList(_steps);
  }

  /**
   * Clear the progress.
   */
  public synchronized void clear() {
    this._mutable = true;
    _steps.clear();
    _startTimes.clear();
  }

  @Override
  public synchronized String toString() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < _steps.size(); i++) {
      OperationStep step = _steps.get(i);
      long time = (i == _steps.size() - 1 ? System.currentTimeMillis() : _startTimes.get(i + 1)) - _startTimes.get(i);
      sb.append(String.format("(%6d ms) - (%3.1f%%) %s: %s%n",
                              time,  step.completionPercentage() * UNIT_INTERVAL_TO_PERCENTAGE, step.name(), step.description()));
    }
    return sb.toString();
  }

  /**
   * @return The array describing the progress of the operation.
   */
  public synchronized Object[] getJsonArray() {
    Object[] progressArray = new Object[_steps.size()];
    for (int i = 0; i < _steps.size(); i++) {
      OperationStep step = _steps.get(i);
      long time = (i == _steps.size() - 1 ? System.currentTimeMillis() : _startTimes.get(i + 1)) - _startTimes.get(i);
      Map<String, Object> stepProgressMap = new HashMap<>();
      stepProgressMap.put(STEP, step.name());
      stepProgressMap.put(DESCRIPTION, step.description());
      stepProgressMap.put(TIME_IN_MS, time);
      stepProgressMap.put(COMPLETION_PERCENTAGE, step.completionPercentage() * UNIT_INTERVAL_TO_PERCENTAGE);
      progressArray[i] = stepProgressMap;
    }
    return progressArray;
  }

  private void ensureMutable() {
    if (!_mutable) {
      throw new IllegalStateException("Cannot change this operation progress because it is immutable.");
    }
  }
}
