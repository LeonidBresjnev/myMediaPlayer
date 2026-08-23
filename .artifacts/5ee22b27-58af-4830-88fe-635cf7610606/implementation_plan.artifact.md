# Add Reverb Settings to Phone App

This plan adds a three-slider reverb setting (Balance, Feedback R, Gain g) to the phone application's sound settings. The reverb filter logic is ported from the provided `audioStarter` project and integrated into the native audio engine.

## User Review Required

> [!IMPORTANT]
> The reverb effect is implemented in the native layer and will be applied to all audio processed by the custom equalizer. The "R" and "g" parameters are mapped to Feedback and Gain multipliers respectively, as described in the reference project.

## Proposed Changes

### Native Audio Engine (C++)

#### [NEW] [Reverb.h](file:///C:/Users/simon/StudioProjects/myMediaPlayer/common/src/main/cpp/include/Reverb.h)
Port of the reverb filter structure from `audioStarter`. It will include `SingleReverbFilter` and `ReverbFilter` classes.

#### [NEW] [Reverb.cpp](file:///C:/Users/simon/StudioProjects/myMediaPlayer/common/src/main/cpp/Reverb.cpp)
Implementation of the reverb filter logic, including a local circular buffer implementation to avoid conflicts with the existing (size-3) `CircularBuffer`.

#### [MODIFY] [FilterSource.h](file:///C:/Users/simon/StudioProjects/myMediaPlayer/common/src/main/cpp/include/FilterSource.h)
- Add `ReverbFilter` instances for both channels.
- Add atomic floats for `reverbBalance`, `reverbR`, and `reverbG`.
- Declare `setReverbParams`.

#### [MODIFY] [FilterSource.cpp](file:///C:/Users/simon/StudioProjects/myMediaPlayer/common/src/main/cpp/FilterSource.cpp)
- Initialize `ReverbFilter` instances.
- Update `getSample()` to apply the reverb filter after the equalizer bands and before the final output.
- Implement `setReverbParams` to update the multipliers and balance.

#### [MODIFY] [Equalizer.h](file:///C:/Users/simon/StudioProjects/myMediaPlayer/common/src/main/cpp/include/Equalizer.h)
- Add `setReverbParams` to the `Equalizer` class.

#### [MODIFY] [Equalizer.cpp](file:///C:/Users/simon/StudioProjects/myMediaPlayer/common/src/main/cpp/Equalizer.cpp)
- Implement `setReverbParams` to delegate to `FilterSource`.
- Add the JNI implementation for `nativeSetReverbParams`.

---

### Common Kotlin Module

#### [MODIFY] [Equalizer.kt](file:///C:/Users/simon/StudioProjects/myMediaPlayer/common/src/main/java/com/equalizer/common/Equalizer.kt)
- Add `nativeSetReverbParams` external function.
- Add `setReverbParams` helper method.

#### [MODIFY] [MyMediaService.kt](file:///C:/Users/simon/StudioProjects/myMediaPlayer/common/src/main/java/com/equalizer/common/MyMediaService.kt)
- Add a custom command handler for `setReverbParams`.

---

### Phone App Module

#### [MODIFY] [AudioModel.kt](file:///C:/Users/simon/StudioProjects/myMediaPlayer/app/src/main/java/com/equalizer/mymediaplayer/AudioModel.kt)
- Add LiveData for `reverbBalance`, `reverbR`, and `reverbG`.
- Implement `setReverbParams` to send custom commands to the media service.

#### [MODIFY] [equalizerScreen.kt](file:///C:/Users/simon/StudioProjects/myMediaPlayer/app/src/main/java/com/equalizer/mymediaplayer/equalizerScreen.kt)
- Add a new "Reverb Settings" section with three sliders (Balance, R, g) ranging from 0.0 to 1.0.

## Verification Plan

### Automated Tests
- Build the project to ensure no C++ or Kotlin compilation errors.

### Manual Verification
- Deploy the app to a phone.
- Navigate to the Equalizer screen.
- Adjust the Reverb sliders and verify that the sound changes accordingly.
- Ensure that the "Balance" slider correctly mixes dry and wet signals (0.0 = no reverb, 1.0 = fully wet).
