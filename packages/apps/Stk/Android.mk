# Copyright 2007-2008 The Android Open Source Project

LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

#call diff androidmanifest.xml with project name@CHENHUO20130227
ifeq ($(TARGET_BUILD_PROJECT),HK6186)
$(shell cp $(LOCAL_PATH)/AndroidManifest.hk6186 $(LOCAL_PATH)/AndroidManifest.xml)
else
$(shell cp $(LOCAL_PATH)/AndroidManifest.default $(LOCAL_PATH)/AndroidManifest.xml)
endif

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := $(call all-subdir-java-files)

LOCAL_PACKAGE_NAME := Stk
LOCAL_CERTIFICATE := platform

include $(BUILD_PACKAGE)
