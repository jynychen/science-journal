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

package com.google.android.apps.forscience.whistlepunk.metadata;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import androidx.annotation.VisibleForTesting;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import com.google.android.apps.forscience.javalib.MaybeConsumer;
import com.google.android.apps.forscience.javalib.Success;
import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.LoggingConsumer;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.StatsAccumulator;
import com.google.android.apps.forscience.whistlepunk.WhistlePunkApplication;
import com.google.android.apps.forscience.whistlepunk.analytics.TrackerConstants;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Trial;
import com.google.android.apps.forscience.whistlepunk.filemetadata.TrialStats;
import com.google.android.apps.forscience.whistlepunk.metadata.nano.GoosciTrial;
import com.google.android.apps.forscience.whistlepunk.sensorapi.StreamConsumer;
import com.google.android.apps.forscience.whistlepunk.sensordb.ScalarReadingList;
import com.google.android.apps.forscience.whistlepunk.sensordb.TimeRange;
import com.google.common.collect.Range;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/** Helper class for cropping. */
public class CropHelper {
  public static final String TAG = "CropHelper";

  // Time buffer for non-overlapping crop: We do not allow crop less than 1 second.
  // If this is changed, make sure to update R.string.crop_failed_range_too_small as well.
  public static final long MINIMUM_CROP_MILLIS = 1000;

  private static final int DATAPOINTS_PER_LOAD = 500;

  private static final String ACTION_CROP_STATS_RECALCULATED = "action_crop_stats_recalculated";
  public static final String EXTRA_SENSOR_ID = "extra_sensor_id";
  public static final String EXTRA_TRIAL_ID = "extra_trial_id";

  private static final IntentFilter STATS_INTENT_FILTER =
      new IntentFilter(ACTION_CROP_STATS_RECALCULATED);

  static class CropLabels {
    ApplicationLabel cropStartLabel;
    ApplicationLabel cropEndLabel;

    CropLabels() {}
  }

  private static class ProcessPriorityThreadFactory implements ThreadFactory {
    private final int mThreadPriority;

    ProcessPriorityThreadFactory(int threadPriority) {
      super();
      mThreadPriority = threadPriority;
    }

    @Override
    public Thread newThread(Runnable r) {
      Thread thread = new Thread(r);
      thread.setPriority(mThreadPriority);
      return thread;
    }
  }

  public interface CropTrialListener {
    /**
     * Called when a crop is completed, i.e. the metadata for the experiment is updated. The min,
     * max and average may not yet be recalculated.
     */
    void onCropCompleted();

    /**
     * Called when a crop fails to complete. No changes were made to the experiment, min, max or
     * average.
     */
    void onCropFailed(int errorId);
  }

  private int mStatsUpdated = 0;
  private Executor mCropStatsExecutor;
  private DataController mDataController;

  public CropHelper(DataController dataController) {
    this(
        Executors.newSingleThreadExecutor(
            new ProcessPriorityThreadFactory(android.os.Process.THREAD_PRIORITY_BACKGROUND)),
        dataController);
  }

  @VisibleForTesting
  CropHelper(Executor executor, DataController dataController) {
    mCropStatsExecutor = executor;
    mDataController = dataController;
  }

  public void cropTrial(
      final Context context,
      final Experiment experiment,
      final String trialId,
      long startTimestamp,
      long endTimestamp,
      final CropTrialListener listener) {

    final Trial trial = experiment.getTrial(trialId);

    // Are we trying to crop too wide? Too narrow? Are the timestamps valid?
    if (startTimestamp < trial.getOriginalFirstTimestamp()
        || trial.getOriginalLastTimestamp() < endTimestamp) {
      logEvent(context, TrackerConstants.ACTION_CROP_FAILED);
      listener.onCropFailed(R.string.crop_failed_range_too_large);
      return;
    }
    if (endTimestamp - MINIMUM_CROP_MILLIS <= startTimestamp) {
      logEvent(context, TrackerConstants.ACTION_CROP_FAILED);
      listener.onCropFailed(R.string.crop_failed_range_too_small);
      return;
    }

    GoosciTrial.Range cropRange = trial.getCropRange();
    if (cropRange == null) {
      cropRange = new GoosciTrial.Range();
    }
    cropRange.startMs = startTimestamp;
    cropRange.endMs = endTimestamp;
    trial.setCropRange(cropRange);
    for (String sensorId : trial.getSensorIds()) {
      // First delete the min/max/avg stats, but leave the rest available, because they are
      // used in loading data by ZoomPresenter.
      TrialStats stats = trial.getStatsForSensor(sensorId);
      stats.setStatStatus(GoosciTrial.SensorTrialStats.StatStatus.NEEDS_UPDATE);
      trial.setStats(stats);
    }
    mDataController.updateExperiment(
        experiment.getExperimentId(),
        new LoggingConsumer<Success>(TAG, "edit crop times in trial") {
          public void success(Success value) {
            logEvent(context, TrackerConstants.ACTION_CROP_COMPLETED);
            // At this point, we can go back to RunReview.
            listener.onCropCompleted();
            for (String sensorId : trial.getSensorIds()) {
              adjustTrialStats(context, experiment, trialId, sensorId);
            }
          }
        });
  }

  private void logEvent(Context context, String event) {
    WhistlePunkApplication.getUsageTracker(context)
        .trackEvent(TrackerConstants.CATEGORY_RUNS, event, "", 1);
  }

  private void adjustTrialStats(
      final Context context,
      final Experiment experiment,
      final String trialId,
      final String sensorId) {
    Runnable runnable =
        new Runnable() {
          @Override
          public void run() {
            // Moves the current Thread into the background
            StatsAdjuster adjuster = new StatsAdjuster(sensorId, experiment, trialId, context);
            adjuster.recalculateStats(mDataController);
          }
        };
    // Update the stats in the background, without blocking anything.
    mCropStatsExecutor.execute(runnable);
  }

  // A class that recalculates and resaves the stats in a trial.
  private class StatsAdjuster {
    private final String mSensorId;
    private final Experiment mExperiment;
    private final String mTrialId;
    private StatsAccumulator mStatsAccumulator;
    StreamConsumer mStreamConsumer;
    private Context mContext;

    StatsAdjuster(String sensorId, Experiment experiment, String trialId, Context context) {
      mStatsAccumulator = new StatsAccumulator(sensorId);
      mSensorId = sensorId;
      mExperiment = experiment;
      mTrialId = trialId;
      mStreamConsumer =
          new StreamConsumer() {
            @Override
            public boolean addData(long timestampMillis, double value) {
              mStatsAccumulator.updateRecordingStreamStats(timestampMillis, value);
              return true;
            }
          };
      mContext = context;
    }

    void recalculateStats(DataController dc) {
      TimeRange range =
          TimeRange.oldest(
              Range.closed(
                  mExperiment.getTrial(mTrialId).getFirstTimestamp(),
                  mExperiment.getTrial(mTrialId).getLastTimestamp()));
      addReadingsToStats(dc, range);
    }

    private void addReadingsToStats(final DataController dc, final TimeRange range) {
      dc.getScalarReadings(
          mTrialId,
          mSensorId, /* tier 0 */
          0,
          range,
          DATAPOINTS_PER_LOAD,
          new MaybeConsumer<ScalarReadingList>() {
            @Override
            public void success(ScalarReadingList list) {
              list.deliver(mStreamConsumer);
              Trial trial = mExperiment.getTrial(mTrialId);
              if (list.size() == 0
                  || list.size() < DATAPOINTS_PER_LOAD
                  || mStatsAccumulator.getLatestTimestamp() >= trial.getLastTimestamp()) {
                if (!mStatsAccumulator.isInitialized()) {
                  // There was no data in this region, so the stats are still
                  // not valid.
                  return;
                }
                // Done! Save back to the database.
                TrialStats fullStats = trial.getStatsForSensor(mSensorId);
                mStatsAccumulator.populateTrialStats(fullStats);
                trial.setStats(fullStats);
                dc.updateExperiment(
                    mExperiment.getExperimentId(),
                    new LoggingConsumer<Success>(TAG, "update stats") {
                      @Override
                      public void success(Success value) {
                        sendStatsUpdatedBroadcast(mContext, mSensorId, mTrialId);
                      }
                    });
              } else {
                TimeRange nextRange =
                    TimeRange.oldest(
                        Range.openClosed(
                            mStatsAccumulator.getLatestTimestamp(), trial.getLastTimestamp()));
                addReadingsToStats(dc, nextRange);
              }
            }

            @Override
            public void fail(Exception e) {
              Log.e(TAG, "Error loading data to adjust stats after crop");
            }
          });
    }
  }

  // Use a Broadcast to tell RunReviewFragment or ExperimentDetailsFragment or anyone who uses
  // stats that the stats are updated for this sensor on this run.
  private static void sendStatsUpdatedBroadcast(Context context, String sensorId, String trialId) {
    if (context == null) {
      return;
    }
    // Use a LocalBroadcastManager, because we do not need this broadcast outside the app.
    LocalBroadcastManager lbm = getBroadcastManager(context);
    Intent intent = new Intent();
    intent.setAction(ACTION_CROP_STATS_RECALCULATED);
    intent.putExtra(EXTRA_SENSOR_ID, sensorId);
    intent.putExtra(EXTRA_TRIAL_ID, trialId);
    lbm.sendBroadcast(intent);
  }

  private static LocalBroadcastManager getBroadcastManager(Context context) {
    // For security, only use local broadcasts (See b/32803250)
    return LocalBroadcastManager.getInstance(context);
  }

  public static void registerStatsBroadcastReceiver(Context context, BroadcastReceiver receiver) {
    getBroadcastManager(context).registerReceiver(receiver, STATS_INTENT_FILTER);
  }

  public static void unregisterBroadcastReceiver(Context context, BroadcastReceiver receiver) {
    getBroadcastManager(context).unregisterReceiver(receiver);
  }

  public static boolean experimentIsLongEnoughForCrop(Trial trial) {
    return trial.getOriginalLastTimestamp() - trial.getOriginalFirstTimestamp()
        > CropHelper.MINIMUM_CROP_MILLIS;
  }

  public void throwAwayDataOutsideCroppedRegion(DataController dc, Trial trial) {
    // TODO
  }
}
