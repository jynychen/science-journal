/*
 *  Copyright 2016 Google Inc. All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.google.android.apps.forscience.whistlepunk;

import android.content.ContentResolver;
import android.net.Uri;
import com.google.android.apps.forscience.javalib.MaybeConsumer;
import com.google.android.apps.forscience.javalib.Success;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.InputDeviceSpec;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Trial;
import com.google.android.apps.forscience.whistlepunk.metadata.ExperimentSensors;
import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;
import com.google.android.apps.forscience.whistlepunk.metadata.nano.GoosciExperiment;
import com.google.android.apps.forscience.whistlepunk.metadata.nano.GoosciScalarSensorData;
import com.google.android.apps.forscience.whistlepunk.metadata.nano.GoosciUserMetadata;
import com.google.android.apps.forscience.whistlepunk.sensordb.ScalarReading;
import com.google.android.apps.forscience.whistlepunk.sensordb.ScalarReadingList;
import com.google.android.apps.forscience.whistlepunk.sensordb.TimeRange;
import io.reactivex.Observable;
import java.util.List;
import java.util.Map;

/**
 * A DataController with empty implementations of all the methods, for tests to extend and override
 * only what they need.
 */
public class StubDataController implements DataController {
  @Override
  public void getScalarReadings(
      String trialId,
      String databaseTag,
      int resolutionTier,
      TimeRange timeRange,
      int maxRecords,
      MaybeConsumer<ScalarReadingList> onSuccess) {}

  @Override
  public void getScalarReadingProtosInBackground(
      GoosciExperiment.Experiment experiment,
      MaybeConsumer<GoosciScalarSensorData.ScalarSensorData> onSuccess) {}

  @Override
  public Observable<ScalarReading> createScalarObservable(
      String trialId, String[] sensorIds, TimeRange timeRange, int resolutionTier) {
    return null;
  }

  @Override
  public void deleteTrialData(Trial trial, MaybeConsumer<Success> onSuccess) {}

  @Override
  public void createExperiment(MaybeConsumer<Experiment> onSuccess) {}

  @Override
  public void deleteExperiment(Experiment experiment, MaybeConsumer<Success> onSuccess) {}

  @Override
  public void getExperimentById(String experimentId, MaybeConsumer<Experiment> onSuccess) {}

  @Override
  public void updateExperiment(String experimentId, MaybeConsumer<Success> onSuccess) {}

  @Override
  public void updateExperiment(Experiment experiment, MaybeConsumer<Success> onSuccess) {}

  @Override
  public void importExperimentFromZip(
      Uri zipUri, ContentResolver resolver, MaybeConsumer<String> onSuccess) {}

  @Override
  public void saveImmediately(MaybeConsumer<Success> onSuccess) {}

  @Override
  public String generateNewLabelId() {
    return null;
  }

  @Override
  public void getExperimentOverviews(
      boolean includeArchived,
      MaybeConsumer<List<GoosciUserMetadata.ExperimentOverview>> onSuccess) {}

  @Override
  public List<GoosciUserMetadata.ExperimentOverview> blockingGetExperimentOverviews(
      boolean includeArchived) {
    return null;
  }

  @Override
  public void getLastUsedUnarchivedExperiment(MaybeConsumer<Experiment> onSuccess) {}

  @Override
  public void getExternalSensors(MaybeConsumer<Map<String, ExternalSensorSpec>> onSuccess) {}

  @Override
  public void getExternalSensorsByExperiment(
      String experimentId, MaybeConsumer<ExperimentSensors> onSuccess) {}

  @Override
  public void getExternalSensorById(String id, MaybeConsumer<ExternalSensorSpec> onSuccess) {}

  @Override
  public void addSensorToExperiment(
      String experimentId, String sensorId, MaybeConsumer<Success> onSuccess) {}

  @Override
  public void removeSensorFromExperiment(
      String experimentId, String sensorId, MaybeConsumer<Success> onSuccess) {}

  @Override
  public void addOrGetExternalSensor(ExternalSensorSpec sensor, MaybeConsumer<String> onSensorId) {}

  @Override
  public void replaceSensorInExperiment(
      String experimentId,
      String oldSensorId,
      String newSensorId,
      MaybeConsumer<Success> onSuccess) {}

  @Override
  public void getMyDevices(MaybeConsumer<List<InputDeviceSpec>> onSuccess) {}

  @Override
  public void forgetMyDevice(InputDeviceSpec spec, MaybeConsumer<Success> onSuccess) {}

  @Override
  public void addMyDevice(InputDeviceSpec spec, MaybeConsumer<Success> onSuccess) {}
}
