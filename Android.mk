LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

contacts_common_dir := ../ContactsCommon
incallui_dir := ../InCallUI

src_dirs := src $(contacts_common_dir)/src $(incallui_dir)/src
res_dirs := res $(contacts_common_dir)/res $(incallui_dir)/res

# Google Play Services resource files
res_dirs += ../../../external/google/google_play_services/libproject/google-play-services_lib/res

LOCAL_SRC_FILES := $(call all-java-files-under, $(src_dirs))
LOCAL_RESOURCE_DIR := $(addprefix $(LOCAL_PATH)/, $(res_dirs))
LOCAL_ASSET_DIR := $(LOCAL_PATH)/assets

LOCAL_AAPT_FLAGS := \
    --auto-add-overlay \
    --extra-packages com.android.contacts.common \
    --extra-packages com.android.incallui \
    --extra-packages com.google.android.gms

LOCAL_JAVA_LIBRARIES := telephony-common
LOCAL_STATIC_JAVA_LIBRARIES := \
    com.android.phone.shared \
    com.android.services.telephony.common \
    com.android.vcard \
    android-common \
    guava \
    android-support-v13 \
    android-support-v4 \
    android-ex-variablespeed \
    googleplayservices

LOCAL_REQUIRED_MODULES := libvariablespeed

LOCAL_PACKAGE_NAME := Dialer
LOCAL_CERTIFICATE := shared
LOCAL_PRIVILEGED_MODULE := true

LOCAL_PROGUARD_FLAG_FILES := proguard.flags $(incallui_dir)/proguard.flags

include $(BUILD_PACKAGE)

include $(CLEAR_VARS)
LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES := \
    googleplayservices:../../../external/google/google_play_services/libproject/google-play-services_lib/libs/google-play-services.jar
include $(BUILD_MULTI_PREBUILT)

# Use the following include to make our test apk.
include $(call all-makefiles-under,$(LOCAL_PATH))
