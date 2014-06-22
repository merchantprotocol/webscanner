package com.google.zxing.client.android;

/**
 * Created by michael on 22/06/14.
 */

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Base64;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.client.android.camera.CameraManager;
import com.google.zxing.common.HybridBinarizer;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import fi.iki.elonen.NanoHTTPD;

public class HttpServer extends Service {
    private static int notifyId = 1;
    private NotificationManager mNotificationManager;
    private Server htserver;

    public HttpServer() {

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId){
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (startServer()) {
            createNotification(null);
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy(){
        stopServer();
        removeNotification();
    }

    private boolean started = false;
    private boolean even = true;
    private void createNotification(String tickertxt){
        //removeNotification();
        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(android.R.drawable.ic_media_play)
                        .setContentTitle("Sever Running")
                        .setContentText("The Xzing barcode server is running!");
        PendingIntent pe = PendingIntent.getActivity(this, 0, new Intent(this, HttpServerUI.class), PendingIntent.FLAG_UPDATE_CURRENT);
        mBuilder.setContentIntent(pe);
        if (tickertxt!=null){
            mBuilder.setTicker(tickertxt+(even?"":" ")); // a hack to make ticker show each request even though text has not changed
            even = !even;
        }
        mBuilder.setOngoing(true);
        if (started) {
            mNotificationManager.notify(notifyId, mBuilder.build());
        } else {
            startForeground(notifyId, mBuilder.build());
            started = true;
        }
    }

    private void removeNotification(){
        mNotificationManager.cancel(notifyId);
    }

    private boolean startServer(){
        htserver = new Server();
        try {
            htserver.start();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void stopServer(){

        htserver.stop();
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    private class Server extends NanoHTTPD {

        private Camera camera;
        CameraManager cameraManager;
        private MultiFormatReader reader;
        private byte[] curbitmap = new byte[0];
        private String curbarcode = "";
        private String lastbarcode = "0";

        public Server(){

            super("0.0.0.0", 8080);
            try {
                cameraManager = new CameraManager(getApplication());
                cameraManager.openDriver(null);
                camera = cameraManager.getCamera();
                camera.startPreview();
                camera.autoFocus(focusCallback);
                //camera.getParameters().setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
            } catch (IOException e) {
                e.printStackTrace();
            }
            reader = new MultiFormatReader();
            System.out.println("Server on port 8080;");
        }

        @Override
        public void stop(){
            super.stop();
            cameraManager.closeDriver();
        }

        private Camera.AutoFocusCallback focusCallback = new Camera.AutoFocusCallback() {
            @Override
            public void onAutoFocus(boolean success, Camera cam) {
                System.out.println("Focused: " + success);
                if (success) {
                    camera.setOneShotPreviewCallback(new Camera.PreviewCallback() {
                        @Override
                        public void onPreviewFrame(byte[] data, Camera camera) {
                            onCameraFocus(data);
                        }
                    });

                }
                // request refocus
                camera.autoFocus(focusCallback);

            }
        };

        private void onCameraFocus(byte[] data){
            // try to decode
            PlanarYUVLuminanceSource source = cameraManager.buildLuminanceSource(data, camera.getParameters().getPreviewSize().width, camera.getParameters().getPreviewSize().height);
            BinaryBitmap binbitmap = new BinaryBitmap(new HybridBinarizer(source));
            String barcode;
            try {
                Result result = reader.decodeWithState(binbitmap);
                barcode = result.getText();
                lastbarcode = barcode;
                if(barcode.equals(curbarcode)) barcode="";
                System.out.println("Barcode decoded!");
            } catch (NotFoundException e) {
                barcode = "";
                e.printStackTrace();
            } finally {
                reader.reset();
            }
            curbarcode = barcode;
            savePreviewFrame(source);
        }

        private void savePreviewFrame(PlanarYUVLuminanceSource source){
            int[] pixels = source.renderThumbnail();
            int width = source.getThumbnailWidth();
            int height = source.getThumbnailHeight();
            Bitmap bitmap = Bitmap.createBitmap(pixels, 0, width, width, height, Bitmap.Config.ARGB_8888);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 50, baos);

            curbitmap = baos.toByteArray();
        }

        @Override
        public Response serve(IHTTPSession session) {
            Map<String, String> files = new HashMap<String, String>();
            Method method = session.getMethod();
            if (Method.PUT.equals(method) || Method.POST.equals(method)) {
                try {
                    session.parseBody(files);
                } catch (IOException ioe) {
                    return new Response(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "SERVER INTERNAL ERROR: IOException: " + ioe.getMessage());
                } catch (ResponseException re) {
                    return new Response(re.getStatus(), MIME_PLAINTEXT, re.getMessage());
                }
            }
            // start detecting barcode, on the next request we might get it
            camera.setOneShotPreviewCallback(new Camera.PreviewCallback() {
                @Override
                public void onPreviewFrame(byte[] data, Camera camera) {
                    // save bitmap thumb in jpeg
                    PlanarYUVLuminanceSource source = cameraManager.buildLuminanceSource(data, camera.getParameters().getPreviewSize().width, camera.getParameters().getPreviewSize().height);
                    savePreviewFrame(source);
                }
            });

            if (!curbarcode.equals("") && !curbarcode.equals(lastbarcode)){
                createNotification("New barcode detected");
            }
            // create json response
            String bytestring = Base64.encodeToString(curbitmap, Base64.DEFAULT);

            JSONObject jsobject = new JSONObject();
            try {
                jsobject.put("prevdata", bytestring);
                jsobject.put("barcode", curbarcode);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            //return super.serve(session);
            Response response = new Response(jsobject.toString());
            response.addHeader("Access-Control-Allow-Origin", "*");
            response.addHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS, DELETE");
            response.addHeader("Access-Control-Max-Age", "3600");
            response.addHeader("Access-Control-Allow-Headers", "x-requested-with");
            return response;
        }
    }

}
