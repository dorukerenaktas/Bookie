package com.karambit.bookie.model;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Random;

/**
 * Created by doruk on 24.12.2016.
 */
public class Notification implements Parcelable {

    public enum Type {
        REQUESTED(0),
        REQUEST_ACCEPTED(1),
        REQUEST_REJECTED(2),
        BOOK_OWNER_CHANGED(3),
        BOOK_LOST(4);

        private final int mTypeCode;

        Type(int code) {
            mTypeCode = code;
        }

        public int getTypeCode() {
            return mTypeCode;
        }

        public static Type valueOf(int typeCode) {
            switch (typeCode) {
                case 0:
                    return Type.REQUESTED;
                case 1:
                    return Type.REQUEST_ACCEPTED;
                case 2:
                    return Type.REQUEST_REJECTED;
                case 3:
                    return Type.BOOK_OWNER_CHANGED;
                case 4:
                    return Type.BOOK_LOST;
                default:
                    return null;
            }
        }
    }

    private Type mType;
    private Calendar mCreatedAt;
    private Book mBook;
    private User mOppositeUser;
    private boolean mSeen;

    public Notification(Type type, Calendar createdAt, Book book, User oppositeUser, boolean seen) {
        mType = type;
        mCreatedAt = createdAt;
        mBook = book;
        mOppositeUser = oppositeUser;
        mSeen = seen;
    }

    protected Notification(Parcel in) {
        mType = Notification.Type.values()[in.readInt()];
        Calendar cal = GregorianCalendar.getInstance();
        cal.setTimeInMillis(in.readLong());
        mCreatedAt = cal;
        mBook = in.readParcelable(Book.class.getClassLoader());
        mOppositeUser = in.readParcelable(User.class.getClassLoader());
        mSeen = in.readByte() != 0;
    }

    public Type getType() {
        return mType;
    }

    public void setType(Type type) {
        mType = type;
    }

    public Calendar getCreatedAt() {
        return mCreatedAt;
    }

    public void setCreatedAt(Calendar createdAt) {
        mCreatedAt = createdAt;
    }

    public Book getBook() {
        return mBook;
    }

    public void setBook(Book book) {
        mBook = book;
    }

    public User getOppositeUser() {
        return mOppositeUser;
    }

    public void setOppositeUser(User oppositeUser) {
        mOppositeUser = oppositeUser;
    }

    public boolean isSeen() {
        return mSeen;
    }

    public void setSeen(boolean seen) {
        mSeen = seen;
    }

    public static class GENERATOR {
        public static Notification generateNotification() {
            Type randomType = Type.values()[new Random().nextInt(Type.values().length)];
            return new Notification(randomType, Calendar.getInstance(), Book.GENERATOR.generateBook(), User.GENERATOR.generateUser(), false);
        }

        public static ArrayList<Notification> generateNotificationList(int size) {
            ArrayList<Notification> notifications = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                notifications.add(generateNotification());
            }
            return notifications;
        }
    }

    public static final Parcelable.Creator<Notification> CREATOR = new Parcelable.Creator<Notification>() {
        @Override
        public Notification createFromParcel(Parcel in) {
            return new Notification(in);
        }

        @Override
        public Notification[] newArray(int size) {
            return new Notification[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mType.getTypeCode());
        dest.writeLong(mCreatedAt.getTimeInMillis());
        dest.writeParcelable(mBook, flags);
        dest.writeParcelable(mOppositeUser, flags);
        dest.writeByte((byte) (mSeen ? 1 : 0));
    }


}
