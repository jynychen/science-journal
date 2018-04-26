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

import android.content.Context;
import android.graphics.drawable.Drawable;
import com.google.android.apps.forscience.whistlepunk.data.nano.GoosciIcon;
import com.google.android.apps.forscience.whistlepunk.data.nano.GoosciSensorAppearance;
import io.reactivex.Single;
import java.text.NumberFormat;

public class ProtoSensorAppearance implements SensorAppearance {
  public static final int DEFAULT_POINTS_AFTER_DECIMAL = -1;

  // Don't allow more than 10 places after the decimal to be displayed. The UX can't
  // handle this very well.
  // TODO: Revisit this constant -- should it be even smaller, like 5?
  public static final int MAX_POINTS_AFTER_DECIMAL = 10;

  private GoosciSensorAppearance.BasicSensorAppearance mProto;
  private NumberFormat mNumberFormat;

  public static SensorAppearance getAppearanceFromProtoOrProvider(
      GoosciSensorAppearance.BasicSensorAppearance proto,
      String sensorId,
      SensorAppearanceProvider appearanceProvider) {

    SensorAppearance providerAppearance = appearanceProvider.getAppearance(sensorId);
    if (providerAppearance != null && providerAppearance != appearanceProvider.getAppearance("0")) {
      return providerAppearance;
    } else {
      return new ProtoSensorAppearance(proto);
    }
  }

  public ProtoSensorAppearance(GoosciSensorAppearance.BasicSensorAppearance proto) {
    mProto = proto;
    mNumberFormat = SensorAppearanceProviderImpl.createNumberFormat(mProto.pointsAfterDecimal);
  }

  @Override
  public String getName(Context context) {
    return mProto.name;
  }

  @Override
  public String getUnits(Context context) {
    return mProto.units;
  }

  @Override
  public Drawable getIconDrawable(Context context) {
    return context.getResources().getDrawable(R.drawable.ic_sensors_white_24dp);
  }

  @Override
  public String getShortDescription(Context context) {
    return mProto.shortDescription;
  }

  @Override
  public boolean hasLearnMore() {
    return false;
  }

  @Override
  public Single<LearnMoreContents> loadLearnMore(final Context context) {
    return Single.error(new IllegalStateException("No learn more"));
  }

  // These images aren't rendered now, so for now, this is safe.
  // TODO: Export images to proto
  @Override
  public GoosciIcon.IconPath getSmallIconPath() {
    return new GoosciIcon.IconPath();
  }

  @Override
  public GoosciIcon.IconPath getLargeIconPath() {
    return new GoosciIcon.IconPath();
  }

  @Override
  public SensorAnimationBehavior getSensorAnimationBehavior() {
    return ImageViewSensorAnimationBehavior.createDefault();
  }

  @Override
  public NumberFormat getNumberFormat() {
    return mNumberFormat;
  }

  @Override
  public int getPointsAfterDecimal() {
    return mProto.pointsAfterDecimal;
  }
}
