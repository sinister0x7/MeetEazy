package net.intensecorp.meeteazy.activities;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.bumptech.glide.Glide;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textview.MaterialTextView;
import com.google.firebase.auth.FirebaseAuth;

import net.intensecorp.meeteazy.R;
import net.intensecorp.meeteazy.api.ApiClient;
import net.intensecorp.meeteazy.api.ApiService;
import net.intensecorp.meeteazy.utils.ApiUtility;
import net.intensecorp.meeteazy.utils.Extras;
import net.intensecorp.meeteazy.utils.FormatterUtility;
import net.intensecorp.meeteazy.utils.SharedPrefsManager;

import org.jitsi.meet.sdk.JitsiMeetActivity;
import org.jitsi.meet.sdk.JitsiMeetConferenceOptions;
import org.jitsi.meet.sdk.JitsiMeetUserInfo;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Objects;

import de.hdodenhof.circleimageview.CircleImageView;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class IncomingCallActivity extends AppCompatActivity {

    public static final int INCOMING_CALL_NOTIFICATION_ID = 1;
    public static final String INCOMING_CALL_NOTIFICATION_CHANNEL_ID = "0";
    public static final String NOTIFICATION_CHANNEL_INCOMING_CALLS = "Incoming call notifications";
    public static final String NOTIFICATION_CHANNEL_DESCRIPTION_INCOMING_CALLS = "Incoming call notifications to notify about personal and group calls.";
    private static final String TAG = IncomingCallActivity.class.getSimpleName();
    private static final long INCOMING_CALL_TIMEOUT_TIMER = 60000;
    BroadcastReceiver mCallEndRequestReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String requestType = intent.getStringExtra(Extras.EXTRA_REQUEST_TYPE);

            if (requestType != null) {
                if (requestType.equals(ApiUtility.REQUEST_TYPE_ENDED)) {
                    cancelIncomingCallNotification();
                    finish();
                }
            }
        }
    };

    private String mRoomId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_incoming_call);

        MaterialTextView incomingCallTypeView = findViewById(R.id.textView_incoming_call_type);
        CircleImageView callerProfilePictureView = findViewById(R.id.circleImageView_caller_profile_picture);
        MaterialTextView callerFullNameView = findViewById(R.id.textView_caller_full_name);
        MaterialTextView callerEmailView = findViewById(R.id.textView_caller_email);
        LinearLayout isCallingYouAndManyOthersLayout = findViewById(R.id.linearLayout_is_calling_you_and_other_or_others);
        MaterialTextView otherCalleesCountView = findViewById(R.id.textView_other_members_count);
        MaterialTextView othersOrOtherView = findViewById(R.id.textView_others_or_other);
        FloatingActionButton rejectCallButton = findViewById(R.id.floatingActionButton_reject_call);
        FloatingActionButton answerCallButton = findViewById(R.id.floatingActionButton_answer_call);

        Intent incomingCallIntent = getIntent();
        String incomingCallType = incomingCallIntent.getStringExtra(Extras.EXTRA_CALL_TYPE);
        String callerFirstName = incomingCallIntent.getStringExtra(Extras.EXTRA_CALLER_FIRST_NAME);
        String callerLastName = incomingCallIntent.getStringExtra(Extras.EXTRA_CALLER_LAST_NAME);
        String callerEmail = incomingCallIntent.getStringExtra(Extras.EXTRA_CALLER_EMAIL);
        String callerProfilePictureUrl = incomingCallIntent.getStringExtra(Extras.EXTRA_CALLER_PROFILE_PICTURE_URL);
        String callerFcmToken = incomingCallIntent.getStringExtra(Extras.EXTRA_CALLER_FCM_TOKEN);
        mRoomId = incomingCallIntent.getStringExtra(Extras.EXTRA_ROOM_ID);

        switch (incomingCallType) {
            case ApiUtility.CALL_TYPE_PERSONAL:
                incomingCallTypeView.setText(R.string.text_incoming_call);
                callerEmailView.setText(callerEmail);
                break;

            case ApiUtility.CALL_TYPE_GROUP:
                String otherCalleesCount = incomingCallIntent.getStringExtra(Extras.EXTRA_OTHER_CALLEES_COUNT);
                incomingCallTypeView.setText(R.string.text_incoming_group_call);
                callerEmailView.setVisibility(View.GONE);
                otherCalleesCountView.setText(otherCalleesCount);
                if (otherCalleesCount.equals("1")) {
                    othersOrOtherView.setText(R.string.text_other);
                }
                isCallingYouAndManyOthersLayout.setVisibility(View.VISIBLE);
                break;

            default:
                break;
        }

        Glide.with(getBaseContext())
                .load(callerProfilePictureUrl)
                .centerCrop()
                .placeholder(R.drawable.img_profile_picture)
                .into(callerProfilePictureView);

        callerFullNameView.setText(FormatterUtility.getFullName(callerFirstName, callerLastName));

        callerEmailView.setText(callerEmail);

        rejectCallButton.setOnClickListener(v -> {
            craftCallResponseMessageBody(callerFcmToken, ApiUtility.RESPONSE_TYPE_REJECTED);
            cancelIncomingCallNotification();
            finish();
        });

        answerCallButton.setOnClickListener(v -> craftCallResponseMessageBody(callerFcmToken, ApiUtility.RESPONSE_TYPE_ANSWERED));

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            craftCallResponseMessageBody(callerFcmToken, ApiUtility.RESPONSE_TYPE_REJECTED);
            cancelIncomingCallNotification();
            finish();
        }, INCOMING_CALL_TIMEOUT_TIMER);
    }

    @Override
    protected void onStart() {
        super.onStart();
        LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver(mCallEndRequestReceiver, new IntentFilter(ApiUtility.MESSAGE_TYPE_CALL_REQUEST));
    }

    @Override
    protected void onStop() {
        super.onStop();
        LocalBroadcastManager.getInstance(getApplicationContext()).unregisterReceiver(mCallEndRequestReceiver);
    }

    private void cancelIncomingCallNotification() {
        NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(String.valueOf(R.string.app_name), INCOMING_CALL_NOTIFICATION_ID);
    }

    private void craftCallResponseMessageBody(String callerFcmToken, String responseType) {
        try {
            JSONArray callerFcmTokensArray = new JSONArray();
            callerFcmTokensArray.put(callerFcmToken);

            JSONObject body = new JSONObject();
            JSONObject data = new JSONObject();

            data.put(ApiUtility.KEY_MESSAGE_TYPE, ApiUtility.MESSAGE_TYPE_CALL_RESPONSE);
            data.put(ApiUtility.KEY_RESPONSE_TYPE, responseType);

            body.put(ApiUtility.JSON_OBJECT_DATA, data);
            body.put(ApiUtility.JSON_OBJECT_REGISTRATION_IDS, callerFcmTokensArray);

            sendCallResponseMessage(body.toString(), responseType);
        } catch (Exception exception) {
            cancelIncomingCallNotification();
            finish();
            Log.e(TAG, "Message can't be crafted: " + exception.getMessage());
        }
    }

    private void sendCallResponseMessage(String messageBody, String responseType) {
        ApiClient.getClient()
                .create(ApiService.class)
                .sendRemoteMessage(ApiUtility.getMessageHeaders(), messageBody)
                .enqueue(new Callback<String>() {
                    @Override
                    public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                        if (response.isSuccessful()) {
                            switch (responseType) {
                                case ApiUtility.RESPONSE_TYPE_ANSWERED:
                                    SharedPrefsManager sharedPrefsManager = new SharedPrefsManager(IncomingCallActivity.this, SharedPrefsManager.PREF_USER_DATA);
                                    HashMap<String, String> userData = sharedPrefsManager.getUserDataPrefs();
                                    String firstName = userData.get(SharedPrefsManager.PREF_FIRST_NAME);
                                    String lastName = userData.get(SharedPrefsManager.PREF_LAST_NAME);
                                    String profilePictureLink = userData.get(SharedPrefsManager.PREF_PROFILE_PICTURE_URL);

                                    String fullName = FormatterUtility.getFullName(firstName, lastName);
                                    URL profilePictureUrl;

                                    JitsiMeetUserInfo jitsiMeetUserInfo = new JitsiMeetUserInfo();
                                    jitsiMeetUserInfo.setDisplayName(fullName);
                                    jitsiMeetUserInfo.setEmail(Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getEmail());

                                    try {
                                        profilePictureUrl = new URL(profilePictureLink);
                                        jitsiMeetUserInfo.setAvatar(profilePictureUrl);
                                    } catch (MalformedURLException e) {
                                        e.printStackTrace();
                                    }

                                    JitsiMeetConferenceOptions.Builder conferenceOptionsBuilder = new JitsiMeetConferenceOptions.Builder()
                                            .setServerURL(ApiUtility.getJitsiMeetServerUrl())
                                            .setWelcomePageEnabled(false)
                                            .setRoom(mRoomId)
                                            .setUserInfo(jitsiMeetUserInfo)
                                            .setVideoMuted(true)
                                            .setAudioMuted(true);

                                    JitsiMeetActivity.launch(IncomingCallActivity.this, conferenceOptionsBuilder.build());

                                    cancelIncomingCallNotification();
                                    finish();
                                    break;

                                case ApiUtility.RESPONSE_TYPE_REJECTED:
                                    Log.d(TAG, "Call rejected response message successfully sent.");
                                    break;

                                default:
                                    Log.e(TAG, "Unknown response.");
                                    break;
                            }
                        } else {
                            Log.d(TAG, "Response of sent message: " + response.message());
                            cancelIncomingCallNotification();
                            finish();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<String> call, @NonNull Throwable t) {
                        cancelIncomingCallNotification();
                        finish();
                        Log.e(TAG, "Message not sent: " + t.getMessage());
                    }
                });
    }
}
