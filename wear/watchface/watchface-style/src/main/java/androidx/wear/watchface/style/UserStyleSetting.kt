/*
 * Copyright 2020 The Android Open Source Project
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

package androidx.wear.watchface.style

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import androidx.annotation.Px
import androidx.annotation.RequiresApi
import androidx.annotation.RestrictTo
import androidx.annotation.StringRes
import androidx.wear.watchface.complications.ComplicationSlotBounds
import androidx.wear.watchface.complications.NAMESPACE_ANDROID
import androidx.wear.watchface.complications.NAMESPACE_APP
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.hasValue
import androidx.wear.watchface.style.UserStyleSetting.ComplicationSlotsUserStyleSetting.ComplicationSlotOverlay
import androidx.wear.watchface.style.UserStyleSetting.ComplicationSlotsUserStyleSetting.ComplicationSlotsOption
import androidx.wear.watchface.style.data.BooleanOptionWireFormat
import androidx.wear.watchface.style.data.BooleanUserStyleSettingWireFormat
import androidx.wear.watchface.style.data.ComplicationOverlayWireFormat
import androidx.wear.watchface.style.data.ComplicationsOptionWireFormat
import androidx.wear.watchface.style.data.ComplicationsUserStyleSettingWireFormat
import androidx.wear.watchface.style.data.CustomValueOptionWireFormat
import androidx.wear.watchface.style.data.CustomValueUserStyleSettingWireFormat
import androidx.wear.watchface.style.data.DoubleRangeOptionWireFormat
import androidx.wear.watchface.style.data.DoubleRangeUserStyleSettingWireFormat
import androidx.wear.watchface.style.data.ListOptionWireFormat
import androidx.wear.watchface.style.data.ListUserStyleSettingWireFormat
import androidx.wear.watchface.style.data.LongRangeOptionWireFormat
import androidx.wear.watchface.style.data.LongRangeUserStyleSettingWireFormat
import androidx.wear.watchface.style.data.OptionWireFormat
import androidx.wear.watchface.style.data.UserStyleSettingWireFormat
import java.io.DataOutputStream
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.nio.ByteBuffer
import java.security.DigestOutputStream
import java.security.InvalidParameterException

/** Wrapper around either a [CharSequence] or a string resource. */
internal sealed class DisplayText {
    abstract fun toCharSequence(): CharSequence

    override fun toString(): String = toCharSequence().toString()

    /** Used in evaluating [UserStyleSchema.getDigestHash]. */
    internal open fun write(dos: DataOutputStream) {
        // Intentionally empty.
    }

    class CharSequenceDisplayText(private val charSequence: CharSequence) : DisplayText() {
        override fun toCharSequence() = charSequence

        override fun write(dos: DataOutputStream) {
            dos.writeUTF(charSequence.toString())
        }
    }

    class ResourceDisplayText(
        private val resources: Resources,
        @StringRes private val id: Int
    ) : DisplayText() {
        override fun toCharSequence() = resources.getString(id)

        override fun write(dos: DataOutputStream) {
            dos.writeInt(id)
        }
    }
}

/**
 * Watch faces often have user configurable styles, the definition of what is a style is left up to
 * the watch face but it typically incorporates a variety of settings such as: color, visual theme
 * for watch hands, font, tick shape, complication slots, audio elements, etc...
 *
 * A UserStyleSetting represents one of these dimensions. See also [UserStyleSchema] which defines
 * the list of UserStyleSettings provided by the watch face.
 *
 * Styling data gets shared with the companion phone to support editors (typically over bluetooth),
 * as a result the size of serialized UserStyleSettings could become an issue if large.
 *
 * @property id Identifier for the element, must be unique. Styling data gets shared with the
 * companion (typically via bluetooth) so size is a consideration and short ids are encouraged.
 * There is a maximum length see [UserStyleSetting.Id.MAX_LENGTH].
 * @property icon [Icon] for use in the companion editor style selection UI.
 * @property watchFaceEditorData Optional data for an on watch face editor, this will not be sent to
 * the companion and its contents may be used in preference to other fields by an on watch face
 * editor.
 * @property options List of options for this UserStyleSetting. Depending on the type of
 * UserStyleSetting this may be an exhaustive list, or just examples to populate a ListView in case
 * the UserStyleSetting isn't supported by the UI (e.g. a new WatchFace with an old companion).
 * @property defaultOptionIndex The default option index, used if nothing has been selected within
 * the [options] list.
 * @property affectedWatchFaceLayers Used by the style configuration UI. Describes which rendering
 * layers this style affects.
 */
public sealed class UserStyleSetting private constructor(
    public val id: Id,
    private val displayNameInternal: DisplayText,
    private val descriptionInternal: DisplayText,
    public val icon: Icon?,
    public val watchFaceEditorData: WatchFaceEditorData?,
    public val options: List<Option>,
    public val defaultOptionIndex: Int,
    public val affectedWatchFaceLayers: Collection<WatchFaceLayer>
) {
    /**
     * Optional data for an on watch face editor (not the companion editor).
     *
     * @property icon The icon to use on the watch face editor in preference to
     * [UserStyleSetting.icon], [ListUserStyleSetting.ListOption.icon] and
     * [ComplicationSlotsOption.icon]. This Icon should be smaller than the one used by the
     * companion due to the watches smaller screen size.
     */
    public class WatchFaceEditorData(public val icon: Icon?) {
        @Suppress("DEPRECATION")
        internal constructor(wireFormat: Bundle) : this(wireFormat.getParcelable(ICON_KEY))

        internal fun toWireFormat() = Bundle().apply {
            icon?.let { putParcelable(ICON_KEY, it) }
        }

        internal fun write(dos: DataOutputStream) {
            icon?.write(dos)
        }

        internal companion object {
            internal const val ICON_KEY = "ICON"

            @SuppressLint("ResourceType")
            fun inflate(resources: Resources, parser: XmlResourceParser): WatchFaceEditorData {
                val icon = createIcon(
                    resources,
                    parser
                )
                return WatchFaceEditorData(icon)
            }
        }
    }

    /** Localized human readable name for the element, used in the userStyle selection UI. */
    public val displayName: CharSequence
        get() = displayNameInternal.toCharSequence()

    /** Localized description string displayed under the displayName. */
    public val description: CharSequence
        get() = descriptionInternal.toCharSequence()

    /**
     * For hierarchical style schemas, whether this UserStyleSetting has a parent [Option]. Editors
     * should respect the hierarchy for the best user experience.
     */
    internal var hasParent: Boolean = false
        internal set

    /**
     * Estimates the wire size of the UserStyleSetting in bytes. This does not account for the
     * overhead of the serialization method. Where possible the exact wire size for any referenced
     * [Icon]s is used but this isn't possible in all cases and as a fallback width x height x 4
     * is used.
     *
     * Note this method can be slow.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun estimateWireSizeInBytesAndValidateIconDimensions(
        context: Context,
        @Px maxWidth: Int,
        @Px maxHeight: Int
    ): Int {
        var sizeEstimate = id.value.length + displayName.length + description.length +
            4 /** [defaultOptionIndex] */ + affectedWatchFaceLayers.size * 4
        icon?.getWireSizeAndDimensions(context)?.let { wireSizeAndDimensions ->
            wireSizeAndDimensions.wireSizeBytes?.let {
                sizeEstimate += it
            }
            require(
                wireSizeAndDimensions.width <= maxWidth && wireSizeAndDimensions.height <= maxHeight
            ) {
                "UserStyleSetting id $id has a ${wireSizeAndDimensions.width} x " +
                    "${wireSizeAndDimensions.height} icon. This is too big, the maximum size is " +
                    "$maxWidth x $maxHeight."
            }
        }
        for (option in options) {
            sizeEstimate += option.estimateWireSizeInBytesAndValidateIconDimensions(
                context,
                maxWidth,
                maxHeight
            )
        }
        return sizeEstimate
    }

    /**
     * Machine readable identifier for [UserStyleSetting]s. The length of this identifier may not
     * exceed [MAX_LENGTH].
     */
    public class Id(public val value: String) {
        public companion object {
            /** Maximum length of the [value] field. */
            public const val MAX_LENGTH: Int = 40
        }

        init {
            require(value.length <= MAX_LENGTH) {
                "UserStyleSetting.value.length (${value.length}) must be less than MAX_LENGTH " +
                    "($MAX_LENGTH)"
            }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Id

            return value == other.value
        }

        override fun hashCode(): Int {
            return value.hashCode()
        }

        override fun toString(): String = value
    }

    public companion object {
        internal fun createFromWireFormat(
            wireFormat: UserStyleSettingWireFormat
        ): UserStyleSetting = when (wireFormat) {
            is BooleanUserStyleSettingWireFormat -> BooleanUserStyleSetting(wireFormat)

            is ComplicationsUserStyleSettingWireFormat ->
                ComplicationSlotsUserStyleSetting(wireFormat)

            is CustomValueUserStyleSettingWireFormat -> CustomValueUserStyleSetting(wireFormat)

            is DoubleRangeUserStyleSettingWireFormat -> DoubleRangeUserStyleSetting(wireFormat)

            is ListUserStyleSettingWireFormat -> ListUserStyleSetting(wireFormat)

            is LongRangeUserStyleSettingWireFormat -> LongRangeUserStyleSetting(wireFormat)

            else -> throw IllegalArgumentException(
                "Unknown UserStyleSettingWireFormat " + wireFormat::javaClass.name
            )
        }

        internal fun affectsWatchFaceLayersFlagsToSet(
            affectsWatchFaceLayers: Int
        ) = HashSet<WatchFaceLayer>().apply {
            if ((affectsWatchFaceLayers and 0x1) != 0) {
                add(WatchFaceLayer.BASE)
            }
            if ((affectsWatchFaceLayers and 0x2) != 0) {
                add(WatchFaceLayer.COMPLICATIONS)
            }
            if ((affectsWatchFaceLayers and 0x4) != 0) {
                add(WatchFaceLayer.COMPLICATIONS_OVERLAY)
            }
        }

        internal fun createDisplayText(
            resources: Resources,
            parser: XmlResourceParser,
            attributeId: String,
            defaultValue: DisplayText? = null
        ): DisplayText {
            val displayNameId = parser.getAttributeResourceValue(NAMESPACE_APP, attributeId, -1)
            return if (displayNameId != -1) {
                DisplayText.ResourceDisplayText(resources, displayNameId)
            } else if (parser.hasValue(attributeId) || defaultValue == null) {
                DisplayText.CharSequenceDisplayText(
                    parser.getAttributeValue(NAMESPACE_APP, attributeId) ?: "")
            } else {
                defaultValue
            }
        }

        internal fun createIcon(
            resources: Resources,
            parser: XmlResourceParser
        ): Icon? {
            val iconId = parser.getAttributeResourceValue(NAMESPACE_ANDROID, "icon", -1)
            return if (iconId != -1) {
                Icon.createWithResource(resources.getResourcePackageName(iconId), iconId)
            } else {
                null
            }
        }

        /**
         * Creates appropriate UserStyleSetting base on parent="@xml/..." resource reference.
         */
        internal fun <T> createParent(
            resources: Resources,
            parser: XmlResourceParser,
            parentNodeName: String,
            inflateSetting: (resources: Resources, parser: XmlResourceParser) -> T
        ): T? {
            val parentRef = parser.getAttributeResourceValue(NAMESPACE_APP, "parent", 0)
            return if (0 != parentRef) {
                val parentParser = resources.getXml(parentRef)
                parentParser.moveToStart(parentNodeName)
                inflateSetting(resources, parentParser)
            } else {
                null
            }
        }

        internal class Params(
            val id: Id,
            val displayName: DisplayText,
            val description: DisplayText,
            val icon: Icon?,
            val watchFaceEditorData: WatchFaceEditorData?,
            val options: List<Option>,
            val defaultOptionIndex: Int?,
            val affectedWatchFaceLayers: Collection<WatchFaceLayer>
        )

        /**
         * Parses base UserStyleSettings params. If a parent is specified, inherits its attributes
         * unless they are explicitly specified.
         */
        internal fun createBaseWithParent(
            resources: Resources,
            parser: XmlResourceParser,
            parent: UserStyleSetting?,
            inflateDefault: Boolean,
            optionInflater:
                Pair<String, ((resources: Resources, parser: XmlResourceParser) -> Option)>? = null
        ): Params {
            val settingType = "UserStyleSetting"
            val id = getStringRefAttribute(resources, parser, "id") ?: parent?.id?.value
                ?: throw IllegalArgumentException("$settingType must have id")
            val displayName = createDisplayText(
                resources,
                parser,
                "displayName",
                parent?.displayNameInternal
            )
            val description = createDisplayText(
                resources,
                parser,
                "description",
                parent?.descriptionInternal
            )
            val icon = createIcon(
                resources,
                parser
            ) ?: parent?.icon

            val defaultOptionIndex = if (inflateDefault) {
                getAttributeChecked(
                    parser,
                    "defaultOptionIndex",
                    String::toInt,
                    parent?.defaultOptionIndex ?: 0,
                    settingType
                )
            } else null

            val affectsWatchFaceLayers =
                getAttributeChecked(
                    parser,
                    "affectedWatchFaceLayers",
                    { value -> affectsWatchFaceLayersFlagsToSet(Integer.decode(value)) },
                    parent?.affectedWatchFaceLayers ?: WatchFaceLayer.ALL_WATCH_FACE_LAYERS,
                    settingType
                )

            var watchFaceEditorData: WatchFaceEditorData? = null
            val options = ArrayList<Option>()
            var type = 0
            val outerDepth = parser.depth
            do {
                if (type == XmlPullParser.START_TAG) {
                    if (parser.name == "OnWatchEditorData") {
                        if (watchFaceEditorData == null) {
                            watchFaceEditorData =
                                WatchFaceEditorData.inflate(resources, parser)
                        } else {
                            throw IllegalArgumentException(
                                "Unexpected node OnWatchEditorData at line " +
                                    parser.lineNumber
                            )
                        }
                    } else if (optionInflater != null && optionInflater.first == parser.name) {
                        options.add(optionInflater.second(resources, parser))
                    } else {
                        throw IllegalArgumentException(
                            "Unexpected node ${parser.name} at line ${parser.lineNumber}"
                        )
                    }
                }
                type = parser.next()
            } while (type != XmlPullParser.END_DOCUMENT && parser.depth > outerDepth)

            return Params(
                Id(id),
                displayName,
                description,
                icon,
                watchFaceEditorData ?: parent?.watchFaceEditorData,
                if (parent == null || options.isNotEmpty()) options else parent.options,
                defaultOptionIndex,
                affectsWatchFaceLayers
            )
        }
    }

    init {
        require(defaultOptionIndex >= 0 && defaultOptionIndex < options.size) {
            "defaultOptionIndex must be in the range [0 .. options.size)"
        }
    }

    internal fun getSettingOptionForId(id: ByteArray?) =
        if (id == null) {
            options[defaultOptionIndex]
        } else {
            getOptionForId(Option.Id(id))
        }

    private constructor(wireFormat: UserStyleSettingWireFormat) : this(
        Id(wireFormat.mId),
        DisplayText.CharSequenceDisplayText(wireFormat.mDisplayName),
        DisplayText.CharSequenceDisplayText(wireFormat.mDescription),
        wireFormat.mIcon,
        wireFormat.mOnWatchFaceEditorBundle?.let { WatchFaceEditorData(it) },
        wireFormat.mOptions.map { Option.createFromWireFormat(it) },
        wireFormat.mDefaultOptionIndex,
        wireFormat.mAffectsLayers.map { WatchFaceLayer.values()[it] }
    )

    /** @hide */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
    public abstract fun toWireFormat(): UserStyleSettingWireFormat

    /** @hide */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
    public fun getWireFormatOptionsList(): List<OptionWireFormat> =
        options.map { it.toWireFormat() }

    internal fun updateMessageDigest(digestOutputStream: DigestOutputStream) {
        val dos = DataOutputStream(digestOutputStream)
        dos.writeUTF(id.value)

        displayNameInternal.write(dos)
        descriptionInternal.write(dos)
        icon?.write(dos)
        watchFaceEditorData?.write(dos)
        for (option in options) {
            option.write(dos)
        }
        dos.writeInt(defaultOptionIndex)
        for (layer in affectedWatchFaceLayers.toSortedSet()) {
            dos.writeInt(layer.ordinal)
        }
        dos.close()
    }

    /** Returns the default for when the user hasn't selected an option. */
    public val defaultOption: Option
        get() = options[defaultOptionIndex]

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UserStyleSetting

        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun toString(): String = "{${id.value} : " +
        options.joinToString(transform = { it.toString() }) + "}"

    /**
     * Represents a choice within a style setting which can either be an option from the list or a
     * an arbitrary value depending on the nature of the style setting.
     *
     * @property id Machine readable [Id] for the style setting. Identifier for the option (or the
     * option itself for [CustomValueUserStyleSetting.CustomValueOption]), must be unique within
     * the UserStyleSetting. Short ids are encouraged.
     * @property childSettings The list of child [UserStyleSetting]s, if any. These must be in
     * [UserStyleSchema.userStyleSettings].
     */
    public abstract class Option internal constructor(
        public val id: Id,
        @Suppress("OPT_IN_MARKER_ON_WRONG_TARGET")
        @get:ExperimentalHierarchicalStyle
        public val childSettings: Collection<UserStyleSetting>
    ) {
        /**
         * This constructor is unused (the parent class is sealed), but is required to make tooling
         * happy.
         */
        constructor(id: Id) : this(id, emptyList())

        init {
            @OptIn(ExperimentalHierarchicalStyle::class)
            for (child in childSettings) {
                child.hasParent = true
            }
        }

        /**
         * Computes a lower bound estimate of the wire size of the Option in bytes. This does not
         * account for the overhead of the serialization method.
         */
        internal open fun estimateWireSizeInBytesAndValidateIconDimensions(
            context: Context,
            @Px maxWidth: Int,
            @Px maxHeight: Int
        ): Int = id.value.size

        // We don't want Option to be subclassed by users.
        @SuppressWarnings("HiddenAbstractMethod")
        internal abstract fun getUserStyleSettingClass(): Class<out UserStyleSetting>

        /**
         * Machine readable identifier for [Option]s. The length of this identifier may not exceed
         * [MAX_LENGTH].
         *
         * @param value The [ByteArray] value of this Id.
         */
        public class Id(public val value: ByteArray) {
            /**
             * Constructs an [Id] with a [String] encoded to a [ByteArray] by
             * [String.encodeToByteArray].
             */
            public constructor(value: String) : this(value.encodeToByteArray())

            public companion object {
                /** Maximum length of the [value] field. */
                public const val MAX_LENGTH: Int = 1024
            }

            init {
                require(value.size <= MAX_LENGTH) {
                    "Option.Id.value.size (${value.size}) must be less than MAX_LENGTH " +
                        "($MAX_LENGTH)"
                }
            }

            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false
                other as Id
                return value.contentEquals(other.value)
            }

            override fun hashCode(): Int {
                return value.contentHashCode()
            }

            override fun toString(): String =
                try {
                    value.decodeToString()
                } catch (e: Exception) {
                    value.toString()
                }
        }

        public companion object {
            /** @hide */
            @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
            public fun createFromWireFormat(
                wireFormat: OptionWireFormat
            ): Option =
                when (wireFormat) {
                    is BooleanOptionWireFormat ->
                        BooleanUserStyleSetting.BooleanOption.fromWireFormat(wireFormat)

                    is ComplicationsOptionWireFormat ->
                        ComplicationSlotsUserStyleSetting.ComplicationSlotsOption(wireFormat)

                    is CustomValueOptionWireFormat ->
                        CustomValueUserStyleSetting.CustomValueOption(wireFormat)

                    is DoubleRangeOptionWireFormat ->
                        DoubleRangeUserStyleSetting.DoubleRangeOption(wireFormat)

                    is ListOptionWireFormat ->
                        ListUserStyleSetting.ListOption(wireFormat)

                    is LongRangeOptionWireFormat ->
                        LongRangeUserStyleSetting.LongRangeOption(wireFormat)

                    else -> throw IllegalArgumentException(
                        "Unknown UserStyleSettingWireFormat.OptionWireFormat " +
                            wireFormat::javaClass.name
                    )
                }
        }

        /** @hide */
        @Suppress("HiddenAbstractMethod")
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
        public abstract fun toWireFormat(): OptionWireFormat

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Option

            return id == other.id
        }

        override fun hashCode(): Int {
            return id.hashCode()
        }

        override fun toString(): String =
            try {
                id.value.decodeToString()
            } catch (e: Exception) {
                id.value.toString()
            }

        internal open fun write(dos: DataOutputStream) {
            // Intentionally empty.
        }
    }

    /**
     * Translates an option name into an option. This will need to be overridden for userStyle
     * categories that can't sensibly be fully enumerated (e.g. a full 24-bit color picker).
     *
     * @param optionId The [Option.Id] of the option
     * @return An [Option] corresponding to the name. This could either be one of the options from
     * [UserStyleSetting]s or a newly constructed Option depending on the nature of the
     * UserStyleSetting. If optionName is unrecognized then the default value for the setting should
     * be returned.
     */
    public open fun getOptionForId(optionId: Option.Id): Option =
        options.find { it.id.value.contentEquals(optionId.value) } ?: options[defaultOptionIndex]

    /** A BooleanUserStyleSetting represents a setting with a true and a false setting. */
    public class BooleanUserStyleSetting : UserStyleSetting {

        /**
         * Constructs a BooleanUserStyleSetting.
         *
         * @param id [Id] for the element, must be unique.
         * @param displayName Localized human readable name for the element, used in the userStyle
         * selection UI.
         * @param description Localized description string displayed under the displayName.
         * @param icon [Icon] for use in the companion userStyle selection UI. This gets sent to the
         * companion over bluetooth and should be small (ideally a few kb in size).
         * @param affectsWatchFaceLayers Used by the style configuration UI. Describes which watch
         * face rendering layers this style affects.
         * @param defaultValue The default value for this BooleanUserStyleSetting.
         * @param watchFaceEditorData Optional data for an on watch face editor, this will not be sent
         * to the companion and its contents may be used in preference to other fields by an on
         * watch face editor.
         * @hide
         */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        @JvmOverloads
        public constructor (
            id: Id,
            displayName: CharSequence,
            description: CharSequence,
            icon: Icon?,
            affectsWatchFaceLayers: Collection<WatchFaceLayer>,
            defaultValue: Boolean,
            watchFaceEditorData: WatchFaceEditorData? = null
        ) : super(
            id,
            DisplayText.CharSequenceDisplayText(displayName),
            DisplayText.CharSequenceDisplayText(description),
            icon,
            watchFaceEditorData,
            listOf(BooleanOption.TRUE, BooleanOption.FALSE),
            when (defaultValue) {
                true -> 0
                false -> 1
            },
            affectsWatchFaceLayers
        )

        /**
         * Constructs a BooleanUserStyleSetting where [BooleanUserStyleSetting.displayName] and
         * [BooleanUserStyleSetting.description] are specified as resources.
         *
         * @param id [Id] for the element, must be unique.
         * @param resources The [Resources] from which [displayNameResourceId] and
         * [descriptionResourceId] are loaded.
         * @param displayNameResourceId String resource id for a human readable name for the element,
         * used in the userStyle selection UI.
         * @param descriptionResourceId String resource id for a human readable description string
         * displayed under the displayName.
         * @param icon [Icon] for use in the companion userStyle selection UI. This gets sent to the
         * companion over bluetooth and should be small (ideally a few kb in size).
         * @param affectsWatchFaceLayers Used by the style configuration UI. Describes which watch
         * face rendering layers this style affects.
         * @param defaultValue The default value for this BooleanUserStyleSetting.
         * @param watchFaceEditorData Optional data for an on watch face editor, this will not be sent
         * to the companion and its contents may be used in preference to other fields by an on
         * watch face editor.
         */
        @JvmOverloads
        public constructor(
            id: Id,
            resources: Resources,
            @StringRes displayNameResourceId: Int,
            @StringRes descriptionResourceId: Int,
            icon: Icon?,
            affectsWatchFaceLayers: Collection<WatchFaceLayer>,
            defaultValue: Boolean,
            watchFaceEditorData: WatchFaceEditorData? = null
        ) : super(
            id,
            DisplayText.ResourceDisplayText(resources, displayNameResourceId),
            DisplayText.ResourceDisplayText(resources, descriptionResourceId),
            icon,
            watchFaceEditorData,
            listOf(BooleanOption.TRUE, BooleanOption.FALSE),
            when (defaultValue) {
                true -> 0
                false -> 1
            },
            affectsWatchFaceLayers
        )

        internal constructor (
            id: Id,
            displayName: DisplayText,
            description: DisplayText,
            icon: Icon?,
            watchFaceEditorData: WatchFaceEditorData?,
            affectsWatchFaceLayers: Collection<WatchFaceLayer>,
            defaultValue: Boolean
        ) : super(
            id,
            displayName,
            description,
            icon,
            watchFaceEditorData,
            listOf(BooleanOption.TRUE, BooleanOption.FALSE),
            when (defaultValue) {
                true -> 0
                false -> 1
            },
            affectsWatchFaceLayers
        )

        internal constructor(wireFormat: BooleanUserStyleSettingWireFormat) : super(wireFormat)

        /** @hide */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
        override fun toWireFormat(): BooleanUserStyleSettingWireFormat =
            BooleanUserStyleSettingWireFormat(
                id.value,
                displayName,
                description,
                icon,
                getWireFormatOptionsList(),
                defaultOptionIndex,
                affectedWatchFaceLayers.map { it.ordinal },
                watchFaceEditorData?.toWireFormat(),
                /* optionsOnWatchFaceEditorIcons = */ null
            )

        /** Returns the default value. */
        public fun getDefaultValue(): Boolean = (options[defaultOptionIndex] as BooleanOption).value

        internal companion object {
            @SuppressLint("ResourceType")
            fun inflate(resources: Resources, parser: XmlResourceParser): BooleanUserStyleSetting {
                val settingType = "BooleanUserStyleSetting"
                val parent =
                    createParent(resources, parser, settingType, ::inflate)
                val defaultValue = getAttributeChecked(
                    parser,
                    "defaultBoolean",
                    String::toBoolean,
                    parent?.getDefaultValue(),
                    settingType
                )
                val params = createBaseWithParent(
                    resources,
                    parser,
                    parent,
                    inflateDefault = false
                )
                return BooleanUserStyleSetting(
                    params.id,
                    params.displayName,
                    params.description,
                    params.icon,
                    params.watchFaceEditorData,
                    params.affectedWatchFaceLayers,
                    defaultValue
                )
            }
        }

        /**
         * Represents a true or false option in the [BooleanUserStyleSetting].
         * @param value The boolean value this instance represents.
         */
        public class BooleanOption private constructor(
            public val value: Boolean
        ) : Option(
            Id(ByteArray(1).apply { this[0] = if (value) 1 else 0 }),
            emptyList()
        ) {
            /** @hide */
            @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
            override fun toWireFormat(): BooleanOptionWireFormat =
                BooleanOptionWireFormat(id.value)

            internal override fun getUserStyleSettingClass(): Class<out UserStyleSetting> =
                BooleanUserStyleSetting::class.java

            override fun toString(): String = if (id.value[0] == 1.toByte()) "true" else "false"

            override fun write(dos: DataOutputStream) {
                dos.write(id.value)
                dos.writeBoolean(value)
            }

            public companion object {
                @JvmField
                public val TRUE = BooleanOption(true)

                @JvmField
                public val FALSE = BooleanOption(false)

                @JvmStatic
                public fun from(value: Boolean): BooleanOption {
                    return if (value) TRUE else FALSE
                }

                @JvmStatic
                internal fun fromWireFormat(
                    wireFormat: BooleanOptionWireFormat
                ): BooleanOption {
                    return from(wireFormat.mId[0] == 1.toByte())
                }
            }
        }
    }

    /**
     * ComplicationSlotsUserStyleSetting is the recommended [UserStyleSetting] for representing
     * complication slot configuration, options such as the number of active complication slots,
     * their location, etc... The [ComplicationSlotsOption] class allows you to apply a list of
     * [ComplicationSlotOverlay]s on top of the base config as specified by the
     * [androidx.wear.watchface.ComplicationSlot] constructor.
     *
     * The ComplicationsManager listens for style changes with this setting and when a
     * [ComplicationSlotsOption] is selected the overrides are automatically applied. Note its
     * suggested that the default [ComplicationSlotOverlay] (the first entry in the list) does
     * not apply any overrides. Only a single [ComplicationSlotsUserStyleSetting] is permitted in
     * the [UserStyleSchema].
     *
     * Not to be confused with complication data source selection.
     */
    public class ComplicationSlotsUserStyleSetting : UserStyleSetting {

        /**
         * Overrides to be applied to the corresponding androidx.wear.watchface.ComplicationSlot]'s
         * initial config (as specified in it's constructor) when the setting is selected.
         *
         * @param complicationSlotId The id of the [androidx.wear.watchface.ComplicationSlot] to
         * configure.
         * @param enabled If non null, whether the complication should be enabled for this
         * configuration. If null then no changes are made.
         * @param complicationSlotBounds If non null, the [ComplicationSlotBounds] for this
         * configuration. If null then no changes are made.
         * @param accessibilityTraversalIndex If non null the accessibility traversal index
         * for this configuration. This is used to determine the order in which accessibility labels
         * for the watch face are read to the user.
         */
        public class ComplicationSlotOverlay constructor(
            public val complicationSlotId: Int,
            @Suppress("AutoBoxing")
            @get:Suppress("AutoBoxing")
            @get:JvmName("isEnabled")
            public val enabled: Boolean? = null,
            public val complicationSlotBounds: ComplicationSlotBounds? = null,
            @SuppressWarnings("AutoBoxing")
            @get:SuppressWarnings("AutoBoxing")
            public val accessibilityTraversalIndex: Int? = null
        ) {
            internal fun write(dos: DataOutputStream) {
                dos.write(complicationSlotId)
                enabled?.let { dos.writeBoolean(it) }
                complicationSlotBounds?.write(dos)
                accessibilityTraversalIndex?.let { dos.writeInt(it) }
            }

            /**
             * Constructs a [ComplicationSlotOverlay].Builder.
             *
             * @param complicationSlotId The id of the [androidx.wear.watchface.ComplicationSlot]
             * to configure.
             */
            public class Builder(
                private val complicationSlotId: Int
            ) {
                private var enabled: Boolean? = null
                private var complicationSlotBounds: ComplicationSlotBounds? = null
                private var accessibilityTraversalIndex: Int? = null

                /** Overrides the complication's enabled flag. */
                @Suppress("MissingGetterMatchingBuilder")
                public fun setEnabled(enabled: Boolean): Builder = apply {
                    this.enabled = enabled
                }

                /** Overrides the complication's per [ComplicationSlotBounds]. */
                public fun setComplicationSlotBounds(
                    complicationSlotBounds: ComplicationSlotBounds
                ): Builder = apply {
                    this.complicationSlotBounds = complicationSlotBounds
                }

                /**
                 * Overrides the [androidx.wear.watchface.ComplicationSlot]'s accessibility
                 * traversal index. This is used to sort
                 * [androidx.wear.watchface.ContentDescriptionLabel]s. If unset we will order the
                 * complications by their initial accessibilityTraversalIndex (usually the same
                 * as their id).
                 */
                public fun setAccessibilityTraversalIndex(accessibilityTraversalIndex: Int):
                    Builder = apply {
                    this.accessibilityTraversalIndex = accessibilityTraversalIndex
                }

                public fun build(): ComplicationSlotOverlay =
                    ComplicationSlotOverlay(
                        complicationSlotId,
                        enabled,
                        complicationSlotBounds,
                        accessibilityTraversalIndex
                    )
            }

            internal constructor(
                wireFormat: ComplicationOverlayWireFormat
            ) : this(
                wireFormat.mComplicationSlotId,
                when (wireFormat.mEnabled) {
                    ComplicationOverlayWireFormat.ENABLED_UNKNOWN -> null
                    ComplicationOverlayWireFormat.ENABLED_YES -> true
                    ComplicationOverlayWireFormat.ENABLED_NO -> false
                    else -> throw InvalidParameterException(
                        "Unrecognised wireFormat.mEnabled " + wireFormat.mEnabled
                    )
                },
                wireFormat.mPerComplicationTypeBounds?.let { perComplicationTypeBounds ->
                    ComplicationSlotBounds.createFromPartialMap(
                        perComplicationTypeBounds.mapKeys { ComplicationType.fromWireType(it.key) }
                    )
                },
                wireFormat.accessibilityTraversalIndex
            )

            /**
             * Computes a lower bound estimate of the wire format size of this
             * ComplicationSlotOverlay.
             */
            internal fun estimateWireSizeInBytes(): Int {
                var estimate = 16 // Estimate for everything except complicationSlotBounds
                complicationSlotBounds?.let {
                    estimate += it.perComplicationTypeBounds.size * (4 + 16)
                }
                return estimate
            }

            internal fun toWireFormat() =
                ComplicationOverlayWireFormat(
                    complicationSlotId,
                    enabled,
                    complicationSlotBounds?.perComplicationTypeBounds?.mapKeys {
                        it.key.toWireComplicationType()
                    },
                    accessibilityTraversalIndex
                )

            internal companion object {
                @SuppressLint("ResourceType")
                fun inflate(
                    resources: Resources,
                    parser: XmlResourceParser
                ): ComplicationSlotOverlay {
                    val complicationSlotId = getIntRefAttribute(
                        resources, parser, "complicationSlotId"
                    )
                    require(complicationSlotId != null) {
                        "ComplicationSlotOverlay missing complicationSlotId"
                    }
                    val enabled =
                        if (parser.hasValue("enabled")) {
                            parser.getAttributeBooleanValue(
                                NAMESPACE_APP,
                                "enabled",
                                true
                            )
                        } else {
                            null
                        }
                    val accessibilityTraversalIndex =
                        if (parser.hasValue("accessibilityTraversalIndex")) {
                            parser.getAttributeIntValue(
                                NAMESPACE_APP,
                                "accessibilityTraversalIndex",
                                0
                            )
                        } else {
                            null
                        }
                    val bounds = ComplicationSlotBounds.inflate(parser)

                    return ComplicationSlotOverlay(
                        complicationSlotId,
                        enabled,
                        bounds,
                        accessibilityTraversalIndex
                    )
                }
            }
        }

        /**
         * Constructs a ComplicationSlotsUserStyleSetting.
         *
         * @param id [Id] for the element, must be unique.
         * @param displayName Localized human readable name for the element, used in the userStyle
         * selection UI.
         * @param description Localized description string displayed under the displayName.
         * @param icon [Icon] for use in the companion userStyle selection UI. This gets sent to the
         * companion over bluetooth and should be small (ideally a few kb in size).
         * @param complicationConfig The configuration for affected complications.
         * @param affectsWatchFaceLayers Used by the style configuration UI. Describes which watch
         * face rendering layers this style affects, must include
         * [WatchFaceLayer.COMPLICATIONS].
         * @param defaultOption The default option, used when data isn't persisted. Optional
         * parameter which defaults to the first element of [complicationConfig].
         * @param watchFaceEditorData Optional data for an on watch face editor, this will not be sent
         * to the companion and its contents may be used in preference to other fields by an on
         * watch face editor.
         * @hide
         */
        @JvmOverloads
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public constructor (
            id: Id,
            displayName: CharSequence,
            description: CharSequence,
            icon: Icon?,
            complicationConfig: List<ComplicationSlotsOption>,
            affectsWatchFaceLayers: Collection<WatchFaceLayer>,
            defaultOption: ComplicationSlotsOption = complicationConfig.first(),
            watchFaceEditorData: WatchFaceEditorData? = null
        ) : super(
            id,
            DisplayText.CharSequenceDisplayText(displayName),
            DisplayText.CharSequenceDisplayText(description),
            icon,
            watchFaceEditorData,
            complicationConfig,
            complicationConfig.indexOf(defaultOption),
            affectsWatchFaceLayers
        ) {
            require(affectsWatchFaceLayers.contains(WatchFaceLayer.COMPLICATIONS)) {
                "ComplicationSlotsUserStyleSetting must affect the complications layer"
            }
            requireUniqueOptionIds(id, complicationConfig)
        }

        /**
         * Constructs a ComplicationSlotsUserStyleSetting where
         * [ComplicationSlotsUserStyleSetting.displayName] and
         * [ComplicationSlotsUserStyleSetting.description] are specified as resources.
         *
         * @param id [Id] for the element, must be unique.
         * @param resources The [Resources] from which [displayNameResourceId] and
         * [descriptionResourceId] are loaded.
         * @param displayNameResourceId String resource id for a human readable name for the element,
         * used in the userStyle selection UI.
         * @param descriptionResourceId String resource id for a human readable description string
         * displayed under the displayName.
         * @param icon [Icon] for use in the companion userStyle selection UI. This gets sent to the
         * companion over bluetooth and should be small (ideally a few kb in size).
         * @param complicationConfig The configuration for affected complications.
         * @param affectsWatchFaceLayers Used by the style configuration UI. Describes which watch
         * face rendering layers this style affects, must include
         * [WatchFaceLayer.COMPLICATIONS].
         * @param defaultOption The default option, used when data isn't persisted. Optional
         * parameter which defaults to the first element of [complicationConfig].
         * @param watchFaceEditorData Optional data for an on watch face editor, this will not be sent
         * to the companion and its contents may be used in preference to other fields by an on
         * watch face editor.
         */
        @JvmOverloads
        public constructor (
            id: Id,
            resources: Resources,
            @StringRes displayNameResourceId: Int,
            @StringRes descriptionResourceId: Int,
            icon: Icon?,
            complicationConfig: List<ComplicationSlotsOption>,
            affectsWatchFaceLayers: Collection<WatchFaceLayer>,
            defaultOption: ComplicationSlotsOption = complicationConfig.first(),
            watchFaceEditorData: WatchFaceEditorData? = null
        ) : super(
            id,
            DisplayText.ResourceDisplayText(resources, displayNameResourceId),
            DisplayText.ResourceDisplayText(resources, descriptionResourceId),
            icon,
            watchFaceEditorData,
            complicationConfig,
            complicationConfig.indexOf(defaultOption),
            affectsWatchFaceLayers
        ) {
            require(affectsWatchFaceLayers.contains(WatchFaceLayer.COMPLICATIONS)) {
                "ComplicationSlotsUserStyleSetting must affect the complications layer"
            }
            requireUniqueOptionIds(id, complicationConfig)
        }

        internal constructor (
            id: Id,
            displayName: DisplayText,
            description: DisplayText,
            icon: Icon?,
            watchFaceEditorData: WatchFaceEditorData? = null,
            options: List<ComplicationSlotsOption>,
            affectsWatchFaceLayers: Collection<WatchFaceLayer>,
            defaultOptionIndex: Int
        ) : super(
            id,
            displayName,
            description,
            icon,
            watchFaceEditorData,
            options,
            defaultOptionIndex,
            affectsWatchFaceLayers
        ) {
            require(defaultOptionIndex >= 0 && defaultOptionIndex < options.size) {
                "defaultOptionIndex must be within the range of the options list"
            }
            require(affectsWatchFaceLayers.contains(WatchFaceLayer.COMPLICATIONS)) {
                "ComplicationSlotsUserStyleSetting must affect the complications layer"
            }
            requireUniqueOptionIds(id, options)
        }

        internal constructor(
            wireFormat: ComplicationsUserStyleSettingWireFormat
        ) : super(wireFormat) {
            wireFormat.mPerOptionOnWatchFaceEditorBundles?.let { optionsOnWatchFaceEditorIcons ->
                val optionsIterator = options.iterator()
                for (bundle in optionsOnWatchFaceEditorIcons) {
                    val option = optionsIterator.next() as ComplicationSlotsOption
                    bundle?.let {
                        option.watchFaceEditorData = WatchFaceEditorData(it)
                    }
                }
            }
        }

        /** @hide */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
        override fun toWireFormat(): ComplicationsUserStyleSettingWireFormat =
            ComplicationsUserStyleSettingWireFormat(
                id.value,
                displayName,
                description,
                icon,
                getWireFormatOptionsList(),
                defaultOptionIndex,
                affectedWatchFaceLayers.map { it.ordinal },
                watchFaceEditorData?.toWireFormat(),
                options.map {
                    (it as ComplicationSlotsOption).watchFaceEditorData?.toWireFormat() ?: Bundle()
                }
            )

        internal companion object {
            @SuppressLint("ResourceType")
            @Suppress("UNCHECKED_CAST")
            fun inflate(
                resources: Resources,
                parser: XmlResourceParser
            ): ComplicationSlotsUserStyleSetting {
                val params = createBaseWithParent(
                    resources,
                    parser,
                    createParent(
                        resources, parser, "ComplicationSlotsUserStyleSetting", ::inflate
                    ),
                    inflateDefault = true,
                    optionInflater = "ComplicationSlotsOption" to ComplicationSlotsOption::inflate
                )
                return ComplicationSlotsUserStyleSetting(
                    params.id,
                    params.displayName,
                    params.description,
                    params.icon,
                    params.watchFaceEditorData,
                    params.options as List<ComplicationSlotsOption>,
                    params.affectedWatchFaceLayers,
                    params.defaultOptionIndex!!
                )
            }
        }

        /**
         * Represents an override to the initial [androidx.wear.watchface.ComplicationSlotsManager]
         * configuration.
         */
        public class ComplicationSlotsOption : Option {
            /**
             * Overlays to be applied when this ComplicationSlotsOption is selected. If this is empty
             * then the net result is the initial complication configuration.
             */
            public val complicationSlotOverlays: Collection<ComplicationSlotOverlay>

            /** Backing field for [displayName]. */
            private val displayNameInternal: DisplayText

            /** Localized human readable name for the setting, used in the style selection UI. */
            public val displayName: CharSequence
                get() = displayNameInternal.toCharSequence()

            /** Icon for use in the companion style selection UI. */
            public val icon: Icon?

            /**
             * Optional data for an on watch face editor, this will not be sent to the companion
             * and its contents may be used in preference to other fields by an on watch face
             * editor.
             */
            public var watchFaceEditorData: WatchFaceEditorData?
                internal set

            /**
             * Constructs a ComplicationSlotsUserStyleSetting.
             *
             * @param id [Id] for the element, must be unique.
             * @param displayName Localized human readable name for the element, used in the
             * userStyle selection UI.
             * @param icon [Icon] for use in the companion style selection UI. This gets sent to the
             * companion over bluetooth and should be small (ideally a few kb in size).
             * @param complicationSlotOverlays Overlays to be applied when this
             * ComplicationSlotsOption is selected. If this is empty then the net result is the
             * initial complication configuration.
             * @param watchFaceEditorData Optional data for an on watch face editor, this will not be
             * sent to the companion and its contents may be used in preference to other fields by
             * an on watch face editor.
             * @hide
             */
            @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
            @JvmOverloads
            public constructor(
                id: Id,
                displayName: CharSequence,
                icon: Icon?,
                complicationSlotOverlays: Collection<ComplicationSlotOverlay>,
                watchFaceEditorData: WatchFaceEditorData? = null
            ) : super(id, emptyList()) {
                this.complicationSlotOverlays = complicationSlotOverlays
                this.displayNameInternal = DisplayText.CharSequenceDisplayText(displayName)
                this.icon = icon
                this.watchFaceEditorData = watchFaceEditorData
            }

            /**
             * Constructs a ComplicationSlotsUserStyleSetting with [displayName] constructed from
             * Resources.
             *
             * @param id [Id] for the element, must be unique.
             * @param resources The [Resources] from which [displayNameResourceId] is load.
             * @param displayNameResourceId String resource id for a human readable name for the
             * element, used in the userStyle selection UI.
             * @param icon [Icon] for use in the companion style selection UI. This gets sent to the
             * companion over bluetooth and should be small (ideally a few kb in size).
             * @param complicationSlotOverlays Overlays to be applied when this
             * ComplicationSlotsOption is selected. If this is empty then the net result is the
             * initial complication configuration.
             * @param watchFaceEditorData Optional data for an on watch face editor, this will not be sent
             * to the companion and its contents may be used in preference to other fields by an on
             * watch face editor.
             */
            @JvmOverloads
            public constructor(
                id: Id,
                resources: Resources,
                @StringRes displayNameResourceId: Int,
                icon: Icon?,
                complicationSlotOverlays: Collection<ComplicationSlotOverlay>,
                watchFaceEditorData: WatchFaceEditorData? = null
            ) : super(id, emptyList()) {
                this.complicationSlotOverlays = complicationSlotOverlays
                this.displayNameInternal =
                    DisplayText.ResourceDisplayText(resources, displayNameResourceId)
                this.icon = icon
                this.watchFaceEditorData = watchFaceEditorData
            }

            internal constructor(
                id: Id,
                displayName: DisplayText,
                icon: Icon?,
                watchFaceEditorData: WatchFaceEditorData?,
                complicationSlotOverlays: Collection<ComplicationSlotOverlay>
            ) : super(id, emptyList()) {
                this.complicationSlotOverlays = complicationSlotOverlays
                this.displayNameInternal = displayName
                this.icon = icon
                this.watchFaceEditorData = watchFaceEditorData
            }

            internal constructor(
                wireFormat: ComplicationsOptionWireFormat
            ) : super(Id(wireFormat.mId), emptyList()) {
                complicationSlotOverlays =
                    wireFormat.mComplicationOverlays.map { ComplicationSlotOverlay(it) }
                displayNameInternal = DisplayText.CharSequenceDisplayText(wireFormat.mDisplayName)
                icon = wireFormat.mIcon
                watchFaceEditorData = null // This will get overwritten.
            }

            internal override fun getUserStyleSettingClass(): Class<out UserStyleSetting> =
                ComplicationSlotsUserStyleSetting::class.java

            internal override fun estimateWireSizeInBytesAndValidateIconDimensions(
                context: Context,
                @Px maxWidth: Int,
                @Px maxHeight: Int
            ): Int {
                var sizeEstimate = id.value.size + displayName.length
                for (overlay in complicationSlotOverlays) {
                    sizeEstimate += overlay.estimateWireSizeInBytes()
                }
                icon?.getWireSizeAndDimensions(context)?.let { wireSizeAndDimensions ->
                    wireSizeAndDimensions.wireSizeBytes?.let {
                        sizeEstimate += it
                    }
                    require(
                        wireSizeAndDimensions.width <= maxWidth &&
                            wireSizeAndDimensions.height <= maxHeight
                    ) {
                        "ComplicationSlotsOption id $id has a ${wireSizeAndDimensions.width} x " +
                            "${wireSizeAndDimensions.height} icon. This is too big, the maximum " +
                            "size is $maxWidth x $maxHeight."
                    }
                }
                return sizeEstimate
            }

            /** @hide */
            @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
            override fun toWireFormat():
                ComplicationsOptionWireFormat =
                ComplicationsOptionWireFormat(
                    id.value,
                    displayName,
                    icon,
                    complicationSlotOverlays.map { it.toWireFormat() }.toTypedArray()
                )

            override fun write(dos: DataOutputStream) {
                dos.write(id.value)
                for (overlay in complicationSlotOverlays) {
                    overlay.write(dos)
                }
                displayNameInternal.write(dos)
                icon?.write(dos)
                watchFaceEditorData?.write(dos)
            }

            internal companion object {
                @SuppressLint("ResourceType")
                fun inflate(
                    resources: Resources,
                    parser: XmlResourceParser
                ): ComplicationSlotsOption {
                    val id = getStringRefAttribute(resources, parser, "id")
                    require(id != null) { "ComplicationSlotsOption must have an id" }
                    val displayName = createDisplayText(
                        resources,
                        parser,
                        "displayName"
                    )
                    val icon = createIcon(
                        resources,
                        parser
                    )

                    var watchFaceEditorData: WatchFaceEditorData? = null
                    val complicationSlotOverlays = ArrayList<ComplicationSlotOverlay>()
                    var type = 0
                    val outerDepth = parser.depth
                    do {
                        if (type == XmlPullParser.START_TAG) {
                            when (parser.name) {
                                "ComplicationSlotOverlay" -> complicationSlotOverlays.add(
                                    ComplicationSlotOverlay.inflate(resources, parser)
                                )

                                "OnWatchEditorData" -> {
                                    if (watchFaceEditorData == null) {
                                        watchFaceEditorData =
                                            WatchFaceEditorData.inflate(resources, parser)
                                    } else {
                                        throw IllegalArgumentException(
                                            "Unexpected node OnWatchEditorData at line " +
                                                parser.lineNumber
                                        )
                                    }
                                }

                                else -> throw IllegalArgumentException(
                                    "Unexpected node ${parser.name} at line ${parser.lineNumber}"
                                )
                            }
                        }
                        type = parser.next()
                    } while (type != XmlPullParser.END_DOCUMENT && parser.depth > outerDepth)

                    return ComplicationSlotsOption(
                        Id(id),
                        displayName,
                        icon,
                        watchFaceEditorData,
                        complicationSlotOverlays
                    )
                }
            }
        }
    }

    /**
     * A DoubleRangeUserStyleSetting represents a setting with a [Double] value in the range
     * `[minimumValue .. maximumValue]`.
     */
    public class DoubleRangeUserStyleSetting : UserStyleSetting {

        internal companion object {
            internal fun createOptionsList(
                minimumValue: Double,
                maximumValue: Double,
                defaultValue: Double
            ): List<DoubleRangeOption> {
                require(minimumValue < maximumValue)
                require(defaultValue >= minimumValue)
                require(defaultValue <= maximumValue)

                return if (defaultValue != minimumValue && defaultValue != maximumValue) {
                    listOf(
                        DoubleRangeOption(minimumValue),
                        DoubleRangeOption(defaultValue),
                        DoubleRangeOption(maximumValue)
                    )
                } else {
                    listOf(DoubleRangeOption(minimumValue), DoubleRangeOption(maximumValue))
                }
            }

            @SuppressLint("ResourceType")
            fun inflate(
                resources: Resources,
                parser: XmlResourceParser
            ): DoubleRangeUserStyleSetting {
                val settingType = "DoubleRangeUserStyleSetting"
                val parent =
                    createParent(resources, parser, settingType, ::inflate)
                val maxDouble = getAttributeChecked(
                    parser, "maxDouble", String::toDouble, parent?.maximumValue, settingType
                )
                val minDouble = getAttributeChecked(
                    parser, "minDouble", String::toDouble, parent?.minimumValue, settingType
                )
                val defaultDouble = getAttributeChecked(
                    parser, "defaultDouble", String::toDouble, parent?.defaultValue, settingType
                )
                val params = createBaseWithParent(
                    resources,
                    parser,
                    parent,
                    inflateDefault = false
                )
                return DoubleRangeUserStyleSetting(
                    params.id,
                    params.displayName,
                    params.description,
                    params.icon,
                    params.watchFaceEditorData,
                    minDouble,
                    maxDouble,
                    params.affectedWatchFaceLayers,
                    defaultDouble
                )
            }
        }

        /**
         * Constructs a DoubleRangeUserStyleSetting.
         *
         * @param id [Id] for the element, must be unique.
         * @param displayName Localized human readable name for the element, used in the user style
         * selection UI.
         * @param description Localized description string displayed under the displayName.
         * @param icon [Icon] for use in the companion style selection UI. This gets sent to the
         * companion over bluetooth and should be small (ideally a few kb in size).
         * @param minimumValue Minimum value (inclusive).
         * @param maximumValue Maximum value (inclusive).
         * @param affectsWatchFaceLayers Used by the style configuration UI. Describes which watch
         * face rendering layers this style affects.
         * @param defaultValue The default value for this DoubleRangeUserStyleSetting.
         * @param watchFaceEditorData Optional data for an on watch face editor, this will not be sent
         * to the companion and its contents may be used in preference to other fields by an on
         * watch face editor.
         * @hide
         */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        @JvmOverloads
        public constructor (
            id: Id,
            displayName: CharSequence,
            description: CharSequence,
            icon: Icon?,
            minimumValue: Double,
            maximumValue: Double,
            affectsWatchFaceLayers: Collection<WatchFaceLayer>,
            defaultValue: Double,
            watchFaceEditorData: WatchFaceEditorData? = null
        ) : super(
            id,
            DisplayText.CharSequenceDisplayText(displayName),
            DisplayText.CharSequenceDisplayText(description),
            icon,
            watchFaceEditorData,
            createOptionsList(minimumValue, maximumValue, defaultValue),
            // The index of defaultValue can only ever be 0 or 1.
            when (defaultValue) {
                minimumValue -> 0
                else -> 1
            },
            affectsWatchFaceLayers
        )

        /**
         * Constructs a DoubleRangeUserStyleSetting where
         * [DoubleRangeUserStyleSetting.displayName] and
         * [DoubleRangeUserStyleSetting.description] are specified as resources.
         *
         * @param id [Id] for the element, must be unique.
         * @param resources The [Resources] from which [displayNameResourceId] and
         * [descriptionResourceId] are loaded.
         * @param displayNameResourceId String resource id for a human readable name for the element,
         * used in the userStyle selection UI.
         * @param descriptionResourceId String resource id for a human readable description string
         * displayed under the displayName.
         * @param icon [Icon] for use in the companion userStyle selection UI. This gets sent to the
         * companion over bluetooth and should be small (ideally a few kb in size).
         * @param minimumValue Minimum value (inclusive).
         * @param maximumValue Maximum value (inclusive).
         * @param affectsWatchFaceLayers Used by the style configuration UI. Describes which watch
         * face rendering layers this style affects.
         * @param defaultValue The default value for this DoubleRangeUserStyleSetting.
         * @param watchFaceEditorData Optional data for an on watch face editor, this will not be sent
         * to the companion and its contents may be used in preference to other fields by an on
         * watch face editor.
         */
        @JvmOverloads
        public constructor (
            id: Id,
            resources: Resources,
            @StringRes displayNameResourceId: Int,
            @StringRes descriptionResourceId: Int,
            icon: Icon?,
            minimumValue: Double,
            maximumValue: Double,
            affectsWatchFaceLayers: Collection<WatchFaceLayer>,
            defaultValue: Double,
            watchFaceEditorData: WatchFaceEditorData? = null
        ) : super(
            id,
            DisplayText.ResourceDisplayText(resources, displayNameResourceId),
            DisplayText.ResourceDisplayText(resources, descriptionResourceId),
            icon,
            watchFaceEditorData,
            createOptionsList(minimumValue, maximumValue, defaultValue),
            // The index of defaultValue can only ever be 0 or 1.
            when (defaultValue) {
                minimumValue -> 0
                else -> 1
            },
            affectsWatchFaceLayers
        )

        internal constructor (
            id: Id,
            displayName: DisplayText,
            description: DisplayText,
            icon: Icon?,
            watchFaceEditorData: WatchFaceEditorData?,
            minimumValue: Double,
            maximumValue: Double,
            affectsWatchFaceLayers: Collection<WatchFaceLayer>,
            defaultValue: Double
        ) : super(
            id,
            displayName,
            description,
            icon,
            watchFaceEditorData,
            createOptionsList(minimumValue, maximumValue, defaultValue),
            // The index of defaultValue can only ever be 0 or 1.
            when (defaultValue) {
                minimumValue -> 0
                else -> 1
            },
            affectsWatchFaceLayers
        )

        internal constructor(wireFormat: DoubleRangeUserStyleSettingWireFormat) : super(wireFormat)

        /** @hide */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
        override fun toWireFormat(): DoubleRangeUserStyleSettingWireFormat =
            DoubleRangeUserStyleSettingWireFormat(
                id.value,
                displayName,
                description,
                icon,
                getWireFormatOptionsList(),
                defaultOptionIndex,
                affectedWatchFaceLayers.map { it.ordinal },
                watchFaceEditorData?.toWireFormat(),
                /* optionsOnWatchFaceEditorIcons = */null
            )

        /** Represents an option as a [Double] in the range [minimumValue .. maximumValue]. */
        public class DoubleRangeOption : Option {
            /* The value for this option. Must be within the range [minimumValue .. maximumValue].*/
            public val value: Double

            /**
             * Constructs a DoubleRangeOption.
             *
             * @param value The value of this DoubleRangeOption
             */
            public constructor(value: Double) : super(
                Id(ByteArray(8).apply { ByteBuffer.wrap(this).putDouble(value) }),
                emptyList()
            ) {
                this.value = value
            }

            internal constructor(
                wireFormat: DoubleRangeOptionWireFormat
            ) : super(Id(wireFormat.mId), emptyList()) {
                value = ByteBuffer.wrap(wireFormat.mId).double
            }

            internal override fun getUserStyleSettingClass(): Class<out UserStyleSetting> =
                DoubleRangeUserStyleSetting::class.java

            /** @hide */
            @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
            override fun toWireFormat(): DoubleRangeOptionWireFormat =
                DoubleRangeOptionWireFormat(id.value)

            override fun write(dos: DataOutputStream) {
                dos.write(id.value)
                dos.writeDouble(value)
            }

            override fun toString(): String = value.toString()
        }

        /** Returns the minimum value. */
        public val minimumValue: Double
            get() = (options.first() as DoubleRangeOption).value

        /** Returns the maximum value. */
        public val maximumValue: Double
            get() = (options.last() as DoubleRangeOption).value

        /** Returns the default value. */
        public val defaultValue: Double
            get() = (options[defaultOptionIndex] as DoubleRangeOption).value

        /** We support all values in the range [min ... max] not just min & max. */
        override fun getOptionForId(optionId: Option.Id): Option =
            options.find { it.id.value.contentEquals(optionId.value) } ?: checkedOptionForId(
                optionId.value
            )

        private fun checkedOptionForId(optionId: ByteArray): DoubleRangeOption {
            return try {
                val value = ByteBuffer.wrap(optionId).double
                if (value < minimumValue || value > maximumValue) {
                    options[defaultOptionIndex] as DoubleRangeOption
                } else {
                    DoubleRangeOption(value)
                }
            } catch (e: Exception) {
                options[defaultOptionIndex] as DoubleRangeOption
            }
        }
    }

    /** A ListUserStyleSetting represents a setting with options selected from a List. */
    public open class ListUserStyleSetting : UserStyleSetting {

        /**
         * Constructs a ListUserStyleSetting.
         *
         * @param id [Id] for the element, must be unique.
         * @param displayName Localized human readable name for the element, used in the userStyle
         * selection UI.
         * @param description Localized description string displayed under the displayName.
         * @param icon [Icon] for use in the companion userStyle selection UI. This gets sent to the
         * companion over bluetooth and should be small (ideally a few kb in size).
         * @param options List of all options for this ListUserStyleSetting.
         * @param affectsWatchFaceLayers Used by the style configuration UI. Describes which watch
         * face rendering layers this style affects.
         * @param defaultOption The default option, used when data isn't persisted.
         * @param watchFaceEditorData Optional data for an on watch face editor, this will not be sent
         * to the companion and its contents may be used in preference to other fields by an on
         * watch face editor.
         * @hide
         */
        @JvmOverloads
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public constructor(
            id: Id,
            displayName: CharSequence,
            description: CharSequence,
            icon: Icon?,
            options: List<ListOption>,
            affectsWatchFaceLayers: Collection<WatchFaceLayer>,
            defaultOption: ListOption = options.first(),
            watchFaceEditorData: WatchFaceEditorData? = null
        ) : super(
            id,
            DisplayText.CharSequenceDisplayText(displayName),
            DisplayText.CharSequenceDisplayText(description),
            icon,
            watchFaceEditorData,
            options,
            options.indexOf(defaultOption),
            affectsWatchFaceLayers
        ) {
            requireUniqueOptionIds(id, options)
        }

        /**
         * Constructs a ListUserStyleSetting where [ListUserStyleSetting.displayName] and
         * [ListUserStyleSetting.description] are specified as resources.
         *
         * @param id [Id] for the element, must be unique.
         * @param resources The [Resources] from which [displayNameResourceId] and
         * [descriptionResourceId] are loaded.
         * @param displayNameResourceId String resource id for a human readable name for the element,
         * used in the userStyle selection UI.
         * @param descriptionResourceId String resource id for a human readable description string
         * displayed under the displayName.
         * @param icon [Icon] for use in the companion userStyle selection UI. This gets sent to the
         * companion over bluetooth and should be small (ideally a few kb in size).
         * @param options List of all options for this ListUserStyleSetting.
         * @param affectsWatchFaceLayers Used by the style configuration UI. Describes which watch
         * face rendering layers this style affects.
         * @param defaultOption The default option, used when data isn't persisted.
         * @param watchFaceEditorData Optional data for an on watch face editor, this will not be sent
         * to the companion and its contents may be used in preference to other fields by an on
         * watch face editor.
         */
        @JvmOverloads
        public constructor (
            id: Id,
            resources: Resources,
            @StringRes displayNameResourceId: Int,
            @StringRes descriptionResourceId: Int,
            icon: Icon?,
            options: List<ListOption>,
            affectsWatchFaceLayers: Collection<WatchFaceLayer>,
            defaultOption: ListOption = options.first(),
            watchFaceEditorData: WatchFaceEditorData? = null
        ) : super(
            id,
            DisplayText.ResourceDisplayText(resources, displayNameResourceId),
            DisplayText.ResourceDisplayText(resources, descriptionResourceId),
            icon,
            watchFaceEditorData,
            options,
            options.indexOf(defaultOption),
            affectsWatchFaceLayers
        ) {
            requireUniqueOptionIds(id, options)
        }

        internal constructor (
            id: Id,
            displayName: DisplayText,
            description: DisplayText,
            icon: Icon?,
            watchFaceEditorData: WatchFaceEditorData?,
            options: List<ListOption>,
            affectsWatchFaceLayers: Collection<WatchFaceLayer>,
            defaultOptionIndex: Int
        ) : super(
            id,
            displayName,
            description,
            icon,
            watchFaceEditorData,
            options,
            defaultOptionIndex,
            affectsWatchFaceLayers
        ) {
            require(defaultOptionIndex >= 0 && defaultOptionIndex < options.size) {
                "defaultOptionIndex must be within the range of the options list"
            }
        }

        internal constructor(wireFormat: ListUserStyleSettingWireFormat) : super(wireFormat) {
            wireFormat.mPerOptionOnWatchFaceEditorBundles?.let { optionsOnWatchFaceEditorIcons ->
                val optionsIterator = options.iterator()
                for (bundle in optionsOnWatchFaceEditorIcons) {
                    val option = optionsIterator.next() as ListOption
                    bundle?.let {
                        option.watchFaceEditorData = WatchFaceEditorData(it)
                    }
                }
            }
        }

        /** @hide */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
        override fun toWireFormat(): ListUserStyleSettingWireFormat =
            ListUserStyleSettingWireFormat(
                id.value,
                displayName,
                description,
                icon,
                getWireFormatOptionsList(),
                defaultOptionIndex,
                affectedWatchFaceLayers.map { it.ordinal },
                watchFaceEditorData?.toWireFormat(),
                options.map { (it as ListOption).watchFaceEditorData?.toWireFormat() ?: Bundle() }
            )

        internal companion object {
            private fun <T> bindIdToSetting(
                function: (
                    resources: Resources,
                    parser: XmlResourceParser,
                    idToSetting: Map<String, UserStyleSetting>
                ) -> T,
                idToSetting: Map<String, UserStyleSetting>
            ): (resources: Resources, parser: XmlResourceParser) -> T {
                return { resources: Resources, parser: XmlResourceParser ->
                    function(resources, parser, idToSetting)
                }
            }

            @SuppressLint("ResourceType")
            @Suppress("UNCHECKED_CAST")
            fun inflate(
                resources: Resources,
                parser: XmlResourceParser,
                idToSetting: Map<String, UserStyleSetting>
            ): ListUserStyleSetting {
                val params = createBaseWithParent(
                    resources,
                    parser,
                    createParent(
                        resources,
                        parser,
                        "ListUserStyleSetting",
                        bindIdToSetting(::inflate, idToSetting)
                    ),
                    inflateDefault = true,
                    "ListOption" to bindIdToSetting(ListOption::inflate, idToSetting)
                )
                return ListUserStyleSetting(
                    params.id,
                    params.displayName,
                    params.description,
                    params.icon,
                    params.watchFaceEditorData,
                    params.options as List<ListOption>,
                    params.affectedWatchFaceLayers,
                    params.defaultOptionIndex!!
                )
            }
        }

        /**
         * Represents choice within a [ListUserStyleSetting], these must be enumerated up front.
         *
         * If [childSettings] is not empty, then an editor needs to treat this as a node in a
         * hierarchy of editor widgets.
         */
        public class ListOption : Option {
            /** Backing field for [displayName]. */
            private val displayNameInternal: DisplayText

            /** Localized human readable name for the setting, used in the style selection UI. */
            public val displayName: CharSequence
                get() = displayNameInternal.toCharSequence()

            /** Icon for use in the companion style selection UI. */
            public val icon: Icon?

            /**
             * Optional data for an on watch face editor, this will not be sent to the companion
             * and its contents may be used in preference to other fields by an on watch face
             * editor.
             */
            public var watchFaceEditorData: WatchFaceEditorData?
                internal set

            /**
             * Constructs a ListOption.
             *
             * @param id The [Id] of this ListOption, must be unique within the
             * [ListUserStyleSetting].
             * @param displayName Localized human readable name for the setting, used in the style
             * selection UI.
             * @param icon [Icon] for use in the companion style selection UI. This gets sent to the
             * companion over bluetooth and should be small (ideally a few kb in size).
             * @param watchFaceEditorData Optional data for an on watch face editor, this will not be
             * sent to the companion and its contents may be used in preference to other fields by
             * an on watch face editor.
             * @hide
             */
            @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
            constructor(
                id: Id,
                displayName: CharSequence,
                icon: Icon?,
                childSettings: Collection<UserStyleSetting> = emptyList(),
                watchFaceEditorData: WatchFaceEditorData? = null
            ) : super(id, childSettings) {
                displayNameInternal = DisplayText.CharSequenceDisplayText(displayName)
                this.icon = icon
                this.watchFaceEditorData = watchFaceEditorData
            }

            /**
             * Constructs a ListOption.
             *
             * @param id The [Id] of this ListOption, must be unique within the
             * [ListUserStyleSetting].
             * @param resources The [Resources] used to load [displayNameResourceId].
             * @param displayNameResourceId String resource id for a human readable name for the
             * setting, used in the style selection UI.
             * @param icon [Icon] for use in the companion style selection UI. This gets sent to the
             * companion over bluetooth and should be small (ideally a few kb in size)
             * @param watchFaceEditorData Optional data for an on watch face editor, this will not be
             * sent to the companion and its contents may be used in preference to other fields by
             * an on watch face editor.
             */
            @JvmOverloads
            constructor(
                id: Id,
                resources: Resources,
                @StringRes displayNameResourceId: Int,
                icon: Icon?,
                watchFaceEditorData: WatchFaceEditorData? = null
            ) : super(id, emptyList()) {
                displayNameInternal =
                    DisplayText.ResourceDisplayText(resources, displayNameResourceId)
                this.icon = icon
                this.watchFaceEditorData = watchFaceEditorData
            }

            /**
             * Constructs a ListOption.
             *
             * @param id The [Id] of this ListOption, must be unique within the
             * [ListUserStyleSetting].
             * @param resources The [Resources] used to load [displayNameResourceId].
             * @param displayNameResourceId String resource id for a human readable name for the
             * setting, used in the style selection UI.
             * @param icon [Icon] for use in the style selection UI. This gets sent to the
             * companion over bluetooth and should be small (ideally a few kb in size).
             * These must be in
             * @param childSettings The list of child [UserStyleSetting]s, which may be empty. Any
             * child settings must be listed in [UserStyleSchema.userStyleSettings].
             * @param watchFaceEditorData Optional data for an on watch face editor, this will not be
             * sent to the companion and its contents may be used in preference to other fields by
             * an on watch face editor.
             */
            @ExperimentalHierarchicalStyle
            constructor(
                id: Id,
                resources: Resources,
                @StringRes displayNameResourceId: Int,
                icon: Icon?,
                childSettings: Collection<UserStyleSetting> = emptyList(),
                watchFaceEditorData: WatchFaceEditorData? = null
            ) : super(id, childSettings) {
                displayNameInternal =
                    DisplayText.ResourceDisplayText(resources, displayNameResourceId)
                this.icon = icon
                this.watchFaceEditorData = watchFaceEditorData
            }

            internal constructor(
                id: Id,
                displayName: DisplayText,
                icon: Icon?,
                watchFaceEditorData: WatchFaceEditorData?,
                childSettings: Collection<UserStyleSetting> = emptyList()
            ) : super(id, childSettings) {
                displayNameInternal = displayName
                this.icon = icon
                this.watchFaceEditorData = watchFaceEditorData
            }

            internal constructor(
                wireFormat: ListOptionWireFormat
            ) : super(Id(wireFormat.mId), ArrayList()) {
                displayNameInternal = DisplayText.CharSequenceDisplayText(wireFormat.mDisplayName)
                icon = wireFormat.mIcon
                watchFaceEditorData = null // This gets overwritten.
            }

            internal override fun getUserStyleSettingClass(): Class<out UserStyleSetting> =
                ListUserStyleSetting::class.java

            internal override fun estimateWireSizeInBytesAndValidateIconDimensions(
                context: Context,
                @Px maxWidth: Int,
                @Px maxHeight: Int
            ): Int {
                var sizeEstimate = id.value.size + displayName.length
                icon?.getWireSizeAndDimensions(context)?.let { wireSizeAndDimensions ->
                    wireSizeAndDimensions.wireSizeBytes?.let {
                        sizeEstimate += it
                    }
                    require(
                        wireSizeAndDimensions.width <= maxWidth &&
                            wireSizeAndDimensions.height <= maxHeight
                    ) {
                        "ListOption id $id has a ${wireSizeAndDimensions.width} x " +
                            "${wireSizeAndDimensions.height} icon. This is too big, the maximum " +
                            "size is $maxWidth x $maxHeight."
                    }
                }
                return sizeEstimate
            }

            /** @hide */
            @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
            override fun toWireFormat(): ListOptionWireFormat =
                ListOptionWireFormat(
                    id.value,
                    displayName,
                    icon
                )

            override fun write(dos: DataOutputStream) {
                dos.write(id.value)
                displayNameInternal.write(dos)
                icon?.write(dos)
                watchFaceEditorData?.write(dos)
            }

            internal companion object {
                @SuppressLint("ResourceType")
                fun inflate(
                    resources: Resources,
                    parser: XmlResourceParser,
                    idToSetting: Map<String, UserStyleSetting>
                ): ListOption {
                    val id = getStringRefAttribute(resources, parser, "id")
                    require(id != null) { "ListOption must have an id" }
                    val displayName = createDisplayText(
                        resources,
                        parser,
                        "displayName"
                    )
                    val icon = createIcon(
                        resources,
                        parser
                    )

                    var watchFaceEditorData: WatchFaceEditorData? = null
                    val childSettings = ArrayList<UserStyleSetting>()
                    var type = 0
                    val outerDepth = parser.depth
                    do {
                        if (type == XmlPullParser.START_TAG) {
                            when (parser.name) {
                                "ChildSetting" -> {
                                    val childId = getStringRefAttribute(resources, parser, "id")
                                    require(childId != null) {
                                        "ChildSetting must have an id"
                                    }
                                    val setting = idToSetting[childId]
                                    require(setting != null) {
                                        "Unknown ChildSetting id $childId, note only backward " +
                                            "references are supported."
                                    }
                                    childSettings.add(setting)
                                }

                                "OnWatchEditorData" -> {
                                    if (watchFaceEditorData == null) {
                                        watchFaceEditorData =
                                            WatchFaceEditorData.inflate(resources, parser)
                                    } else {
                                        throw IllegalArgumentException(
                                            "Unexpected node OnWatchEditorData at line " +
                                                parser.lineNumber
                                        )
                                    }
                                }

                                else -> throw IllegalArgumentException(
                                    "Unexpected node ${parser.name} at line ${parser.lineNumber}"
                                )
                            }
                        }
                        type = parser.next()
                    } while (type != XmlPullParser.END_DOCUMENT && parser.depth > outerDepth)

                    return ListOption(
                        Id(id),
                        displayName,
                        icon,
                        watchFaceEditorData,
                        childSettings
                    )
                }
            }
        }
    }

    /**
     * A LongRangeUserStyleSetting represents a setting with a [Long] value in the range
     * [minimumValue .. maximumValue].
     */
    public class LongRangeUserStyleSetting : UserStyleSetting {

        internal companion object {
            internal fun createOptionsList(
                minimumValue: Long,
                maximumValue: Long,
                defaultValue: Long
            ): List<LongRangeOption> {
                require(minimumValue < maximumValue)
                require(defaultValue >= minimumValue)
                require(defaultValue <= maximumValue)

                return if (defaultValue != minimumValue && defaultValue != maximumValue) {
                    listOf(
                        LongRangeOption(minimumValue),
                        LongRangeOption(defaultValue),
                        LongRangeOption(maximumValue)
                    )
                } else {
                    listOf(
                        LongRangeOption(minimumValue),
                        LongRangeOption(maximumValue)
                    )
                }
            }

            @SuppressLint("ResourceType")
            fun inflate(
                resources: Resources,
                parser: XmlResourceParser
            ): LongRangeUserStyleSetting {
                val settingType = "LongRangeUserStyleSetting"
                val parent =
                    createParent(resources, parser, settingType, ::inflate)
                val maxInteger = getAttributeChecked(
                    parser, "maxLong", String::toLong, parent?.maximumValue, settingType
                )
                val minInteger = getAttributeChecked(
                    parser, "minLong", String::toLong, parent?.minimumValue, settingType
                )
                val defaultInteger = getAttributeChecked(
                    parser, "defaultLong", String::toLong, parent?.defaultValue, settingType
                )
                val params = createBaseWithParent(
                    resources,
                    parser,
                    parent,
                    inflateDefault = false
                )
                return LongRangeUserStyleSetting(
                    params.id,
                    params.displayName,
                    params.description,
                    params.icon,
                    params.watchFaceEditorData,
                    minInteger,
                    maxInteger,
                    params.affectedWatchFaceLayers,
                    defaultInteger
                )
            }
        }

        /**
         * Constructs a LongRangeUserStyleSetting.
         *
         * @param id [Id] for the element, must be unique.
         * @param displayName Localized human readable name for the element, used in the userStyle
         * selection UI.
         * @param description Localized description string displayed under the displayName.
         * @param icon [Icon] for use in the companion userStyle selection UI. This gets sent to the
         * companion over bluetooth and should be small (ideally a few kb in size).
         * @param minimumValue Minimum value (inclusive).
         * @param maximumValue Maximum value (inclusive).
         * @param affectsWatchFaceLayers Used by the style configuration UI. Describes which watch
         * face rendering layers this style affects.
         * @param defaultValue The default value for this LongRangeUserStyleSetting.
         * @param watchFaceEditorData Optional data for an on watch face editor, this will not be sent
         * to the companion and its contents may be used in preference to other fields by an on
         * watch face editor.
         * @hide
         */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        @JvmOverloads
        public constructor (
            id: Id,
            displayName: CharSequence,
            description: CharSequence,
            icon: Icon?,
            minimumValue: Long,
            maximumValue: Long,
            affectsWatchFaceLayers: Collection<WatchFaceLayer>,
            defaultValue: Long,
            watchFaceEditorData: WatchFaceEditorData? = null
        ) : super(
            id,
            DisplayText.CharSequenceDisplayText(displayName),
            DisplayText.CharSequenceDisplayText(description),
            icon,
            watchFaceEditorData,
            createOptionsList(minimumValue, maximumValue, defaultValue),
            // The index of defaultValue can only ever be 0 or 1.
            when (defaultValue) {
                minimumValue -> 0
                else -> 1
            },
            affectsWatchFaceLayers
        )

        /**
         * Constructs a LongRangeUserStyleSetting where [LongRangeUserStyleSetting.displayName] and
         * [LongRangeUserStyleSetting.description] are specified as resources.
         *
         * @param id [Id] for the element, must be unique.
         * @param resources The [Resources] from which [displayNameResourceId] and
         * [descriptionResourceId] are loaded.
         * @param displayNameResourceId String resource id for a human readable name for the element,
         * used in the userStyle selection UI.
         * @param descriptionResourceId String resource id for a human readable description string
         * displayed under the displayName.
         * @param icon [Icon] for use in the companion userStyle selection UI. This gets sent to the
         * companion over bluetooth and should be small (ideally a few kb in size).
         * @param minimumValue Minimum value (inclusive).
         * @param maximumValue Maximum value (inclusive).
         * @param affectsWatchFaceLayers Used by the style configuration UI. Describes which watch
         * face rendering layers this style affects.
         * @param defaultValue The default value for this LongRangeUserStyleSetting.
         * @param watchFaceEditorData Optional data for an on watch face editor, this will not be sent
         * to the companion and its contents may be used in preference to other fields by an on
         * watch face editor.
         */
        @JvmOverloads
        public constructor (
            id: Id,
            resources: Resources,
            @StringRes displayNameResourceId: Int,
            @StringRes descriptionResourceId: Int,
            icon: Icon?,
            minimumValue: Long,
            maximumValue: Long,
            affectsWatchFaceLayers: Collection<WatchFaceLayer>,
            defaultValue: Long,
            watchFaceEditorData: WatchFaceEditorData? = null
        ) : super(
            id,
            DisplayText.ResourceDisplayText(resources, displayNameResourceId),
            DisplayText.ResourceDisplayText(resources, descriptionResourceId),
            icon,
            watchFaceEditorData,
            createOptionsList(minimumValue, maximumValue, defaultValue),
            // The index of defaultValue can only ever be 0 or 1.
            when (defaultValue) {
                minimumValue -> 0
                else -> 1
            },
            affectsWatchFaceLayers
        )

        internal constructor (
            id: Id,
            displayName: DisplayText,
            description: DisplayText,
            icon: Icon?,
            watchFaceEditorData: WatchFaceEditorData?,
            minimumValue: Long,
            maximumValue: Long,
            affectsWatchFaceLayers: Collection<WatchFaceLayer>,
            defaultValue: Long
        ) : super(
            id,
            displayName,
            description,
            icon,
            watchFaceEditorData,
            createOptionsList(minimumValue, maximumValue, defaultValue),
            // The index of defaultValue can only ever be 0 or 1.
            when (defaultValue) {
                minimumValue -> 0
                else -> 1
            },
            affectsWatchFaceLayers
        )

        internal constructor(wireFormat: LongRangeUserStyleSettingWireFormat) : super(wireFormat)

        /** @hide */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
        override fun toWireFormat(): LongRangeUserStyleSettingWireFormat =
            LongRangeUserStyleSettingWireFormat(
                id.value,
                displayName,
                description,
                icon,
                getWireFormatOptionsList(),
                defaultOptionIndex,
                affectedWatchFaceLayers.map { it.ordinal },
                watchFaceEditorData?.toWireFormat(),
                /* optionsOnWatchFaceEditorIcons = */null
            )

        /**
         * Represents an option a [Long] in the range [minimumValue .. maximumValue].
         */
        public class LongRangeOption : Option {
            /* The value for this option. Must be within the range [minimumValue..maximumValue]. */
            public val value: Long

            /**
             * Constructs a LongRangeOption.
             *
             * @param value The value of this LongRangeOption
             */
            public constructor(value: Long) : super(
                Id(ByteArray(8).apply { ByteBuffer.wrap(this).putLong(value) }),
                emptyList()
            ) {
                this.value = value
            }

            internal constructor(
                wireFormat: LongRangeOptionWireFormat
            ) : super(Id(wireFormat.mId), emptyList()) {
                value = ByteBuffer.wrap(wireFormat.mId).long
            }

            internal override fun getUserStyleSettingClass(): Class<out UserStyleSetting> =
                LongRangeUserStyleSetting::class.java

            /** @hide */
            @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
            override fun toWireFormat(): LongRangeOptionWireFormat =
                LongRangeOptionWireFormat(id.value)

            override fun write(dos: DataOutputStream) {
                dos.write(id.value)
                dos.writeLong(value)
            }

            override fun toString(): String = value.toString()
        }

        /** The minimum value. */
        public val minimumValue: Long
            get() = (options.first() as LongRangeOption).value

        /** The maximum value. */
        public val maximumValue: Long
            get() = (options.last() as LongRangeOption).value

        /** The default value. */
        public val defaultValue: Long
            get() = (options[defaultOptionIndex] as LongRangeOption).value

        /**
         * We support all values in the range [min ... max] not just min & max.
         */
        override fun getOptionForId(optionId: Option.Id): Option =
            options.find { it.id.value.contentEquals(optionId.value) } ?: checkedOptionForId(
                optionId.value
            )

        private fun checkedOptionForId(optionId: ByteArray): LongRangeOption {
            return try {
                val value = ByteBuffer.wrap(optionId).long
                if (value < minimumValue || value > maximumValue) {
                    options[defaultOptionIndex] as LongRangeOption
                } else {
                    LongRangeOption(value)
                }
            } catch (e: Exception) {
                options[defaultOptionIndex] as LongRangeOption
            }
        }
    }

    /**
     * An application specific style setting. This style is ignored by the system editor. This is
     * expected to be used in conjunction with an on watch face editor. Only a single
     * [ComplicationSlotsUserStyleSetting] is permitted in the [UserStyleSchema].
     */
    public class CustomValueUserStyleSetting : UserStyleSetting {
        internal companion object {
            internal const val CUSTOM_VALUE_USER_STYLE_SETTING_ID = "CustomValue"
        }

        /**
         * Constructs a CustomValueUserStyleSetting.
         *
         * @param affectsWatchFaceLayers Used by the style configuration UI. Describes which watch
         * face rendering layers this style affects.
         * @param defaultValue The default value [ByteArray].
         */
        public constructor (
            affectsWatchFaceLayers: Collection<WatchFaceLayer>,
            defaultValue: ByteArray
        ) : super(
            Id(CUSTOM_VALUE_USER_STYLE_SETTING_ID),
            DisplayText.CharSequenceDisplayText(""),
            DisplayText.CharSequenceDisplayText(""),
            null,
            null,
            listOf(CustomValueOption(defaultValue)),
            0,
            affectsWatchFaceLayers
        )

        internal constructor(wireFormat: CustomValueUserStyleSettingWireFormat) : super(wireFormat)

        /** @hide */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
        override fun toWireFormat(): CustomValueUserStyleSettingWireFormat =
            CustomValueUserStyleSettingWireFormat(
                id.value,
                displayName,
                description,
                icon,
                getWireFormatOptionsList(),
                affectedWatchFaceLayers.map { it.ordinal },
                watchFaceEditorData?.toWireFormat(),
                /* optionsOnWatchFaceEditorIcons = */null
            )

        /**
         * An application specific custom value. NB the [CustomValueOption.customValue] is the same
         * as the [CustomValueOption.id].
         */
        public class CustomValueOption : Option {
            /* The [ByteArray] value for this option which is the same as the [id]. */
            public val customValue: ByteArray
                get() = id.value

            /**
             * Constructs a CustomValueOption.
             *
             * @param customValue The [ByteArray] [id] and value of this CustomValueOption. This
             * may not exceed [Id.MAX_LENGTH].
             */
            public constructor(customValue: ByteArray) : super(Id(customValue), emptyList())

            internal constructor(
                wireFormat: CustomValueOptionWireFormat
            ) : super(Id(wireFormat.mId), emptyList())

            internal override fun getUserStyleSettingClass(): Class<out UserStyleSetting> =
                CustomValueUserStyleSetting::class.java

            /** @hide */
            @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
            override fun toWireFormat(): CustomValueOptionWireFormat =
                CustomValueOptionWireFormat(id.value)

            override fun write(dos: DataOutputStream) {
                dos.write(id.value)
            }
        }

        override fun getOptionForId(optionId: Option.Id): Option =
            options.find { it.id.value.contentEquals(optionId.value) } ?: CustomValueOption(
                optionId.value
            )
    }
}

internal fun requireUniqueOptionIds(
    setting: UserStyleSetting.Id,
    options: List<UserStyleSetting.Option>
) {
    val uniqueIds = HashSet<UserStyleSetting.Option.Id>()
    for (option in options) {
        require(uniqueIds.add(option.id)) {
            "duplicated option id: ${option.id} in $setting"
        }
    }
}

internal class WireSizeAndDimensions(
    val wireSizeBytes: Int?,
    val width: Int,
    val height: Int
)

@RequiresApi(Build.VERSION_CODES.P)
internal class IconHelper {
    internal companion object {
        @SuppressLint("ResourceType")
        fun getWireSizeAndDimensions(icon: Icon, context: Context): WireSizeAndDimensions? {
            when (icon.type) {
                Icon.TYPE_RESOURCE -> {
                    return getWireSizeAndDimensionsFromStream(
                        context.resources.openRawResource(icon.resId, TypedValue()),
                        context.resources
                    )
                }

                Icon.TYPE_URI -> {
                    if (icon.uri.scheme == ContentResolver.SCHEME_CONTENT) {
                        context.contentResolver.openInputStream(icon.uri)?.let {
                            return getWireSizeAndDimensionsFromStream(it, context.resources)
                        }
                    }
                }

                Icon.TYPE_URI_ADAPTIVE_BITMAP -> {
                    if (icon.uri.scheme == ContentResolver.SCHEME_CONTENT) {
                        context.contentResolver.openInputStream(icon.uri)?.let {
                            return getWireSizeAndDimensionsFromStream(it, context.resources)
                        }
                    }
                }
            }
            return null
        }

        fun writeToDataOutputStream(icon: Icon, dos: DataOutputStream) {
            dos.writeInt(icon.type)
            when (icon.type) {
                Icon.TYPE_RESOURCE -> {
                    dos.writeInt(icon.resId)
                    dos.writeUTF(icon.resPackage)
                }

                Icon.TYPE_URI -> dos.writeUTF(icon.uri.toString())

                Icon.TYPE_URI_ADAPTIVE_BITMAP -> dos.writeUTF(icon.uri.toString())
            }

            // Unsupported cases are ignored, as a fallback we could load the icon drawable and
            // convert to a png but that requires a context and is computationally expensive.
        }
    }
}

internal fun Icon.getWireSizeAndDimensions(context: Context): WireSizeAndDimensions {
    // Where possible use the exact wire size.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val wireSizeAndDimensions = IconHelper.getWireSizeAndDimensions(this, context)
        if (wireSizeAndDimensions != null) {
            return wireSizeAndDimensions
        }
    }

    // Fall back to loading the full drawable (comparatively expensive). We can't provide the
    // wire size in this instance.
    val drawable = loadDrawable(context)!!
    return WireSizeAndDimensions(null, drawable.minimumWidth, drawable.minimumHeight)
}

@SuppressLint("ClassVerificationFailure")
internal fun Icon.write(dos: DataOutputStream) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        IconHelper.writeToDataOutputStream(this, dos)
    }
}

private fun getWireSizeAndDimensionsFromStream(
    stream: InputStream,
    resources: Resources
): WireSizeAndDimensions {
    try {
        val wireSize = stream.available()
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResourceStream(
            resources,
            TypedValue(),
            stream,
            null,
            options
        )
        return WireSizeAndDimensions(wireSize, options.outWidth, options.outHeight)
    } finally {
        stream.close()
    }
}

/**
 * Gets the attribute specified by name. If there is no such attribute, applies defaultValue.
 * Throws exception if calculated result is null.
 */
private fun <T> getAttributeChecked(
    parser: XmlResourceParser,
    name: String,
    converter: (String) -> T,
    defaultValue: T?,
    settingType: String
): T {
    return if (parser.hasValue(name)) {
        converter(parser.getAttributeValue(NAMESPACE_APP, name)!!)
    } else {
        defaultValue ?: throw IllegalArgumentException(
            "$name is required for $settingType")
    }
}

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
fun getStringRefAttribute(
    resources: Resources,
    parser: XmlResourceParser,
    name: String
): String? {
    return if (parser.hasValue(name)) {
        val resId = parser.getAttributeResourceValue(NAMESPACE_APP, name, 0)
        if (resId == 0) {
            parser.getAttributeValue(NAMESPACE_APP, name)
        } else {
            resources.getString(resId)
        }
    } else null
}

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
fun getIntRefAttribute(
    resources: Resources,
    parser: XmlResourceParser,
    name: String
): Int? {
    return if (parser.hasValue(name)) {
        val resId = parser.getAttributeResourceValue(NAMESPACE_APP, name, 0)
        if (resId == 0) {
            parser.getAttributeValue(NAMESPACE_APP, name).toInt()
        } else {
            resources.getInteger(resId)
        }
    } else null
}

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
fun XmlPullParser.moveToStart(expectedNode: String) {
    var type: Int
    do {
        type = next()
    } while (type != XmlPullParser.END_DOCUMENT && type != XmlPullParser.START_TAG)

    require(name == expectedNode) {
        "Expected a $expectedNode node but is $name"
    }
}
