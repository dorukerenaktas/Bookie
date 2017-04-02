package com.karambit.bookie.fragment;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.karambit.bookie.AddBookActivity;
import com.karambit.bookie.BookActivity;
import com.karambit.bookie.BookieApplication;
import com.karambit.bookie.MainActivity;
import com.karambit.bookie.PhotoViewerActivity;
import com.karambit.bookie.ProfileActivity;
import com.karambit.bookie.R;
import com.karambit.bookie.adapter.ProfileTimelineAdapter;
import com.karambit.bookie.database.DBManager;
import com.karambit.bookie.helper.ElevationScrollListener;
import com.karambit.bookie.helper.SessionManager;
import com.karambit.bookie.helper.pull_refresh_layout.PullRefreshLayout;
import com.karambit.bookie.model.Book;
import com.karambit.bookie.model.Notification;
import com.karambit.bookie.model.User;
import com.karambit.bookie.rest_api.BookieClient;
import com.karambit.bookie.rest_api.UserApi;
import com.karambit.bookie.service.BookieIntentFilters;
import com.orhanobut.logger.Logger;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import static com.karambit.bookie.model.Book.State.CLOSED_TO_SHARE;
import static com.karambit.bookie.model.Book.State.ON_ROAD;
import static com.karambit.bookie.model.Book.State.OPENED_TO_SHARE;

/**
 * A simple {@link Fragment} subclass.
 */
public class ProfileFragment extends Fragment {

    private static final String TAG = ProfileFragment.class.getSimpleName();

    public static final int TAB_INDEX = 3;
    public static final int VIEW_PAGER_INDEX = 2;
    public static final String TAB_SPEC = "tab_profile";
    public static final String TAB_INDICATOR = "tab3";

    private static final int REQUEST_CODE_ADD_BOOK_ACTIVITY = 3;

    private static final int UPDATE_PROFILE_PICTURE_REQUEST_CODE = 1;
    private static final int UPDATE_BOOK_PROCESS_REQUEST_CODE = 2;

    private static final String EXTRA_USER = "user";

    private User mUser;

    private DBManager mDbManager;
    private ProfileTimelineAdapter mProfileTimelineAdapter;
    private PullRefreshLayout mPullRefreshLayout;
    private User.Details mUserDetails;
    private BroadcastReceiver mMessageReceiver;
    private RecyclerView mProfileRecyclerView;

    public ProfileFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param user Parameter 2.
     * @return A new instance of fragment BlankFragment.
     */
    public static ProfileFragment newInstance(User user) {
        ProfileFragment fragment = new ProfileFragment();
        Bundle args = new Bundle();
        args.putParcelable(EXTRA_USER, user);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mUser = getArguments().getParcelable(EXTRA_USER);
        }
    }


    @Override
    public View onCreateView(final LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View rootView = inflater.inflate(R.layout.fragment_profile, container, false);

        mProfileRecyclerView = (RecyclerView) rootView.findViewById(R.id.profileRecyclerView);
        mProfileRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        mProfileRecyclerView.setItemAnimator(new DefaultItemAnimator() {
            @Override
            public boolean canReuseUpdatedViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, @NonNull List<Object> payloads) {
                return true;
            }

            @Override
            public boolean canReuseUpdatedViewHolder(@NonNull RecyclerView.ViewHolder viewHolder) {
                return true;
            }
        });

        mDbManager = new DBManager(getContext());
        mDbManager.open();

        final User currentUser = SessionManager.getCurrentUser(getContext());

        if (mUser.equals(currentUser)){
            mProfileTimelineAdapter = new ProfileTimelineAdapter(getContext(), SessionManager.getCurrentUserDetails(getContext()));
        }else {
            mProfileTimelineAdapter = new ProfileTimelineAdapter(getContext(), mUser);
        }

        mProfileTimelineAdapter.setBookClickListener(new ProfileTimelineAdapter.BookClickListener() {
            @Override
            public void onBookClick(Book book) {
                Intent intent = new Intent(getContext(), BookActivity.class);
                intent.putExtra(BookActivity.EXTRA_BOOK, book);
                startActivityForResult(intent, UPDATE_BOOK_PROCESS_REQUEST_CODE);
            }
        });

        mProfileTimelineAdapter.setStartReadingClickListener(new ProfileTimelineAdapter.StartReadingClickListener() {
            @Override
            public void onStartReadingClick(User.Details userDetails) {

                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                View dialogView = getActivity().getLayoutInflater().inflate(R.layout.dialog_start_reading, null);
                builder.setView(dialogView);
                final AlertDialog startReadingDialog = builder.create();

                Button addBookButton = (Button) dialogView.findViewById(R.id.addYourBook);
                Button searchBookButton = (Button) dialogView.findViewById(R.id.searchYourBook);
                Button existingBook = (Button) dialogView.findViewById(R.id.existingBook);
                View existingBookDivider = dialogView.findViewById(R.id.existingBookDivider);

                addBookButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startReadingDialog.dismiss();
                        startActivityForResult(new Intent(getActivity(), AddBookActivity.class), REQUEST_CODE_ADD_BOOK_ACTIVITY);
                    }
                });

                searchBookButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startReadingDialog.dismiss();
                        ((MainActivity) getActivity()).setCurrentPage(1);
                    }
                });

                if (mUserDetails.getBooksOnHandCount() > 0) {

                    existingBook.setVisibility(View.VISIBLE);
                    existingBookDivider.setVisibility(View.VISIBLE);

                    existingBook.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            startReadingDialog.dismiss();

                            int firstBookOnHandIndex = mProfileTimelineAdapter.getFirstBookOnHandIndex();
                            LinearLayoutManager layoutManager = (LinearLayoutManager) mProfileRecyclerView.getLayoutManager();

                            // scrollToPositionWithOffset() is scrolling to position and aligns item to top unlike other scroll methods
                            layoutManager.scrollToPositionWithOffset(firstBookOnHandIndex - 1, 0);
                        }
                    });

                } else {
                    existingBook.setVisibility(View.GONE);
                    existingBookDivider.setVisibility(View.GONE);
                }

                startReadingDialog.show();
            }
        });

        mProfileTimelineAdapter.setHeaderClickListeners(new ProfileTimelineAdapter.HeaderClickListeners() {
            @Override
            public void onProfilePictureClick(User.Details details) {
                Intent intent = new Intent(getContext(), PhotoViewerActivity.class);
                Bundle bundle = new Bundle();
                bundle.putParcelable(PhotoViewerActivity.EXTRA_USER, details.getUser());
                bundle.putString(PhotoViewerActivity.EXTRA_IMAGE, details.getUser().getImageUrl());
                intent.putExtras(bundle);
                startActivityForResult(intent, UPDATE_PROFILE_PICTURE_REQUEST_CODE);
            }

            @Override
            public void onLocationClick(User.Details details) {

            }
        });

        mProfileRecyclerView.setAdapter(mProfileTimelineAdapter);

        if (mUser.equals(currentUser)){
            mProfileRecyclerView.setOnScrollListener(new ElevationScrollListener((MainActivity) getActivity(), TAB_INDEX));
        }else{
            mProfileRecyclerView.setOnScrollListener(new ElevationScrollListener((ProfileActivity) getActivity()));
        }

        mPullRefreshLayout = (PullRefreshLayout) rootView.findViewById(R.id.swipeRefreshLayout);

        // listen refresh event
        mPullRefreshLayout.setOnRefreshListener(new PullRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                // start refresh
                fetchProfilePageArguments();
            }
        });

        mPullRefreshLayout.setRefreshing(true);
        fetchProfilePageArguments();

        mMessageReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equalsIgnoreCase(BookieIntentFilters.FCM_INTENT_FILTER_ACCEPTED_REQUEST_RECEIVED)){
                    if (intent.getParcelableExtra(BookieIntentFilters.EXTRA_NOTIFICATION) != null){
                        Notification notification = intent.getParcelableExtra(BookieIntentFilters.EXTRA_NOTIFICATION);

                        Logger.d("Accepted request received from FCM: " + notification);

                        if (mUser.equals(currentUser)) {
                            int onRoadBookCountBeforeAdding = mUserDetails.getOnRoadBooks().size();

                            mUserDetails.getOnRoadBooks().add(0, notification.getBook());

                            mProfileTimelineAdapter.notifyItemInserted(mProfileTimelineAdapter.getFirstOnRoadBookIndex());

                            if (onRoadBookCountBeforeAdding == 0) {
                                mProfileTimelineAdapter.notifyItemInserted(mProfileTimelineAdapter.getOnRoadBookSubtitleIndex(false));
                            }

                        }
                    }
                } else if (intent.getAction().equalsIgnoreCase(BookieIntentFilters.FCM_INTENT_FILTER_BOOK_OWNER_CHANGED_RECEIVED)){
                    if (intent.getParcelableExtra(BookieIntentFilters.EXTRA_NOTIFICATION) != null){
                        Notification notification = intent.getParcelableExtra(BookieIntentFilters.EXTRA_NOTIFICATION);

                        if (mUserDetails.getBooksOnHand().contains(notification.getBook())) {
                            int removedBookIndex = mUserDetails.getBooksOnHand().indexOf(notification.getBook());
                            int firstBookOnHandIndexBeforeRemoving = mProfileTimelineAdapter.getFirstBookOnHandIndex();

                            mUserDetails.getBooksOnHand().remove(notification.getBook());
                            mUserDetails.setPoint(mUserDetails.getPoint() + User.POINT_SHARE_BOOK);
                            mUserDetails.setSharedPoint(mUserDetails.getSharedPoint() + 1);

                            mProfileTimelineAdapter.notifyItemRemoved(firstBookOnHandIndexBeforeRemoving + removedBookIndex);

                            mProfileTimelineAdapter.notifyItemChanged(mProfileTimelineAdapter.getHeaderIndex());

                        } else if (mUserDetails.getCurrentlyReading().contains(notification.getBook())) {

                            mUserDetails.getBooksOnHand().remove(notification.getBook());
                            mUserDetails.setPoint(mUserDetails.getPoint() + User.POINT_SHARE_BOOK);
                            mUserDetails.setSharedPoint(mUserDetails.getSharedPoint() + 1);

                            mProfileTimelineAdapter.notifyItemChanged(mProfileTimelineAdapter.getCurrentlyReadingIndex());

                            mProfileTimelineAdapter.notifyItemChanged(mProfileTimelineAdapter.getHeaderIndex());
                        }

                        Logger.d("Book owner changed received from FCM: " + notification);

                        if (mUserDetails.getBooksOnHand().size() == 0){
                            mProfileTimelineAdapter.notifyItemRemoved(mProfileTimelineAdapter.getBooksOnHandSubtitleIndex(true));

                            Logger.d("notifyItemRemoved called to position " +
                                         mProfileTimelineAdapter.getBooksOnHandSubtitleIndex(true) + " for BooksOnHand Subtitle");
                        }
                    }
                } else if (intent.getAction().equalsIgnoreCase(BookieIntentFilters.FCM_INTENT_FILTER_BOOK_LOST)){
                    if (intent.getParcelableExtra(BookieIntentFilters.EXTRA_NOTIFICATION) != null){
                        Notification notification = intent.getParcelableExtra(BookieIntentFilters.EXTRA_NOTIFICATION);

                        int removedBookIndex = mUserDetails.getBooksOnHand().indexOf(notification.getBook());
                        int firstBookOnHandIndexBeforeRemoving = mProfileTimelineAdapter.getFirstBookOnHandIndex();

                        mUserDetails.getOnRoadBooks().remove(notification.getBook());
                        mUserDetails.setPoint(mUserDetails.getPoint() + User.POINT_LOST);

                        mProfileTimelineAdapter.notifyItemRemoved(firstBookOnHandIndexBeforeRemoving + removedBookIndex);

                        mProfileTimelineAdapter.notifyItemChanged(mProfileTimelineAdapter.getHeaderIndex());

                        Logger.d("Book lost received from FCM: " + notification);

                        if (mUserDetails.getBooksOnHand().size() == 0){
                            mProfileTimelineAdapter.notifyItemRemoved(mProfileTimelineAdapter.getBooksOnHandSubtitleIndex(true));
                        }
                    }
                } else if (intent.getAction().equalsIgnoreCase(BookieIntentFilters.INTENT_FILTER_BOOK_STATE_CHANGED)){
                    if (mUserDetails != null){
                        Book book = intent.getParcelableExtra(BookieIntentFilters.EXTRA_BOOK);
                        if (book != null) {
                            if (mUserDetails.getBooksOnHand().contains(book)){

                                switch (book.getState()){
                                    case READING:{
                                        int removedBookIndex = mUserDetails.getBooksOnHand().indexOf(book);
                                        int firstBookOnHandIndexBeforeRemoving = mProfileTimelineAdapter.getFirstBookOnHandIndex();

                                        mUserDetails.getBooksOnHand().remove(book);
                                        mUserDetails.getCurrentlyReading().add(book);

                                        mProfileTimelineAdapter.notifyItemRemoved(firstBookOnHandIndexBeforeRemoving + removedBookIndex);

                                        mProfileTimelineAdapter.notifyItemChanged(mProfileTimelineAdapter.getCurrentlyReadingIndex());

                                        Logger.d("Book state changed received from Local Broadcast: " + book);

                                        if (mUserDetails.getBooksOnHand().size() == 0) {
                                            mProfileTimelineAdapter.notifyItemRemoved(mProfileTimelineAdapter.getBooksOnHandSubtitleIndex(true));
                                        }

                                        break;
                                    }

                                    case OPENED_TO_SHARE:{
                                        int changedBookIndex = mUserDetails.getBooksOnHand().indexOf(book);

                                        mUserDetails.getBooksOnHand().get(changedBookIndex).setState(OPENED_TO_SHARE);

                                        mProfileTimelineAdapter.notifyItemChanged(mProfileTimelineAdapter.getFirstBookOnHandIndex() + changedBookIndex);

                                        Logger.d("Book state changed received from Local Broadcast: " + book);

                                        break;
                                    }

                                    case CLOSED_TO_SHARE:{
                                        int changedBookIndex = mUserDetails.getBooksOnHand().indexOf(book);

                                        mUserDetails.getBooksOnHand().get(changedBookIndex).setState(CLOSED_TO_SHARE);

                                        mProfileTimelineAdapter.notifyItemChanged(mProfileTimelineAdapter.getFirstBookOnHandIndex() + changedBookIndex);

                                        Logger.d("Book state changed received from Local Broadcast: " + book);

                                        break;
                                    }

                                    case ON_ROAD:{
                                        int changedBookIndex = mUserDetails.getBooksOnHand().indexOf(book);

                                        mUserDetails.getBooksOnHand().get(changedBookIndex).setState(ON_ROAD);

                                        mProfileTimelineAdapter.notifyItemChanged(mProfileTimelineAdapter.getFirstBookOnHandIndex() + changedBookIndex);

                                        Logger.d("Book state changed received from Local Broadcast:" + book);

                                        break;
                                    }
                                }
                            } else if (mUserDetails.getCurrentlyReading().contains(book)){

                                // Finished reading
                                mUserDetails.setPoint(mUserDetails.getPoint() + User.POINT_READ_FINISHED);

                                mProfileTimelineAdapter.notifyItemChanged(mProfileTimelineAdapter.getHeaderIndex());

                                switch (book.getState()){

                                    case OPENED_TO_SHARE:{

                                        int booksOnHandCountBeforeAdding = mUserDetails.getBooksOnHand().size();
                                        int firstBookOnHandIndexBeforeRemoving = mProfileTimelineAdapter.getFirstBookOnHandIndex();

                                        mUserDetails.getCurrentlyReading().remove(book);

                                        mUserDetails.getBooksOnHand().add(0, book);

                                        mProfileTimelineAdapter.notifyItemInserted(firstBookOnHandIndexBeforeRemoving);

                                        mProfileTimelineAdapter.notifyItemChanged(mProfileTimelineAdapter.getCurrentlyReadingIndex());

                                        Logger.d("Book state changed received from Local Broadcast: " + book);

                                        if (booksOnHandCountBeforeAdding == 0) {
                                            mProfileTimelineAdapter.notifyItemInserted(mProfileTimelineAdapter.getBooksOnHandSubtitleIndex(false));
                                        }

                                        if (!mUserDetails.getReadBooks().contains(book)){

                                            int readBooksCountBeforeAdding = mUserDetails.getReadBooks().size();

                                            mUserDetails.getReadBooks().add(0, book);

                                            mProfileTimelineAdapter.notifyItemInserted(mProfileTimelineAdapter.getFirstReadBookIndex());

                                            Logger.d("Book state changed received from Local Broadcast: " + book);

                                            if (readBooksCountBeforeAdding == 0) {
                                                mProfileTimelineAdapter.notifyItemInserted(mProfileTimelineAdapter.getReadBooksSubtitleIndex(false));
                                            }
                                        }

                                        break;
                                    }

                                    case CLOSED_TO_SHARE:{
                                        int booksOnHandCountBeforeAdding = mUserDetails.getBooksOnHand().size();

                                        mUserDetails.getCurrentlyReading().remove(book);
                                        mUserDetails.getBooksOnHand().add(0, book);

                                        mProfileTimelineAdapter.notifyItemInserted(mProfileTimelineAdapter.getFirstBookOnHandIndex());

                                        mProfileTimelineAdapter.notifyItemChanged(mProfileTimelineAdapter.getCurrentlyReadingIndex());

                                        Logger.d("Book state changed received from Local Broadcast: " + book);

                                        if (booksOnHandCountBeforeAdding == 0) {
                                            mProfileTimelineAdapter.notifyItemInserted(mProfileTimelineAdapter.getBooksOnHandSubtitleIndex(false));
                                        }

                                        if (!mUserDetails.getReadBooks().contains(book)){

                                            int readBooksCountBeforeAdding = mUserDetails.getReadBooks().size();

                                            mUserDetails.getReadBooks().add(0, book);

                                            mProfileTimelineAdapter.notifyItemInserted(mProfileTimelineAdapter.getFirstReadBookIndex());

                                            if (readBooksCountBeforeAdding == 0) {
                                                mProfileTimelineAdapter.notifyItemInserted(mProfileTimelineAdapter.getReadBooksSubtitleIndex(false));
                                            }
                                        }
                                        break;
                                    }

                                    case ON_ROAD:{
                                        int booksOnHndCountCountBeforeAdding = mUserDetails.getOnRoadBooks().size();

                                        mUserDetails.getCurrentlyReading().remove(book);
                                        mUserDetails.getBooksOnHand().add(0, book);

                                        mProfileTimelineAdapter.notifyItemInserted(mProfileTimelineAdapter.getFirstBookOnHandIndex());

                                        mProfileTimelineAdapter.notifyItemChanged(mProfileTimelineAdapter.getCurrentlyReadingIndex());

                                        Logger.d("Book state changed received from Local Broadcast: " + book);

                                        if (booksOnHndCountCountBeforeAdding == 0) {
                                            mProfileTimelineAdapter.notifyItemInserted(mProfileTimelineAdapter.getBooksOnHandSubtitleIndex(false));
                                        }

                                        if (!mUserDetails.getReadBooks().contains(book)){
                                            int readBooksCountBeforeAdding = mUserDetails.getReadBooks().size();

                                            mUserDetails.getReadBooks().add(0, book);

                                            mProfileTimelineAdapter.notifyItemInserted(mProfileTimelineAdapter.getFirstReadBookIndex());

                                            Logger.d("Book state changed received from Local Broadcast: " + book);

                                            if (readBooksCountBeforeAdding == 0) {
                                                mProfileTimelineAdapter.notifyItemInserted(mProfileTimelineAdapter.getReadBooksSubtitleIndex(false));
                                            }
                                        }

                                        break;
                                    }
                                }
                            }else if (mUserDetails.getOnRoadBooks().contains(book)){

                                mUserDetails.setPoint(mUserDetails.getPoint() + User.POINT_BOOK_COME_TO_HAND);

                                switch (book.getState()){

                                    case READING:{
                                        int removedBookIndex = mUserDetails.getOnRoadBooks().indexOf(book);

                                        int firstOnRoadBookIndexBeforeRemoving = mProfileTimelineAdapter.getFirstOnRoadBookIndex();

                                        mUserDetails.getOnRoadBooks().remove(book);
                                        mUserDetails.getCurrentlyReading().add(0, book);

                                        mProfileTimelineAdapter.notifyItemRemoved(firstOnRoadBookIndexBeforeRemoving + removedBookIndex);

                                        mProfileTimelineAdapter.notifyItemChanged(mProfileTimelineAdapter.getCurrentlyReadingIndex());

                                        Logger.d("Book state changed received from Local Broadcast: " + book);

                                        if (mUserDetails.getOnRoadBooks().size() == 0){
                                            mProfileTimelineAdapter.notifyItemRemoved(mProfileTimelineAdapter.getOnRoadBookSubtitleIndex(true));
                                        }

                                        break;
                                    }

                                    case OPENED_TO_SHARE:{
                                        int removedBookIndex = mUserDetails.getOnRoadBooks().indexOf(book);
                                        int booksOnHandCountBeforeAdding = mUserDetails.getBooksOnHand().size();
                                        int firstOnRoadBookIndexBeforeRemoving = mProfileTimelineAdapter.getFirstOnRoadBookIndex();

                                        mUserDetails.getOnRoadBooks().remove(book);
                                        mUserDetails.getBooksOnHand().add(0, book);

                                        mProfileTimelineAdapter.notifyItemMoved(firstOnRoadBookIndexBeforeRemoving + removedBookIndex,
                                                                                mProfileTimelineAdapter.getFirstBookOnHandIndex());


                                        Logger.d("Book state changed received from Local Broadcast: " + book);

                                        if (mUserDetails.getOnRoadBooks().size() == 0){
                                            mProfileTimelineAdapter.notifyItemRemoved(mProfileTimelineAdapter.getOnRoadBookSubtitleIndex(true));
                                        }

                                        if (booksOnHandCountBeforeAdding == 0){
                                            mProfileTimelineAdapter.notifyItemInserted(mProfileTimelineAdapter.getBooksOnHandSubtitleIndex(false));
                                        }

                                        break;
                                    }

                                    case CLOSED_TO_SHARE:{
                                        int removedBookIndex = mUserDetails.getOnRoadBooks().indexOf(book);
                                        int booksOnHandCountBeforeAdding = mUserDetails.getBooksOnHand().size();
                                        int firstOnRoadBookIndexBeforeRemoving = mProfileTimelineAdapter.getFirstOnRoadBookIndex();

                                        mUserDetails.getOnRoadBooks().remove(book);
                                        mUserDetails.getBooksOnHand().add(book);

                                        mProfileTimelineAdapter.notifyItemMoved(firstOnRoadBookIndexBeforeRemoving + removedBookIndex,
                                                                                mProfileTimelineAdapter.getFirstBookOnHandIndex() + booksOnHandCountBeforeAdding);

                                        Logger.d("Book state changed received from Local Broadcast: " + book);

                                        if (mUserDetails.getOnRoadBooks().size() == 0){
                                            mProfileTimelineAdapter.notifyItemRemoved(mProfileTimelineAdapter.getOnRoadBookSubtitleIndex(true));
                                        }

                                        if (booksOnHandCountBeforeAdding == 0){
                                            mProfileTimelineAdapter.notifyItemInserted(mProfileTimelineAdapter.getBooksOnHandSubtitleIndex(false));
                                        }

                                        break;
                                    }
                                }
                            } else {
                                Logger.e("Invalid book state on state changing!");
                            }
                        } else {
                            Logger.e("Null book in intent extra");
                        }
                    }
                } else if (intent.getAction().equalsIgnoreCase(BookieIntentFilters.INTENT_FILTER_PROFILE_PICTURE_CHANGED)) {

                    String profilePictureUrl = intent.getStringExtra(BookieIntentFilters.EXTRA_PROFILE_PICTURE_URL);
                    String thumbnailUrl = intent.getStringExtra(BookieIntentFilters.EXTRA_PROFILE_THUMBNAIL_URL);

                    if (profilePictureUrl != null && thumbnailUrl != null) {

                        mUserDetails.getUser().setImageUrl(profilePictureUrl);
                        mUserDetails.getUser().setThumbnailUrl(thumbnailUrl);

                        Logger.d("Profile picture changed received from Local Broadcast: \n" +
                                     "Profile Picture URL: " + profilePictureUrl + "\nThumbnail URL: " + thumbnailUrl);

                        mProfileTimelineAdapter.notifyItemChanged(mProfileTimelineAdapter.getHeaderIndex());
                    }
                } else if (intent.getAction().equalsIgnoreCase(BookieIntentFilters.INTENT_FILTER_BOOK_ADDED)) {

                    Book book = intent.getParcelableExtra(BookieIntentFilters.EXTRA_BOOK);

                    if (book != null) {

                        Logger.d("Book added received from Local Broadcast: " + book);

                        switch (book.getState()) {
                            case OPENED_TO_SHARE:
                            case CLOSED_TO_SHARE: {
                                mUserDetails.getBooksOnHand().add(0, book);
                                mProfileTimelineAdapter.notifyItemInserted(mProfileTimelineAdapter.getFirstBookOnHandIndex());
                                break;
                            }

                            case READING: {
                                mUserDetails.getCurrentlyReading().add(book);
                                mProfileTimelineAdapter.notifyItemChanged(mProfileTimelineAdapter.getCurrentlyReadingIndex());
                                break;
                            }

                            default: {
                                Logger.e("Invalid book state after adding: " + book);
                            }

                        }
                    }
                } else if (intent.getAction().equalsIgnoreCase(BookieIntentFilters.FCM_INTENT_FILTER_USER_VERIFIED)){
                    //TODO When user verified
                }
            }
        };

        LocalBroadcastManager.getInstance(getContext()).registerReceiver(mMessageReceiver, new IntentFilter(BookieIntentFilters.FCM_INTENT_FILTER_ACCEPTED_REQUEST_RECEIVED));
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(mMessageReceiver, new IntentFilter(BookieIntentFilters.FCM_INTENT_FILTER_BOOK_OWNER_CHANGED_RECEIVED));
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(mMessageReceiver, new IntentFilter(BookieIntentFilters.FCM_INTENT_FILTER_USER_VERIFIED));
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(mMessageReceiver, new IntentFilter(BookieIntentFilters.FCM_INTENT_FILTER_BOOK_LOST));

        LocalBroadcastManager.getInstance(getContext()).registerReceiver(mMessageReceiver, new IntentFilter(BookieIntentFilters.INTENT_FILTER_BOOK_STATE_CHANGED));
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(mMessageReceiver, new IntentFilter(BookieIntentFilters.INTENT_FILTER_PROFILE_PICTURE_CHANGED));
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(mMessageReceiver, new IntentFilter(BookieIntentFilters.INTENT_FILTER_BOOK_ADDED));

        return rootView;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(mMessageReceiver);
    }

    private void fetchProfilePageArguments() {
        final UserApi userApi = BookieClient.getClient().create(UserApi.class);

        User.Details currentUserDetails = SessionManager.getCurrentUserDetails(getContext());

        String email = currentUserDetails.getEmail();
        String password = currentUserDetails.getPassword();
        final Call<ResponseBody> getUserProfilePageComponents = userApi.getUserProfilePageComponents(email, password, mUser.getID());

        Logger.d("getUserProfilePageComponents() API called with parameters: \n" +
                     "\temail=" + email + ", \n\tpassword=" + password + ", \n\tuserID=" + mUser.getID());

        getUserProfilePageComponents.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {

                try {
                    if (response != null){
                        if (response.body() != null){
                            String json = response.body().string();

                            Logger.json(json);

                            JSONObject responseObject = new JSONObject(json);
                            boolean error = responseObject.getBoolean("error");

                            if (!error) {
                                mProfileTimelineAdapter.setError(ProfileTimelineAdapter.ERROR_TYPE_NONE);

                                if (!responseObject.isNull("userDetails")){
                                    JSONObject userObject = responseObject.getJSONObject("userDetails");
                                    mUserDetails = User.jsonObjectToUserDetails(userObject);

                                    // Updating local database
                                    User currentUser = SessionManager.getCurrentUser(getContext());
                                    if (mUser.equals(currentUser)) {
                                        // Update user info from fetched user
                                        mUser = mUserDetails.getUser();
                                        mDbManager.getUserDataSource().updateUserDetails(mUserDetails);
                                        SessionManager.updateCurrentUser(mUserDetails);
                                    } else {
                                        mDbManager.checkAndUpdateAllUsers(mUserDetails.getUser());
                                    }

                                    if (!responseObject.isNull("currentlyReading")){
                                        if (mUserDetails != null) {
                                            mUserDetails.setCurrentlyReading(Book.jsonArrayToBookList(responseObject.getJSONArray("currentlyReading")));
                                        }

                                    }
                                    if (!responseObject.isNull("booksOnHand")){
                                        if (mUserDetails != null) {
                                            mUserDetails.setBooksOnHand(Book.jsonArrayToBookList(responseObject.getJSONArray("booksOnHand")));
                                        }

                                    }
                                    if (!responseObject.isNull("readBooks")){
                                        if (mUserDetails != null) {
                                            mUserDetails.setReadBooks(Book.jsonArrayToBookList(responseObject.getJSONArray("readBooks")));
                                        }
                                    }

                                    if (!responseObject.isNull("onRoadBooks")){
                                        if (mUserDetails != null) {
                                            mUserDetails.setOnRoadBooks(Book.jsonArrayToBookList(responseObject.getJSONArray("onRoadBooks")));
                                        }
                                    }

                                    if (mUserDetails != null) {
                                        Logger.d("Profile page fetched:" +
                                                     "\n" + mUser.toString() +
                                                     "\n\nCurrently Reading:\n\n" + Book.listToShortString(mUserDetails.getCurrentlyReading()) +
                                                     "\n\nOn Road Books:\n\n" + Book.listToShortString(mUserDetails.getOnRoadBooks()) +
                                                     "\n\nBooks On Hand:\n\n" + Book.listToShortString(mUserDetails.getBooksOnHand()) +
                                                     "\n\nRead Books:\n\n" + Book.listToShortString(mUserDetails.getReadBooks()));
                                    }

                                    mProfileTimelineAdapter.setUserDetails(mUserDetails);
                                    mProfileTimelineAdapter.notifyDataSetChanged();
                                }

                            } else {
                                int errorCode = responseObject.getInt("errorCode");

                                Logger.e("Error true in response: errorCode = " + errorCode);

                                mProfileTimelineAdapter.setError(ProfileTimelineAdapter.ERROR_TYPE_UNKNOWN_ERROR);
                            }
                        }else{
                            Logger.e("Response body is null. (Profile Page Error)");
                            mProfileTimelineAdapter.setError(ProfileTimelineAdapter.ERROR_TYPE_UNKNOWN_ERROR);
                        }
                    }else {
                        Logger.e("Response object is null. (Profile Page Error)");
                        mProfileTimelineAdapter.setError(ProfileTimelineAdapter.ERROR_TYPE_UNKNOWN_ERROR);
                    }
                } catch (IOException | JSONException e) {
                    Logger.e("IOException or JSONException caught: " + e.getMessage());

                    if(BookieApplication.hasNetwork()){
                        mProfileTimelineAdapter.setError(ProfileTimelineAdapter.ERROR_TYPE_UNKNOWN_ERROR);
                    }else{
                        mProfileTimelineAdapter.setError(ProfileTimelineAdapter.ERROR_TYPE_NO_CONNECTION);
                    }
                }

                mPullRefreshLayout.setRefreshing(false);
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {

                if(BookieApplication.hasNetwork()){
                    mProfileTimelineAdapter.setError(ProfileTimelineAdapter.ERROR_TYPE_UNKNOWN_ERROR);
                }else{
                    mProfileTimelineAdapter.setError(ProfileTimelineAdapter.ERROR_TYPE_NO_CONNECTION);
                }

                mPullRefreshLayout.setRefreshing(false);
                Logger.e("getUserProfilePageComponents Failure: " + t.getMessage());
            }
        });
    }

    public void refreshProfilePage(){
        fetchProfilePageArguments();
    }
}
