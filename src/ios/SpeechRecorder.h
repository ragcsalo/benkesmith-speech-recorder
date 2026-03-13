#import <Cordova/CDV.h>
#import <AVFoundation/AVFoundation.h>

@interface SpeechRecorder : CDVPlugin <AVAudioRecorderDelegate>

@property (nonatomic, strong) AVAudioRecorder *recorder;
@property (nonatomic, strong) NSString *callbackId;
@property (nonatomic, strong) NSTimer *timer;

- (void)voicerec_audio_start:(CDVInvokedUrlCommand*)command;
- (void)voicerec_audio_stop:(CDVInvokedUrlCommand*)command;
- (void)voicerec_audio_restart:(CDVInvokedUrlCommand*)command;

@end
