package com.benkesmith.speechrecorder;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class SpeechRecorder extends CordovaPlugin {

    private static final String LOG_TAG = "SpeechRecorder";
    private AudioRecord audioRecord;
    private boolean isRecordingAudio = false;
    private File recordFile;
    private int sampleRate = 16000;
    private int bufferSize;
    private int lastSecondsToKeep = 0;
    private Handler audioTimeoutHandler = new Handler(Looper.getMainLooper());
    private Runnable audioTimeoutRunnable;
    private Activity activity;
    private AudioManager audioManager;

    @Override
    protected void pluginInitialize() {
        activity = cordova.getActivity();
        audioManager = (AudioManager) activity.getSystemService(Context.AUDIO_SERVICE);
        bufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if ("voicerec_audio_start".equals(action)) {
            long durationMs = args.optLong(0, 30) * 1000;
            this.lastSecondsToKeep = args.optInt(1, 0);
            startAudioRecording(durationMs, callbackContext);
            return true;
        }
        if ("voicerec_audio_restart".equals(action)) {
            stopAudioRecording(false, null);
            startAudioRecording(30000, callbackContext);
            return true;
        }
        if ("voicerec_audio_stop".equals(action)) {
            stopAudioRecording(true, callbackContext);
            return true;
        }
        return false;
    }

    @SuppressLint("MissingPermission")
    private void startAudioRecording(long durationMs, CallbackContext callbackContext) {
        if (!cordova.hasPermission(Manifest.permission.RECORD_AUDIO)) {
            cordova.requestPermission(this, 1, Manifest.permission.RECORD_AUDIO);
            return;
        }

        if (audioManager.isBluetoothScoAvailableOffCall()) {
            audioManager.startBluetoothSco();
            audioManager.setBluetoothScoOn(true);
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        }

        cordova.getThreadPool().execute(() -> {
            try {
                recordFile = new File(activity.getCacheDir(), "voicerec_raw.pcm");
                audioRecord = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
                audioRecord.startRecording();
                isRecordingAudio = true;
                new Thread(this::writePCMToFile).start();
                audioTimeoutRunnable = () -> stopAudioRecording(true, callbackContext);
                audioTimeoutHandler.postDelayed(audioTimeoutRunnable, durationMs);
                JSONObject res = new JSONObject();
                res.put("status", "recording_audio");
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, res));
            } catch (Exception e) { callbackContext.error(e.getMessage()); }
        });
    }

    private void writePCMToFile() {
        byte[] data = new byte[bufferSize];
        try (FileOutputStream os = new FileOutputStream(recordFile)) {
            while (isRecordingAudio) {
                int read = audioRecord.read(data, 0, bufferSize);
                if (read > 0) os.write(data, 0, read);
            }
        } catch (IOException e) { e.printStackTrace(); }
    }

    private void stopAudioRecording(boolean sendResult, CallbackContext callbackContext) {
        audioTimeoutHandler.removeCallbacks(audioTimeoutRunnable);
        isRecordingAudio = false;
        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
        }
        if (audioManager.isBluetoothScoOn()) {
            audioManager.stopBluetoothSco();
            audioManager.setBluetoothScoOn(false);
            audioManager.setMode(AudioManager.MODE_NORMAL);
        }
        if (sendResult && callbackContext != null) {
            File wavFile = new File(activity.getCacheDir(), "final_voicerec.wav");
            saveAsWav(recordFile, wavFile);
            try {
                JSONObject res = new JSONObject();
                res.put("base64", getBase64(wavFile));
                callbackContext.success(res);
            } catch (Exception e) { callbackContext.error(e.getMessage()); }
        }
    }

    private void saveAsWav(File pcm, File wav) {
        long bytesToKeep = (lastSecondsToKeep > 0) ? (lastSecondsToKeep * sampleRate * 2L) : pcm.length();
        long skip = Math.max(0, pcm.length() - bytesToKeep);
        try (FileInputStream in = new FileInputStream(pcm); FileOutputStream out = new FileOutputStream(wav)) {
            in.skip(skip);
            out.write(getWavHeader(Math.min(pcm.length(), bytesToKeep)));
            byte[] buf = new byte[bufferSize];
            int r;
            while ((r = in.read(buf)) != -1) out.write(buf, 0, r);
        } catch (IOException e) { e.printStackTrace(); }
    }

    private byte[] getWavHeader(long len) {
        byte[] h = new byte[44];
        long total = len + 36;
        h[0]='R'; h[1]='I'; h[2]='F'; h[3]='F';
        h[4]=(byte)(total & 0xff); h[5]=(byte)((total>>8)&0xff); h[6]=(byte)((total>>16)&0xff); h[7]=(byte)((total>>24)&0xff);
        h[8]='W'; h[9]='A'; h[10]='V'; h[11]='E'; h[12]='f'; h[13]='m'; h[14]='t'; h[15]=' ';
        h[16]=16; h[20]=1; h[22]=1; h[24]=(byte)(sampleRate&0xff); h[25]=(byte)((sampleRate>>8)&0xff);
        h[28]=(byte)((sampleRate*2)&0xff); h[29]=(byte)(((sampleRate*2)>>8)&0xff);
        h[32]=2; h[34]=16; h[36]='d'; h[37]='a'; h[38]='t'; h[39]='a';
        h[40]=(byte)(len&0xff); h[41]=(byte)((len>>8)&0xff); h[42]=(byte)((len>>16)&0xff); h[43]=(byte)((len>>24)&0xff);
        return h;
    }

    private String getBase64(File f) throws IOException {
        byte[] b = new byte[(int) f.length()];
        try (FileInputStream fis = new FileInputStream(f)) { fis.read(b); }
        return android.util.Base64.encodeToString(b, android.util.Base64.NO_WRAP);
    }
}