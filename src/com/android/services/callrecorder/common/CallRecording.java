package com.android.services.callrecorder.common;

import android.os.Environment;
import android.os.Parcel;
import android.os.Parcelable;

import java.io.File;

public final class CallRecording implements Parcelable {

    public String phoneNumber;
    public long creationTime;
    public String fileName;

    public static final String PUBLIC_DIRECTORY_NAME = "Recordings";

    public static final Parcelable.Creator<CallRecording> CREATOR = new
            Parcelable.Creator<CallRecording>() {
                public CallRecording createFromParcel(Parcel in) {
                    return new CallRecording(in);
                }

                public CallRecording[] newArray(int size) {
                    return new CallRecording[size];
                }
            };

    public CallRecording(String phoneNumber, long creationTime, String fileName) {
        this.phoneNumber = phoneNumber;
        this.creationTime = creationTime;
        this.fileName = fileName;
    }

    public CallRecording(Parcel in) {
        phoneNumber = in.readString();
        creationTime = in.readLong();
        fileName = in.readString();
    }

    public File getFile() {
        return new File(Environment.getExternalStoragePublicDirectory(PUBLIC_DIRECTORY_NAME), fileName);
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeString(phoneNumber);
        out.writeLong(creationTime);
        out.writeString(fileName);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public String toString() {
        return "phoneNumber=" + phoneNumber + ", creationTime=" + creationTime + ", fileName=" + fileName;
    }
}