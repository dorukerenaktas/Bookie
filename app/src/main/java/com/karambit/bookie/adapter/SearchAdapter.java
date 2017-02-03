package com.karambit.bookie.adapter;

import android.content.Context;
import android.graphics.Bitmap;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.SimpleTarget;
import com.karambit.bookie.R;
import com.karambit.bookie.helper.ImageScaler;
import com.karambit.bookie.helper.LayoutUtils;
import com.karambit.bookie.helper.pull_refresh_layout.SmartisanProgressBarDrawable;
import com.karambit.bookie.model.Book;
import com.karambit.bookie.model.User;

import java.util.ArrayList;

/**
 * Created by orcan on 10/19/16.
 */

public class SearchAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private ArrayList<Integer> mGenreCodes = new ArrayList<>();
    private ArrayList<Book> mBooks = new ArrayList<>();
    private ArrayList<User> mUsers = new ArrayList<>();

    private int genrePosition = 0;
    private int bookPosition = 0;
    private int userPosition = 0;


    public static final int TYPE_GENRE_SUBTITLE = 0;
    public static final int TYPE_GENRE = 1;
    public static final int TYPE_BOOK_SUBTITLE = 2;
    public static final int TYPE_BOOK = 3;
    public static final int TYPE_USER_SUBTITLE = 4;
    public static final int TYPE_USER = 5;
    public static final int TYPE_FOOTER = 6;

    private Context mContext;

    private boolean mProgressBarActive;

    private SearchItemClickListener mSearchItemClickListener;

    public SearchAdapter(Context context, ArrayList<Integer> genreCodes, ArrayList<Book> books, ArrayList<User> users) {
        mContext = context;
        mGenreCodes = genreCodes;
        mBooks = books;
        mUsers = users;

        mProgressBarActive = false;
    }

    private static class GenreSubtitleViewHolder extends RecyclerView.ViewHolder {

        private TextView mSubtitleTextView;

        private GenreSubtitleViewHolder(View genreSubtitleView) {
            super(genreSubtitleView);

            mSubtitleTextView = (TextView) genreSubtitleView.findViewById(R.id.subtitleTextView);
        }
    }

    private static class GenreViewHolder extends RecyclerView.ViewHolder {

        private View mGenreItemView;
        private TextView mGenreTextView;

        private GenreViewHolder(View genreView) {
            super(genreView);

            mGenreItemView = genreView.findViewById(R.id.genreItemView);
            mGenreTextView = (TextView) genreView.findViewById(R.id.genreTextView);
        }
    }

    private static class BookSubtitleViewHolder extends RecyclerView.ViewHolder {

        private TextView mSubtitleTextView;

        private BookSubtitleViewHolder(View bookSubtitleView) {
            super(bookSubtitleView);

            mSubtitleTextView = (TextView) bookSubtitleView.findViewById(R.id.subtitleTextView);
        }
    }

    private static class BookViewHolder extends RecyclerView.ViewHolder {

        private View mElevatedSection;
        private ImageView mBookImage;
        private CardView mBookImageCard;
        private TextView mBookName;
        private TextView mBookAuthor;

        private BookViewHolder(View itemBookView) {
            super(itemBookView);

            mElevatedSection = itemBookView.findViewById(R.id.itemBookElevatedSectionRelativeLayout);
            ViewCompat.setElevation(mElevatedSection, LayoutUtils.DP * 2);

            mBookImageCard = (CardView) itemBookView.findViewById(R.id.itemBookImageCardView);
            mBookImageCard.setCardElevation(LayoutUtils.DP * 4);

            mBookImage = (ImageView) itemBookView.findViewById(R.id.itemBookImageView);
            mBookName = (TextView) itemBookView.findViewById(R.id.itemBookNameTextView);
            mBookAuthor = (TextView) itemBookView.findViewById(R.id.itemBookAuthorTextView);

        }
    }

    private static class UserSubtitleViewHolder extends RecyclerView.ViewHolder {

        private TextView mSubtitleTextView;

        private UserSubtitleViewHolder(View userSubtitleView) {
            super(userSubtitleView);

            mSubtitleTextView = (TextView) userSubtitleView.findViewById(R.id.subtitleTextView);
        }
    }

    private static class UserViewHolder extends RecyclerView.ViewHolder {

        private View mUserItemView;
        private ImageView mProfilePictureImageView;
        private TextView mUserNameTextView;

        private UserViewHolder(View userView) {
            super(userView);

            mUserItemView = userView.findViewById(R.id.userItemView);
            mProfilePictureImageView = (ImageView) userView.findViewById(R.id.profilePicureImageView);
            mUserNameTextView = (TextView) userView.findViewById(R.id.userNameTextView);
        }
    }

    private static class FooterViewHolder extends RecyclerView.ViewHolder {

        private ProgressBar mProgressBar;

        private FooterViewHolder(View footerView) {
            super(footerView);

            mProgressBar = (ProgressBar) footerView.findViewById(R.id.footerProgressBar);
        }
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getItemCount() {
        return calculateItemCount();
    }

    /*
       RESULT BOOK
       FOOTER
    */
    @Override
    public int getItemViewType(int position) {

        if (position == getItemCount() - 1) {
            return TYPE_FOOTER;

        } else if (mGenreCodes.size() > 0 && mBooks.size() > 0 && mUsers.size() > 0) {
            genrePosition = 1;
            bookPosition = mGenreCodes.size() + 2;
            userPosition = mGenreCodes.size() + mBooks.size() + 3;
            if (position == 0) {
                return TYPE_GENRE_SUBTITLE;
            } else if (position < mGenreCodes.size() + 1) {
                return TYPE_GENRE;
            } else if (position == mGenreCodes.size() + 1) {
                return TYPE_BOOK_SUBTITLE;
            } else if (position < mGenreCodes.size() + mBooks.size() + 2) {
                return TYPE_BOOK;
            } else if (position == mGenreCodes.size() + mBooks.size() + 2) {
                return TYPE_USER_SUBTITLE;
            } else if (position < mGenreCodes.size() + mBooks.size() + mUsers.size() + 3) {
                return TYPE_USER;
            } else {
                throw new IllegalArgumentException("Invalid view type at position " + position);
            }
        } else if (mGenreCodes.size() > 0 && mBooks.size() > 0 && mUsers.size() < 1) {
            genrePosition = 1;
            bookPosition = mGenreCodes.size() + 2;
            if (position == 0) {
                return TYPE_GENRE_SUBTITLE;
            } else if (position < mGenreCodes.size() + 1) {
                return TYPE_GENRE;
            } else if (position == mGenreCodes.size() + 1) {
                return TYPE_BOOK_SUBTITLE;
            } else if (position < mGenreCodes.size() + mBooks.size() + 2) {
                return TYPE_BOOK;
            } else {
                throw new IllegalArgumentException("Invalid view type at position " + position);
            }
        } else if (mGenreCodes.size() > 0 && mBooks.size() < 1 && mUsers.size() > 0) {
            genrePosition = 1;
            userPosition = mGenreCodes.size() + 2;
            if (position == 0) {
                return TYPE_GENRE_SUBTITLE;
            } else if (position < mGenreCodes.size() + 1) {
                return TYPE_GENRE;
            } else if (position == mGenreCodes.size() + 1) {
                return TYPE_USER_SUBTITLE;
            } else if (position < mGenreCodes.size() + mUsers.size() + 2) {
                return TYPE_USER;
            } else {
                throw new IllegalArgumentException("Invalid view type at position " + position);
            }
        } else if (mGenreCodes.size() < 1 && mBooks.size() > 0 && mUsers.size() > 0) {
            bookPosition = 1;
            userPosition = mBooks.size() + 2;
            if (position == 0) {
                return TYPE_BOOK_SUBTITLE;
            } else if (position < mBooks.size() + 1) {
                return TYPE_BOOK;
            } else if (position == mBooks.size() + 1) {
                return TYPE_USER_SUBTITLE;
            } else if (position < mBooks.size() + mUsers.size() + 2) {
                return TYPE_USER;
            } else {
                throw new IllegalArgumentException("Invalid view type at position " + position);
            }
        } else if (mGenreCodes.size() > 0 && mBooks.size() < 1 && mUsers.size() < 1) {
            genrePosition = 1;
            if (position == 0) {
                return TYPE_GENRE_SUBTITLE;
            } else if (position < mGenreCodes.size() + 1) {
                return TYPE_GENRE;
            } else {
                throw new IllegalArgumentException("Invalid view type at position " + position);
            }
        } else if (mGenreCodes.size() < 1 && mBooks.size() < 1 && mUsers.size() > 0) {
            userPosition = 1;
            if (position == 0) {
                return TYPE_USER_SUBTITLE;
            } else if (position < mUsers.size() + 1) {
                return TYPE_USER;
            } else {
                throw new IllegalArgumentException("Invalid view type at position " + position);
            }
        } else if (mGenreCodes.size() < 1 && mBooks.size() > 0 && mUsers.size() < 1) {
            bookPosition = 1;
            if (position == 0) {
                return TYPE_BOOK_SUBTITLE;
            } else if (position < mBooks.size() + 1) {
                return TYPE_BOOK;
            } else {
                throw new IllegalArgumentException("Invalid view type at position " + position);
            }
        } else {
            throw new IllegalArgumentException("Invalid view type at position " + position);
        }
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) { //View inflating for view types and creating ViewHolders

        switch (viewType) {

            case TYPE_GENRE_SUBTITLE:
                View genreSubtitleView = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_search_subtitle, parent, false);
                return new GenreSubtitleViewHolder(genreSubtitleView);

            case TYPE_GENRE:
                View genreView = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_genre, parent, false);
                return new GenreViewHolder(genreView);

            case TYPE_BOOK_SUBTITLE:
                View bookSubtitleView = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_search_subtitle, parent, false);
                return new BookSubtitleViewHolder(bookSubtitleView);

            case TYPE_BOOK:
                View bookView = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_search_book, parent, false);
                return new BookViewHolder(bookView);

            case TYPE_USER_SUBTITLE:
                View userSubtitleView = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_search_subtitle, parent, false);
                return new UserSubtitleViewHolder(userSubtitleView);

            case TYPE_USER:
                View userView = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_user, parent, false);
                return new UserViewHolder(userView);

            case TYPE_FOOTER:
                View footerView = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_footer, parent, false);
                return new FooterViewHolder(footerView);

            default:
                throw new IllegalArgumentException("Invalid view type variable: viewType=" + viewType);
        }
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {

        switch (getItemViewType(position)) {

            case TYPE_GENRE_SUBTITLE: {
                final GenreSubtitleViewHolder genreSubtitleViewHolder = (GenreSubtitleViewHolder) holder;
                genreSubtitleViewHolder.mSubtitleTextView.setText(mContext.getString(R.string.genres));
                break;
            }

            case TYPE_GENRE: {
                final GenreViewHolder genreViewHolder = (GenreViewHolder) holder;

                final int finalPosition = position;

                if (mSearchItemClickListener != null) {
                    genreViewHolder.mGenreItemView.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            mSearchItemClickListener.onGenreClick(mGenreCodes.get(finalPosition - genrePosition));
                        }
                    });
                }

                genreViewHolder.mGenreTextView.setText(mContext.getResources().getStringArray(R.array.genre_types)[mGenreCodes.get(position - genrePosition)]);
                break;
            }

            case TYPE_BOOK_SUBTITLE: {
                final BookSubtitleViewHolder bookSubtitleViewHolder = (BookSubtitleViewHolder) holder;
                bookSubtitleViewHolder.mSubtitleTextView.setText(mContext.getString(R.string.books));
                break;
            }

            case TYPE_BOOK: {

                final BookViewHolder bookHolder = (BookViewHolder) holder;
                final Book book = mBooks.get(position - bookPosition);

                if (mSearchItemClickListener != null) {
                    bookHolder.mElevatedSection.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            mSearchItemClickListener.onBookClick(book);
                        }
                    });
                }
                bookHolder.mBookName.setText(book.getName());

                bookHolder.mBookAuthor.setText(book.getAuthor());

                Glide.with(mContext)
                        .load(book.getThumbnailURL())
                        .asBitmap()
                        .placeholder(R.drawable.placeholder_88dp)
                        .error(R.drawable.error_88dp)
                        .into(new SimpleTarget<Bitmap>() {
                            @Override
                            public void onResourceReady(Bitmap resource, GlideAnimation<? super Bitmap> glideAnimation) {
                                Bitmap croppedBitmap = ImageScaler.cropImage(resource, 72 / 96f);
                                bookHolder.mBookImage.setImageBitmap(croppedBitmap);
                            }
                        });
                break;
            }

            case TYPE_USER_SUBTITLE: {
                final UserSubtitleViewHolder userSubtitleViewHolder = (UserSubtitleViewHolder) holder;
                userSubtitleViewHolder.mSubtitleTextView.setText(mContext.getString(R.string.users));
                break;
            }

            case TYPE_USER: {
                final UserViewHolder userViewHolder = (UserViewHolder) holder;
                final User user = mUsers.get(position - userPosition);

                if (mSearchItemClickListener != null) {
                    userViewHolder.mUserItemView.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            mSearchItemClickListener.onUserClick(user);
                        }
                    });
                }

                userViewHolder.mUserNameTextView.setText(user.getName());

                Glide.with(mContext)
                        .load(user.getThumbnailUrl())
                        .asBitmap()
                        .placeholder(R.drawable.placeholder_88dp)
                        .error(R.drawable.error_88dp)
                        .into(new SimpleTarget<Bitmap>() {
                            @Override
                            public void onResourceReady(Bitmap resource, GlideAnimation<? super Bitmap> glideAnimation) {
                                Bitmap croppedBitmap = ImageScaler.cropImage(resource, 72 / 96f);
                                userViewHolder.mProfilePictureImageView.setImageBitmap(croppedBitmap);
                            }
                        });
                break;
            }

            case TYPE_FOOTER: {

                FooterViewHolder footerHolder = (FooterViewHolder) holder;
                footerHolder.mProgressBar.setIndeterminateDrawable(new SmartisanProgressBarDrawable(mContext));

                if (mProgressBarActive) {
                    footerHolder.mProgressBar.setVisibility(View.VISIBLE);
                } else {
                    footerHolder.mProgressBar.setVisibility(View.GONE);
                }

                break;
            }
        }
    }

    private int calculateItemCount() {
        int totalSize = mGenreCodes.size();
        if (mGenreCodes.size() > 0) {
            totalSize++; //GenreSubtitle
        }

        totalSize += mBooks.size();
        if (mBooks.size() > 0) {
            totalSize++; //BookSubtitle
        }

        totalSize += mUsers.size();
        if (mUsers.size() > 0) {
            totalSize++; //UserSubtitle
        }
        totalSize++; //Footer
        return totalSize;
    }

    public boolean isProgressBarActive() {
        return mProgressBarActive;
    }

    public void setProgressBarActive(boolean progressBarActive) {
        mProgressBarActive = progressBarActive;
        notifyItemChanged(getItemCount() - 1);
    }

    public interface SearchItemClickListener {
        void onGenreClick(int genreCode);

        void onBookClick(Book book);

        void onUserClick(User user);
    }

    public void setBookClickListener(SearchItemClickListener searchItemClickListener) {
        mSearchItemClickListener = searchItemClickListener;
    }

    public void setItems(ArrayList<Integer> genreCodes, ArrayList<Book> books, ArrayList<User> users){
        mGenreCodes = genreCodes;
        mBooks = books;
        mUsers = users;

        notifyDataSetChanged();
    }

}
