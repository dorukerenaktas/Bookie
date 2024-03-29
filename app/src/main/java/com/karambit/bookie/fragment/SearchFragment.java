package com.karambit.bookie.fragment;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SimpleItemAnimator;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.StyleSpan;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import com.karambit.bookie.BookActivity;
import com.karambit.bookie.BookieApplication;
import com.karambit.bookie.InfoActivity;
import com.karambit.bookie.LocationActivity;
import com.karambit.bookie.MainActivity;
import com.karambit.bookie.ProfileActivity;
import com.karambit.bookie.R;
import com.karambit.bookie.adapter.SearchAdapter;
import com.karambit.bookie.database.DBManager;
import com.karambit.bookie.helper.InformationDialog;
import com.karambit.bookie.helper.SessionManager;
import com.karambit.bookie.helper.string_similarity.JaroWinkler;
import com.karambit.bookie.model.Book;
import com.karambit.bookie.model.User;
import com.karambit.bookie.rest_api.BookieClient;
import com.karambit.bookie.rest_api.SearchApi;
import com.karambit.bookie.service.BookieIntentFilters;
import com.orhanobut.logger.Logger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * A simple {@link Fragment} subclass.
 */
public class SearchFragment extends Fragment {

    private static final String TAG = SearchFragment.class.getSimpleName();

    public static final int TAB_INDEX = 1;
    public static final int VIEW_PAGER_INDEX = 1;
    public static final String TAB_SPEC = "tab_search";
    public static final String TAB_INDICATOR = "tab1";

    public static final long INTERVAL_LOCATION_REMINDER_MILLIS = TimeUnit.DAYS.toMillis(2);
    public static final int UNSELECTED_GENRE_CODE = -1;
    private static final int SEARCH_BUTTON_PRESSED_TAG = Integer.MAX_VALUE - 564;

    private final StyleSpan STYLE_SPAN_BOLD = new StyleSpan(Typeface.BOLD);

    private String[] mAllGenres;
    private ArrayList<Integer> mGenreCodes = new ArrayList<>();
    private ArrayList<Book> mBooks = new ArrayList<>();
    private ArrayList<User> mUsers = new ArrayList<>();
    private ArrayList<User> mHistoryUsers = new ArrayList<>();
    private ArrayList<Book> mHistoryBooks = new ArrayList<>();

    private int mFetchGenreCode = -1;
    private SearchAdapter mSearchAdapter;
    private DBManager mDbManager;
    private EditText mSearchEditText;
    private ImageButton mSearchButton;
    private BroadcastReceiver mMessageReceiver;
    private Hashtable<Book, Object> mBookLocations;

    public SearchFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View rootView = inflater.inflate(R.layout.fragment_search, container, false);

        RecyclerView recyclerView = (RecyclerView) rootView.findViewById(R.id.searchResultsRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        mSearchEditText = ((MainActivity) getActivity()).getSearchEditText();

        mDbManager = new DBManager(getContext());
        mDbManager.open();

        mAllGenres = getContext().getResources().getStringArray(R.array.genre_types);

        mSearchAdapter = new SearchAdapter(getContext(), mGenreCodes, mBooks, mUsers);
        mSearchAdapter.setBookClickListener(new SearchAdapter.SearchItemClickListener() {
            @Override
            public void onGenreClick(int genreCode) {
                mFetchGenreCode = genreCode;

                String genre = mAllGenres[genreCode];

                SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder(genre);

                spannableStringBuilder.setSpan(STYLE_SPAN_BOLD, 0, genre.length(), Spannable.SPAN_INCLUSIVE_INCLUSIVE);
                mSearchEditText.setText(spannableStringBuilder);
                mSearchEditText.setSelection(spannableStringBuilder.length());

                getSearchResults("", false);
            }

            @Override
            public void onBookClick(Book book) {
                addBookToSearchHistory(book);

                Intent intent = new Intent(getContext(), BookActivity.class);
                intent.putExtra(BookActivity.EXTRA_BOOK, book);
                getContext().startActivity(intent);
            }

            @Override
            public void onUserClick(User user) {
                addUserToSearchHistory(user);

                Intent intent = new Intent(getContext(), ProfileActivity.class);
                Bundle bundle = new Bundle();
                bundle.putParcelable(ProfileActivity.EXTRA_USER, user);
                intent.putExtras(bundle);
                startActivity(intent);
            }
        });

        recyclerView.setAdapter(mSearchAdapter);

        recyclerView.getItemAnimator().setChangeDuration(100);

        mSearchButton = ((MainActivity) getActivity()).getSearchImageButton();

        mSearchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mSearchEditText.length() > 0) {
                    getSearchResults(mSearchEditText.getText().toString(), true);
                    changeSearchButtonMode(true);
                }

            }
        });

        mSearchEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    getSearchResults(mSearchEditText.getText().toString(), true);
                    changeSearchButtonMode(true);
                    return true;
                }
                return false;
            }
        });

        mSearchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                Spannable spannable = mSearchEditText.getText();
                if (spannable.getSpans(0, s.length(), StyleSpan.class).length > 0) {
                    spannable.removeSpan(STYLE_SPAN_BOLD);
                }
            }

            private static final int INTERVAL_SEARCH_TEXT_CHANGED_MILLIS = 500;
            private long mLastKeyPressTime = Calendar.getInstance().getTimeInMillis();

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

                changeSearchButtonMode(false);

                mLastKeyPressTime = Calendar.getInstance().getTimeInMillis();

                if (mFetchGenreCode == UNSELECTED_GENRE_CODE) {

                    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (Calendar.getInstance().getTimeInMillis() - mLastKeyPressTime >= INTERVAL_SEARCH_TEXT_CHANGED_MILLIS &&
                                !isSearchButtonPressed()) {

                                String string = mSearchEditText.getText().toString();
                                getSearchResults(string, false);
                            }
                        }
                    }, INTERVAL_SEARCH_TEXT_CHANGED_MILLIS);
                } else {
                    Spannable spannable = mSearchEditText.getText();
                    if (spannable.getSpans(0, before, StyleSpan.class).length == 0) {
                        mFetchGenreCode = UNSELECTED_GENRE_CODE;
                    }
                }
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });

        mSearchEditText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    if (SessionManager.getCurrentUser(getContext()).getLocation() == null) {
                        Calendar lastRemindedAt = SessionManager.getLastLocationReminderTime(getContext());
                        if (Calendar.getInstance().getTimeInMillis() - lastRemindedAt.getTimeInMillis() > INTERVAL_LOCATION_REMINDER_MILLIS) {

                            final InformationDialog informationDialog = new InformationDialog(getContext());
                            informationDialog.setCancelable(false);
                            informationDialog.setPrimaryMessage(R.string.null_location_info_short);
                            informationDialog.setSecondaryMessage(R.string.null_location_search_info);
                            informationDialog.setDefaultClickListener(new InformationDialog.DefaultClickListener() {
                                @Override
                                public void onOkClick() {
                                    informationDialog.dismiss();
                                }

                                @Override
                                public void onMoreInfoClick() {
                                    Intent intent = new Intent(getActivity(), InfoActivity.class);
                                    intent.putExtra(InfoActivity.EXTRA_INFO_CODES, new int[]{
                                        InfoActivity.INFO_CODE_LOCATION
                                    });
                                    startActivity(intent);
                                }
                            });
                            informationDialog.setExtraButtonClickListener(R.string.set_location, new InformationDialog.ExtraButtonClickListener() {
                                @Override
                                public void onExtraButtonClick() {
                                    // No need for result because searching can be done without location
                                    startActivity(new Intent(getActivity(), LocationActivity.class));
                                    informationDialog.dismiss();
                                }
                            });

                            informationDialog.show();

                            SessionManager.notifyLocationReminded(getContext());
                        }
                    }
                }
            }
        });

        showSearchHistory();

        mMessageReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equalsIgnoreCase(BookieIntentFilters.INTENT_FILTER_DATABASE_USER_CHANGED)) {
                    User updatedUser = intent.getParcelableExtra(BookieIntentFilters.EXTRA_USER);
                    if (updatedUser != null) {
                        if (mSearchAdapter.isShowHistory()) {
                            for (int i = 0; i < mHistoryUsers.size(); i++) {
                                User user = mHistoryUsers.get(i);
                                if (updatedUser.equals(user)) {
                                    mHistoryUsers.set(i, updatedUser);
                                }
                            }
                            mSearchAdapter.notifyDataSetChanged();
                        }
                    }
                } else if (intent.getAction().equalsIgnoreCase(BookieIntentFilters.INTENT_FILTER_DATABASE_BOOK_CHANGED)) {
                    Book updatedBook = intent.getParcelableExtra(BookieIntentFilters.INTENT_FILTER_DATABASE_BOOK_CHANGED);
                    if (updatedBook != null) {
                        if (mSearchAdapter.isShowHistory()) {
                            for (int i = 0; i < mHistoryBooks.size(); i++) {
                                Book book = mHistoryBooks.get(i);
                                if (updatedBook.equals(book)) {
                                    mHistoryBooks.set(i, updatedBook);
                                }
                            }
                            mSearchAdapter.notifyDataSetChanged();
                        }
                    }
                }
            }
        };

        LocalBroadcastManager.getInstance(getContext()).registerReceiver(mMessageReceiver, new IntentFilter(BookieIntentFilters.INTENT_FILTER_DATABASE_USER_CHANGED));
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(mMessageReceiver, new IntentFilter(BookieIntentFilters.INTENT_FILTER_DATABASE_BOOK_CHANGED));

        return rootView;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(mMessageReceiver);
    }

    private void getSearchResults(final String searchString, final boolean isSearchButtonPressed) {
        final SearchApi searchApi = BookieClient.getClient().create(SearchApi.class);

        User.Details currentUserDetails = SessionManager.getCurrentUserDetails(getContext());

        String email = currentUserDetails.getEmail();
        String password = currentUserDetails.getPassword();
        Call<ResponseBody> searchResults = searchApi.getSearchResults(email, password, searchString, mFetchGenreCode, isSearchButtonPressed);

        Logger.d("getSearchResults() API called with parameters: \n" +
                     "\temail=" + email + ", \n\tpassword=" + password + ", \n\tsearchString=" + searchString +
                     ", \n\tsearchGenre=" + mFetchGenreCode + ", \n\tsearchPressed=" + isSearchButtonPressed);

        searchResults.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                try {
                    if (response != null) {
                        if (response.body() != null) {
                            String json = response.body().string();

                            Logger.json(json);

                            JSONObject responseObject = new JSONObject(json);
                            boolean error = responseObject.getBoolean("error");

                            if (!error) {
                                mSearchAdapter.setError(SearchAdapter.ERROR_TYPE_NONE);
                                mSearchAdapter.setWarning(SearchAdapter.WARNING_TYPE_NONE);

                                mBooks.clear();
                                mUsers.clear();

                                if (!responseObject.isNull("listBooks")) {
                                    JSONArray booksArray = responseObject.getJSONArray("listBooks");
                                    mBooks.addAll(Book.jsonArrayToBookList(booksArray));
                                }
                                if (!responseObject.isNull("listUsers")) {
                                    JSONArray usersArray = responseObject.getJSONArray("listUsers");
                                    mUsers.addAll(User.jsonArrayToUserList(usersArray));
                                }

                                if (mFetchGenreCode != UNSELECTED_GENRE_CODE || TextUtils.isEmpty(searchString)) {
                                    mSearchAdapter.setItems(new ArrayList<Integer>(), mBooks, mUsers, isSearchButtonPressed);
                                } else {
                                    mSearchAdapter.setItems(getMostSimilarGenreIndex(searchString), mBooks, mUsers, isSearchButtonPressed);
                                }

                                if (mBooks.size() < 1 && mUsers.size() < 1) {
                                    if (TextUtils.isEmpty(mSearchEditText.getText().toString())) {
                                        showSearchHistory();
                                    } else {
                                        mSearchAdapter.setWarning(SearchAdapter.WARNING_TYPE_NO_RESULT_FOUND);
                                    }
                                } else {
                                    fetchBookLocations(mSearchAdapter.isShowHistory());
                                }

                                ((MainActivity) getActivity()).hideError();

                                Logger.d("Search fetched:" +
                                             "\n\n" + Book.listToShortString(mBooks) +
                                             "\n\n" + User.listToShortString(mUsers));

                            } else {

                                int errorCode = responseObject.getInt("errorCode");

                                Logger.e("Error true in response: errorCode = " + errorCode);

                                ((MainActivity) getActivity()).showUnknownError();

                                // TODO Error sadece resim gösterilecek
                                mSearchAdapter.setError(SearchAdapter.ERROR_TYPE_UNKNOWN_ERROR);

                                ((MainActivity) getActivity()).showUnknownError();
                            }
                        } else {
                            Logger.e("Response body is null. (Search Page Error)");

                            mSearchAdapter.setError(SearchAdapter.ERROR_TYPE_UNKNOWN_ERROR);

                            ((MainActivity) getActivity()).showUnknownError();
                        }
                    } else {
                        Logger.e("Response object is null. (Search Page Error)");

                        mSearchAdapter.setError(SearchAdapter.ERROR_TYPE_UNKNOWN_ERROR);

                        ((MainActivity) getActivity()).showUnknownError();
                    }
                } catch (IOException | JSONException e) {
                    Logger.e("IOException or JSONException caught: " + e.getMessage());

                    if (BookieApplication.hasNetwork()) {
                        ((MainActivity) getActivity()).showUnknownError();
                        mSearchAdapter.setError(SearchAdapter.ERROR_TYPE_UNKNOWN_ERROR);
                    } else {
                        ((MainActivity) getActivity()).showConnectionError();
                        mSearchAdapter.setError(SearchAdapter.ERROR_TYPE_NO_CONNECTION);
                    }

                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                if (BookieApplication.hasNetwork()) {
                    ((MainActivity) getActivity()).showUnknownError();
                    mSearchAdapter.setError(SearchAdapter.ERROR_TYPE_UNKNOWN_ERROR);
                } else {
                    ((MainActivity) getActivity()).showConnectionError();
                    mSearchAdapter.setError(SearchAdapter.ERROR_TYPE_NO_CONNECTION);
                }

                Logger.e("getSearchResults Failure: " + t.getMessage());
            }
        });
    }

    private void changeSearchButtonMode(boolean isSearchButtonPressed) {

        if (isSearchButtonPressed) {
            mSearchButton.setColorFilter(ContextCompat.getColor(getContext(), R.color.colorAccent));
            mSearchButton.setTag(SEARCH_BUTTON_PRESSED_TAG, true);
        } else {
            mSearchButton.setColorFilter(ContextCompat.getColor(getContext(), R.color.secondaryTextColor));
            mSearchButton.setTag(SEARCH_BUTTON_PRESSED_TAG, false);
        }
    }

    private boolean isSearchButtonPressed() {
        return mSearchButton.getTag(SEARCH_BUTTON_PRESSED_TAG) != null ? ((Boolean) mSearchButton.getTag(SEARCH_BUTTON_PRESSED_TAG)) : false;
    }

    private ArrayList<Integer> getMostSimilarGenreIndex(String searchString) {
        final HashMap<Integer, Double> searchGenreSimilarityMap = new HashMap<>();
        for (int i = 0; i < mAllGenres.length; i++) {
            double similarity = getStringSimilarity(mAllGenres[i], searchString);
            if (similarity > 0.7) {
                searchGenreSimilarityMap.put(i, similarity);
                if (similarity > 0.9) {
                    ArrayList<Integer> finalResult = new ArrayList<>();
                    finalResult.add(i);
                    return finalResult;
                }
            }
        }

        ArrayList<Integer> sortedGenres = new ArrayList<>(searchGenreSimilarityMap.keySet());
        Collections.sort(sortedGenres, new Comparator<Integer>() {
            @Override
            public int compare(Integer s1, Integer s2) {
                Double similarity1 = searchGenreSimilarityMap.get(s1);
                Double similarity2 = searchGenreSimilarityMap.get(s2);
                return similarity2.compareTo(similarity1);
            }
        });


        ArrayList<Integer> finalResults = new ArrayList<>();
        if (sortedGenres.size() > 1) {
            finalResults.add(sortedGenres.get(0));
        }
        if (sortedGenres.size() > 2) {
            finalResults.add(sortedGenres.get(1));
        }

        return finalResults;
    }

    private double getStringSimilarity(String reference, String searchString) {
        reference = reference.toLowerCase();
        searchString = searchString.toLowerCase();

        if (reference.contains(searchString)) {
            return 1;
        } else if (reference.contains(searchString)) {
            return 1;
        } else {
            JaroWinkler jaroWinkler = new JaroWinkler();

            return jaroWinkler.similarity(reference, searchString);
        }
    }

    private void showSearchHistory() {
        mHistoryBooks = mDbManager.getSearchBookDataSource().getAllBooks();
        mHistoryUsers = mDbManager.getSearchUserDataSource().getAllUsers();

        if (mHistoryBooks.size() + mHistoryUsers.size() > 0) {
            mSearchAdapter.showHistory(mHistoryBooks, mHistoryUsers);
        } else {
            mSearchAdapter.hideHistory();
            mSearchAdapter.setWarning(SearchAdapter.WARNING_TYPE_NOTHING_TO_SHOW);
        }

        fetchBookLocations(mSearchAdapter.isShowHistory());
    }

    private void addBookToSearchHistory(Book book) {
        ArrayList<Book> historyBooks = mDbManager.getSearchBookDataSource().getAllBooks();

        if (historyBooks.size() > 2) {
            mDbManager.Threaded(mDbManager.getSearchBookDataSource().cDeleteAllBooks());

            historyBooks.remove(0);

            for (Book historyBook : historyBooks) {
                mDbManager.Threaded(mDbManager.getSearchBookDataSource().cSaveBook(historyBook));
            }
        }

        mDbManager.Threaded(mDbManager.getSearchBookDataSource().cSaveBook(book));
    }

    private void addUserToSearchHistory(User user) {
        ArrayList<User> historyUsers = mDbManager.getSearchUserDataSource().getAllUsers();

        if (historyUsers.size() > 2) {
            mDbManager.Threaded(mDbManager.getSearchUserDataSource().cDeleteAllUsers());

            historyUsers.remove(0);

            for (User historyUser : historyUsers) {
                mDbManager.Threaded(mDbManager.getSearchUserDataSource().cSaveUser(historyUser));
            }
        }

        mDbManager.Threaded(mDbManager.getSearchUserDataSource().cSaveUser(user));
    }

    public boolean isSearchEditTextEmpty() {
        return mSearchEditText.length() == 0;
    }

    public void clearSearchEditText() {
        mFetchGenreCode = UNSELECTED_GENRE_CODE;
        mSearchEditText.setText("");
    }

    private void fetchBookLocations(final boolean isHistoryShowing) {

        new Thread(new Runnable() {
            @Override
            public void run() {
                ArrayList<Book> books = isHistoryShowing ? mHistoryBooks : mBooks;

                mSearchAdapter.setBookLocations(new Hashtable<Book, String>(books.size()));

                for (int i = 0; i < books.size(); i++) {
                    final Book book = books.get(i);
                    User owner = book.getOwner();

                    if (owner.getLocation() != null) {

                        final double latitude = owner.getLocation().latitude;
                        final double longitude = owner.getLocation().longitude;

                        try {
                            Geocoder geocoder = new Geocoder(getContext(), Locale.getDefault());
                            List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);

                            // Admin area equals Istanbul
                            // Subadmin are equals Bahçelievler

                            if (addresses.size() > 0) {
                                String locationString = "";

                                String subAdminArea = addresses.get(0).getSubAdminArea();
                                if (!TextUtils.isEmpty(subAdminArea) && !subAdminArea.equals("null")) {
                                    locationString += subAdminArea + " / ";
                                }

                                String adminArea = addresses.get(0).getAdminArea();
                                if (!TextUtils.isEmpty(adminArea) && !adminArea.equals("null")) {
                                    locationString += adminArea;
                                }

                                if (!TextUtils.isEmpty(locationString)) {
                                    mSearchAdapter.getBookLocations().put(book, locationString);
                                    final int finalI = i;
                                    getActivity().runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            int adapterBookPosition = mSearchAdapter.getFirstBookPosition() + finalI;
                                            mSearchAdapter.notifyItemChanged(adapterBookPosition);
                                        }
                                    });
                                }

                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }).start();
    }
}
