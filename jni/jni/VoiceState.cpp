#include <jni.h>

#include "speech.h"
#include "speak_lib.h"
#include "phoneme.h"
#include "synthesize.h"
#include "voice.h"

// Reuse the loaded translator/dictionary, but retain the normal voice-selection
// command that restores Wavegen's working timbre, echo and formant state.
extern "C" JNIEXPORT jboolean JNICALL
Java_com_reecedunn_espeak_SpeechSynthesis_nativeReapplyVoice(JNIEnv*, jobject)
{
    if (voice == NULL)
        return JNI_FALSE;
    // VoiceReset normally resets the breath resonators when loading both the
    // base voice and a variant. Preserve that reset without reopening files.
    InitBreath();
    const int previous_tail = wcmdq_tail;
    DoVoiceChange(voice);
    // DoVoiceChange can fail to allocate its small queued copy. A cache hit
    // must not claim that the reset happened if no command was queued.
    return wcmdq_tail != previous_tail ? JNI_TRUE : JNI_FALSE;
}
