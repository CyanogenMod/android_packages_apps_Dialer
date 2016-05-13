LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

incallui_dir := ../../InCallUI
contacts_common_dir := ../../ContactsCommon
phone_common_dir := ../../PhoneCommon
uicommon_dir := ../../../../external/uicommon
dialer_dir := ..

# We only want this apk build for tests.
LOCAL_MODULE_TAGS := optional
LOCAL_CERTIFICATE := shared
LOCAL_PRIVILEGED_MODULE := true

LOCAL_STATIC_JAVA_AAR_LIBRARIES += \
    ambientsdk

LOCAL_JAVA_LIBRARIES := android.test.runner

# Include all test java files.
LOCAL_SRC_FILES := $(call all-java-files-under, src)

src_dirs := src \
    $(incallui_dir)/src \
    $(contacts_common_dir)/src \
    $(phone_common_dir)/src \
    $(phone_common_dir)/src-ambient \
    $(uicommon_dir)/src \
    $(dialer_dir)/src \
    $(contacts_common_dir)/TestCommon/src

res_dirs := res \
    $(incallui_dir)/res \
    $(dialer_dir)/res \
    $(contacts_common_dir)/res \
    $(phone_common_dir)/res \
    $(uicommon_dir)/res

# Include all test java files.
LOCAL_SRC_FILES := $(call all-java-files-under, $(src_dirs))

LOCAL_RESOURCE_DIR := $(addprefix $(LOCAL_PATH)/, $(res_dirs)) \
    frameworks/support/v7/cardview/res \
    frameworks/support/v7/recyclerview/res \
    frameworks/support/v7/appcompat/res \
    frameworks/support/design/res

LOCAL_AAPT_FLAGS := \
    --auto-add-overlay \
    --extra-packages android.support.v7.cardview \
    --extra-packages android.support.v7.recyclerview \
    --extra-packages com.android.incallui \
    --extra-packages com.android.dialer \
    --extra-packages com.android.contacts.common \
    --extra-packages com.android.phone.common \
    --extra-packages com.cyanogen.ambient \
    --extra-packages com.cyngn.uicommon

LOCAL_STATIC_JAVA_LIBRARIES := \
    android-common \
    android-support-v13 \
    android-support-v4 \
    android-support-v7-cardview \
    android-support-v7-recyclerview \
    com.android.services.telephony.common \
    com.android.vcard \
    guava \
    libphonenumber \
    org.cyanogenmod.platform.sdk \
    picasso-dialer \
    uicommon

LOCAL_JAVA_LIBRARIES := \
    telephony-common \
    android.test.runner \
    ims-common

LOCAL_PACKAGE_NAME := DialerTests

LOCAL_INSTRUMENTATION_FOR := Dialer

include $(BUILD_PACKAGE)
