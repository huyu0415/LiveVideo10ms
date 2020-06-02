package constantin.test;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;

// Service that transcodes all files passed with EXTRA
// Supports transcoding multiple files at the same time
// When canceled, all transcoding tasks are  canceled

public class TranscodeService extends Service {
    private static final String TAG="TranscodeService";
    public static final String EXTRA_START_TRANSCODING_FILE="EXTRA_START_TRANSCODING_FILE";
    //public static final String EXTRA_STOP_TRANSCODING_FILE="EXTRA_STOP_TRANSCODING_FILE";

    public static final String NOTIFICATION_CHANNEL_ID = "TranscodeServiceChannel";
    public static final int NOTIFICATION_ID=1;
    private final ArrayList<Thread> workerThreads=new ArrayList<>();


    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID,createNotification());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        final String startTranscoding = intent.getStringExtra(EXTRA_START_TRANSCODING_FILE);
        System.out.println("Extras "+startTranscoding);
        // Create a new Thread for this decoding task
        // on completion,also run a runnable on the main looper
        final Handler handler=new Handler(getMainLooper());
        final Thread worker=new Thread(new Runnable() {
            @Override
            public void run() {
                new SimpleTranscoder(startTranscoding).run();
                final Thread thisWorker=Thread.currentThread();
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        final boolean contained=workerThreads.remove(thisWorker);
                        System.out.println("Contained worker thread ?:"+contained);
                        // update the notification
                        NotificationManagerCompat.from(getApplication()).notify(NOTIFICATION_ID,createNotification());
                        if(workerThreads.isEmpty()){
                            stopSelf();
                        }
                    }
                });
            }
        });
        // Have to add thread to workers array before starting
        workerThreads.add(worker);
        worker.start();
        // update the notification
        NotificationManagerCompat.from(getApplication()).notify(NOTIFICATION_ID,createNotification());
        //
        return START_NOT_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "FPV-VR";
            String description = "Transcoder";
            NotificationChannel serviceChannel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, name,
                    NotificationManager.IMPORTANCE_DEFAULT);
            serviceChannel.setDescription(description);
            NotificationManagerCompat.from(getApplication()).createNotificationChannel(serviceChannel);
        }
    }

    private Notification createNotification(){
        Bitmap bmp=Bitmap.createBitmap(128,128,Bitmap.Config.RGB_565);
        bmp.eraseColor(Color.BLUE);
        StringBuilder text= new StringBuilder();
        for(int i=0;i<workerThreads.size();i++){
            text.append("Worker ").append(i);
        }
        /*Drawable icon=null;
        try {
            icon = getApplication().getPackageManager().getApplicationIcon("constantin.fpv_vr.wifibroadcast");
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        try {
            Resources resources=getApplication().getPackageManager().getResourcesForApplication("constantin.fpv_vr.wifibroadcast");
            resources.get
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }*/
        return new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("Transcoder Service")
                .setContentText(text.toString())
                .setSmallIcon(getApplicationInfo().icon)
                //.setLargeIcon(bmp)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setOnlyAlertOnce(true)
                .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG,"onDestroy1");
        for(final Thread worker:workerThreads){
            worker.interrupt();
        }
        for(final Thread worker:workerThreads){
            try{
                worker.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        Log.d(TAG,"onDestroy2");
        // Do not forget to delete the notification channel. This will also delete any notifications in this channel
        NotificationManagerCompat.from(getApplication()).deleteNotificationChannel(NOTIFICATION_CHANNEL_ID);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public static void startTranscoding(final Context context,final String filenamePath){
        Intent serviceIntent = new Intent(context, TranscodeService.class);
        serviceIntent.putExtra(TranscodeService.EXTRA_START_TRANSCODING_FILE, filenamePath);
        ContextCompat.startForegroundService(context, serviceIntent);
    }

}
