package com.shishimao.rtcatdemo3;

import android.Manifest;
import android.content.res.Resources;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.Toast;



import com.shishimao.sdk.AbstractStream;
import com.shishimao.sdk.Configs;
import com.shishimao.sdk.Errors;
import com.shishimao.sdk.LocalStream;
import com.shishimao.sdk.RTCat;
import com.shishimao.sdk.Receiver;
import com.shishimao.sdk.Receiver.ReceiverObserver;
import com.shishimao.sdk.RemoteStream;
import com.shishimao.sdk.Sender;
import com.shishimao.sdk.Sender.SenderObserver;
import com.shishimao.sdk.Session;
import com.shishimao.sdk.Session.SessionObserver;
import com.shishimao.sdk.LocalStream.StreamObserver;
import com.shishimao.sdk.apprtc.AppRTCAudioManager;
import com.shishimao.sdk.http.RTCatRequests;
import com.shishimao.sdk.tools.L;
import com.shishimao.sdk.view.VideoPlayer;
import com.shishimao.sdk.view.VideoPlayerLayout;


import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;

public class MainActivity extends AppCompatActivity {

    private final static String TAG = "MainActivity";


    VideoPlayerLayout videoRenderLayout;
    VideoPlayer localVideoRenderer;
    String[] audio_device_list;
    Spinner spinner;



    //webrtc
    RTCat cat;
    LocalStream localStream;
    Session session;

    HashMap<String,Sender> senders = new HashMap<>();
    HashMap<String,Receiver> receivers = new HashMap<>();

    ArrayList<VideoPlayer> render_list = new ArrayList<>();
    HashMap<String, VideoPlayerLayout> render2_list = new HashMap<>();

    int layout_width = 50;
    int layout_height = 50;

    int x = 0;
    int y = 0;

    public String token;

    public String messageToken;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        requestPermission();
        //webrtc

        localVideoRenderer = (VideoPlayer) findViewById(R.id.local_video_render);
        videoRenderLayout = (VideoPlayerLayout) findViewById(R.id.local_video_layout);
        videoRenderLayout.setPosition(50,50,50,50);

        cat = new RTCat(this,true,true,true,false, AppRTCAudioManager.AudioDevice.SPEAKER_PHONE,RTCat.CodecSupported.H264, L.VERBOSE);
        cat.initVideoPlayer(localVideoRenderer);

    }

    public void requestPermission(){
        String[] permssions = {
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CAMERA
        };
        ActivityCompat.requestPermissions(this,permssions, 123);
    }

    public void connect(View view){

        localStream = cat.createStream(true,true,15,RTCat.VideoFormat.Lv0, LocalStream.CameraFacing.FRONT);

        localStream.addObserver(new StreamObserver() {

            @Override
            public void error(Errors errors) {

            }

            @Override
            public void afterSwitch(boolean isFrontCamera) {}

            @Override
            public void accepted() {
                localStream.play(localVideoRenderer);
                createSession();
            }
        });

        localStream.init();
    }

    public void createSession()
    {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {

                    RTCatRequests requests = new RTCatRequests(Config.APIKEY, Config.SECRET);
                    token = requests.getToken(Config.SESSION, "pub");
                    l("token is " + token);
//                    session = cat.createSession(token);
                    session = cat.createSession(token, Session.SessionType.P2P);

                    class SessionHandler implements SessionObserver {
                        @Override
                        public void in(String token) {
                            messageToken = token;
                            l(token + " is in");
                            l(String.valueOf(session.getWits().size()));

                            if (session.getWits().size() < 3)
                            {
                                session.sendTo(localStream,false,null, token);
                            }
                        }

                        @Override
                        public void close() {
                            finish();
                        }

                        @Override
                        public void out(String token) {
                            final VideoPlayerLayout layout =  render2_list.get(token);

                            if( x == 0 && y == 50)
                            {
                                x = 50 ; y =0;
                            }else if(x == 50 && y == 0)
                            {
                                x = 0;
                            }


                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if(layout != null)
                                    {
                                        RelativeLayout relativeLayout = (RelativeLayout) findViewById(R.id.video_layout);
                                        relativeLayout.removeView(layout);
                                    }
                                }
                            });
                        }

                        @Override
                        public void connected(ArrayList wits) {
                            l("connected main");

                            String wit = "";
                            for (int i = 0; i < wits.size(); i++) {
                                if( i == 3)
                                {
                                    break;
                                }
                                try {
                                    wit = wit + wits.get(i);

                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }


                            session.send(localStream,false,null);
                        }

                        @Override
                        public void remote(final Receiver receiver) {
                            try {
                                receivers.put(receiver.getId(), receiver);

                                receiver.addObserver(new ReceiverObserver() {
                                    @Override
                                    public void log(JSONObject object) {
                                        Log.d("Receiver Log ->",object.toString());
                                    }


                                    @Override
                                    public void error(Errors errors) {

                                    }

                                    @Override
                                    public void stream(final RemoteStream stream) {
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                t(receiver.getFrom() + " stream");
                                                VideoPlayer videoViewRemote = new VideoPlayer(MainActivity.this);
                                                render_list.add(videoViewRemote);

                                                cat.initVideoPlayer(videoViewRemote);

                                                RelativeLayout layout = (RelativeLayout) findViewById(R.id.video_layout);
                                                VideoPlayerLayout remote_video_layout = new VideoPlayerLayout(MainActivity.this);

                                                render2_list.put(receiver.getFrom(),remote_video_layout);

                                                remote_video_layout.addView(videoViewRemote);

                                                remote_video_layout.setPosition(x,y,layout_width,layout_height);

                                                if( x == 0 && y == 0)
                                                {
                                                    x = 50;
                                                }else if(x == 50 && y == 0)
                                                {
                                                    x = 0; y= 50;
                                                }

                                                layout.addView(remote_video_layout);

                                                stream.play(videoViewRemote);
                                            }
                                        });

                                    }

                                    @Override
                                    public void message(String message) {
                                        try {
                                            JSONObject data = new JSONObject(message);
                                            String mes = data.getString("content");
                                        } catch (JSONException e) {
                                            l(e.toString());
                                        }

                                    }

                                    @Override
                                    public void close() {

                                    }
                                });

                                receiver.response();
                            } catch (Exception e) {
                                l(e.toString());
                            }


                        }

                        @Override
                        public void local(final Sender sender) {
                            senders.put(sender.getId(), sender);
                            sender.addObserver(new SenderObserver() {
                                @Override
                                public void log(JSONObject object) {
                                    Log.d("Sender Log ->",object.toString());
                                }

                                @Override
                                public void close() {
                                    if(session.getState() == Configs.ConnectState.CONNECTED)
                                    {
                                        session.sendTo(localStream,false,null,sender.getTo());
                                    }
                                }

                                @Override
                                public void error(Errors errors) {

                                }
                            });
                        }

                        @Override
                        public void message(String token, String message) {
                            l(token + ":" +message);
                        }

                        @Override
                        public void error(String error) {

                        }
                    }

                    SessionHandler sh = new SessionHandler();

                    session.addObserver(sh);

                    session.connect();

                } catch (Exception e) {
                    l(e.toString());
                }
            }
        }).start();

    }

    public void l(String o)
    {

        Log.d(TAG, o);
    }


    public void t(String o)
    {
        Toast.makeText(this, o,
                Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {

        if(session != null)
        {
            session.disconnect();
        }


        if(localStream != null)
        {
            localStream.dispose();
        }

        if(localVideoRenderer != null)
        {
            localVideoRenderer.release();
            localVideoRenderer = null;
        }

        for (VideoPlayer renderer:render_list)
        {
            renderer.release();
        }

        if(cat != null)
        {
            cat.release();
        }

        Log.d("Test","EXIT");

        super.onDestroy();

    }

    @Override
    protected void onStop() {
        if(localStream != null)
        {
            localStream.stop();
        }

        super.onStop();
    }

    @Override
    protected void onResume() {
        if(localStream != null)
        {
            localStream.start();
        }
        super.onResume();
    }


}
