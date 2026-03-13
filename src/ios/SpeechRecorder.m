#import "SpeechRecorder.h"

@implementation SpeechRecorder

- (void)voicerec_audio_start:(CDVInvokedUrlCommand*)command {
    self.callbackId = command.callbackId;
    double duration = [[command.arguments objectAtIndex:0] doubleValue];
    // Az iOS-nél a tail-cut bonyolultabb, itt most a teljes fájlt mentjük, 
    // de a hívás kompatibilis marad a JS-sel.

    [self.commandDelegate runInBackground:^{
        AVAudioSession *session = [AVAudioSession sharedInstance];
        NSError *error = nil;

        // Bluetooth támogatás bekapcsolása
        [session setCategory:AVAudioSessionCategoryPlayAndRecord 
                 withOptions:AVAudioSessionCategoryOptionAllowBluetooth | AVAudioSessionCategoryOptionDefaultToSpeaker 
                       error:&error];
        [session setMode:AVAudioSessionModeVoiceChat error:&error];
        [session setActive:YES error:&error];

        NSString *tempPath = [NSTemporaryDirectory() stringByAppendingPathComponent:@"voicerec_raw.wav"];
        NSURL *url = [NSURL fileURLWithPath:tempPath];

        // WAV formátum beállításai (16kHz, Mono, 16-bit PCM)
        NSDictionary *settings = @{
            AVFormatIDKey: @(kAudioFormatLinearPCM),
            AVSampleRateKey: @(16000.0),
            AVNumberOfChannelsKey: @(1),
            AVLinearPCMBitDepthKey: @(16),
            AVLinearPCMIsBigEndianKey: @(NO),
            AVLinearPCMIsFloatKey: @(NO)
        };

        self.recorder = [[AVAudioRecorder alloc] initWithURL:url settings:settings error:&error];
        self.recorder.delegate = self;

        if ([self.recorder record]) {
            CDVPluginResult* result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:@{@"status":@"recording_audio"}];
            [result setKeepCallbackAsBool:YES];
            [self.commandDelegate sendPluginResult:result callbackId:self.callbackId];

            // Automatikus stop időzítő
            dispatch_async(dispatch_get_main_queue(), ^{
                [self.timer invalidate];
                self.timer = [NSTimer scheduledTimerWithTimeInterval:duration target:self selector:@selector(autoStop) userInfo:nil repeats:NO];
            });
        } else {
            [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:error.localizedDescription] callbackId:self.callbackId];
        }
    }];
}

- (void)autoStop {
    [self stopAndSend:YES];
}

- (void)voicerec_audio_stop:(CDVInvokedUrlCommand*)command {
    self.callbackId = command.callbackId;
    [self stopAndSend:YES];
}

- (void)voicerec_audio_restart:(CDVInvokedUrlCommand*)command {
    [self.recorder stop];
    [self voicerec_audio_start:command];
}

- (void)stopAndSend:(BOOL)sendResult {
    [self.timer invalidate];
    [self.recorder stop];

    if (sendResult) {
        NSData *audioData = [NSData dataWithContentsOfURL:self.recorder.url];
        NSString *base64String = [audioData base64EncodedStringWithOptions:0];
        
        NSDictionary *res = @{
            @"uri": [self.recorder.url absoluteString],
            @"base64": base64String
        };
        [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:res] callbackId:self.callbackId];
    }
    
    // Alaphelyzetbe állítjuk a sessiont
    [[AVAudioSession sharedInstance] setActive:NO withOptions:AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation error:nil];
}

@end
