package com.appsinventiv.medicineadmin.Activities;

import android.content.Intent;
import android.media.AudioManager;
import android.media.SoundPool;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;

import com.appsinventiv.medicineadmin.Adapters.ChatAdapter;
import com.appsinventiv.medicineadmin.Models.ChatModel;
import com.appsinventiv.medicineadmin.Models.Customer;
import com.appsinventiv.medicineadmin.R;
import com.appsinventiv.medicineadmin.Utils.Constants;
import com.appsinventiv.medicineadmin.Utils.NotificationAsync;
import com.appsinventiv.medicineadmin.Utils.NotificationObserver;
import com.appsinventiv.medicineadmin.Utils.SharedPrefs;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;

public class LiveChat extends AppCompatActivity implements NotificationObserver {

    DatabaseReference mDatabase;
    EditText message;
    FloatingActionButton send;
    RecyclerView recyclerView;
    LinearLayoutManager layoutManager;
    ChatAdapter adapter;
    ArrayList<ChatModel> chatModelArrayList = new ArrayList<>();
    int soundId;
    SoundPool sp;
    String userFcmKey;
    String username;
    String name;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_live_chat);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }


        Intent i = getIntent();
        username = i.getStringExtra("username");
        name = i.getStringExtra("name");
        this.setTitle(name);


        mDatabase = FirebaseDatabase.getInstance().getReference();
        send = findViewById(R.id.send);
        message = findViewById(R.id.message);

        sp = new SoundPool(5, AudioManager.STREAM_MUSIC, 0);
        soundId = sp.load(LiveChat.this, R.raw.tick_sound, 1);


    }


    private void openFile(Integer CODE) {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("*/*");
        startActivityForResult(i, CODE);
    }


    @Override
    protected void onResume() {
        super.onResume();
        getUserDetails();
        getMessagesFromServer();
        readAllMessages();
    }

    private void getUserDetails() {
        mDatabase.child("customers").child(username).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.getValue() != null) {
                    Customer model = dataSnapshot.getValue(Customer.class);
                    if (model != null) {
                        userFcmKey = model.getFcmKey();
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    private void readAllMessages() {
        mDatabase.child("Chats").child(username).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.getValue() != null) {
                    for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                        ChatModel chatModel = snapshot.getValue(ChatModel.class);
                        if (chatModel != null) {
                            if (!chatModel.getUsername().equals(SharedPrefs.getUsername())) {
                                mDatabase.child("Chats").child(username).child(chatModel.getId()).child("status").setValue("read");
                            }
                        }
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    private void getMessagesFromServer() {
        recyclerView = findViewById(R.id.chats);
        layoutManager = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
        recyclerView.setLayoutManager(layoutManager);
        adapter = new ChatAdapter(LiveChat.this, chatModelArrayList);
        recyclerView.setAdapter(adapter);

        mDatabase.child("Chats").child(username).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.getValue() != null) {
                    chatModelArrayList.clear();
                    for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                        ChatModel model = snapshot.getValue(ChatModel.class);
                        if (model != null) {
                            chatModelArrayList.add(model);
                            recyclerView.scrollToPosition(chatModelArrayList.size() - 1);
                            adapter.notifyDataSetChanged();
                        }
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
        message.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean b) {
                if (b) {
                    recyclerView.scrollToPosition(chatModelArrayList.size() - 1);
                }

            }
        });
        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if (message.getText().length() == 0) {
                    message.setError("Cant send empty message");
                } else {
                    sendMessageToServer(Constants.MESSAGE_TYPE_TEXT, "", "");
                }

            }
        });


    }

    private void sendMessageToServer(final String type, final String url, String extension) {

        final String msg = message.getText().toString();
        message.setText(null);
        final String key = mDatabase.push().getKey();
        mDatabase.child("Chats").child(username).child(key)
                .setValue(new ChatModel(key,
                        msg,
                        SharedPrefs.getUsername(),
                        System.currentTimeMillis(),
                        "sending",
                        username,
                        name,
                        type.equals(Constants.MESSAGE_TYPE_IMAGE) ? url : "",
                        type.equals(Constants.MESSAGE_TYPE_DOCUMENT) ? url : "",
                        "." + extension,
                        type


                )).addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void aVoid) {


                sp.play(soundId, 1, 1, 0, 0, 1);
                adapter.notifyDataSetChanged();
                recyclerView.scrollToPosition(chatModelArrayList.size() - 1);

                mDatabase.child("Chats").child(username).child(key).child("status").setValue("sent");


                NotificationAsync notificationAsync = new NotificationAsync(LiveChat.this);
                String NotificationTitle = "New message from " + SharedPrefs.getUsername();
                String NotificationMessage = "";
                if (type.equals(Constants.MESSAGE_TYPE_TEXT)) {
                    NotificationMessage = SharedPrefs.getUsername() + ": " + msg;
                }
                notificationAsync.execute("ali", userFcmKey, NotificationTitle, NotificationMessage, "Chat", key);

            }
        });
    }


    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (item.getItemId() == android.R.id.home) {

            Intent i = new Intent(LiveChat.this, Chats.class);
            startActivity(i);
            finish();
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public void onSuccess(String chatId) {
        mDatabase.child("Chats").child(username).child(chatId).child("status").setValue("delivered");
    }

    @Override
    public void onFailure() {

    }


}
