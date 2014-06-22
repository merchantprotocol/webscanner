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
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.Bundle;
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
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
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
        if (startRelay()) {
            createNotification(null);
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy(){
        stopRelay();
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
                        .setContentText("The Socket relay is running!");
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

    private boolean startRelay(){
        htserver = new Server();
        try {
            htserver.start();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void stopRelay(){
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
        private byte[] curbitmap;
        private String curbarcode = "";

        public Server(){

            super("0.0.0.0", 8080);
            try {
                cameraManager = new CameraManager(getApplication());
                cameraManager.openDriver(null);
                camera = cameraManager.getCamera();
                camera.getParameters().set("orientation", -90);
                camera.startPreview();
            } catch (IOException e) {
                e.printStackTrace();
            }
            reader = new MultiFormatReader();
            System.out.println("Server on port 8080;");
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
                    // try to decode
                    PlanarYUVLuminanceSource source = cameraManager.buildLuminanceSource(data, camera.getParameters().getPreviewSize().width, camera.getParameters().getPreviewSize().height);
                    BinaryBitmap binbitmap = new BinaryBitmap(new HybridBinarizer(source));
                    try {
                        Result result = reader.decodeWithState(binbitmap);
                        curbarcode = result.getText();
                        System.out.println("Barcode decoded!");
                    } catch (NotFoundException e) {
                        e.printStackTrace();
                    } finally {
                        reader.reset();
                    }
                    // save bitmap in jpeg
                    int[] pixels = source.renderThumbnail();
                    int width = source.getThumbnailWidth();
                    int height = source.getThumbnailHeight();
                    Bitmap bitmap = Bitmap.createBitmap(pixels, 0, width, width, height, Bitmap.Config.ARGB_8888);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 75, baos);

                    curbitmap = baos.toByteArray();
                }
            });

            if (!curbarcode.equals("")){
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
