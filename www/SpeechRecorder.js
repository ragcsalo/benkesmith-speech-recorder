var exec = require('cordova/exec');

module.exports = {
    startRecording: function(successCallback, errorCallback, timeout, lastSeconds) {
      var t = (timeout && timeout > 0) ? timeout : 600;0
      var ls = lastSeconds || 0;
      cordova.exec(successCallback, errorCallback, 'SpeechRecorder', 'voicerec_audio_start', [t, ls]);
    },

    restartRecording: function(successCallback, errorCallback) {
      cordova.exec(successCallback, errorCallback, 'SpeechRecorder', 'voicerec_audio_restart', []);
    },

    stopRecording: function(successCallback, errorCallback) {
      cordova.exec(successCallback, errorCallback, 'SpeechRecorder', 'voicerec_audio_stop', []);
    }
};
