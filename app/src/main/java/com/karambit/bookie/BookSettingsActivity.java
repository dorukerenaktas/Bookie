package com.karambit.bookie;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.karambit.bookie.helper.CircleImageView;
import com.karambit.bookie.helper.ComfortableProgressDialog;
import com.karambit.bookie.helper.ElevationScrollListener;
import com.karambit.bookie.helper.GenrePickerDialog;
import com.karambit.bookie.helper.SessionManager;
import com.karambit.bookie.helper.TypefaceSpan;
import com.karambit.bookie.model.Book;
import com.karambit.bookie.model.User;
import com.karambit.bookie.rest_api.BookApi;
import com.karambit.bookie.rest_api.BookieClient;
import com.karambit.bookie.rest_api.ErrorCodes;
import com.karambit.bookie.service.BookieIntentFilters;
import com.orhanobut.logger.Logger;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class BookSettingsActivity extends AppCompatActivity {

    private static final String TAG = BookSettingsActivity.class.getSimpleName();

    public static final String EXTRA_BOOK = "book";
    public static final String EXTRA_HAS_ANY_TRANSACTIONS = "has_any_transactions";

    private static final int REPORT_BOOK_WRONG_NAME = 1;
    private static final int REPORT_BOOK_WRONG_AUTHOR = 2;
    private static final int REPORT_BOOK_WRONG_GENRE = 3;
    private static final int REPORT_BOOK_WRONG_PHOTO = 4;
    private static final int REPORT_BOOK_TOO_DAMAGED = 5;
    private static final int REPORT_BOOK_OTHER = 6;

    private Book mBook;
    private boolean mHasAnyTransactions;
    private EditText mReportEditText;
    private Button mSendReportButton;
    private ImageButton mDoneButton;
    private RadioGroup mReportRadioGroup;
    private EditText mBookNameEditText;
    private EditText mBookAuthorEditText;
    private int mSelectedGenre;
    private BroadcastReceiver mMessageReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_book_settings);

        mBook = getIntent().getParcelableExtra(EXTRA_BOOK);
        mHasAnyTransactions = getIntent().getBooleanExtra(EXTRA_HAS_ANY_TRANSACTIONS, true);

        SpannableString s = new SpannableString(mBook.getName());
        s.setSpan(new TypefaceSpan(this, MainActivity.FONT_GENERAL_TITLE), 0, s.length(),
                  Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        float titleSize = getResources().getDimension(R.dimen.actionbar_title_size);
        s.setSpan(new AbsoluteSizeSpan((int) titleSize), 0, s.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        s.setSpan(new ForegroundColorSpan(ContextCompat.getColor(this, R.color.primaryTextColor)), 0, s.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            float elevation = getResources().getDimension(R.dimen.actionbar_starting_elevation);
            actionBar.setElevation(elevation);
            actionBar.setTitle("");

            ((TextView) toolbar.findViewById(R.id.toolbarTitle)).setText(s);

            toolbar.findViewById(R.id.closeButton).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }

        final ScrollView scrollView = (ScrollView) findViewById(R.id.settingsScrollView);

        scrollView.getViewTreeObserver().addOnScrollChangedListener(new ViewTreeObserver.OnScrollChangedListener() {
            @Override
            public void onScrollChanged() {
                int scrollY = scrollView.getScrollY();
                if (actionBar != null) {
                    actionBar.setElevation(ElevationScrollListener.getActionbarElevation(scrollY));
                }
            }
        });

        if (BookieApplication.hasNetwork()) {

            User currentUser = SessionManager.getCurrentUser(this);

            if (!mHasAnyTransactions && mBook.getOwner().equals(currentUser)) {
                findViewById(R.id.bookEditContainer).setVisibility(View.VISIBLE);
                findViewById(R.id.reportBookContainer).setVisibility(View.GONE);

                mBookNameEditText = (EditText) findViewById(R.id.bookNameEditText);
                mBookAuthorEditText = (EditText) findViewById(R.id.bookAuthorEditText);
                CircleImageView bookPicture = (CircleImageView) findViewById(R.id.bookPictureImageView);

                mDoneButton = (ImageButton) findViewById(R.id.doneButton);
                mDoneButton.setVisibility(View.VISIBLE);

                mDoneButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        boolean ok = true;

                        String newBookName = mBookNameEditText.getText().toString().trim();
                        String newAuthor = mBookAuthorEditText.getText().toString().trim();

                        if (TextUtils.isEmpty(newBookName)) {
                            mBookNameEditText.setError(getString(R.string.empty_field_message));
                            ok = false;
                        }
                        if (TextUtils.isEmpty(newAuthor)) {
                            mBookAuthorEditText.setError(getString(R.string.empty_field_message));
                            ok = false;
                        }

                        if (ok) {
                            saveChanges();
                        }
                    }
                });

                Glide.with(this)
                     .load(mBook.getThumbnailURL())
                     .asBitmap()
                     .placeholder(R.drawable.placeholder_88dp)
                     .error(R.drawable.error_88dp)
                     .centerCrop()
                     .into(bookPicture);


                findViewById(R.id.bookPictureContainer).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent(BookSettingsActivity.this, PhotoViewerActivity.class);
                        Bundle bundle = new Bundle();
                        bundle.putString(PhotoViewerActivity.EXTRA_IMAGE, mBook.getImageURL());
                        if (!mHasAnyTransactions && mBook.getOwner().equals(SessionManager.getCurrentUser(BookSettingsActivity.this))){
                            bundle.putBoolean(PhotoViewerActivity.EXTRA_CAN_EDIT_BOOK_IMAGE, true);
                            bundle.putInt(PhotoViewerActivity.EXTRA_BOOK_ID, mBook.getID());
                        }
                        intent.putExtras(bundle);
                        startActivity(intent);
                    }
                });

                mBookNameEditText.setText(mBook.getName());
                mBookAuthorEditText.setText(mBook.getAuthor());

                final Button genreButton = (Button) findViewById(R.id.bookGenreButton);

                final String[] genres = getResources().getStringArray(R.array.genre_types);
                mSelectedGenre = mBook.getGenreCode();
                genreButton.setText(genres[mSelectedGenre]);

                genreButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        final GenrePickerDialog genrePickerDialog = new GenrePickerDialog(BookSettingsActivity.this, genres, mSelectedGenre);

                        genrePickerDialog.setOkClickListener(new GenrePickerDialog.OnOkClickListener() {
                            @Override
                            public void onOkClicked(int selectedGenre) {
                                genreButton.setText(genres[selectedGenre]);
                                mSelectedGenre = selectedGenre;
                                genrePickerDialog.dismiss();
                            }
                        });

                        genrePickerDialog.show();
                    }
                });

            } else {
                findViewById(R.id.bookEditContainer).setVisibility(View.GONE);
                findViewById(R.id.reportBookContainer).setVisibility(View.VISIBLE);

                mReportEditText = (EditText) findViewById(R.id.reportBookEditText);

                mReportRadioGroup = (RadioGroup) findViewById(R.id.reportBookRadioGroup);

                mSendReportButton = (Button) findViewById(R.id.reportSendButton);

                mReportEditText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                    @Override
                    public void onFocusChange(View view, boolean focused) {
                        if (focused) {
                            if (mReportRadioGroup.getCheckedRadioButtonId() == -1) {
                                mReportRadioGroup.check(R.id.reportUserOther);
                            }
                        } else {
                            if (mReportEditText.length() == 0) {
                                mReportRadioGroup.clearCheck();
                                mSendReportButton.setClickable(false);
                                mSendReportButton.setTextColor(ContextCompat.getColor(BookSettingsActivity.this, R.color.secondaryTextColor));
                            }
                        }
                    }
                });

                mReportEditText.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}

                    @Override
                    public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                        if (charSequence.length() > 0 || mReportRadioGroup.getCheckedRadioButtonId() != R.id.reportUserOther) {
                            mSendReportButton.setClickable(true);
                            mSendReportButton.setTextColor(ContextCompat.getColor(BookSettingsActivity.this, R.color.colorAccent));
                        } else {
                            mSendReportButton.setClickable(false);
                            mSendReportButton.setTextColor(ContextCompat.getColor(BookSettingsActivity.this, R.color.secondaryTextColor));
                        }
                    }

                    @Override
                    public void afterTextChanged(Editable editable) {}
                });

                mReportRadioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(RadioGroup radioGroup, int id) {
                        switch (id) {
                            case R.id.reportBookWrongName: {

                                mSendReportButton.setClickable(true);
                                mSendReportButton.setTextColor(ContextCompat.getColor(BookSettingsActivity.this, R.color.colorAccent));

                                mReportEditText.setHint(getString(R.string.report_hint_book_additional_info));

                                break;
                            }

                            case R.id.reportBookWrongAuthor: {

                                mSendReportButton.setClickable(true);
                                mSendReportButton.setTextColor(ContextCompat.getColor(BookSettingsActivity.this, R.color.colorAccent));

                                mReportEditText.setHint(getString(R.string.report_hint_book_additional_info));

                                break;
                            }

                            case R.id.reportBookWrongGenre: {

                                mSendReportButton.setClickable(true);
                                mSendReportButton.setTextColor(ContextCompat.getColor(BookSettingsActivity.this, R.color.colorAccent));

                                mReportEditText.setHint(getString(R.string.report_hint_book_additional_info));

                                break;
                            }

                            case R.id.reportBookWrongPhoto: {

                                mSendReportButton.setClickable(true);
                                mSendReportButton.setTextColor(ContextCompat.getColor(BookSettingsActivity.this, R.color.colorAccent));

                                mReportEditText.setHint(getString(R.string.report_hint_book_additional_info));

                                break;
                            }

                            case R.id.reportBookTooDamaged: {

                                mSendReportButton.setClickable(true);
                                mSendReportButton.setTextColor(ContextCompat.getColor(BookSettingsActivity.this, R.color.colorAccent));

                                mReportEditText.setHint(getString(R.string.report_hint_book_additional_info));

                                break;
                            }

                            case R.id.reportBookOther: {

                                if (mReportEditText.length() > 0) {
                                    mSendReportButton.setClickable(true);
                                    mSendReportButton.setTextColor(ContextCompat.getColor(BookSettingsActivity.this, R.color.colorAccent));
                                } else {
                                    mSendReportButton.setClickable(false);
                                    mSendReportButton.setTextColor(ContextCompat.getColor(BookSettingsActivity.this, R.color.secondaryTextColor));
                                }

                                mReportEditText.setHint(getString(R.string.report_hint_book_other, mBook.getName()));

                                mReportEditText.requestFocus();
                                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                                imm.showSoftInput(mReportEditText, InputMethodManager.SHOW_IMPLICIT);

                                break;
                            }
                        }
                    }
                });

                RadioButton tooDamaged = (RadioButton) findViewById(R.id.reportBookTooDamaged);

                if (mBook.getOwner().equals(currentUser)) {
                    tooDamaged.setVisibility(View.VISIBLE);
                } else {
                    tooDamaged.setVisibility(View.GONE);
                }

                mSendReportButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        new AlertDialog.Builder(BookSettingsActivity.this)
                                .setMessage(getString(R.string.send_report_prompt, mBook.getName()))
                                .setPositiveButton(R.string.send, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        sendReport();

                                        mReportEditText.setText("");

                                        mReportRadioGroup.clearCheck();
                                        mSendReportButton.setClickable(false);
                                        mSendReportButton.setTextColor(ContextCompat.getColor(BookSettingsActivity.this, R.color.secondaryTextColor));
                                    }
                                })
                                .setNegativeButton(android.R.string.cancel, null)
                                .create()
                                .show();
                    }
                });
            }

            View lostContainer = findViewById(R.id.lostContainer);

            if (mBook.getOwner().equals(currentUser) && mBook.getState() == Book.State.ON_ROAD) {
                lostContainer.setVisibility(View.VISIBLE);
                Button lostButton = (Button) findViewById(R.id.lostButton);
                lostButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {

                        new AlertDialog.Builder(BookSettingsActivity.this)
                            .setMessage(getString(R.string.lost_prompt, mBook.getName()))
                            .setPositiveButton(R.string.lost, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    lostProcesses();
                                }
                            })
                            .setNegativeButton(android.R.string.cancel, null)
                            .create()
                            .show();
                    }
                });
            } else {
                lostContainer.setVisibility(View.GONE);
            }

        } else {
            scrollView.setVisibility(View.GONE);

            View noConnectionView = findViewById(R.id.noConnectionView);
            noConnectionView.setVisibility(View.VISIBLE);
            ((TextView) noConnectionView.findViewById(R.id.emptyStateTextView)).setText(R.string.no_internet_connection);
        }

        mMessageReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equalsIgnoreCase(BookieIntentFilters.INTENT_FILTER_BOOK_PICTURE_CHANGED)) {
                    String bookPictureUrl = intent.getStringExtra(BookieIntentFilters.EXTRA_BOOK_PICTURE_URL);
                    String thumbnailUrl = intent.getStringExtra(BookieIntentFilters.EXTRA_BOOK_THUMBNAIL_URL);

                    if (bookPictureUrl != null && thumbnailUrl != null) {

                        mBook.setImageURL(bookPictureUrl);
                        mBook.setThumbnailURL(thumbnailUrl);

                        Glide.with(context)
                             .load(mBook.getThumbnailURL())
                             .asBitmap()
                             .placeholder(R.drawable.placeholder_88dp)
                             .error(R.drawable.error_88dp)
                             .centerCrop()
                             .into((ImageView) findViewById(R.id.bookPictureImageView));

                        Logger.d("Book picture changed received from Local Broadcast: \n" +
                                     "Book Picture URL: " + bookPictureUrl + "\nThumbnail URL: " + thumbnailUrl);
                    }
                }
            }
        };

        LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver, new IntentFilter(BookieIntentFilters.INTENT_FILTER_BOOK_PICTURE_CHANGED));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageReceiver);
    }

    private void lostProcesses() {

        sendBookTransactionLost();
    }

    private void sendBookTransactionLost() {
        final ComfortableProgressDialog comfortableProgressDialog = new ComfortableProgressDialog(BookSettingsActivity.this);
        comfortableProgressDialog.setMessage(getString(R.string.updating_process));
        comfortableProgressDialog.show();

        final BookApi bookApi = BookieClient.getClient().create(BookApi.class);

        User.Details currentUserDetails = SessionManager.getCurrentUserDetails(this);

        String email = currentUserDetails.getEmail();
        String password = currentUserDetails.getPassword();
        Call<ResponseBody> bookStateLost = bookApi.setBookStateLost(email, password, mBook.getID());

        Logger.d("setBookStateLost() API called with parameters: \n" +
                     "\temail=" + email + ", \n\tpassword=" + password + ", \n\tbookID=" + mBook.getID());

        bookStateLost.enqueue(new Callback<ResponseBody>() {
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

                                Logger.d("Book lost sent to server successfully");

                                comfortableProgressDialog.dismiss();

                                mBook.setState(Book.State.LOST);

                                Intent intent = new Intent(BookieIntentFilters.INTENT_FILTER_BOOK_LOST);
                                intent.putExtra(BookieIntentFilters.EXTRA_BOOK, mBook);
                                LocalBroadcastManager.getInstance(BookSettingsActivity.this).sendBroadcast(intent);

                                finish();
                            } else {
                                int errorCode = responseObject.getInt("errorCode");

                                Logger.e("Error true in response: errorCode = " + errorCode);

                                comfortableProgressDialog.dismiss();
                                Toast.makeText(BookSettingsActivity.this, getString(R.string.unknown_error), Toast.LENGTH_SHORT).show();
                            }
                        }else{
                            Logger.e("Response body is null. (Book Settings Page Error)");
                            comfortableProgressDialog.dismiss();
                            Toast.makeText(BookSettingsActivity.this, getString(R.string.unknown_error), Toast.LENGTH_SHORT).show();
                        }
                    }else {
                        Logger.e("Response object is null. (Book Settings Page Error)");
                        comfortableProgressDialog.dismiss();
                        Toast.makeText(BookSettingsActivity.this, getString(R.string.unknown_error), Toast.LENGTH_SHORT).show();
                    }
                } catch (IOException | JSONException e) {
                    Logger.e("IOException or JSONException caught: " + e.getMessage());
                    comfortableProgressDialog.dismiss();
                    Toast.makeText(BookSettingsActivity.this, getString(R.string.unknown_error), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Logger.e("bookStateLost Failure: " + t.getMessage());
                comfortableProgressDialog.dismiss();
                Toast.makeText(BookSettingsActivity.this, getString(R.string.unknown_error), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void sendReport() {
        ComfortableProgressDialog progressDialog = new ComfortableProgressDialog(this);
        progressDialog.setMessage(R.string.please_wait);
        progressDialog.setCancelable(false);
        progressDialog.show();

        String reportInfo = mReportEditText.getText().toString();

        int checkedRadioButtonId = mReportRadioGroup.getCheckedRadioButtonId();
        int reportCode = getCheckedReportCode(checkedRadioButtonId);

        uploadReport(reportCode, reportInfo, progressDialog);

    }

    private void uploadReport(int reportCode, String reportInfo, final ComfortableProgressDialog progressDialog) {
        final BookApi userApi = BookieClient.getClient().create(BookApi.class);

        String email = SessionManager.getCurrentUserDetails(this).getEmail();
        String password = SessionManager.getCurrentUserDetails(this).getPassword();
        Call<ResponseBody> uploadBookReport = userApi.uploadBookReport(email, password, mBook.getID(), reportCode, reportInfo);

        Logger.d("uploadBookReport() API called with parameters: \n" +
                     "\temail=" + email + ", \n\tpassword=" + password +
                     ", \n\treportCode=" + reportCode + ", \n\treportInfo=" + reportInfo);

        uploadBookReport.enqueue(new Callback<ResponseBody>() {
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
                                Logger.d("Report uploaded");
                                Toast.makeText(BookSettingsActivity.this, getString(R.string.thaks_for_your_report), Toast.LENGTH_SHORT).show();
                            } else {
                                int errorCode = responseObject.getInt("errorCode");

                                if (errorCode == ErrorCodes.USER_NOT_VERIFIED){
                                    // TODO User not verified dialog
                                    Logger.w("User not verified. (Book Settings Page Error)");
                                }else{
                                    Logger.e("Error true in response: errorCode = " + errorCode);
                                }

                                Toast.makeText(BookSettingsActivity.this, getString(R.string.unknown_error), Toast.LENGTH_SHORT).show();
                            }
                        }else{
                            Logger.e("Response body is null. (Book Settings Page Error)");
                            Toast.makeText(BookSettingsActivity.this, getString(R.string.unknown_error), Toast.LENGTH_SHORT).show();
                        }
                    }else {
                        Logger.e("Response object is null. (Book Settings Page Error)");
                        Toast.makeText(BookSettingsActivity.this, getString(R.string.unknown_error), Toast.LENGTH_SHORT).show();
                    }
                } catch (IOException | JSONException e) {
                    Logger.e("IOException or JSONException caught: " + e.getMessage());

                    Toast.makeText(BookSettingsActivity.this, getString(R.string.unknown_error), Toast.LENGTH_SHORT).show();
                }
                progressDialog.dismiss();
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                progressDialog.dismiss();
                Toast.makeText(BookSettingsActivity.this, getString(R.string.unknown_error), Toast.LENGTH_SHORT).show();
                Logger.e("uploadBookReport Failure: " + t.getMessage());
            }
        });
    }

    private int getCheckedReportCode(int id) {
        switch (id) {
            case R.id.reportBookWrongName:
                return REPORT_BOOK_WRONG_NAME;
            case R.id.reportBookWrongAuthor:
                return REPORT_BOOK_WRONG_AUTHOR;
            case R.id.reportBookWrongGenre:
                return REPORT_BOOK_WRONG_GENRE;
            case R.id.reportBookWrongPhoto:
                return REPORT_BOOK_WRONG_PHOTO;
            case R.id.reportBookTooDamaged:
                return REPORT_BOOK_TOO_DAMAGED;
            case R.id.reportUserOther:
                return REPORT_BOOK_OTHER;
            default:
                throw new IllegalArgumentException();
        }
    }

    private void saveChanges() {
        String bookName = mBookNameEditText.getText().toString();
        String author = mBookAuthorEditText.getText().toString();

        bookName = upperCaseString(bookName);
        author = upperCaseString(author);

        ComfortableProgressDialog progressDialog = new ComfortableProgressDialog(this);
        progressDialog.setMessage(R.string.please_wait);
        progressDialog.setCancelable(false);
        progressDialog.show();

        uploadBookParamsToServer(bookName, author, mSelectedGenre, progressDialog);
    }

    private void uploadBookParamsToServer(final String name, final String author, final int genreCode, final ComfortableProgressDialog progressDialog) {
        final BookApi bookApi = BookieClient.getClient().create(BookApi.class);

        String email = SessionManager.getCurrentUserDetails(BookSettingsActivity.this).getEmail();
        String password = SessionManager.getCurrentUserDetails(BookSettingsActivity.this).getPassword();

        Call<ResponseBody> updateBookDetails = bookApi.updateBookDetails(email, password, mBook.getID(), name, author, genreCode);

        Logger.d("updateBookDetails() API called with parameters: \n" +
                     "\temail=" + email + ", \n\tpassword=" + password + ", \n\tbookID=" + mBook.getID() +
                     ", \n\tbookName=" + name + ", \n\tauthor=" + author + ", \n\tgenreCode=" + genreCode);

        updateBookDetails.enqueue(new Callback<ResponseBody>() {
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

                                Logger.d("Book updated successfully");

                                mBook.setName(name);
                                mBook.setAuthor(author);
                                mBook.setGenreCode(genreCode);

                                Intent intent = new Intent(BookieIntentFilters.INTENT_FILTER_BOOK_UPDATED);
                                intent.putExtra(BookieIntentFilters.EXTRA_BOOK, mBook);
                                LocalBroadcastManager.getInstance(BookSettingsActivity.this).sendBroadcast(intent);

                                finish();
                            } else {
                                int errorCode = responseObject.getInt("errorCode");

                                Logger.e("Error true in response: errorCode = " + errorCode);

                                Toast.makeText(BookSettingsActivity.this, getString(R.string.unknown_error), Toast.LENGTH_SHORT).show();
                            }
                        }else{
                            Logger.e("Response body is null. (Book Settings Page Error)");
                            Toast.makeText(BookSettingsActivity.this, getString(R.string.unknown_error), Toast.LENGTH_SHORT).show();
                        }
                    }else {
                        Logger.e("Response object is null. (Book Settings Page Error)");
                        Toast.makeText(BookSettingsActivity.this, getString(R.string.unknown_error), Toast.LENGTH_SHORT).show();
                    }
                } catch (IOException | JSONException e) {
                    Logger.e("IOException or JSONException caught: " + e.getMessage());

                    Toast.makeText(BookSettingsActivity.this, getString(R.string.unknown_error), Toast.LENGTH_SHORT).show();
                }
                progressDialog.dismiss();
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Logger.e("updateBookDetails Failure: " + t.getMessage());

                progressDialog.dismiss();
                Toast.makeText(BookSettingsActivity.this, getString(R.string.unknown_error), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String upperCaseString(String input){
        String[] words = input.split(" ");
        StringBuilder sb = new StringBuilder();
        if (words[0].length() > 0) {
            sb.append(Character.toUpperCase(words[0].charAt(0)));
            sb.append(words[0].subSequence(1, words[0].length()).toString().toLowerCase());
            for (int i = 1; i < words.length; i++) {
                sb.append(" ");
                sb.append(Character.toUpperCase(words[i].charAt(0)));
                sb.append(words[i].subSequence(1, words[i].length()).toString().toLowerCase());
            }
        }
        return sb.toString();
    }
}
