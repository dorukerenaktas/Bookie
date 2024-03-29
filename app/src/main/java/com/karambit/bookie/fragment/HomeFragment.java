package com.karambit.bookie.fragment;


import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.karambit.bookie.BookActivity;
import com.karambit.bookie.BookieApplication;
import com.karambit.bookie.MainActivity;
import com.karambit.bookie.R;
import com.karambit.bookie.adapter.HomeTimelineAdapter;
import com.karambit.bookie.helper.ElevationScrollListener;
import com.karambit.bookie.helper.SessionManager;
import com.karambit.bookie.helper.pull_refresh_layout.PullRefreshLayout;
import com.karambit.bookie.model.Book;
import com.karambit.bookie.model.User;
import com.karambit.bookie.rest_api.BookApi;
import com.karambit.bookie.rest_api.BookieClient;
import com.orhanobut.logger.Logger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * A simple {@link Fragment} subclass.
 */
public class HomeFragment extends Fragment {

    private static final String TAG = HomeFragment.class.getSimpleName();

    public static final int TAB_INDEX = 0;
    public static final int VIEW_PAGER_INDEX = 0;
    public static final String TAB_SPEC = "tab_home";
    public static final String TAB_INDICATOR = "tab0";

    private ArrayList<Book> mHeaderBooks = new ArrayList<>();
    private ArrayList<Book> mListBooks = new ArrayList<>();
    private RecyclerView mRecyclerView;
    private HomeTimelineAdapter mHomeTimelineAdapter;
    private PullRefreshLayout mPullRefreshLayout;
    private boolean mIsBooksFetching = false;

    public HomeFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        // Inflate the layout for this fragment
        View rootView = inflater.inflate(R.layout.fragment_home, container, false);

        mRecyclerView = (RecyclerView) rootView.findViewById(R.id.homeRecyclerView);

        mRecyclerView.addOnScrollListener(new ElevationScrollListener((MainActivity) getActivity(), TAB_INDEX));

        final LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getContext());
        mRecyclerView.setLayoutManager(linearLayoutManager);

        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                if (isLastItemVisible() && !mIsBooksFetching) {
                    fetchHomePageBooks();
                }
            }
        });

        mHomeTimelineAdapter = new HomeTimelineAdapter(getContext());

        mRecyclerView.setAdapter(mHomeTimelineAdapter);

        mHomeTimelineAdapter.setBookClickListener(new HomeTimelineAdapter.BookClickListener() {
            @Override
            public void onBookClick(Book book) {
                Intent intent = new Intent(getContext(), BookActivity.class);
                intent.putExtra(BookActivity.EXTRA_BOOK, book);
                startActivity(intent);
            }
        });

        mPullRefreshLayout = (PullRefreshLayout) rootView.findViewById(R.id.swipeRefreshLayout);

        // listen refresh event
        mPullRefreshLayout.setOnRefreshListener(new PullRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                // start refresh
                if (!mIsBooksFetching){
                    mListBooks = new ArrayList<>();
                    fetchHomePageBooks();
                    ((MainActivity) getActivity()).fetchNotificationMenuItemValue();
                }
            }
        });

        ((MainActivity)getActivity()).setDoubleTapHomeButtonListener(new MainActivity.DoubleTapHomeButtonListener() {
            @Override
            public void onDoubleTapHomeButton() {
                mRecyclerView.smoothScrollToPosition(0);
            }
        });

        mPullRefreshLayout.setRefreshing(true);
        fetchHomePageBooks();
        return rootView;
    }

    boolean isLastItemVisible() {
        LinearLayoutManager layoutManager = ((LinearLayoutManager)mRecyclerView.getLayoutManager());
        int pos = layoutManager.findLastCompletelyVisibleItemPosition();
        int numItems = mRecyclerView.getAdapter().getItemCount() - 1;

        return (pos >= numItems);
    }

    private void fetchHomePageBooks() {
        mIsBooksFetching = true;
        BookApi bookApi = BookieClient.getClient().create(BookApi.class);
        String fetchedBookIds;

        int i = 0;
        if (mListBooks.size() > 0){
            StringBuilder builder = new StringBuilder();
            for (Book book: mListBooks){
                builder.append(book.getID());
                if (i < mListBooks.size() - 1){
                    builder.append("_");
                }
                i++;
            }
            fetchedBookIds = builder.toString();
        }else{
            fetchedBookIds = "-1";
        }


        User.Details currentUserDetails = SessionManager.getCurrentUserDetails(getContext());

        String email = currentUserDetails.getEmail();
        String password = currentUserDetails.getPassword();
        Call<ResponseBody> getHomePageBooks = bookApi.getHomePageBooks(email, password, fetchedBookIds);

        Logger.d("getHomePageBooks() API called with parameters: \n" +
                     "\temail=" + email + ", \n\tpassword=" + password + ", \n\tbookIDs=" + fetchedBookIds);

        getHomePageBooks.enqueue(new Callback<ResponseBody>() {
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

                                if (mListBooks.size() > 0){
                                    JSONArray listBooksArray = responseObject.getJSONArray("listBooks");
                                    ArrayList<Book> feedBooks = Book.jsonArrayToBookList(listBooksArray);

                                    if (feedBooks.size() < 20){
                                        mHomeTimelineAdapter.setProgressBarActive(false);
                                    }else {
                                        mListBooks.addAll(feedBooks);

                                        mHomeTimelineAdapter.setError(HomeTimelineAdapter.ERROR_TYPE_NONE);
                                        mHomeTimelineAdapter.addFeedBooks(feedBooks);
                                    }
                                }else {
                                    JSONArray headerBooksArray = responseObject.getJSONArray("headerBooks");
                                    mHeaderBooks.addAll(Book.jsonArrayToBookList(headerBooksArray));

                                    JSONArray listBooksArray = responseObject.getJSONArray("listBooks");
                                    ArrayList<Book> feedBooks = Book.jsonArrayToBookList(listBooksArray);
                                    mListBooks.addAll(feedBooks);

                                    mHomeTimelineAdapter.setError(HomeTimelineAdapter.ERROR_TYPE_NONE);
                                    mHomeTimelineAdapter.setHeaderAndFeedBooks(mHeaderBooks, feedBooks);
                                }

                                Logger.d("Home page books fetched:" +
                                             "\n\nHeader Books:\n" + Book.listToShortString(mHeaderBooks) +
                                             "\nList books:\n" + Book.listToShortString(mListBooks));

                                if (BookieApplication.hasNetwork()) {
                                    ((MainActivity) getActivity()).hideError();
                                } else {
                                    ((MainActivity) getActivity()).showConnectionError();
                                }

                            } else {

                                int errorCode = responseObject.getInt("errorCode");

                                Logger.e("Error true in response: errorCode = " + errorCode);

                                if (mHeaderBooks.isEmpty() && mListBooks.isEmpty()) {
                                    mHomeTimelineAdapter.setError(HomeTimelineAdapter.ERROR_TYPE_UNKNOWN_ERROR);
                                }

                                ((MainActivity) getActivity()).showUnknownError();
                            }
                        }else{
                            Logger.e("Response body is null. (Home Page Error)");

                            if (mHeaderBooks.isEmpty() && mListBooks.isEmpty()) {
                                mHomeTimelineAdapter.setError(HomeTimelineAdapter.ERROR_TYPE_UNKNOWN_ERROR);
                            }

                            ((MainActivity) getActivity()).showUnknownError();
                        }
                    }else {
                        Logger.e("Response object is null. (Home Page Error)");

                        if (mHeaderBooks.isEmpty() && mListBooks.isEmpty()) {
                            mHomeTimelineAdapter.setError(HomeTimelineAdapter.ERROR_TYPE_UNKNOWN_ERROR);
                        }

                        ((MainActivity) getActivity()).showUnknownError();
                    }
                } catch (IOException | JSONException e) {
                    Logger.e("IOException or JSONException caught: " + e.getMessage());

                    if (mHeaderBooks.isEmpty() && mListBooks.isEmpty()) {
                        if (BookieApplication.hasNetwork()) {
                            mHomeTimelineAdapter.setError(HomeTimelineAdapter.ERROR_TYPE_UNKNOWN_ERROR);
                        } else {
                            mHomeTimelineAdapter.setError(HomeTimelineAdapter.ERROR_TYPE_NO_CONNECTION);
                        }
                    }

                    if (BookieApplication.hasNetwork()) {
                        ((MainActivity) getActivity()).showUnknownError();
                    } else {
                        ((MainActivity) getActivity()).showConnectionError();
                    }
                }

                mPullRefreshLayout.setRefreshing(false);
                mIsBooksFetching = false;
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Logger.e("getHomePageBooks Failure: " + t.getMessage());

                if (mHeaderBooks.isEmpty() && mListBooks.isEmpty()) {
                    if (BookieApplication.hasNetwork()) {
                        mHomeTimelineAdapter.setError(HomeTimelineAdapter.ERROR_TYPE_UNKNOWN_ERROR);
                    } else {
                        mHomeTimelineAdapter.setError(HomeTimelineAdapter.ERROR_TYPE_NO_CONNECTION);
                    }
                }

                if (BookieApplication.hasNetwork()) {
                    ((MainActivity) getActivity()).showUnknownError();
                } else {
                    ((MainActivity) getActivity()).showConnectionError();
                }

                mPullRefreshLayout.setRefreshing(false);
                mIsBooksFetching = false;
            }
        });
    }
}
