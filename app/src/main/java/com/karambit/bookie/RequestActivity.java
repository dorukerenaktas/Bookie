package com.karambit.bookie;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.ViewCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.karambit.bookie.adapter.RequestAdapter;
import com.karambit.bookie.helper.ComfortableProgressDialog;
import com.karambit.bookie.helper.ElevationScrollListener;
import com.karambit.bookie.helper.LayoutUtils;
import com.karambit.bookie.helper.SessionManager;
import com.karambit.bookie.helper.TypefaceSpan;
import com.karambit.bookie.helper.pull_refresh_layout.PullRefreshLayout;
import com.karambit.bookie.model.Book;
import com.karambit.bookie.model.Notification;
import com.karambit.bookie.model.Request;
import com.karambit.bookie.model.User;
import com.karambit.bookie.rest_api.BookApi;
import com.karambit.bookie.rest_api.BookieClient;
import com.karambit.bookie.rest_api.ErrorCodes;
import com.karambit.bookie.service.BookieIntentFilters;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RequestActivity extends AppCompatActivity {

    private static final String TAG = RequestActivity.class.getSimpleName();

    public static final String EXTRA_BOOK = "book";
    public static final String EXTRA_REQUESTS = "requests";

    private Book mBook;
    private ArrayList<Request> mRequests = new ArrayList<>();
    private PullRefreshLayout mPullRefreshLayout;
    private RequestAdapter mRequestAdapter;
    private RecyclerView mRequestRecyclerView;
    private TextView mAcceptedRequestTextView;
    private Hashtable<Request, String> mLocations;
    private BroadcastReceiver mMessageReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_request);

        final ActionBar actionBar = getSupportActionBar();

        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);

            SpannableString s = new SpannableString(getResources().getString(R.string.request_activity_title));
            s.setSpan(new TypefaceSpan(this, MainActivity.FONT_GENERAL_TITLE), 0, s.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            float titleSize = getResources().getDimension(R.dimen.actionbar_title_size);
            s.setSpan(new AbsoluteSizeSpan((int) titleSize), 0, s.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

            actionBar.setTitle(s);

            float elevation = getResources().getDimension(R.dimen.actionbar_starting_elevation);
            actionBar.setElevation(elevation);
            actionBar.setHomeAsUpIndicator(R.drawable.ic_arrow_back_primary_text_color);
            actionBar.setElevation(0);
        }

        mBook = getIntent().getParcelableExtra(EXTRA_BOOK);
        mRequests = getIntent().getParcelableArrayListExtra(EXTRA_REQUESTS);

        Collections.sort(mRequests);

        mPullRefreshLayout = (PullRefreshLayout) findViewById(R.id.swipeRefreshLayout);
        mPullRefreshLayout.setOnRefreshListener(new PullRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                fetchRequests();
            }
        });

        mAcceptedRequestTextView = (TextView) findViewById(R.id.acceptedRequestText);

        mRequestRecyclerView = (RecyclerView) findViewById(R.id.requestRecyclerView);
        mRequestRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        mRequestAdapter = new RequestAdapter(this, mRequests);

        mRequestRecyclerView.setAdapter(mRequestAdapter);

        mRequestRecyclerView.setOnScrollListener(new RecyclerView.OnScrollListener() {

            int totalScrolled = 0;
            ActionBar actionBar = getSupportActionBar();

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                totalScrolled += dy;
                totalScrolled = Math.abs(totalScrolled);

                actionBar.setElevation(ElevationScrollListener.getActionbarElevation(totalScrolled));
            }
        });

        mRequestAdapter.setRequestClickListeners(new RequestAdapter.RequestClickListeners() {
            @Override
            public void onUserClick(User user) {
                Intent intent = new Intent(RequestActivity.this, ProfileActivity.class);
                intent.putExtra(ProfileActivity.EXTRA_USER, user);
                startActivity(intent);
            }

            @Override
            public void onAcceptClick(final Request clickedRequest) {

                String message;

                if (mBook.getState() == Book.State.READING){
                    message = getString(R.string.accept_request_prompt_when_state_reading, clickedRequest.getRequester().getName());
                }else {
                    message = getString(R.string.accept_request_prompt, clickedRequest.getRequester().getName());
                }

                // Created at changed to Calendar.getInstance() because the new process has been created at the moment.
                new AlertDialog.Builder(RequestActivity.this)
                    .setMessage(message)
                    .setPositiveButton(R.string.accept, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {

                            Request createdRequest = new Request(mBook,
                                                                 clickedRequest.getRequester(),
                                                                 SessionManager.getCurrentUser(RequestActivity.this),
                                                                 Request.Type.ACCEPT,
                                                                 Calendar.getInstance());

                            Log.i(TAG, "Clicked request: " + clickedRequest);
                            Log.i(TAG, "Created request: " + createdRequest);

                            addBookRequestToServer(createdRequest, clickedRequest);
                        }
                    })
                    .setNegativeButton(android.R.string.no, null)
                    .create()
                    .show();
            }

            @Override
            public void onRejectClick(final Request clickedRequest) {
                new AlertDialog.Builder(RequestActivity.this)
                    .setMessage(getString(R.string.reject_request_prompt, clickedRequest.getRequester().getName()))
                    .setPositiveButton(R.string.reject, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {

                            // Created at changed to Calendar.getInstance() because the new process has been created at the moment.
                            Request createdRequest = new Request(mBook,
                                                                 clickedRequest.getRequester(),
                                                                 SessionManager.getCurrentUser(RequestActivity.this),
                                                                 Request.Type.REJECT,
                                                                 Calendar.getInstance());

                            Log.i(TAG, "Clicked request: " + clickedRequest);
                            Log.i(TAG, "Created request: " + createdRequest);

                            addBookRequestToServer(createdRequest, clickedRequest);
                        }
                    })
                    .setNegativeButton(android.R.string.no, null)
                    .create()
                    .show();
            }
        });

        mMessageReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equalsIgnoreCase(BookieIntentFilters.FCM_INTENT_FILTER_SENT_REQUEST_RECEIVED)){
                    if (intent.getParcelableExtra(BookieIntentFilters.EXTRA_NOTIFICATION) != null){
                        Notification notification = intent.getParcelableExtra(BookieIntentFilters.EXTRA_NOTIFICATION);

                        if (notification.getBook().equals(mBook)){

                            Request request = new Request(
                                mBook,
                                notification.getOppositeUser(),
                                SessionManager.getCurrentUser(context),
                                Request.Type.SEND,
                                notification.getCreatedAt());

                            mRequests.add(request);
                            Collections.sort(mRequests);
                            mRequestAdapter.notifyDataSetChanged(); // TODO notifyItemInserted
                        }
                    }
                }
            }
        };

        LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver, new IntentFilter(BookieIntentFilters.FCM_INTENT_FILTER_SENT_REQUEST_RECEIVED));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageReceiver);
    }

    private void fetchRequests() {
        BookApi bookApi = BookieClient.getClient().create(BookApi.class);

        User.Details currentUserDetails = SessionManager.getCurrentUserDetails(this);

        String email = currentUserDetails.getEmail();
        String password = currentUserDetails.getPassword();
        final Call<ResponseBody> getBookRequests = bookApi.getBookRequests(email, password, mBook.getID());

        getBookRequests.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                try {
                    if (response != null){
                        if (response.body() != null){
                            String json = response.body().string();

                            JSONObject responseObject = new JSONObject(json);
                            boolean error = responseObject.getBoolean("error");

                            if (!error) {
                                if (!responseObject.isNull("bookRequests")){
                                    mRequests.clear();
                                    mRequests.addAll(Request.jsonArrayToRequestList(mBook, responseObject.getJSONArray("bookRequests")));

                                    Log.i(TAG, mRequests.toString());

                                    for (Request r : mRequests) {
                                        if (r.getType() == Request.Type.ACCEPT) {
                                            setAcceptedRequestText(r);

                                            for (Request request : mRequests){
                                                if (request.getType() != Request.Type.ACCEPT) {
                                                    request.setType(Request.Type.REJECT);
                                                }
                                            }
                                        }
                                    }

                                    mRequestAdapter.setError(RequestAdapter.ERROR_TYPE_NONE);
                                    mRequestAdapter.setRequests(mRequests);
                                    mRequestAdapter.notifyDataSetChanged();

                                    mLocations = new Hashtable<>(mRequests.size());
                                    mRequestAdapter.setLocations(mLocations);

                                    fetchLocations();

                                    Log.i(TAG, "Book requests fetched and rearranged");

                                    Log.i(TAG, mRequests.toString());
                                }else {
                                    Log.e(TAG, "bookRequests is empty. (Request Page Error)");
                                    mRequestAdapter.setError(RequestAdapter.ERROR_TYPE_UNKNOWN_ERROR);
                                }


                            } else {

                                int errorCode = responseObject.getInt("errorCode");

                                if (errorCode == ErrorCodes.EMPTY_POST){
                                    Log.e(TAG, "Post is empty. (Request Page Error)");
                                }else if (errorCode == ErrorCodes.MISSING_POST_ELEMENT){
                                    Log.e(TAG, "Post element missing. (Request Page Error)");
                                }else if (errorCode == ErrorCodes.INVALID_REQUEST){
                                    Log.e(TAG, "Invalid request. (Request Page Error)");
                                }else if (errorCode == ErrorCodes.INVALID_EMAIL){
                                    Log.e(TAG, "Invalid email. (Request Page Error)");
                                }else if (errorCode == ErrorCodes.UNKNOWN){
                                    Log.e(TAG, "onResponse: errorCode = " + errorCode);
                                }

                                mRequestAdapter.setError(RequestAdapter.ERROR_TYPE_UNKNOWN_ERROR);
                            }
                        }else{
                            Log.e(TAG, "Response body is null. (Home Page Error)");
                            mRequestAdapter.setError(RequestAdapter.ERROR_TYPE_UNKNOWN_ERROR);
                        }
                    }else {
                        Log.e(TAG, "Response object is null. (Home Page Error)");
                        mRequestAdapter.setError(RequestAdapter.ERROR_TYPE_UNKNOWN_ERROR);
                    }
                } catch (IOException | JSONException e) {
                    e.printStackTrace();

                    if (BookieApplication.hasNetwork()){
                        mRequestAdapter.setError(RequestAdapter.ERROR_TYPE_UNKNOWN_ERROR);
                    }else {
                        mRequestAdapter.setError(RequestAdapter.ERROR_TYPE_NO_CONNECTION);
                    }
                }

                mPullRefreshLayout.setRefreshing(false);
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.e(TAG, "Home Page book fetch onFailure: " + t.getMessage());
                if (BookieApplication.hasNetwork()){
                    mRequestAdapter.setError(RequestAdapter.ERROR_TYPE_UNKNOWN_ERROR);
                }else {
                    mRequestAdapter.setError(RequestAdapter.ERROR_TYPE_NO_CONNECTION);
                }

                mPullRefreshLayout.setRefreshing(false);
            }
        });
    }

    private void addBookRequestToServer(final Request request, final Request oldRequest) {

        final ComfortableProgressDialog comfortableProgressDialog = new ComfortableProgressDialog(RequestActivity.this);
        comfortableProgressDialog.setMessage(getString(R.string.updating_process));
        comfortableProgressDialog.show();

        final BookApi bookApi = BookieClient.getClient().create(BookApi.class);

        User.Details currentUserDetails = SessionManager.getCurrentUserDetails(this);

        String email = currentUserDetails.getEmail();
        String password = currentUserDetails.getPassword();
        Call<ResponseBody> addBookRequest = bookApi.addBookRequest(email,
                                                                   password,
                                                                   request.getBook().getID(),
                                                                   request.getRequester().getID(),
                                                                   request.getResponder().getID(),
                                                                   request.getType().getRequestCode());

        Log.i(TAG, "Adding book request to server: " + request);

        addBookRequest.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {

                try {
                    if (response != null){
                        if (response.body() != null){
                            String json = response.body().string();

                            JSONObject responseObject = new JSONObject(json);
                            boolean error = responseObject.getBoolean("error");

                            if (!error) {
                                if (request.getType() == Request.Type.ACCEPT){
                                    int index = mRequests.indexOf(oldRequest);
                                    mRequests.remove(index);

                                    for (Request r : mRequests) {
                                        if (r.getType() != Request.Type.REJECT) {
                                            r.setType(Request.Type.REJECT);
                                        }
                                    }

                                    // All the requests changed so its good to use notifyDataSetChanged()
                                    mRequestAdapter.notifyDataSetChanged();

                                    setAcceptedRequestText(request);

                                    Intent intent = new Intent(BookieIntentFilters.INTENT_FILTER_ACCEPTED_REQUEST);
                                    intent.putExtra(BookieIntentFilters.EXTRA_REQUEST, request);
                                    LocalBroadcastManager.getInstance(RequestActivity.this).sendBroadcast(intent);

                                }else if (request.getType() == Request.Type.REJECT){
                                    // Old request fields changed to new requests field because the adapter move animation notifyItemInserted() works with same object
                                    int indexBefore = mRequests.indexOf(oldRequest);
                                    oldRequest.setType(Request.Type.REJECT);
                                    oldRequest.setCreatedAt(request.getCreatedAt());
                                    Collections.sort(mRequests);
                                    int indexAfter = mRequests.indexOf(oldRequest) + 1; // Subtitle
                                    mRequestAdapter.notifyItemMoved(indexBefore, indexAfter);
                                    Log.i(TAG, "Item moved: " + indexBefore + " -> " + indexAfter);
                                    //mRequestAdapter.notifyItemChanged(mRequestAdapter.getSentRequestCount() - 1);

                                    Intent intent = new Intent(BookieIntentFilters.INTENT_FILTER_REJECTED_REQUEST);
                                    intent.putExtra(BookieIntentFilters.EXTRA_REQUEST, request);
                                    LocalBroadcastManager.getInstance(RequestActivity.this).sendBroadcast(intent);
                                }

                                comfortableProgressDialog.dismiss();
                            } else {
                                int errorCode = responseObject.getInt("errorCode");

                                if (errorCode == ErrorCodes.EMPTY_POST){
                                    Log.e(TAG, "Post is empty. (Book Page Error)");
                                }else if (errorCode == ErrorCodes.MISSING_POST_ELEMENT){
                                    Log.e(TAG, "Post element missing. (Book Page Error)");
                                }else if (errorCode == ErrorCodes.INVALID_REQUEST){
                                    Log.e(TAG, "Invalid request. (Book Page Error)");
                                }else if (errorCode == ErrorCodes.INVALID_EMAIL){
                                    Log.e(TAG, "Invalid email. (Book Page Error)");
                                }else if (errorCode == ErrorCodes.USER_NOT_VERIFIED){
                                    Log.e(TAG, "User not valid. (Book Page Error)");
                                }else if (errorCode == ErrorCodes.USER_BLOCKED){
                                    Log.e(TAG, "User blocked. (Book Page Error)");
                                }else if (errorCode == ErrorCodes.LOCATION_NOT_FOUND){
                                    Log.e(TAG, "Location not found. (Book Page Error)");
                                }else if (errorCode == ErrorCodes.BOOK_COUNT_INSUFFICIENT){
                                    Log.e(TAG, "Book count insufficient. (Book Page Error)");
                                }else if (errorCode == ErrorCodes.UNKNOWN){
                                    Log.e(TAG, "onResponse: errorCode = " + errorCode);
                                }

                                comfortableProgressDialog.dismiss();
                                Toast.makeText(RequestActivity.this, getString(R.string.unknown_error), Toast.LENGTH_SHORT).show();
                            }
                        }else{
                            Log.e(TAG, "Response body is null. (Book Page Error)");
                            comfortableProgressDialog.dismiss();
                            Toast.makeText(RequestActivity.this, getString(R.string.unknown_error), Toast.LENGTH_SHORT).show();
                        }
                    }else {
                        Log.e(TAG, "Response object is null. (Book Page Error)");
                        comfortableProgressDialog.dismiss();
                        Toast.makeText(RequestActivity.this, getString(R.string.unknown_error), Toast.LENGTH_SHORT).show();
                    }
                } catch (IOException | JSONException e) {
                    e.printStackTrace();
                    comfortableProgressDialog.dismiss();
                    Toast.makeText(RequestActivity.this, getString(R.string.unknown_error), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.e(TAG, "Book Page onFailure: " + t.getMessage());
                comfortableProgressDialog.dismiss();
                Toast.makeText(RequestActivity.this, getString(R.string.unknown_error), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setAcceptedRequestText(final Request request) {
        mAcceptedRequestTextView.setVisibility(View.VISIBLE);

        String requesterName = request.getRequester().getName();
        String acceptRequestString = getString(R.string.you_accepted_request, requesterName);
        SpannableString spanAcceptRequest = new SpannableString(acceptRequestString);
        int startIndex = acceptRequestString.indexOf(requesterName);
        int endIndex = startIndex + requesterName.length();

        spanAcceptRequest.setSpan(new ClickableSpan() {
            @Override
            public void onClick(View widget) {
                Intent intent = new Intent(RequestActivity.this, ProfileActivity.class);
                intent.putExtra(ProfileActivity.EXTRA_USER, request.getRequester());
                startActivity(intent);
            }

            @Override
            public void updateDrawState(TextPaint ds) {
                super.updateDrawState(ds);
                ds.setUnderlineText(false);
            }
        }, startIndex, endIndex, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        spanAcceptRequest.setSpan(new StyleSpan(Typeface.BOLD), startIndex, endIndex, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        spanAcceptRequest.setSpan(new ForegroundColorSpan(ContextCompat.getColor(this, R.color.primaryTextColor)),
                                  0, spanAcceptRequest.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        mAcceptedRequestTextView.setMovementMethod(LinkMovementMethod.getInstance());
        mAcceptedRequestTextView.setHighlightColor(Color.TRANSPARENT);
        mAcceptedRequestTextView.setText(spanAcceptRequest);

        getSupportActionBar().setElevation(getResources().getDimension(R.dimen.actionbar_starting_elevation));
        mRequestRecyclerView.setOnScrollListener(null);

        ViewCompat.setElevation(mAcceptedRequestTextView, 8f * LayoutUtils.DP);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
        }
        return super.onOptionsItemSelected(item);
    }

    private void fetchLocations() {

        new Thread(new Runnable() {
            @Override
            public void run() {

                for (int i = 0; i < mRequests.size(); i++) {
                    final Request r = mRequests.get(i);
                    User requester = r.getRequester();

                    if (requester.getLocation() != null) {

                        final double latitude = requester.getLocation().latitude;
                        final double longitude = requester.getLocation().longitude;

                        try {
                            Geocoder geocoder = new Geocoder(RequestActivity.this, Locale.getDefault());
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
                                    mLocations.put(r, locationString);

                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            mRequestAdapter.notifyItemChanged(mRequests.indexOf(r));
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