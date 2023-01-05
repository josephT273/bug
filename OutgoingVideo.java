package com.example.com.pages;

import static android.content.ContentValues.TAG;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.com.R;
import com.example.com.client.ApiClient;
import com.example.com.client.ApiService;
import com.example.com.models.User;
import com.example.com.services.FcmNotificationsSender;
import com.example.com.utilities.Constants;
import com.example.com.utilities.PreferenceManager;
import com.bumptech.glide.Glide;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import org.jitsi.meet.sdk.JitsiMeetActivity;
import org.jitsi.meet.sdk.JitsiMeetConferenceOptions;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URL;
import java.util.HashMap;
import java.util.UUID;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class OutgoingVideo extends AppCompatActivity {

    FloatingActionButton fabDecline;
    TextView txtReceiverName;
    ImageView avatar;
    private String receiver_token, re_name, url, t, uid;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    DocumentReference documentReference, reference;
    FirebaseDatabase database;
    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
    PreferenceManager preferenceManager;
    private String inviterToken = null;
    private String meetingRoom  = null;
    private String userId  = user.getUid();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_outgoing_video);

        preferenceManager = new PreferenceManager(this);
        FirebaseAnalytics.getInstance(this);
        inviterToken = preferenceManager.getString("senderToken");
        fabDecline = findViewById(R.id.fabDecline);
        txtReceiverName = findViewById(R.id.txtReceiverName);
        avatar = findViewById(R.id.avatar);

        Bundle bundle = getIntent().getExtras();
        if (bundle != null){
            receiver_token = bundle.getString(Constants.KEY_FCM_TOKEN);
            re_name = bundle.getString("re_name");
            url = bundle.getString("url");
            uid = bundle.getString("uid");
            t = bundle.getString("t");
            documentReference = db.collection(Constants.VC_REF).document(uid);
//            init();
            initiateMeeting(receiver_token);
            checkResponse();
        }

        fabDecline.setOnClickListener(v -> {
            documentReference.update("response", "no");
            finish();
        });
    }

    private void checkResponse() {
        documentReference.addSnapshotListener((snapshot, e) -> {
            if (e != null) {
                Log.w(TAG, "Listen failed.", e);
                return;
            }

            if (snapshot != null && snapshot.exists()) {
                if (snapshot.get("response").equals("no")){
                    finish();
                }else if (snapshot.get("response").equals("yes")){
                    joinMeting(uid);
                }
            } else {
                Log.d(TAG, "Current data: null");
            }
        });
    }

    public void init(){
        txtReceiverName.setText(re_name);
        Glide.with(getApplicationContext()).load(url).into(avatar);
//        initiateMeeting(meetingType, user.token, null);
//        HashMap<String, String> map = new HashMap<>();
//        map.put("token", preferenceManager.getString("senderToken"));
//        map.put("uid", user.getUid());
//        map.put("response", "0");
//        documentReference.set(map);
//        FcmNotificationsSender sender = new FcmNotificationsSender(receiver_token, t, user.getUid(), getApplicationContext(), OutgoingVideo.this);
//        sender.SendNotifications();
    }
    private void joinMeting(String key) {
        try {
            JitsiMeetConferenceOptions options = new JitsiMeetConferenceOptions.Builder()
                    .setServerURL(new URL("https://meet.jit.si"))
                    .setRoom(key)
                    .setAudioMuted(false)
                    .setVideoMuted(false)
                    .setAudioOnly(false)
                    .build();

            JitsiMeetActivity.launch(this, options);
            this.finish();
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    private void initiateMeeting(String receiverToken) {
        try {

            JSONArray tokens = new JSONArray();

            if (receiverToken != null) {
                tokens.put(receiverToken);
            }
            JSONObject body = new JSONObject();
            JSONObject data = new JSONObject();

            data.put(Constants.REMOTE_MSG_TYPE, Constants.REMOTE_MSG_INVITATION);
            data.put(Constants.REMOTE_MSG_MEETING_TYPE, "video");
            data.put(Constants.DISPLAY_NAME, preferenceManager.getString(Constants.DISPLAY_NAME));
            data.put(Constants.PHONE, preferenceManager.getString(Constants.PHONE));
            data.put(Constants.REMOTE_MSG_INVITER_TOKEN, inviterToken);

            meetingRoom =
                    preferenceManager.getString(userId) + "_" +
                            UUID.randomUUID().toString().substring(0, 5);
            data.put(Constants.REMOTE_MSG_MEETING_ROOM, meetingRoom);

            body.put(Constants.REMOTE_MSG_DATA, data);
            body.put(Constants.REMOTE_MSG_REGISTRATION_IDS, tokens);

            sendRemoteMessage(body.toString(), Constants.REMOTE_MSG_INVITATION);

        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
            finish();
        }
    }
    private void cancelInvitation(String receiverToken) {
        try {

            JSONArray tokens = new JSONArray();

            if (receiverToken != null) {
                tokens.put(receiverToken);
            }

            JSONObject body = new JSONObject();
            JSONObject data = new JSONObject();

            data.put(Constants.REMOTE_MSG_TYPE, Constants.REMOTE_MSG_INVITATION_RESPONSE);
            data.put(Constants.REMOTE_MSG_INVITATION_RESPONSE, Constants.REMOTE_MSG_INVITATION_CANCELLED);

            body.put(Constants.REMOTE_MSG_DATA, data);
            body.put(Constants.REMOTE_MSG_REGISTRATION_IDS, tokens);

            sendRemoteMessage(body.toString(), Constants.REMOTE_MSG_INVITATION_RESPONSE);

        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
            finish();
        }
    }
    private void sendRemoteMessage(String remoteMessageBody, String type) {
        ApiClient.getClient().create(ApiService.class).sendRemoteMessage(
                Constants.getRemoteMessageHeader(), remoteMessageBody
        ).enqueue(new Callback<String>() {
            @Override
            public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                if (response.isSuccessful()) {
                    if (type.equals(Constants.REMOTE_MSG_INVITATION)) {
                        Toast.makeText(OutgoingVideo.this, "Invitation sent successfully", Toast.LENGTH_SHORT).show();
                    } else if (type.equals(Constants.REMOTE_MSG_INVITATION_RESPONSE)) {
                        Toast.makeText(OutgoingVideo.this, "Invitation cancelled", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                } else {
                    Toast.makeText(OutgoingVideo.this, response.message(), Toast.LENGTH_SHORT).show();
                    finish();
                }
            }

            @Override
            public void onFailure(@NonNull Call<String> call,@NonNull Throwable t) {
                Toast.makeText(OutgoingVideo.this, t.getMessage(), Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }
    private BroadcastReceiver invitationResponseReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String type = intent.getStringExtra(Constants.REMOTE_MSG_INVITATION_RESPONSE);
            if (type != null) {
                if (type.equals(Constants.REMOTE_MSG_INVITATION_ACCEPTED)) {
                    joinMeting(meetingRoom);
                } else if (type.equals(Constants.REMOTE_MSG_INVITATION_REJECTED)) {
                    finish();
                }
            }
        }
    };
    @Override
    protected void onStart() {
        super.onStart();
        LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver(
                invitationResponseReceiver,
                new IntentFilter(Constants.REMOTE_MSG_INVITATION_RESPONSE)
        );
    }

    @Override
    protected void onStop() {
        super.onStop();
        LocalBroadcastManager.getInstance(getApplicationContext()).unregisterReceiver(
                invitationResponseReceiver
        );
    }
}
