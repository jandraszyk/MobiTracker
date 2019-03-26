package com.example.janek.wifidirect;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.hardware.Camera;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.widget.VideoView;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

public class MonitorActivity extends Activity {

    /**
     * Port numbers and Client's IP address needed for transmitting data
     */
    public static final String GROUP_PORT = "port";
    public static final String AUDIO_PORT = "audio";
    public static final String VIDEO_PORT = "video";
    private String client;
    private int group_port, audio_port, video_port;

    /**
     * Info for streaming audio
     */
    private static final int SAMPLE_RATE = 8000; //Hertz
    private static final int INTERVAL = 20; //ms
    private static final int BUFFER_SIZE = 8192; //Bytes

    /**
     * State flags
     */
    private boolean isMonitoring = false;
    private boolean isWatching = false;
    private boolean sentNotification = false;
    private boolean isPaired = false;
    private final String WATCH_DOG = "PAUSE";

    /**
     * Visual assets
     */
    private ProgressBar progressBar;
    private Button bt_start, bt_video;

    /**
     * Variables for streaming video
     */
    private MediaRecorder mediaRecorder;
    private Camera camera;
    private SurfaceHolder surfaceHolder;
    private Integer fileNo = 0;
    private Queue<File> videoFileList;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_monitor);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        Intent receivedInfo = getIntent();
        group_port = receivedInfo.getExtras().getInt(GROUP_PORT);
        audio_port = receivedInfo.getExtras().getInt(AUDIO_PORT);
        video_port = receivedInfo.getExtras().getInt(VIDEO_PORT);

        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        bt_start = (Button) findViewById(R.id.bt_stop);
        bt_video = (Button) findViewById(R.id.bt_video);
        VideoView surfaceView = (VideoView) findViewById(R.id.surfaceView);

        surfaceHolder = surfaceView.getHolder();

        videoFileList = new ArrayBlockingQueue<>(5);


        if(!isPaired) {
            new PairDevices().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            Log.i("INFO", "MonitorActivity: Started pairing");
        }

        bt_start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!isMonitoring) {
                    isMonitoring = true;
                    sentNotification = false;
                    new MonitoringSoundThread().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR); // Thread for monitoring the noise in the room
                } else {
                    progressBar.setVisibility(ProgressBar.INVISIBLE);
                    bt_start.setText(R.string.start);
                    isMonitoring = false;
                }
            }
        });

       bt_video.setOnClickListener(new View.OnClickListener() {
           @Override
           public void onClick(View v) {
               if(!isWatching) {
                   isWatching = true;
                   new SendVideoThread().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
               }
               else {
                   isWatching = false;
                   bt_video.setText(R.string.start_recording);
               }
           }
       });


    }

    /**
     * Thread for listening for noise
     */
    private class MonitoringSoundThread extends AsyncTask<Void, Void, String> {

        @Override
        protected void onPreExecute() {
            progressBar.setVisibility(ProgressBar.VISIBLE);
            bt_start.setText(R.string.stop);

        }
        @Override
        protected String doInBackground(Void... params) {
            Log.i("INFO","MonitorActivity: Started monitoring");
            try {
                boolean recorder = true;
                int minSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)*10;
                AudioRecord ar = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,minSize);
                short[] buffer = new short[BUFFER_SIZE];

                Socket socket = new Socket();
                ar.startRecording();
                while(recorder && isMonitoring && !sentNotification) {
                    ar.read(buffer, 0, BUFFER_SIZE);
                    for(short s : buffer) {
                        if(Math.abs(s) >= 20000)
                        {
                            ar.stop();
                            recorder = false;
                            if(!socket.isClosed()) {
                                try {
                                    socket.connect((new InetSocketAddress(client, group_port)), 5000);
                                    Log.i("INFO", "MonitorActivity: Connected to the server");
                                    sentNotification = true;

                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                                try {
                                    socket.close();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }
                }
                ar.stop();
                ar.release();
                if(sentNotification) {
                    synchronized (WATCH_DOG) {
                        try {
                            Log.i("INFO", "MonitorActivity: Waiting for the task to continue");
                            WATCH_DOG.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        sentNotification = false;
                        Log.i("INFO","MonitorActivity: Resumed the task");
                    }
                }

                try {
                    socket.close();
                    Log.i("INFO","MonitorActivity: Closed the socket");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(String result){
            Log.i("INFO", "MonitorActivity: Sent notification to parent");
            if(isMonitoring) {
                Log.i("INFO","MonitorActivity: Continue listening for noise");
                new MonitoringSoundThread().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            }
        }
    }

    /**
     * Thread for pairing the devices (should be run once)
     */
    private class PairDevices extends AsyncTask<Void, Void, String> {

        Socket receivedIp = null;

        @Override
        protected String doInBackground(Void... params) {
            try {
                ServerSocket serverSocket = new ServerSocket(group_port);
                Log.i("INFO","MonitorActivity: Socket opened");
                receivedIp = serverSocket.accept();
                Log.i("INFO","MonitorActivity: Connection made");
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(1024);
                byte[] buffer = new byte[1024];
                int byteRead;
                InputStream inputStream = receivedIp.getInputStream();
                while ((byteRead = inputStream.read(buffer)) != -1) {
                    byteArrayOutputStream.write(buffer, 0, byteRead);
                    client = byteArrayOutputStream.toString("UTF-8");
                    Log.i("INFO","Client's IP: " + client);
                }
                if(serverSocket != null) {
                    try {
                        serverSocket.close();
                        Log.i("INFO", "MonitorActivity: Closed server socket after pairing");
                    } catch (IOException e ) {
                        e.printStackTrace();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if(receivedIp != null) {
                    try {
                        receivedIp.close();
                        Log.i("INFO", "MonitorActivity: Closed connection after pairing devices");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            return null;
        }
        @Override
        protected void onPostExecute(String result) {
            isPaired = true;
            new SendSoundThread().executeOnExecutor(THREAD_POOL_EXECUTOR);
            new SendVideoThread().executeOnExecutor(THREAD_POOL_EXECUTOR);
            new ReceiveMuteThread().executeOnExecutor(THREAD_POOL_EXECUTOR);
        }
    }

    /**
     * Thread for sending an audio on request
     */
    private class SendSoundThread extends AsyncTask<Void,Void,String> {

        @Override
        protected String doInBackground(Void... params) {
            try {
                boolean recorder = true;
                ServerSocket serverAudioSocket = new ServerSocket(audio_port);
                Log.i("INFO","MonitorActivity: Waiting for request");
                Socket socket = serverAudioSocket.accept();
                sentNotification = true;
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e ) {
                    Log.i("INFO", "Thread was interrupted: " + e.getMessage());
                }
                int minSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)*10;
                AudioRecord ar = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,minSize);
                byte[] buffer = new byte[BUFFER_SIZE];

                Log.i("INFO","MonitorActivity: Connection with ParentActivity made");
                ar.startRecording();
                while(recorder) {
                    int readSize = ar.read(buffer,0,BUFFER_SIZE);
                    try {
                        socket.getOutputStream().write(buffer, 0, readSize);
                        Thread.sleep(INTERVAL,0);
                    } catch (Exception e) {
                        Log.i("INFO", "MonitorActivity: Exception was thrown during writing to the stream: " + e.getMessage());
                        recorder = false;
                        e.printStackTrace();
                    }
                }
                ar.stop();
                ar.release();
                try {
                    serverAudioSocket.close();
                    socket.close();
                    Log.i("INFO","MonitorActivity: Closed the socket");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } catch (Exception e) {
                Log.i("INFO","MonitorActivity: Something went wrong while connecting to audio_port: " + e.getMessage());
            }
            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            Log.i("INFO", "MonitorActivity: Finished sending the audio to the ParentActivity");
            if(sentNotification)
                wakeUp();
            new SendSoundThread().executeOnExecutor(THREAD_POOL_EXECUTOR);
        }
    }

    private class SendVideoThread extends AsyncTask <Void,Void,String> {

        @Override
        protected void onPreExecute() {
            bt_video.setText(R.string.stop_recording);
            isWatching = true;
        }

        @Override
        protected String doInBackground(Void... params) {

            try {
                ServerSocket videoSocket = new ServerSocket(video_port);
                Log.i("INFO", "MonitorActivity: Waiting for a request from ParentActivity");
                Socket video = videoSocket.accept();
                Log.i("INFO", "MonitorActivity: Connected and ready to send the video");
                isWatching = true;
                sentNotification = true;
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e ) {

                }
                try {
                    initRecorder(surfaceHolder.getSurface());
                } catch (IOException e) {
                    e.printStackTrace();
                }

                while (isWatching)  {
                    if(videoFileList.size() > 1)  {
                        byte[] bytes = new byte[8192];
                        FileInputStream inputStream = null;
                        DataOutputStream dataOutputStream = new DataOutputStream(video.getOutputStream());
                        long fileSize = videoFileList.element().length();
                        Log.i("INFO", "Sending video: " + videoFileList.element().getAbsolutePath());
                        try {
                            inputStream = new FileInputStream(videoFileList.remove().getPath());
                            int length = 0;
                            //First send file size to receiver
                            dataOutputStream.writeLong(fileSize);
                            //Send actual data
                            while ((length = inputStream.read(bytes, 0, bytes.length)) != -1) {
                                try {
                                    video.getOutputStream().write(bytes, 0, length);
                                } catch (Exception e) {
                                    Log.i("INFO", "MonitorActivity: Connection shut down");
                                    isWatching = false;
                                }
                            }
                            dataOutputStream.flush();
                        } catch (Exception e) {
                            e.getMessage();
                        } finally {
                            if (inputStream != null) {
                                inputStream.close();
                            }
                        }
                    }
                }
                videoSocket.close();
                video.close();

            } catch (IOException e) {
                e.printStackTrace();
                Log.i("INFO", "MonitorActivity: SOMETHING WENT WRONG WHILE CONNECTING TO VIDEO SERVER");
            }
            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            if (mediaRecorder != null) {
                shutdown();
            }
            Toast.makeText(getApplicationContext(),"MonitorActivity: Finished sending video",Toast.LENGTH_SHORT).show();
            videoFileList.clear();
            fileNo = 0;
            if(sentNotification)
                wakeUp();
            new SendVideoThread().executeOnExecutor(THREAD_POOL_EXECUTOR);
        }
    }

    private class ReceiveMuteThread extends AsyncTask <Void,Void,String> {


        @Override
        protected String doInBackground(Void... voids) {
            try {
                ServerSocket muteSocket = new ServerSocket(group_port);
                Log.i("INFO","MonitorActivity: waiting for mute request");
                Socket socket = muteSocket.accept();
                Log.i("INFO", "MonitorActivity: received mute request from ParentActivity");

                if(sentNotification) {
                    wakeUp();

                }
                socket.close();
                muteSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            Log.i("INFO", "MonitorActivity: Resumed monitoring");
            new ReceiveMuteThread().executeOnExecutor(THREAD_POOL_EXECUTOR);
        }
    }

    public void wakeUp() {
        synchronized(WATCH_DOG){
            WATCH_DOG.notify();
        }
    }

    private void initRecorder(Surface surface) throws IOException {
        if(camera == null) {
            camera = Camera.open();
            Camera.Parameters parameters = camera.getParameters();
            parameters.setRotation(90);
            camera.setDisplayOrientation(90);
            camera.setParameters(parameters);
            camera.unlock();
        }

        if(mediaRecorder == null) {
            mediaRecorder = new MediaRecorder();
        }
        mediaRecorder.setPreviewDisplay(surface);
        mediaRecorder.setCamera(camera);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        String filePath = "/storage/emulated/0/video_" + fileNo + ".mp4";
        File videoFile = new File(filePath);
        videoFileList.add(videoFile);
        Log.i("INFO", "Current array size: " + videoFileList.size());
        mediaRecorder.setOutputFile(videoFile.getPath());
        mediaRecorder.setMaxDuration(5000);
        mediaRecorder.setVideoFrameRate(30);
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
        mediaRecorder.setOnInfoListener(new MediaRecorder.OnInfoListener() {
            @Override
            public void onInfo(MediaRecorder mediaRecorder, int i, int i1) {
                if(i == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                    Log.i("INFO", "MonitorActivity: Stopped recording");
                    mediaRecorder.stop();
                    shutdown();
                    if(isWatching) {
                        try {
                            initRecorder(surfaceHolder.getSurface());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        });

        try {
            if (isWatching) {
                mediaRecorder.prepare();
                mediaRecorder.start();
            }
        } catch (IllegalStateException e) {
            e.getMessage();
            isWatching = false;
        }
    }
    private void shutdown() {
        mediaRecorder.reset();
        mediaRecorder.release();
        camera.lock();
        camera.release();
        mediaRecorder = null;
        camera = null;
        if(fileNo >= 4) {
            fileNo = 0;
        } else {
            fileNo++;
        }
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
        super.onDestroy();
        finishActivity(Activity.RESULT_CANCELED);
        super.onBackPressed();
    }


}
