package com.example.janek.wifidirect;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.support.v7.app.NotificationCompat;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

import javax.security.auth.login.LoginException;

public class ParentActivity extends Activity {

    /**
     * Port numbers and Host's IP address needed for transmitting data
     */
    public static final String GROUP_PORT = "port";
    public static final String AUDIO_PORT = "audio";
    public static final String VIDEO_PORT = "video";
    public static final String HOST_ADDRESS = "host";
    private String host;
    private int group_port, audio_port, video_port;


    /**
     * Notification actions
     */
    public static final String MUTE_ACTION = "mute";
    public static final String LISTEN_ACTION = "listen";
    public static final String WATCH_ACTION = "watch";
    NotificationCompat.Builder mBuilder;

    /**
     * Info for playing audio
     */
    private static final int SAMPLE_RATE = 8000; //Hertz
    private static final int BUFFER_SIZE = 8192; //Bytes

    /**
     * Image assets for audio streaming
     */
    private int[] soundImg = {  R.mipmap.ic_sound_1,
                                R.mipmap.ic_sound_2,
                                R.mipmap.ic_sound_3};

    /**
     * State flags
     */
    private boolean isWatching = false;
    private boolean isPlaying = false;
    private boolean isPaired = false;

    /**
     * Visual assets
     */
    private Button bt_monitor;
    private Button bt_listen;
    private ImageView imgSound;
    private VideoView videoView;
    private TextView txt_state;

    /**
     * Variables for playing video
     */
    MediaPlayer mediaPlayer = null;
    SurfaceHolder surfaceHolder;
    private File receivedVideo;
    private int fileNo = 0;

    private int stan = 0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parent);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        Intent receivedInfo = getIntent();
        host = receivedInfo.getExtras().getString(HOST_ADDRESS);
        group_port = receivedInfo.getExtras().getInt(GROUP_PORT);
        audio_port = receivedInfo.getExtras().getInt(AUDIO_PORT);
        video_port = receivedInfo.getExtras().getInt(VIDEO_PORT);

        bt_monitor = (Button) findViewById(R.id.bt_watch);
        bt_listen = (Button) findViewById(R.id.bt_listen);
        imgSound = (ImageView) findViewById(R.id.iv_sound);
        txt_state = (TextView) findViewById(R.id.txt_state);

        videoView = (VideoView) findViewById(R.id.videoView);
        videoView.setVisibility(View.INVISIBLE);

        if(!isPaired) {
            new PairDevices().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            Log.i("INFO", "ParentActivity: Started pairing");
        }

        bt_monitor.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!isWatching) {
                    isWatching = true;
                    new SendVideoRequest().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                } else {
                    isWatching = false;
                    Log.i("INFO", "PARENT: STOPPING PREVIEW");
                }
            }
        });

        bt_listen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!isPlaying){
                    isPlaying = true;
                    new SendAudioRequest().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                }
                else {
                    isPlaying = false;
                }
            }
        });


    }

    /**
     * Thread waiting for notification from MonitorActivity
     */
    private class ReceiveInfoThread extends AsyncTask<Void, Void, String> {

        @Override
        protected String doInBackground(Void... params) {
            isPlaying = true;
            try {
                ServerSocket serverSocket = new ServerSocket(group_port);
                Log.i("INFO","ParentActivity: Receiving notification: Socket opened");
                Socket socket = serverSocket.accept();
                Log.i("INFO","ParentActivity: Receiving notification: Connection done");
                socket.close();
                serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(String result) {

            NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

            // Intent when the user mutes the alert
            Intent muteIntent = makeNotificationIntent();
            muteIntent.setAction(MUTE_ACTION);
            PendingIntent resultMuteIntent = PendingIntent.getActivity(getBaseContext(), 1, muteIntent, PendingIntent.FLAG_UPDATE_CURRENT);

            // Intent when the user wants to listen
            Intent listenIntent = makeNotificationIntent();
            listenIntent.setAction(LISTEN_ACTION);
            PendingIntent resultListenIntent = PendingIntent.getActivity(getBaseContext(), 2, listenIntent, PendingIntent.FLAG_UPDATE_CURRENT);

            // Intent when the user wants to watch
            Intent watchIntent = makeNotificationIntent();
            watchIntent.setAction(WATCH_ACTION);
            PendingIntent resultWatchIntent = PendingIntent.getActivity(getBaseContext(), 3, watchIntent, PendingIntent.FLAG_UPDATE_CURRENT);


            mBuilder = (NotificationCompat.Builder) new NotificationCompat.Builder(getApplicationContext())
                    .setSmallIcon(R.mipmap.app_icon)
                    .setContentTitle("Warning!!")
                    .setContentText("Something is happening in the other room. Better check on that!")
                    .setLights(Color.RED, 500, 500)
                    .setAutoCancel(false)
                    .setDefaults(Notification.DEFAULT_SOUND)
                    .setPriority(Notification.PRIORITY_HIGH)
                    .addAction(R.drawable.ic_mute, "Mute", resultMuteIntent)
                    .addAction(R.drawable.ic_listen, "Listen", resultListenIntent)
                    .addAction(R.drawable.ic_watch, "Watch", resultWatchIntent);
            notificationManager.notify(1, mBuilder.build());

            new ReceiveInfoThread().executeOnExecutor(THREAD_POOL_EXECUTOR);

        }
    }

    /**
     * Thread for pairing the devices
     */
    private class PairDevices extends AsyncTask<Void, Void, String> {

        String ip ="";
        Socket socket = new Socket();
        @Override
        protected String doInBackground(Void... params) {
            Log.i("INFO", "ParentActivity: Started pairing in ParentActivity");
            try {
                Enumeration<NetworkInterface> networkInterfaceEnumeration = NetworkInterface.getNetworkInterfaces();
                while(networkInterfaceEnumeration.hasMoreElements()) {
                    NetworkInterface networkInterface = networkInterfaceEnumeration.nextElement();
                    Enumeration<InetAddress> inetAddressEnumeration = networkInterface.getInetAddresses();
                    while (inetAddressEnumeration.hasMoreElements()) {
                        InetAddress inetAddress = inetAddressEnumeration.nextElement();
                        if(inetAddress.isSiteLocalAddress()) {
                            if(networkInterface.getName().equals("p2p-p2p0-0")) {
                                Log.i("INFO", "Network interface name: " + networkInterface.getName());
                                ip += inetAddress.getHostAddress();
                            }
                        }
                    }
                }

            } catch(SocketException e ) {
                e.printStackTrace();
            }
            try {
                if(!socket.isConnected()) {
                    socket.connect((new InetSocketAddress(host,group_port)),5000);
                }
                OutputStream outputStream  = socket.getOutputStream();
                PrintWriter out = new PrintWriter(outputStream);
                out.print(ip);
                out.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                socket.close();
                Log.i("INFO","ParentActivity: Closed socket after pairing");
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            Log.i("INFO", "ParentActivity: Finished pairing the devices");
            isPaired = true;
            new ReceiveInfoThread().executeOnExecutor(THREAD_POOL_EXECUTOR);
        }
    }

    /**
     * Thread for sending a request to get audio/video from MonitorActivity
     */
    private class SendAudioRequest extends AsyncTask<Void,Void,String> {

        @Override
        protected void onPreExecute() {
            bt_listen.setText(R.string.stop_listening);
            txt_state.setText(R.string.txt_listen);
            imgSound.setVisibility(View.VISIBLE);
        }

        @Override
        protected String doInBackground(Void... params) {
            Socket soundSocket = new Socket();
            AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT,BUFFER_SIZE,AudioTrack.MODE_STREAM);
            audioTrack.setStereoVolume(1f,1f);
            byte[] buf = new byte[BUFFER_SIZE];
            audioTrack.play();
            isPlaying = true;

            try {
                Log.i("INFO","ParentActivity: Trying to connect to Monitor");
                soundSocket.connect((new InetSocketAddress(host,audio_port)),5000);
                Log.i("INFO", "ParentActivity: Connection done");
                while(isPlaying) {
                    Log.i("INFO","ParentActivity: Listening");
                    int readSize;
                    try {
                        readSize = soundSocket.getInputStream().read(buf);

                    }catch (Exception e ) {
                        e.printStackTrace();
                        break;
                    }
                    audioTrack.write(buf, 0 ,readSize);
                    publishProgress(params);
                }

                audioTrack.stop();
                audioTrack.release();

            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                soundSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;

        }

        @Override
        protected void  onProgressUpdate(Void... values) {

            if(stan == 0) {
                imgSound.setImageResource(soundImg[stan]);
                stan = 1;
            } else if(stan == 1) {
                imgSound.setImageResource(soundImg[stan]);
                stan = 2;
            } else {
                imgSound.setImageResource(soundImg[stan]);
                stan = 0;
            }
            super.onProgressUpdate(values);

        }

        @Override
        protected void onPostExecute(String result) {
            Log.i("INFO", "ParentActivity: Finished listening");
            bt_listen.setText(R.string.listen_to_baby);
            imgSound.setVisibility(View.INVISIBLE);
            txt_state.setText(R.string.txt_monitoring);
            Toast.makeText(getApplicationContext(),"ParentActivity: Listening finished ",Toast.LENGTH_SHORT).show();
        }
    }

    private class SendVideoRequest extends AsyncTask<Void,Void,String> {

        @Override
        protected void onPreExecute() {
            bt_monitor.setText(R.string.stop_watching);
            txt_state.setText(R.string.txt_watching);
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);

            videoView.setVisibility(View.VISIBLE);
            videoView.requestFocus();
            surfaceHolder = videoView.getHolder();
            mediaPlayer.setSurface(surfaceHolder.getSurface());
            mediaPlayer.setDisplay(surfaceHolder);
            isWatching = true;
        }

        @Override
        protected String doInBackground(Void... params) {
            Socket videoSocket = new Socket();
            DataInputStream dataInputStream = null;
            InputStream inputStream = null;
            try {
                videoSocket.connect((new InetSocketAddress(host,video_port)),5000);
                Log.i("INFO","Connection made receiving the video");
                while(isWatching) {
                    receivedVideo = new File("/storage/emulated/0/video_" + fileNo + ".mp4");
                    byte[] bytes = new byte[8192];
                    inputStream = videoSocket.getInputStream();
                    dataInputStream = new DataInputStream(inputStream);
                    FileOutputStream fileOutputStream;
                    long receivedFileSize = dataInputStream.readLong();
                    try {
                        fileOutputStream = new FileOutputStream(receivedVideo);
                        int length;
                        while (receivedFileSize > 0 && (length = dataInputStream.read(bytes, 0, (int) Math.min(bytes.length, receivedFileSize))) != -1) {
                            fileOutputStream.write(bytes, 0, length);
                            receivedFileSize -= length;
                        }
                        fileOutputStream.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    mediaPlayer.setDataSource(receivedVideo.getPath());
                    mediaPlayer.prepare();
                    mediaPlayer.start();

                    while(mediaPlayer.isPlaying());
                    mediaPlayer.stop();
                    mediaPlayer.reset();
                }
                Log.i("INFO", "ParentActivity: Finished watching. I want to close the connection");
                videoSocket.shutdownInput();
                videoSocket.shutdownOutput();
                videoSocket.close();
                Log.i("INFO", "ParentActivity: Closed socket connection");
            } catch (Exception e) {
                e.printStackTrace();
                isWatching = false;
            }
            return null;
        }
        @Override
        protected void onPostExecute(String result) {
            mediaPlayer = null;
            videoView.setVisibility(View.INVISIBLE);
            bt_monitor.setText(R.string.watch_on_baby);
            txt_state.setText(R.string.txt_monitoring);
            isWatching = false;


        }
    }

    private class SendMuteThread extends AsyncTask<Void,Void,String> {


        @Override
        protected String doInBackground(Void... voids) {

            Socket socket = new Socket();
            try {
                socket.connect((new InetSocketAddress(host, group_port)));
                Log.i("INFO", "ParentActivity: sending mute request to MonitorActivity");

                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        getIntentAction(intent);
        super.onNewIntent(intent);
    }

    private void getIntentAction (Intent intent) {
        if(intent.getAction() != null) {
            switch (intent.getAction()) {
                case MUTE_ACTION:
                    new SendMuteThread().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                    break;
                case LISTEN_ACTION:
                    new SendAudioRequest().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                    break;
                case WATCH_ACTION:
                    new SendVideoRequest().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                    break;
            }
        }
    }

    private Intent makeNotificationIntent() {
        Intent intent = new Intent(ParentActivity.this,ParentActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        return intent;
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();

    }

    @Override
    public void onSaveInstanceState(Bundle outState, PersistableBundle outPersistentState) {
        super.onSaveInstanceState(outState,outPersistentState);
        outState.clear();
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
    }

    @Override
    public void onBackPressed() {
        finishActivity(Activity.RESULT_CANCELED);
        super.onBackPressed();
    }
}
