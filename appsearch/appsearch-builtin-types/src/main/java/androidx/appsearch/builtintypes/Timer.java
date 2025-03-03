/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.appsearch.builtintypes;

import android.content.Context;
import android.os.Build;
import android.os.SystemClock;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.RestrictTo;
import androidx.appsearch.annotation.Document;
import androidx.appsearch.app.AppSearchSchema.StringPropertyConfig;
import androidx.appsearch.utils.BootCountUtil;
import androidx.core.util.Preconditions;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * AppSearch document representing a {@link Timer} entity.
 */
@Document(name = "builtin:Timer")
public final class Timer {
    /** @hide */
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    @IntDef({STATUS_UNKNOWN, STATUS_STARTED, STATUS_PAUSED, STATUS_EXPIRED, STATUS_MISSED,
            STATUS_RESET})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Status {}

    /** The {@link Timer} is in an unknown error state. */
    public static final int STATUS_UNKNOWN = 0;
    /** The {@link Timer} is started. */
    public static final int STATUS_STARTED = 1;
    /** The {@link Timer} is paused. */
    public static final int STATUS_PAUSED = 2;
    /** The {@link Timer} is expired. */
    public static final int STATUS_EXPIRED = 3;
    /** The {@link Timer} is missed. */
    public static final int STATUS_MISSED = 4;
    /** The {@link Timer} is reset to its initial value. */
    public static final int STATUS_RESET = 5;

    @Document.Namespace
    private final String mNamespace;

    @Document.Id
    private final String mId;

    @Document.Score
    private final int mDocumentScore;

    @Document.CreationTimestampMillis
    private final long mCreationTimestampMillis;

    @Document.TtlMillis
    private final long mDocumentTtlMillis;

    @Document.StringProperty(indexingType = StringPropertyConfig.INDEXING_TYPE_PREFIXES)
    private final String mName;

    @Document.LongProperty
    private final long mDurationMillis;

    @Document.LongProperty
    private final long mOriginalDurationMillis;

    @Document.LongProperty
    private final long mStartTimeMillis;

    @Document.LongProperty
    private final long mBaseTimeMillis;

    @Document.LongProperty
    private final long mBaseTimeMillisInElapsedRealtime;

    @Document.LongProperty
    private final int mBootCount;

    @Document.LongProperty
    private final long mRemainingTimeMillis;

    @Document.StringProperty
    private final String mRingtone;

    @Document.LongProperty
    private final int mStatus;

    @Document.BooleanProperty
    private final boolean mShouldVibrate;

    Timer(@NonNull String namespace, @NonNull String id, int documentScore,
            long creationTimestampMillis, long documentTtlMillis, @Nullable String name,
            long durationMillis, long originalDurationMillis, long startTimeMillis,
            long baseTimeMillis, long baseTimeMillisInElapsedRealtime, int bootCount,
            long remainingTimeMillis, @Nullable String ringtone, int status,
            boolean shouldVibrate) {
        mNamespace = Preconditions.checkNotNull(namespace);
        mId = Preconditions.checkNotNull(id);
        mDocumentScore = documentScore;
        mCreationTimestampMillis = creationTimestampMillis;
        mDocumentTtlMillis = documentTtlMillis;
        mName = name;
        mDurationMillis = durationMillis;
        mOriginalDurationMillis = originalDurationMillis;
        mStartTimeMillis = startTimeMillis;
        mBaseTimeMillis = baseTimeMillis;
        mBaseTimeMillisInElapsedRealtime = baseTimeMillisInElapsedRealtime;
        mBootCount = bootCount;
        mRemainingTimeMillis = remainingTimeMillis;
        mRingtone = ringtone;
        mStatus = status;
        mShouldVibrate = shouldVibrate;
    }

    /** Returns the namespace. */
    @NonNull
    public String getNamespace() {
        return mNamespace;
    }

    /** Returns the unique identifier. */
    @NonNull
    public String getId() {
        return mId;
    }

    /**
     * Returns the user-provided opaque document score of the current AppSearch document, which can
     * be used for ranking using
     * {@link androidx.appsearch.app.SearchSpec.RankingStrategy#RANKING_STRATEGY_DOCUMENT_SCORE}.
     *
     * <p>See {@link Document.Score} for more information on score.
     */
    public int getDocumentScore() {
        return mDocumentScore;
    }

    /**
     * Returns the creation timestamp for the current AppSearch entity, in milliseconds using the
     * {@link System#currentTimeMillis()} time base.
     *
     * <p>This timestamp refers to the creation time of the AppSearch entity, not when the
     * document is written into AppSearch.
     *
     * <p>If not set, then the current timestamp will be used.
     *
     * <p>See {@link androidx.appsearch.annotation.Document.CreationTimestampMillis} for more
     * information on creation timestamp.
     */
    public long getCreationTimestampMillis() {
        return mCreationTimestampMillis;
    }

    /**
     * Returns the time-to-live (TTL) for the current AppSearch document as a duration in
     * milliseconds.
     *
     * <p>The document will be automatically deleted when the TTL expires.
     *
     * <p>See {@link Document.TtlMillis} for more information on TTL.
     */
    public long getDocumentTtlMillis() {
        return mDocumentTtlMillis;
    }

    /** Returns the name. */
    @Nullable
    public String getName() {
        return mName;
    }

    /**
     * Returns the total duration in milliseconds, including additional time added by the user.
     *
     * <p>Applications may allow the user to add additional durations. The durationMillis will
     * always return the new updated duration.
     */
    public long getDurationMillis() {
        return mDurationMillis;
    }

    /**
     * Returns the original duration in milliseconds when the {@link Timer} was first created.
     *
     * <p>Applications may allow the user to add additional durations. The
     * originalDurationMillis will always return the original duration before any change has
     * taken place.
     */
    public long getOriginalDurationMillis() {
        return mOriginalDurationMillis;
    }

    /**
     * Returns the start time in milliseconds using the {@link System#currentTimeMillis()} time
     * base.
     *
     * <p>The start time is the first time that a new Timer, or a Timer that has been reset is
     * started. Pausing and resuming should not change its start time.
     */
    public long getStartTimeMillis() {
        return mStartTimeMillis;
    }

    /**
     * Returns the point in time that the {@link Timer} counts down from, relative to its
     * {@link #getRemainingTimeMillis()}. In milliseconds using the
     * {@link System#currentTimeMillis()} time base.
     *
     * <p>The expire time of the Timer can be calculated using the sum of its base time and
     * remaining time.
     *
     * <p>Use {@link #calculateBaseTimeMillis(Context)} to get a more accurate base time that
     * accounts for the current boot count of the device.
     */
    public long getBaseTimeMillis() {
        return mBaseTimeMillis;
    }

    /**
     * Returns the point in time that the {@link Timer} counts down from, relative to its
     * {@link #getRemainingTimeMillis()}. In milliseconds using the
     * {@link android.os.SystemClock#elapsedRealtime()} time base.
     *
     * <p>ElapsedRealtime should only be used if the {@link #getBootCount()} matches the
     * bootCount of the current device. It is used to calculate
     * {@link #calculateExpirationTimeMillis(Context).
     *
     * <p>The expire time of the Timer can be calculated using the sum of its base time and
     * remaining time.
     */
    public long getBaseTimeMillisInElapsedRealtime() {
        return mBaseTimeMillisInElapsedRealtime;
    }

    /**
     * Returns the boot count of the device when this document is last updated.
     *
     * <p>The boot count of the device can be accessed from Global Settings. See
     * {@link android.provider.Settings.Global#BOOT_COUNT}.
     *
     * <p>On older APIs where boot count is not available, this value should not be used.
     *
     * <p>If available, this value is used to calculate
     * {@link #calculateExpirationTimeMillis(Context)}.
     */
    public int getBootCount() {
        return mBootCount;
    }

    /**
     * Returns the amount of time remaining in milliseconds for the {@link Timer} relative to its
     * {@link #getBaseTimeMillis()}.
     *
     * <p>The expire time of the Timer can be calculated using the sum of its base time and
     * remaining time.
     *
     * <p>Use this method to get the static remaining time stored in the document. Use
     * {@link #calculateCurrentRemainingTimeMillis(Context)} to calculate the remaining time at
     * runtime.
     */
    public long getRemainingTimeMillis() {
        return mRemainingTimeMillis;
    }

    /**
     * Returns the ringtone as a content URI to be played, or
     * {@link android.provider.AlarmClock#VALUE_RINGTONE_SILENT} if no ringtone will be played.
     */
    @Nullable
    public String getRingtone() {
        return mRingtone;
    }

    /**
     * Returns the current status.
     *
     * <p>Status can be {@link #STATUS_UNKNOWN}, {@link #STATUS_STARTED}, {@link #STATUS_PAUSED},
     * {@link #STATUS_EXPIRED}, {@link #STATUS_MISSED}, or {@link #STATUS_RESET}.
     */
    @Status
    public int getStatus() {
        return mStatus;
    }

    /** Returns whether or not to activate the device vibrator when the {@link Timer} expires. */
    public boolean shouldVibrate() {
        return mShouldVibrate;
    }

    /**
     * Calculates the base time in milliseconds using the {@link System#currentTimeMillis()} time
     * base.
     *
     * <p>If the boot count retrieved from the context matches {@link #getBootCount()}, then
     * {@link #getBaseTimeMillisInElapsedRealtime()} will be used to calculate the base time
     * in the {@link System#currentTimeMillis()} time base. Otherwise return
     * {@link #getBaseTimeMillis()}.
     */
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
    public long calculateBaseTimeMillis(@NonNull Context context) {
        int currentBootCount = BootCountUtil.getCurrentBootCount(context);
        if (currentBootCount == -1 || currentBootCount != mBootCount) {
            // Boot count doesn't exist, or it doesn't match the current device boot count.
            // Therefore return the wall clock time since elapsed realtime is not valid.
            return mBaseTimeMillis;
        } else {
            // Boot count matches the current device boot count. Therefore calculate the wall
            // clock base time using elapsed realtime.
            long elapsedTime = SystemClock.elapsedRealtime() - mBaseTimeMillisInElapsedRealtime;
            return System.currentTimeMillis() - elapsedTime;
        }
    }

    /**
     * Calculates the expire time in milliseconds in the {@link System#currentTimeMillis()} time
     * base.
     *
     * <p>{@link Long#MAX_VALUE} will be returned if the {@link Timer} is {@link #STATUS_PAUSED}
     * or {@link #STATUS_RESET}.
     *
     * <p>A negative value may be returned if the {@link Timer} is {@link #STATUS_MISSED} or
     * {@link #STATUS_EXPIRED} to indicate it expired in the past.
     *
     * @param context The app context
     */
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
    public long calculateExpirationTimeMillis(@NonNull Context context) {
        if (mStatus == STATUS_PAUSED || mStatus == STATUS_RESET) {
            return Long.MAX_VALUE;
        }

        return calculateBaseTimeMillis(context) + mRemainingTimeMillis;
    }

    /**
     * Calculates the current remaining time in milliseconds.
     *
     * <p>A negative value may be returned if the {@link Timer} is {@link #STATUS_MISSED} or
     * {@link #STATUS_EXPIRED} to indicate it has already fired.
     *
     * <p>Use this method to calculate the remaining time at runtime. Use
     * {@link #getRemainingTimeMillis()} to get the static remaining time stored in the document.
     *
     * @param context The app context
     */
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
    public long calculateCurrentRemainingTimeMillis(@NonNull Context context) {
        if (mStatus == STATUS_PAUSED || mStatus == STATUS_RESET) {
            // The timer has not started, so the remaining time is the same as the last updated one.
            return mRemainingTimeMillis;
        }

        long elapsedTime = System.currentTimeMillis() - calculateBaseTimeMillis(context);
        return mRemainingTimeMillis - elapsedTime;
    }

    /** Builder for {@link Timer}. */
    public static final class Builder extends BaseBuiltinTypeBuilder<Builder> {
        private String mName;
        private long mDurationMillis;
        private long mOriginalDurationMillis;
        private long mStartTimeMillis;
        private long mBaseTimeMillis;
        private long mBaseTimeMillisInElapsedRealtime;
        private int mBootCount;
        private long mRemainingTimeMillis;
        private String mRingtone;
        private int mStatus;
        private boolean mShouldVibrate;

        /**
         * Constructor for {@link Timer.Builder}.
         *
         * @param namespace Namespace for the Document. See {@link Document.Namespace}.
         * @param id Unique identifier for the Document. See {@link Document.Id}.
         */
        public Builder(@NonNull String namespace, @NonNull String id) {
            super(namespace, id);
        }

        /**
         * Constructor for {@link Timer.Builder} with all the existing values.
         */
        public Builder(@NonNull Timer timer) {
            this(timer.getNamespace(), timer.getId());
            mDocumentScore = timer.getDocumentScore();
            mCreationTimestampMillis = timer.getCreationTimestampMillis();
            mDocumentTtlMillis = timer.getDocumentTtlMillis();
            mName = timer.getName();
            mDurationMillis = timer.getDurationMillis();
            mOriginalDurationMillis = timer.getOriginalDurationMillis();
            mStartTimeMillis = timer.getStartTimeMillis();
            mBaseTimeMillis = timer.getBaseTimeMillis();
            mBaseTimeMillisInElapsedRealtime = timer.getBaseTimeMillisInElapsedRealtime();
            mBootCount = timer.getBootCount();
            mRemainingTimeMillis = timer.getRemainingTimeMillis();
            mRingtone = timer.getRingtone();
            mStatus = timer.getStatus();
            mShouldVibrate = timer.shouldVibrate();
        }

        /** Sets the name. */
        @NonNull
        public Builder setName(@Nullable String name) {
            mName = name;
            return this;
        }

        /**
         * Sets the total duration in milliseconds, including additional time added by the user.
         */
        @NonNull
        public Builder setDurationMillis(long durationMillis) {
            mDurationMillis = durationMillis;
            return this;
        }

        /**
         * Sets the original duration in milliseconds when the {@link Timer} was first created.
         */
        @NonNull
        public Builder setOriginalDurationMillis(long originalDurationMillis) {
            mOriginalDurationMillis = originalDurationMillis;
            return this;
        }

        /**
         * Sets the start time in milliseconds using the {@link System#currentTimeMillis()} time
         * base.
         *
         * <p>The start time is the first time that a new Timer, or a Timer that has been reset is
         * started. Pausing and resuming should not change its start time.
         */
        @NonNull
        public Builder setStartTimeMillis(long startTimeMillis) {
            mStartTimeMillis = startTimeMillis;
            return this;
        }

        /**
         * Sets the point in time that the {@link Timer} counts down from, relative to its
         * {@link #setRemainingTimeMillis(long)}.
         *
         * <p>The expire time of the Timer can be calculated using the sum of its base time and
         * remaining time.
         *
         * <p>Base time should be sampled in both the {@link System#currentTimeMillis()} and
         * {@link android.os.SystemClock#elapsedRealtime()} time base. In addition, the boot
         * count of the device is needed to check if the
         * {@link android.os.SystemClock#elapsedRealtime()} time base is valid.
         *
         * @param baseTimeMillis The base time in milliseconds using the
         * {@link System#currentTimeMillis()} time base.
         * @param baseTimeMillisInElapsedRealtime The base time in milliseconds using the
         * {@link android.os.SystemClock#elapsedRealtime()} time base.
         * @param bootCount The current boot count of the device. See
         * {@link android.provider.Settings.Global#BOOT_COUNT}.
         */
        @NonNull
        public Builder setBaseTimeMillis(long baseTimeMillis,
                long baseTimeMillisInElapsedRealtime, int bootCount) {
            mBaseTimeMillis = baseTimeMillis;
            mBaseTimeMillisInElapsedRealtime = baseTimeMillisInElapsedRealtime;
            mBootCount = bootCount;
            return this;
        }

        /**
         * Sets the point in time that the {@link Timer} counts down from, relative to its
         * {@link #setRemainingTimeMillis(long)}.
         *
         * <p>The expire time of the Timer can be calculated using the sum of its base time and
         * remaining time.
         *
         * <p>See {@link #setBaseTimeMillis(long, long, int)}.
         *
         * @param context The app context used to fetch boot count.
         * @param baseTimeMillis The base time in milliseconds using the
         * {@link System#currentTimeMillis()} time base.
         * @param baseTimeMillisInElapsedRealtime The base time in milliseconds using the
         * {@link android.os.SystemClock#elapsedRealtime()} time base.
         */
        @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
        @NonNull
        public Builder setBaseTimeMillis(@NonNull Context context, long baseTimeMillis,
                long baseTimeMillisInElapsedRealtime) {
            int bootCount = BootCountUtil.getCurrentBootCount(context);
            return setBaseTimeMillis(baseTimeMillis, baseTimeMillisInElapsedRealtime, bootCount);
        }

        /**
         * Sets the amount of time remaining in milliseconds for the {@link Timer} relative to its
         * {@link #setBaseTimeMillis(long, long, int)}.
         *
         * <p>The expire time of the Timer can be calculated using the sum of its base time and
         * remaining time.
         */
        @NonNull
        public Builder setRemainingTimeMillis(long remainingTimeMillis) {
            mRemainingTimeMillis = remainingTimeMillis;
            return this;
        }

        /**
         * Sets the content URI for the ringtone to be played, or
         * {@link android.provider.AlarmClock#VALUE_RINGTONE_SILENT} if no ringtone will be played.
         */
        @NonNull
        public Builder setRingtone(@Nullable String ringtone) {
            mRingtone = ringtone;
            return this;
        }

        /**
         * Sets the current status.
         *
         * <p>Status can be {@link #STATUS_UNKNOWN}, {@link #STATUS_STARTED},
         * {@link #STATUS_PAUSED}, {@link #STATUS_EXPIRED}, {@link #STATUS_MISSED}, or
         * {@link #STATUS_RESET}.
         */
        @NonNull
        public Builder setStatus(@Status int status) {
            mStatus = status;
            return this;
        }

        /** Sets whether or not to activate the device vibrator when the {@link Timer} expires. */
        @NonNull
        public Builder setShouldVibrate(boolean shouldVibrate) {
            mShouldVibrate = shouldVibrate;
            return this;
        }

        /** Builds the {@link Timer}. */
        @NonNull
        public Timer build() {
            return new Timer(mNamespace, mId, mDocumentScore,
                    mCreationTimestampMillis, mDocumentTtlMillis, mName, mDurationMillis,
                    mOriginalDurationMillis, mStartTimeMillis,
                    mBaseTimeMillis, mBaseTimeMillisInElapsedRealtime, mBootCount,
                    mRemainingTimeMillis, mRingtone, mStatus, mShouldVibrate);
        }
    }
}
